package me.rerere.ai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Provider-neutral failure classes used by retry and recovery decisions. */
enum class LlmFailureCode {
    AUTH,
    QUOTA,
    RATE_LIMIT,
    SERVER,
    TIMEOUT,
    TRANSPORT,
    EMPTY_RESPONSE,
    CONTEXT_WINDOW_EXCEEDED,
    INVALID_REQUEST,
    UNKNOWN,
}

open class LlmFailure(
    message: String,
    val code: LlmFailureCode,
    val statusCode: Int? = null,
    val retryAfterMs: Long? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class HttpException(
    message: String,
    code: LlmFailureCode = LlmFailureCode.UNKNOWN,
    statusCode: Int? = null,
    retryAfterMs: Long? = null,
) : LlmFailure(message, code, statusCode, retryAfterMs)

fun JsonElement.parseErrorDetail(): HttpException {
    return when (this) {
        is JsonObject -> {
            val code = codeFromJson(this)
            val messageField = listOf("error", "detail", "message", "description")
                .firstOrNull { this[it] != null }
            val message = if (messageField != null) {
                val nested = this[messageField]!!.parseErrorDetail()
                nested.message ?: "Unknown error"
            } else {
                Json.encodeToString(JsonElement.serializer(), this)
            }
            HttpException(message, code)
        }

        is JsonArray -> {
            if (this.isEmpty()) {
                HttpException("Unknown error: Empty JSON array")
            } else {
                this.first().parseErrorDetail()
            }
        }

        is JsonPrimitive -> HttpException(
            message = this.jsonPrimitive.content,
            code = codeFromText(this.jsonPrimitive.content),
        )
    }
}

fun codeForStatus(statusCode: Int?, message: String? = null): LlmFailureCode {
    val textCode = codeFromText(message.orEmpty())
    if (textCode != LlmFailureCode.UNKNOWN) return textCode
    return when {
        statusCode == 401 || statusCode == 403 -> LlmFailureCode.AUTH
        statusCode == 402 -> LlmFailureCode.QUOTA
        statusCode == 429 -> LlmFailureCode.RATE_LIMIT
        statusCode == 408 -> LlmFailureCode.TIMEOUT
        statusCode != null && statusCode >= 500 -> LlmFailureCode.SERVER
        statusCode == 400 || statusCode == 413 -> LlmFailureCode.INVALID_REQUEST
        else -> LlmFailureCode.UNKNOWN
    }
}

/** Parse Retry-After as seconds or an HTTP date; ignore malformed values. */
fun retryAfterMillis(value: String?): Long? {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) return null
    text.toLongOrNull()?.let { seconds -> return (seconds * 1000).takeIf { it > 0 } }
    return runCatching {
        val at = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        (at - System.currentTimeMillis()).takeIf { it > 0 }
    }.getOrNull()
}

fun HttpException.withResponse(response: Response?): HttpException {
    if (response == null) return this
    val resolvedCode = if (code != LlmFailureCode.UNKNOWN) {
        code
    } else {
        codeForStatus(response.code, message)
    }
    return HttpException(
        message = message ?: "HTTP ${response.code}",
        code = resolvedCode,
        statusCode = response.code,
        retryAfterMs = retryAfterMillis(response.header("Retry-After")),
    )
}

fun Response.toHttpException(body: String? = null): HttpException {
    val parsed = runCatching {
        Json.parseToJsonElement(body.orEmpty()).parseErrorDetail()
    }.getOrNull()
    val failureCode = parsed?.code?.takeIf { it != LlmFailureCode.UNKNOWN }
        ?: if (parsed != null) codeForStatus(code, parsed.message) else codeForStatus(code)
    val message = listOfNotNull("HTTP $code", body?.takeIf { it.isNotBlank() })
        .joinToString(": ")
    return HttpException(
        message = message,
        code = failureCode,
        statusCode = code,
        retryAfterMs = retryAfterMillis(header("Retry-After")),
    )
}

private fun codeFromJson(obj: JsonObject): LlmFailureCode {
    val signals = mutableListOf<String>()
    collectSignals(obj, signals)
    return codeFromText(signals.joinToString(" "))
}

private fun collectSignals(element: JsonElement, signals: MutableList<String>) {
    when (element) {
        is JsonObject -> {
            listOf("code", "type", "status", "reason", "message", "@type").forEach { key ->
                element[key]?.let { signals += it.asSignal() }
            }
            listOf("error", "details", "errors").forEach { key ->
                element[key]?.let { collectSignals(it, signals) }
            }
        }

        is JsonArray -> element.forEach { collectSignals(it, signals) }
        is JsonPrimitive -> signals += element.content
    }
}

private fun JsonElement.asSignal(): String = when (this) {
    is JsonPrimitive -> content
    is JsonObject -> jsonObject.values.joinToString(" ") { it.asSignal() }
    is JsonArray -> jsonArray.joinToString(" ") { it.asSignal() }
}

private fun codeFromText(raw: String): LlmFailureCode {
    val text = raw.lowercase()
    return when {
        text.contains("insufficient_quota") ||
            text.contains("quota_exceeded") ||
            text.contains("quota exceeded") ||
            text.contains("quotafailure") -> LlmFailureCode.QUOTA
        text.contains("context_length_exceeded") ||
            text.contains("context length") ||
            text.contains("prompt is too long") ||
            (text.contains("input token count") && text.contains("exceeds the maximum")) ||
            text.contains("maximum context") -> LlmFailureCode.CONTEXT_WINDOW_EXCEEDED
        text.contains("rate_limit") ||
            text.contains("rate limit") ||
            text.contains("resource_exhausted") ||
            text.contains("too many requests") -> LlmFailureCode.RATE_LIMIT
        text.contains("invalid_api_key") ||
            text.contains("invalid api key") ||
            text.contains("authentication") ||
            text.contains("unauthenticated") ||
            text.contains("permission_denied") ||
            text.contains("permission denied") ||
            text.contains("unauthorized") ||
            text.contains("forbidden") -> LlmFailureCode.AUTH
        text.contains("gateway_timeout") ||
            text.contains("timeout") ||
            text.contains("timed out") -> LlmFailureCode.TIMEOUT
        text.contains("overloaded") ||
            text.contains("server_error") ||
            text.contains("internal") ||
            text.contains("unavailable") ||
            text.contains("bad gateway") -> LlmFailureCode.SERVER
        text.contains("invalid_request") ||
            text.contains("invalid argument") ||
            text.contains("bad request") -> LlmFailureCode.INVALID_REQUEST
        text.contains("quota") -> LlmFailureCode.QUOTA
        else -> LlmFailureCode.UNKNOWN
    }
}
