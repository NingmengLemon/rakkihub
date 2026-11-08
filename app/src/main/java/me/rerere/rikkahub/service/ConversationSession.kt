package me.rerere.rikkahub.service

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishReasoning
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.selectBranch
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

private const val TAG = "ConversationSession"
private const val IDLE_TIMEOUT_MS = 5_000L

/** 会话的持久化，由 ChatService 实现 */
interface ConversationStore {
    /**
     * 落库前对会话的补全（比如把助手配置固定到会话上），结果同样写回内存。
     * 返回的变换在最新状态上原子执行，不能挂起。
     */
    suspend fun prepareWrite(): (Conversation) -> Conversation

    /** [previous] 是上次落库或加载时的状态，从未落库的新会话为 null */
    suspend fun write(previous: Conversation?, current: Conversation)

    /** 只写一个节点的 select_index，避免重新编码消息；返回受影响行数 */
    suspend fun updateNodeSelectIndex(conversationId: Uuid, nodeId: Uuid, selectIndex: Int): Int

    /** 只写一个消息节点，返回落库 JSON 的字符数 */
    suspend fun persistNode(conversationId: Uuid, nodeIndex: Int, node: MessageNode): Long

    /** 只刷新会话的 update_at，返回受影响行数 */
    suspend fun touchConversationUpdateAt(conversationId: Uuid, updateAt: Instant): Int
}

/**
 * 一个会话在内存中的状态，加载之后它就是唯一的权威：所有修改都在最新状态上做变换，
 * 落库时写入内存此刻的内容，不接受外部传入的整份快照，数据库里的旧版本也不会再读回来覆盖内存。
 */
