package me.rerere.ai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            // 尝试获取常见的错误字段
            val errorFields = listOf("error", "detail", "message", "description")

            // 查找第一个存在的错误字段
            val foundField = errorFields.firstOrNull { this[it] != null }

            if (foundField != null) {
                // 递归解析找到的字段值
                this[foundField]!!.parseErrorDetail()
            } else {
                // 如果没有找到任何错误字段，序列化整个对象
                HttpException(Json.encodeToString(JsonElement.serializer(), this))
            }
        }

        is JsonArray -> {
            if (this.isEmpty()) {
                HttpException("Unknown error: Empty JSON array")
            } else {
                // 递归解析数组的第一个元素
                this.first().parseErrorDetail()
            }
        }

        is JsonPrimitive -> {
            // 对于基本类型，直接使用其内容
            HttpException(this.jsonPrimitive.content)
        }
    }
}

fun codeForStatus(statusCode: Int?, message: String? = null): LlmFailureCode = when {
    statusCode == 401 || statusCode == 403 -> LlmFailureCode.AUTH
    statusCode == 402 -> LlmFailureCode.QUOTA
    statusCode == 429 -> LlmFailureCode.RATE_LIMIT
    statusCode == 408 -> LlmFailureCode.TIMEOUT
    statusCode != null && statusCode >= 500 -> LlmFailureCode.SERVER
    statusCode == 400 || statusCode == 413 -> {
        val text = message.orEmpty()
        if (text.contains("context", ignoreCase = true) &&
            (text.contains("length", ignoreCase = true) ||
                text.contains("window", ignoreCase = true) ||
                text.contains("too long", ignoreCase = true))
        ) {
            LlmFailureCode.CONTEXT_WINDOW_EXCEEDED
        } else {
            LlmFailureCode.INVALID_REQUEST
        }
    }

    else -> LlmFailureCode.UNKNOWN
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
    return HttpException(
        message = message ?: "HTTP ${response.code}",
        code = codeForStatus(response.code, message),
        statusCode = response.code,
        retryAfterMs = retryAfterMillis(response.header("Retry-After")),
    )
}

fun Response.toHttpException(body: String? = null): HttpException {
    val message = listOfNotNull("HTTP $code", body?.takeIf { it.isNotBlank() })
        .joinToString(": ")
    return HttpException(
        message = message,
        code = codeForStatus(code, body),
        statusCode = code,
        retryAfterMs = retryAfterMillis(header("Retry-After")),
    )
}
