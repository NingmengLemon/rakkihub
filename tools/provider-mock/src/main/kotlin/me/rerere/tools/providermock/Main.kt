package me.rerere.tools.providermock

/**
 * Standalone dev server: `.\gradlew.bat :tools:provider-mock:run`.
 *
 * Pass `--port <port>` to bind a fixed port, which keeps `adb reverse` stable when the app on a
 * device points at the mock. Without it the server picks an ephemeral port and prints it.
 *
 * Point the app's provider base URLs at the printed addresses and pick a scenario with the model id
 * (`mock-normal`, `mock-tool`, ...) or the `X-Mock-Scenario` header.
 */
fun main(args: Array<String>) {
    val port = parsePort(args)
    val server = ProviderMockServer(port = port)
    Runtime.getRuntime().addShutdownHook(Thread(server::close))

    println("provider-mock listening on ${server.baseUrl}")
    println("  OpenAI Chat Completions base: ${server.openAiBaseUrl}  (POST /chat/completions)")
    println("  OpenAI Responses base:        ${server.responsesBaseUrl}  (POST /responses, useResponseApi)")
    println("  Claude Messages base:         ${server.claudeBaseUrl}  (POST /messages)")
    println("  Gemini base:                  ${server.googleBaseUrl}  (POST /models/{model}:streamGenerateContent)")
    println()
    println("Model ids select a scenario, or override with the X-Mock-Scenario header:")
    MockScenario.entries.forEach { scenario ->
        println("  ${scenario.modelName.padEnd(24)} -> ${scenario.wireName}")
    }
    println()
    println("App on a device: adb reverse tcp:${server.port} tcp:${server.port}")
    println("Press Ctrl+C to stop.")
    Thread.currentThread().join()
}

private fun parsePort(args: Array<String>): Int {
    val raw = args.toList().let { list ->
        val flagIndex = list.indexOf("--port")
        when {
            flagIndex >= 0 -> list.getOrNull(flagIndex + 1)
            else -> list.firstOrNull { it.startsWith("--port=") }?.substringAfter('=')
        }
    } ?: return 0
    return raw.toIntOrNull()?.takeIf { it in 0..65535 }
        ?: error("invalid --port value: $raw (expected 0..65535)")
}
