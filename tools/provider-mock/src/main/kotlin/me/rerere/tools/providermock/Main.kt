package me.rerere.tools.providermock

/**
 * Standalone dev server: `.\gradlew.bat :tools:provider-mock:run`.
 *
 * Point the app's provider base URLs at the printed addresses and pick a scenario with the model id
 * (`mock-normal`, `mock-tool`, ...) or the `X-Mock-Scenario` header.
 */
fun main() {
    val server = ProviderMockServer()
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
    println("Press Ctrl+C to stop.")
    Thread.currentThread().join()
}
