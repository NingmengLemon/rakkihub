package me.rerere.ai.provider.providers.openai

import kotlinx.coroutines.test.runTest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.HttpException
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.LlmFailureCode
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionsErrorTest {
    @Test
    fun `non successful response carries status code and structured failure`() = runTest {
        val body = """{"error":{"type":"insufficient_quota","code":"insufficient_quota","message":"quota"}}"""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .header("Retry-After", "2")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
        val api = ChatCompletionsAPI(client, KeyRoulette.default())

        val error = runCatching {
            api.generateText(
                providerSetting = ProviderSetting.OpenAI(apiKey = "test"),
                messages = listOf(UIMessage.user("hi")),
                params = TextGenerationParams(model = Model(modelId = "test-model")),
            )
        }.exceptionOrNull()

        assertTrue(error.toString(), error is HttpException)
        error as HttpException
        assertEquals(429, error.statusCode)
        assertEquals(LlmFailureCode.QUOTA, error.code)
        assertEquals(2_000L, error.retryAfterMs)
    }
}
