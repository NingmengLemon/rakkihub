package me.rerere.rikkahub.data.ai.mcp

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.UIMessagePart
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.oauth.OAuthHttpClient
import me.rerere.oauth.OAuthLoopbackCallbackServer
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.allMcpServers
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.saveUploadFromBytes
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.WorkspaceProcessLauncher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * MCP 子系统的公共入口。
 *
 * 这里仅协调配置、OAuth、连接注册表与 UI 内容转换；单个服务器的连接状态机由
 * [McpSessionRegistry] 管理，OAuth 协议细节由 [McpOAuthCoordinator] 管理。
 */
class McpManager(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val filesManager: FilesManager,
    processLauncher: WorkspaceProcessLauncher,
    workspaceDao: WorkspaceDAO,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followSslRedirects(true)
        .followRedirects(true)
        .build()

    private val httpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(SSE)
    }

    private val statusStore = McpStatusStore()
    private val oauthCallbackServer = OAuthLoopbackCallbackServer(
        port = MCP_OAUTH_CALLBACK_PORT,
        callbackPath = MCP_OAUTH_CALLBACK_PATH,
    )
    private val oauthCoordinator = McpOAuthCoordinator(
        settingsStore = settingsStore,
        appScope = appScope,
        oauthClient = OAuthHttpClient(okHttpClient),
        discoveryClient = McpOAuthDiscoveryClient(okHttpClient),
        callbackServer = oauthCallbackServer,
        authorizationLauncher = CustomTabsOAuthAuthorizationLauncher,
        updateStatus = statusStore::update,
    )
    private val sessionRegistry = McpSessionRegistry(
        settingsStore = settingsStore,
        appScope = appScope,
        httpClient = httpClient,
        oauthCoordinator = oauthCoordinator,
        statusStore = statusStore,
        processLauncher = processLauncher,
        workspaceDao = workspaceDao,
    )

    init {
        appScope.launch {
            settingsStore.settingsFlow
                .map { settings -> settings.allMcpServers() }
                .distinctUntilChanged()
                .collect(sessionRegistry::reconcile)
        }
    }

    val syncingStatus: StateFlow<Map<Uuid, McpStatus>>
        get() = statusStore.status

    fun getClient(config: McpServerConfig): Client? = sessionRegistry.getClient(config.id)

    fun getStatus(config: McpServerConfig): Flow<McpStatus> = sessionRegistry.getStatus(config.id)

    fun getAllAvailableTools(assistant: Assistant): List<Triple<Uuid, String, McpTool>> {
        val settings = settingsStore.settingsFlow.value
        return availableMcpTools(
            servers = settings.allMcpServers(),
            assistant = assistant,
            isStopped = { id ->
                isMcpServerStopped(id, settings.stoppedMcpServerIds, sessionRegistry::isManuallyStopped)
            },
        )
    }

    suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart> {
        val tool = settingsStore.settingsFlow.value.allMcpServers()
            .firstOrNull { it.id == serverId }
            ?.commonOptions
            ?.tools
            ?.firstOrNull { it.name == toolName }
        val result = try {
            withMcpToolRetry(
                tool = tool,
                onRetry = { nextAttempt ->
                    Log.i(
                        TAG,
                        "Retrying MCP tool ${tool?.name ?: "unknown"} (attempt $nextAttempt/$MCP_TOOL_MAX_ATTEMPTS)",
                    )
                },
                call = { sessionRegistry.callTool(serverId, toolName, args) },
                isErrorResult = { it.isError == true },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: McpClientUnavailableException) {
            return listOf(UIMessagePart.Text("Failed to execute MCP tool: ${e.message ?: e.javaClass.name}"))
        }
        return result.content.map { content ->
            when (content) {
                is TextContent -> UIMessagePart.Text(content.text)
                is ImageContent -> convertImageContentToFilePart(content)
                else -> UIMessagePart.Text(JsonInstant.encodeToString(content))
            }
        }
    }

    suspend fun addClient(config: McpServerConfig) = sessionRegistry.addClient(config)

    suspend fun removeClient(config: McpServerConfig) = sessionRegistry.removeClient(config)

    /** 停止后重新启动，避免 addClient 在旧 client 仍存在时提前返回。 */
    fun restartClient(config: McpServerConfig) {
        appScope.launch { sessionRegistry.restartClient(config) }
    }

    fun stopClient(config: McpServerConfig) {
        appScope.launch { sessionRegistry.stopClient(config) }
    }

    suspend fun syncAll() = sessionRegistry.syncAll()

    fun startAuthorization(config: McpServerConfig, context: Context) {
        oauthCoordinator.startAuthorization(config, context)
    }

    fun cancelAuthorization(config: McpServerConfig) {
        oauthCoordinator.cancelAuthorization(config.id)
    }

    suspend fun clearAuthorization(config: McpServerConfig) {
        val freshConfig = oauthCoordinator.clearAuthorization(config)
        sessionRegistry.addClient(freshConfig)
    }

    private suspend fun convertImageContentToFilePart(image: ImageContent): UIMessagePart.Image {
        val bytes = Base64.decode(image.data)
        val extension = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(image.mimeType) ?: "bin"
        val entity = filesManager.saveUploadFromBytes(
            bytes = bytes,
            displayName = "mcp_image.$extension",
            mimeType = image.mimeType,
        )
        return UIMessagePart.Image(url = filesManager.getFile(entity).toUri().toString())
    }
}

