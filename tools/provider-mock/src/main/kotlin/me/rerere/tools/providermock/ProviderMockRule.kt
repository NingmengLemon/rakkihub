package me.rerere.tools.providermock

import org.junit.rules.ExternalResource

/**
 * JUnit 4 fixture: starts one [ProviderMockServer] for the test and stops it afterwards.
 *
 * ```kotlin
 * @get:Rule
 * val mock = ProviderMockRule()
 *
 * @Test
 * fun example() {
 *     assertEquals("Hello from mock", call(mock.openAiBaseUrl).text)
 * }
 * ```
 */
class ProviderMockRule(
    private val slowChunkDelayMillis: Long = ProviderMockServer.DEFAULT_SLOW_DELAY_MILLIS,
) : ExternalResource() {
    lateinit var server: ProviderMockServer
        private set

    val recorder: RequestRecorder get() = server.recorder
    val baseUrl: String get() = server.baseUrl
    val openAiBaseUrl: String get() = server.openAiBaseUrl
    val responsesBaseUrl: String get() = server.responsesBaseUrl
    val claudeBaseUrl: String get() = server.claudeBaseUrl
    val googleBaseUrl: String get() = server.googleBaseUrl

    override fun before() {
        server = ProviderMockServer(slowChunkDelayMillis = slowChunkDelayMillis)
    }

    override fun after() {
        server.close()
    }
}
