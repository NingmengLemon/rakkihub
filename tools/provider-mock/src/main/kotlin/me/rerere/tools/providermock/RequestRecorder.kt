package me.rerere.tools.providermock

/**
 * One HTTP request observed by [ProviderMockServer].
 *
 * Sensitive headers (credentials and cookies) are recorded as presence plus, for `Authorization`,
 * the scheme prefix only. Raw credential values never enter this object, the logs or a test failure
 * message.
 */
data class RecordedRequest(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, List<String>>,
    val rawBody: String,
    val timestampMillis: Long,
) {
    fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()

    fun headerValues(name: String): List<String> = headers[name.lowercase()].orEmpty()

    fun hasHeader(name: String): Boolean = header(name) != null
}

/**
 * In-memory request log used by tests and by the standalone server.
 *
 * Assertion helpers throw [AssertionError] with request metadata only, never with credential values.
 */
class RequestRecorder {
    private val lock = Any()
    private val items = mutableListOf<RecordedRequest>()

    fun record(
        method: String,
        path: String,
        query: String,
        headers: Map<String, List<String>>,
        rawBody: String,
    ) {
        val recorded = RecordedRequest(
            method = method,
            path = path,
            query = query,
            headers = headers.mapKeys { it.key.lowercase() },
            rawBody = rawBody,
            timestampMillis = System.currentTimeMillis(),
        )
        synchronized(lock) { items += recorded }
    }

    fun requests(): List<RecordedRequest> = synchronized(lock) { items.toList() }

    fun size(): Int = synchronized(lock) { items.size }

    fun clear() {
        synchronized(lock) { items.clear() }
    }

    fun last(): RecordedRequest = synchronized(lock) { items.lastOrNull() }
        ?: fail("no request was recorded")

    fun single(): RecordedRequest {
        val all = requests()
        check(all.size == 1) { "expected exactly one recorded request, got ${all.size}" }
        return all.single()
    }

    fun find(method: String, path: String): RecordedRequest? =
        requests().lastOrNull { it.method == method && it.path == path }

    fun assertHasRequest(method: String, path: String): RecordedRequest =
        find(method, path)
            ?: fail("no $method $path request; recorded: ${requests().map { "${it.method} ${it.path}" }}")

    fun awaitRequest(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        predicate: (RecordedRequest) -> Boolean,
    ): RecordedRequest {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            requests().firstOrNull(predicate)?.let { return it }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return fail("no matching request within ${timeoutMillis}ms")
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)

    companion object {
        const val REDACTED = "<redacted>"

        private const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        private const val POLL_INTERVAL_MILLIS = 10L

        private val SENSITIVE_HEADERS = setOf(
            "authorization",
            "proxy-authorization",
            "x-api-key",
            "x-goog-api-key",
            "api-key",
            "cookie",
            "set-cookie",
        )

        fun redact(name: String, value: String): String {
            val lower = name.lowercase()
            if (lower !in SENSITIVE_HEADERS) return value
            if (lower != "authorization" && lower != "proxy-authorization") return REDACTED
            val scheme = value.substringBefore(' ')
            return if (scheme.isNotBlank() && scheme.length <= MAX_SCHEME_LENGTH) "$scheme $REDACTED" else REDACTED
        }

        private const val MAX_SCHEME_LENGTH = 16
    }
}
