package me.rerere.tools.providermock

/**
 * Scenario served for one request.
 *
 * Pick it with the model id (`mock-<wireName>`, e.g. `mock-tool`) or override it per request with the
 * [HEADER] request header. Unknown model ids fall back to [NORMAL] so pointing an app at the server
 * with an arbitrary model still works.
 */
enum class MockScenario(val wireName: String) {
    /** Plain text deltas, usage and the protocol terminal event. */
    NORMAL("normal"),

    /** Reasoning deltas followed by text deltas, in the shape each protocol really uses. */
    REASONING("reasoning"),

    /** One tool call split into incremental argument deltas plus a tool finish reason. */
    TOOL("tool"),

    /** HTTP 429 with `Retry-After: 2` and the protocol's error body. */
    RATE_LIMIT("rate-limit"),

    /** HTTP 500 with an HTML body, so it cannot be parsed as a stream. */
    SERVER_ERROR("server-error"),

    /** Some deltas, then the connection closes without the protocol terminal event. */
    TRUNCATE("truncate"),

    /** Protocol terminal event with no content, so the caller sees an empty response. */
    EMPTY("empty"),

    /**
     * Normal content with a delay between chunks; tune it with [ProviderMockServer.slowChunkDelayMillis]
     * or [DELAY_HEADER].
     */
    SLOW("slow"),

    /**
     * Valid multi-byte text written a few bytes at a time, so CJK and emoji characters span write
     * boundaries. Only the Claude payload uses the non-ASCII text; other protocols keep their normal
     * payload while still receiving the split writes.
     */
    UTF8_SPLIT("utf8-split"),

    /**
     * Like [UTF8_SPLIT], but one continuation byte is dropped from a CJK character, so the stream
     * carries invalid UTF-8 and a decoder replaces it with U+FFFD.
     */
    UTF8_BROKEN("utf8-broken"),

    /**
     * 请求里出现连续同角色的 user 时返回 400，否则按 [NORMAL] 正常返回。
     * 用于模拟要求角色严格交替的端点，JVM 集成测试与设备联调都可以用。
     */
    STRICT_ALTERNATION("strict-alternation"),
    ;

    /** Model id that selects this scenario. */
    val modelName: String get() = "mock-$wireName"

    val isFailure: Boolean get() = this == RATE_LIMIT || this == SERVER_ERROR

    companion object {
        const val HEADER = "X-Mock-Scenario"
        const val DELAY_HEADER = "X-Mock-Delay-Ms"

        fun fromWireName(raw: String?): MockScenario? {
            val value = raw?.trim().orEmpty()
            return entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
        }

        fun resolve(headerValue: String?, model: String?): MockScenario {
            fromWireName(headerValue)?.let { return it }
            val fromModel = model?.trim()?.removePrefix("mock-")
            return fromWireName(fromModel) ?: NORMAL
        }
    }
}