/**
 * 当前可提供给模型的 MCP 工具：Server 已启用、已绑定到当前助手、未被手动停止，
 * 且工具自身已启用。停止的 Server 不应继续出现在工具列表里，否则模型会调用一个
 * 不可用的工具，只能得到运行时错误。
 */
internal fun isMcpServerStopped(
    id: Uuid,
    persistedStoppedIds: Set<Uuid>,
    registryStopped: (Uuid) -> Boolean,
): Boolean = id in persistedStoppedIds || registryStopped(id)

internal fun availableMcpTools(
    servers: List<McpServerConfig>,
    assistant: Assistant,
    isStopped: (Uuid) -> Boolean,
): List<Triple<Uuid, String, McpTool>> = servers
    .filter { server ->
        server.commonOptions.enable &&
            server.id in assistant.mcpServers &&
            !isStopped(server.id)
    }
    .flatMap { server ->
        server.commonOptions.tools
            .filter { tool -> tool.enable }
            .map { tool -> Triple(server.id, server.commonOptions.name, tool) }
    }

internal enum class McpToolFailure {
    /** 请求确定没有发到 Server，重试不会产生副作用。 */
    NotSent,

    /** 请求可能已经执行，只有 marked reentrant 才允许重试。 */
    Sent,
}

internal fun shouldRetryMcpTool(tool: McpTool?, failure: McpToolFailure): Boolean =
    tool?.retryable == true && (failure == McpToolFailure.NotSent || tool.reentrant)

internal const val MCP_TOOL_MAX_ATTEMPTS = 2
internal const val MCP_TOOL_RETRY_DELAY_MS = 500L

/**
 * 在单次 MCP 工具调用内做有限重试。
 *
 * - 未发出的失败只要求 `retryable`；
 * - 已发出的异常、超时或 `isError` 结果还要求 `reentrant`，避免非幂等工具被执行两次；
 * - 取消不重试，重试耗尽后保留最后一次失败的原有处理方式。
 */
internal suspend fun <T> withMcpToolRetry(
    tool: McpTool?,
    maxAttempts: Int = MCP_TOOL_MAX_ATTEMPTS,
    retryDelayMillis: Long = MCP_TOOL_RETRY_DELAY_MS,
    onRetry: (Int) -> Unit = {},
    call: suspend () -> T,
    isErrorResult: (T) -> Boolean,
): T {
    require(maxAttempts >= 1)
    var attempt = 1
    while (true) {
        try {
            val result = call()
            if (!isErrorResult(result) ||
                attempt >= maxAttempts ||
                !shouldRetryMcpTool(tool, McpToolFailure.Sent)
            ) {
                return result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: McpClientUnavailableException) {
            if (attempt >= maxAttempts || !shouldRetryMcpTool(tool, McpToolFailure.NotSent)) throw e
        } catch (e: Exception) {
            if (attempt >= maxAttempts || !shouldRetryMcpTool(tool, McpToolFailure.Sent)) throw e
        }
        attempt++
        onRetry(attempt)
        if (retryDelayMillis > 0) delay(retryDelayMillis)
    }
}

private const val TAG = "McpManager"
