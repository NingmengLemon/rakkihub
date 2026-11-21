package me.rerere.tools.providermock

/** One Server-Sent Event frame. [encode] emits the canonical wire form the mock server writes. */
data class SseFrame(
    val data: String,
    val event: String? = null,
    val id: String? = null,
) {
    fun encode(): String = buildString {
        if (id != null) append("id: ").append(id).append('\n')
        if (event != null) append("event: ").append(event).append('\n')
        data.split('\n').forEach { line -> append("data: ").append(line).append('\n') }
        append('\n')
    }
}