class ConversationSession(
    val id: Uuid,
    initial: Conversation,
    private val scope: CoroutineScope,
    private val store: ConversationStore,
    private val onIdle: (Uuid) -> Unit,
    private val onGenerationFinished: (Uuid, Throwable?) -> Unit = { _, _ -> },
) {
    // 会话状态
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<Conversation> = _state.asStateFlow()
    private val initializationMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    var isInitialized = false
        private set

    // 上次落库、加载或新建时的状态，内存还是这个实例就说明没有要落库的修改；只在 writeMutex 内修改
    private var lastWritten: Conversation = initial

    // 最近一次成功写入后，库里每个节点实际的消息数；只在 writeMutex 内读写。
    // 分支切换的 select_index 守卫只信这张表，避免依赖内存或 lastWritten 的旧分支数量。
    private val persistedMessageCounts = mutableMapOf<Uuid, Int>()

    @Volatile
    var existsInDatabase = false
        private set

    // 压缩轮次进行中：区别于普通生成，普通生成允许切分支，压缩期间不允许
    private val _compressionActive = MutableStateFlow(false)

    internal val isCompressing: Boolean get() = _compressionActive.value

    internal fun setCompressionActive(active: Boolean) {
        _compressionActive.value = active
    }

    // 每次 [replace] 加一：跨挂起点的操作据此发现会话已经被删除或整条换掉，不再把结果写进去
    @Volatile
    var incarnation = 0
        private set

    // 会话正在被整条替换/删除：压缩轮次被取消时不弹「压缩已取消」，由删除流程自己处理
    @Volatile
    var isClosing = false

    val messageQueue = MessageQueue()

    /**
     * 页面切换和 SSE 重连只加载一次。[load] 返回 null 表示数据库里没有这个会话，改用 [create] 新建。
     * 加载完成前会话不接受修改，所以不会出现加载结果覆盖新内容的情况。
     */
    suspend fun initialize(
        load: suspend () -> Conversation?,
        create: suspend () -> Conversation = { throw IllegalStateException("Conversation $id not found") },
    ) {
        initializationMutex.withLock {
            if (isInitialized) return
            val loaded = load()
            val conversation = loaded ?: create()
            require(conversation.id == id)
            writeMutex.withLock {
                lastWritten = conversation
                existsInDatabase = loaded != null
                rebuildPersistedCounts(conversation)
            }
            synchronized(this) {
                _state.value = conversation
                isInitialized = true
            }
        }
    }

    /** 在最新状态上修改会话，只改内存；需要落库时之后调用 [flush] */
    fun update(transform: (Conversation) -> Conversation): Conversation = checkNotNull(tryUpdate(transform))

    /** 同 [update]，[transform] 返回 null 表示没什么可改的，这时不修改并返回 null */
    @Synchronized
    fun tryUpdate(transform: (Conversation) -> Conversation?): Conversation? {
        check(isInitialized) { "Conversation $id is not loaded yet" }
        val updated = transform(_state.value) ?: return null
        require(updated.id == id)
        _state.value = updated
        return updated
    }

    /**
     * 在写锁内校验 incarnation，对最新状态做一次变换并立即整份落库；会话被替换或还没加载时返回 null。
     * 压缩成功后的「插检查点 + 删提示词/回复」用它保证一次写完。
     */
    internal suspend fun flushWith(
        expectedIncarnation: Int,
        transform: (Conversation) -> Conversation,
    ): Conversation? {
        return writeMutex.withLock {
            if (!isInitialized || incarnation != expectedIncarnation) return@withLock null
            update(transform)
            val current = update(store.prepareWrite())
            store.write(previous = lastWritten.takeIf { existsInDatabase }, current = current)
            lastWritten = current
            existsInDatabase = true
            rebuildPersistedCounts(current)
            current
        }
    }

    /**
     * 把内存此刻的状态落库。落库串行执行，每次都读取最新状态，后执行的一定带着更新的内容；
     * 和上次落库相比没有修改，或者是还没内容的新会话时不写。
     */
    suspend fun flush(): Conversation {
        if (!isInitialized) return state.value
        return writeMutex.withLock {
            val latest = state.value
            if (latest === lastWritten) return@withLock latest
            // 还没内容的新会话不落库，也不能提前固定配置：会话开始前聊天页的修改要写回助手
            if (!existsInDatabase && latest.title.isBlank() && latest.messageNodes.isEmpty()) {
                return@withLock latest
            }
            val current = update(store.prepareWrite())
            store.write(previous = lastWritten.takeIf { existsInDatabase }, current = current)
            lastWritten = current
            existsInDatabase = true
            rebuildPersistedCounts(current)
            current
        }
    }

    /**
     * 只修改还没落库的新会话，只改内存，首次落库时一并写入；会话已经落库时不修改，返回 false。
     * 在 [flush] 的锁内判断，不会和首次落库交错
     */
    internal suspend fun updateDraft(transform: (Conversation) -> Conversation): Boolean {
        return writeMutex.withLock {
            if (existsInDatabase) return@withLock false
            update(transform)
            true
        }
    }

    /**
     * 只更新部分列的元数据：改内存，再按列落库，不重写消息节点。
     * 修改和落库都在 [flush] 的锁内进行，落库的先后与内存修改的先后一致。
     *
     * @param expectedIncarnation 跨挂起点的操作在开始时记下的 [incarnation]。[replace] 在同一把锁内递增它，
     * 所以在锁内比较才可靠：会话在这期间被删除或整条换掉时不写入，返回 false
     */
    internal suspend fun updateMetadata(
        transform: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
        expectedIncarnation: Int = incarnation,
    ): Boolean {
        return writeMutex.withLock {
            if (incarnation != expectedIncarnation) return@withLock false
            val (before, updated) = synchronized(this) {
                val before = _state.value
                before to update(transform)
            }
            persist(updated)
            // 改之前内存和数据库一致，按列写完两边又一致了，回收时不必整份重写；
            // 之后如果又有别的修改，内存已经不是 updated 这个实例，仍然算作没落库
            if (existsInDatabase && lastWritten === before) lastWritten = updated
            true
        }
    }

    /**
     * 切换分支：只改内存；只有最近一次成功写入后的持久化计数显示该节点已有目标下标时，才单独写 select_index。
     *
     * 节点行可能比内存旧（生成中的新分支还没落库），这时只写列会让库里的下标超出消息数量、
     * 下次加载直接失败；这种情况留给下一次全量 [flush] 把消息和下标一起写。
     *
     * 在受支持的写入路径下只保证节点消息与 `select_index` 的对应；外部直接改库（手工改库等）不在保证范围内。
     *
     * @param expectedIncarnation 调用方进入时记录的 [incarnation]；会话被替换或删除后不写
     * @return 是否改了内存
     */
    internal suspend fun selectMessageNode(
        nodeId: Uuid,
        selectIndex: Int,
        expectedIncarnation: Int,
    ): Boolean {
        return writeMutex.withLock {
            if (!isInitialized || incarnation != expectedIncarnation) return@withLock false
            val selection = synchronized(this) {
                val before = _state.value
                val node = before.messageNodes.firstOrNull { it.id == nodeId } ?: return@synchronized null
                if (selectIndex !in node.messages.indices || node.selectIndex == selectIndex) {
                    return@synchronized null
                }
                val canWriteColumn = existsInDatabase &&
                    selectIndex < (persistedMessageCounts[nodeId] ?: 0)
                val updated = before.selectBranch(nodeId, selectIndex)
                _state.value = updated
                Triple(before, updated, canWriteColumn)
            } ?: return@withLock false
            val (before, updated, canWriteColumn) = selection
            if (canWriteColumn) {
                val affected = store.updateNodeSelectIndex(id, nodeId, selectIndex)
                if (affected == 0) {
                    Log.w(TAG, "updateNodeSelectIndex matched no row: node=$nodeId")
                }
                // 只改了 select_index 且写成功，内存和库仍一致；有别的未落库修改时不推进
                if (affected > 0 && lastWritten === before) lastWritten = updated
            }
            true
        }
    }

    /**
     * 只写当前回复节点，供生成期间的定期保存使用。
     *
     * 允许两种情况：节点在上次落库的状态里且下标一致（重新生成分支）；节点不在、当前下标等于
     * 上次落库的节点数且前面的节点 id 序列一致（正常的末尾追加）。其它结构性漂移一律跳过，
     * 等下一次全量 [flush] 校正。
     *
     * 保存副本先 [finishReasoning]；含 base64 图片的节点跳过，避免落库一份要等生成结束才转换的行。
     * 不推进 [lastWritten]，只更新该节点的持久化计数。
     *
     * @param expectedIncarnation 调用方在 turn 开始时记录的 [incarnation]
     * @return 落库 JSON 的字符数；跳过返回 null
     */
    internal suspend fun persistMessageNode(nodeId: Uuid, expectedIncarnation: Int): Long? {
        return writeMutex.withLock {
            if (!isInitialized || incarnation != expectedIncarnation || !existsInDatabase) {
                return@withLock null
            }
            val target = synchronized(this) {
                val conversation = _state.value
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index == -1) return@synchronized null
                val writtenIndex = lastWritten.messageNodes.indexOfFirst { it.id == nodeId }
                val allowed = if (writtenIndex >= 0) {
                    writtenIndex == index
                } else {
                    index == lastWritten.messageNodes.size &&
                        conversation.messageNodes.take(index).map { it.id } ==
                        lastWritten.messageNodes.map { it.id }
                }
                if (!allowed) return@synchronized null
                val node = conversation.messageNodes[index]
                val copy = node.copy(messages = node.messages.map { it.finishReasoning() })
                if (copy.messages.any { message -> message.parts.any { it.isBase64Image() } }) {
                    return@synchronized null
                }
                index to copy
            } ?: return@withLock null
            val (index, node) = target
            val storedChars = store.persistNode(id, index, node)
            persistedMessageCounts[nodeId] = node.messages.size
            storedChars
        }
    }

    private fun rebuildPersistedCounts(conversation: Conversation) {
        persistedMessageCounts.clear()
        conversation.messageNodes.forEach { persistedMessageCounts[it.id] = it.messages.size }
    }

    /**
     * 刷新会话的 update_at：内存和列一起改，只在内存与库仍一致时推进 [lastWritten]。
     * 生成期间每轮第一次单节点写成功后调用一次。
     */
    internal suspend fun touchUpdateAt(expectedIncarnation: Int): Boolean {
        return writeMutex.withLock {
            if (!isInitialized || incarnation != expectedIncarnation || !existsInDatabase) {
                return@withLock false
            }
            val (before, updated) = synchronized(this) {
                val before = _state.value
                val updated = before.copy(updateAt = Instant.now())
                _state.value = updated
                before to updated
            }
            val affected = store.touchConversationUpdateAt(id, updated.updateAt)
            if (affected > 0 && lastWritten === before) lastWritten = updated
            affected > 0
        }
    }

    /** 定期保存专用：递归检查工具输出里的 base64 图片，不改 UIMessage.hasBase64Part 的顶层语义 */
    private fun UIMessagePart.isBase64Image(): Boolean = when (this) {
        is UIMessagePart.Image -> url.startsWith("data:")
        is UIMessagePart.Tool -> output.any { it.isBase64Image() }
        else -> false
    }

    /**
     * 整条替换会话（删除、撤销删除）：先停止并等待所有任务，在锁内改写数据库，再把内存换成与之一致的状态。
     * 页面和 SSE 仍然持有同一个会话对象，直接看到新状态，引用计数也不受影响。
     *
     * @param write 改写数据库；参数是替换前的内存状态，会话还没加载时为 null
     */
    internal suspend fun replace(
        conversation: Conversation,
        existsInDatabase: Boolean,
        write: suspend (previous: Conversation?) -> Unit,
    ) {
        require(conversation.id == id)
        isClosing = true
        cancelJobs().forEach { it.join() }
        // 正在进行的加载结束后才替换，否则加载结果会盖掉替换后的状态
        initializationMutex.withLock {
            writeMutex.withLock {
                write(state.value.takeIf { isInitialized })
                synchronized(this) {
                    _state.value = conversation
                    isInitialized = true
                    incarnation++
                }
                lastWritten = conversation
                this.existsInDatabase = existsInDatabase
                if (existsInDatabase) {
                    rebuildPersistedCounts(conversation)
                } else {
                    persistedMessageCounts.clear()
                }
            }
        }
        processingStatus.value = null
        replyMessageId.value = null
        generationStartedAt.value = null
        _compressionActive.value = false
        isClosing = false
    }

    // 失败和取消也必须保存已收到的内容，且保存完成前不能释放生成任务。
    suspend fun finishGeneration(): Conversation = withContext(NonCancellable) {
        update { current ->
            current.copy(
                messageNodes = current.messageNodes.map { node ->
                    node.copy(messages = node.messages.map { it.finishReasoning() })
                },
                updateAt = Instant.now(),
            )
        }
        flush()
    }

    // 从队列取出到写入会话历史之间，附件仍需作为有效引用保留。
    @Volatile
    var submittingMessage: QueuedMessage? = null
        internal set

    // 原子引用计数
    private val refCount = AtomicInteger(0)

    // 处理状态（如 OCR 识别中）
    val processingStatus = MutableStateFlow<String?>(null)

    // 最近一轮正在写的助手消息，这一轮还没有输出时为空。重新生成较早的消息时它不在会话末尾，
    // 生成期间那个节点还可能被切到别的分支，只能按 id 找
    val replyMessageId = MutableStateFlow<Uuid?>(null)

    // 最近一次生成任务开始的时间，用来认出这一轮报的错。在任务启动前同步记下，任务里报的错不会早于它
    val generationStartedAt = MutableStateFlow<Long?>(null)

    // 生成任务（内聚在 session 中）
    private val _generationJob = MutableStateFlow<Job?>(null)
    private val activeJobs = mutableSetOf<Job>()
    val generationJob: StateFlow<Job?> = _generationJob.asStateFlow()
    val isGenerating: Boolean get() = _generationJob.value?.isActive == true
    val isInUse: Boolean
        get() = refCount.get() > 0 || _generationJob.value != null ||
            messageQueue.state.value.messages.isNotEmpty()

    // 空闲检查任务
    private var idleCheckJob: Job? = null

    internal fun acquire(): Int = refCount.incrementAndGet().also {
        cancelIdleCheck()
    }

    internal fun release(): Int = refCount.decrementAndGet().also {
        if (it <= 0) scheduleIdleCheck()
    }

    @Synchronized
    fun setJob(job: Job?, cancelPrevious: Boolean = true) {
        val previous = _generationJob.value
        if (job != null) generationStartedAt.value = System.currentTimeMillis()
        _generationJob.value = job
        if (cancelPrevious) previous?.cancel()
        if (job != null) activeJobs.add(job)
        job?.invokeOnCompletion { cause ->
            synchronized(this) {
                activeJobs.remove(job)
                // Also propagate cancellation when a queued coroutine never entered its body.
                if (!cancelPrevious && cause is CancellationException) previous?.cancel()
                // A replaced job must not clear or advance its successor.
                if (_generationJob.compareAndSet(job, null)) {
                    onGenerationFinished(id, cause)
                    if (refCount.get() <= 0) scheduleIdleCheck()
                }
            }
        }
        job?.start()
    }

    fun getJob(): Job? = _generationJob.value

    @Synchronized
    fun cancelJobs(): List<Job> = activeJobs.toList().also { jobs ->
        // Cancel waiters first so a predecessor finishing cannot start the next approval.
        jobs.asReversed().forEach { it.cancel() }
    }

    private fun scheduleIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (refCount.get() <= 0 && !isGenerating) {
                // 回收前把只改了内存的内容落库；落库失败就留在内存里，等下次释放引用或生成结束时再检查。
                // 还没落过库的会话不在这里新建：只打开过、没发过消息的新会话不该留下记录
                try {
                    if (existsInDatabase) flush()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist conversation $id before eviction", e)
                    return@launch
                }
                onIdle(id)
            }
        }
    }

    private fun cancelIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = null
    }

    @Synchronized
    fun cleanup() {
        _generationJob.value = null
        cancelJobs()
        idleCheckJob?.cancel()
        idleCheckJob = null
    }
}

/** Serialize approval saves without cancelling earlier decisions; stopping cancels the whole chain. */
internal suspend fun afterPreviousGeneration(previous: Job?, block: suspend () -> Unit) {
    try {
        previous?.join()
        block()
    } catch (e: CancellationException) {
        previous?.cancel()
        withContext(NonCancellable) { previous?.join() }
        throw e
    }
}
