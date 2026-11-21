package me.rerere.tools.providermock

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Protocol whose error body shape must be used for a failure scenario. */
enum class MockProtocol {
    OPENAI,
    CLAUDE,
    GOOGLE,
}

/**
 * Wire payloads used by [ProviderMockServer].
 *
 * The text, reasoning, tool call and token counts are fixed on purpose: module tests and the
 * `core/ai` integration tests assert them end to end, so a change here makes both sides fail
 * together instead of drifting silently.
 */
object MockPayloads {
    const val TEXT = "Hello from mock"
    const val TEXT_HEAD = "Hello from "
    const val TEXT_TAIL = "mock"

    /** Multi-byte text for the UTF-8 boundary scenarios: CJK plus a 4-byte emoji. */
    const val UTF8_TEXT = "中文 🙂 边界"

    const val REASONING = "Let me think."
    const val REASONING_HEAD = "Let me "
    const val REASONING_TAIL = "think."

    const val TOOL_NAME = "get_weather"
    const val TOOL_CALL_ID = "call_mock_1"
    const val TOOL_ARGUMENTS = "{\"city\":\"Paris\"}"
    const val TOOL_ARGUMENTS_HEAD = "{\"city\":"
    const val TOOL_ARGUMENTS_TAIL = "\"Paris\"}"

    const val PROMPT_TOKENS = 11
    const val COMPLETION_TOKENS = 5
    const val TOTAL_TOKENS = 16
    const val CACHED_TOKENS = 3

    const val CHAT_COMPLETION_ID = "chatcmpl-mock"
    const val RESPONSE_ID = "resp_mock"
    const val RESPONSE_ITEM_ID = "msg_mock"
    const val RESPONSE_REASONING_ITEM_ID = "rs_mock"
    const val RESPONSE_FUNCTION_ITEM_ID = "fc_mock"
    const val CLAUDE_MESSAGE_ID = "msg_mock"
    const val CLAUDE_SIGNATURE = "sig_mock_1"
    const val GEMINI_RESPONSE_ID = "gemini_mock"

    const val SERVER_ERROR_HTML =
        "<html><body><h1>500 Internal Server Error</h1><p>provider-mock</p></body></html>"

    /** OpenAI Chat Completions streaming frames. */
    fun chatCompletionsFrames(scenario: MockScenario, model: String): List<SseFrame> = when (scenario) {
        MockScenario.REASONING -> chatReasoningFrames(model)
        MockScenario.TOOL -> chatToolFrames(model)
        MockScenario.EMPTY -> listOf(done())
        MockScenario.TRUNCATE -> chatTextFrames(model).take(2)
        else -> chatTextFrames(model)
    }

    /** OpenAI Chat Completions non-stream response. */
    fun chatCompletionsJson(model: String): String = buildJsonObject {
        put("id", CHAT_COMPLETION_ID)
        put("object", "chat.completion")
        put("model", model)
        putJsonArray("choices") {
            addJsonObject {
                put("index", 0)
                putJsonObject("message") {
                    put("role", "assistant")
                    put("content", TEXT)
                }
                put("finish_reason", "stop")
            }
        }
        put("usage", chatUsage())
    }.toString()

    /** OpenAI Responses API typed streaming events. */
    fun responsesFrames(scenario: MockScenario, model: String): List<SseFrame> = when (scenario) {
        MockScenario.REASONING -> responsesReasoningFrames(model)
        MockScenario.TOOL -> responsesToolFrames(model)
        MockScenario.EMPTY -> listOf(responsesCompleted(model, JsonArray(emptyList()), withUsage = false))
        MockScenario.TRUNCATE -> responsesTextFrames(model).take(4)
        else -> responsesTextFrames(model)
    }

    /** OpenAI Responses non-stream response. */
    fun responsesJson(model: String): String = buildJsonObject {
        put("id", RESPONSE_ID)
        put("object", "response")
        put("model", model)
        put("status", "completed")
        putJsonArray("output") { add(responseMessageItem(TEXT)) }
        put("usage", responsesUsage())
    }.toString()

    /** Anthropic Messages streaming frames. */
    fun claudeFrames(scenario: MockScenario, model: String): List<SseFrame> = when (scenario) {
        MockScenario.UTF8_SPLIT, MockScenario.UTF8_BROKEN -> claudeTextFrames(model, UTF8_TEXT)
        MockScenario.REASONING -> claudeReasoningFrames(model)
        MockScenario.TOOL -> claudeToolFrames(model)
        MockScenario.EMPTY -> listOf(claudeMessageStart(model), claudeMessageStop())
        MockScenario.TRUNCATE -> claudeTextFrames(model).take(3)
        else -> claudeTextFrames(model)
    }

    /** Anthropic Messages non-stream response. */
    fun claudeJson(model: String): String = buildJsonObject {
        put("id", CLAUDE_MESSAGE_ID)
        put("type", "message")
        put("role", "assistant")
        put("model", model)
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", TEXT)
            }
        }
        put("stop_reason", "end_turn")
        put("stop_sequence", null as String?)
        put("usage", claudeUsage(outputTokens = COMPLETION_TOKENS))
    }.toString()

    /** Gemini generateContent streaming frames. Closing the stream completes them. */
    fun geminiFrames(scenario: MockScenario, model: String): List<SseFrame> = when (scenario) {
        MockScenario.REASONING -> geminiReasoningFrames(model)
        MockScenario.TOOL -> geminiToolFrames(model)
        MockScenario.EMPTY -> listOf(geminiFinishFrame(model, withUsage = false))
        MockScenario.TRUNCATE -> geminiTextFrames(model).take(1)
        else -> geminiTextFrames(model)
    }

    /** Gemini generateContent non-stream response. */
    fun geminiJson(model: String): String = buildJsonObject {
        put("responseId", GEMINI_RESPONSE_ID)
        put("modelVersion", model)
        putJsonArray("candidates") {
            addJsonObject {
                putJsonObject("content") {
                    put("role", "model")
                    putJsonArray("parts") { add(geminiTextPart(TEXT)) }
                }
                put("finishReason", "STOP")
            }
        }
        put("usageMetadata", geminiUsage(candidatesTokens = COMPLETION_TOKENS))
    }.toString()

    /** Body for the failure scenarios, in the shape the given protocol expects. */
    fun errorBody(protocol: MockProtocol, status: Int): String {
        val rateLimited = status == 429
        val message = if (rateLimited) "mock rate limit" else "mock server error"
        return when (protocol) {
            MockProtocol.OPENAI -> buildJsonObject {
                putJsonObject("error") {
                    put("message", message)
                    put("type", if (rateLimited) "rate_limit_error" else "server_error")
                    put("code", if (rateLimited) "rate_limit_exceeded" else "server_error")
                }
            }.toString()

            MockProtocol.CLAUDE -> buildJsonObject {
                put("type", "error")
                putJsonObject("error") {
                    put("type", if (rateLimited) "rate_limit_error" else "api_error")
                    put("message", message)
                }
            }.toString()

            MockProtocol.GOOGLE -> buildJsonObject {
                putJsonObject("error") {
                    put("code", status)
                    put("message", message)
                    put("status", if (rateLimited) "RESOURCE_EXHAUSTED" else "INTERNAL")
                }
            }.toString()
        }
    }

    /** `GET /v1/models`: accepted by OpenAI and Claude `listModels`. */
    fun modelsJson(): String = buildJsonObject {
        put("object", "list")
        putJsonArray("data") {
            MockScenario.entries.forEach { scenario ->
                addJsonObject {
                    put("id", scenario.modelName)
                    put("object", "model")
                    put("owned_by", "provider-mock")
                    put("display_name", "Mock ${scenario.wireName}")
                }
            }
        }
    }.toString()

    /** `GET /v1beta/models`: accepted by Google `listModels`. */
    fun googleModelsJson(): String = buildJsonObject {
        putJsonArray("models") {
            MockScenario.entries.forEach { scenario ->
                addJsonObject {
                    put("name", "models/${scenario.modelName}")
                    put("displayName", "Mock ${scenario.wireName}")
                    putJsonArray("supportedGenerationMethods") { add("generateContent") }
                }
            }
        }
    }.toString()

    // ---- OpenAI Chat Completions ----

    private fun done() = SseFrame(data = "[DONE]")

    private fun chatTextFrames(model: String): List<SseFrame> = listOf(
        chatChunk(model, delta = chunkDelta(content = TEXT_HEAD)),
        chatChunk(model, delta = chunkDelta(content = TEXT_TAIL)),
        chatChunk(model, delta = buildJsonObject { }, finishReason = "stop"),
        chatUsageFrame(model),
        done(),
    )

    private fun chatReasoningFrames(model: String): List<SseFrame> = listOf(
        chatChunk(model, delta = chunkDelta(reasoning = REASONING_HEAD)),
        chatChunk(model, delta = chunkDelta(reasoning = REASONING_TAIL)),
        chatChunk(model, delta = chunkDelta(content = TEXT)),
        chatChunk(model, delta = buildJsonObject { }, finishReason = "stop"),
        chatUsageFrame(model),
        done(),
    )

    private fun chatToolFrames(model: String): List<SseFrame> = listOf(
        chatChunk(model, delta = buildJsonObject { put("tool_calls", toolStartDelta()) }),
        chatChunk(model, delta = chunkDelta(toolArguments = TOOL_ARGUMENTS_HEAD)),
        chatChunk(model, delta = chunkDelta(toolArguments = TOOL_ARGUMENTS_TAIL)),
        chatChunk(model, delta = buildJsonObject { }, finishReason = "tool_calls"),
        chatUsageFrame(model),
        done(),
    )

    private fun toolStartDelta(): JsonArray = buildJsonArray {
        addJsonObject {
            put("index", 0)
            put("id", TOOL_CALL_ID)
            put("type", "function")
            putJsonObject("function") {
                put("name", TOOL_NAME)
                put("arguments", "")
            }
        }
    }

    private fun chatChunk(model: String, delta: JsonObject, finishReason: String? = null): SseFrame = SseFrame(
        data = buildJsonObject {
            put("id", CHAT_COMPLETION_ID)
            put("object", "chat.completion.chunk")
            put("model", model)
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    put("delta", delta)
                    put("finish_reason", finishReason)
                }
            }
        }.toString(),
    )

    private fun chatUsageFrame(model: String): SseFrame = SseFrame(
        data = buildJsonObject {
            put("id", CHAT_COMPLETION_ID)
            put("object", "chat.completion.chunk")
            put("model", model)
            putJsonArray("choices") { }
            put("usage", chatUsage())
        }.toString(),
    )

    private fun chatUsage(): JsonObject = buildJsonObject {
        put("prompt_tokens", PROMPT_TOKENS)
        put("completion_tokens", COMPLETION_TOKENS)
        put("total_tokens", TOTAL_TOKENS)
        putJsonObject("prompt_tokens_details") { put("cached_tokens", CACHED_TOKENS) }
    }

    private fun chunkDelta(
        content: String? = null,
        reasoning: String? = null,
        toolArguments: String? = null,
    ): JsonObject = buildJsonObject {
        when {
            content != null -> put("content", content)
            reasoning != null -> put("reasoning_content", reasoning)
            toolArguments != null -> putJsonArray("tool_calls") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("function") { put("arguments", toolArguments) }
                }
            }
        }
    }

    // ---- OpenAI Responses ----

    private fun responsesTextFrames(model: String): List<SseFrame> = listOf(
        responsesEvent("response.created") {
            putJsonObject("response") {
                put("id", RESPONSE_ID)
                put("model", model)
                put("status", "in_progress")
                putJsonArray("output") { }
            }
        },
        responseItemEvent("response.output_item.added", responseMessageItem("")),
        responsePartEvent("response.content_part.added", buildJsonObject {
            put("type", "output_text")
            put("text", "")
        }),
        responsesEvent("response.output_text.delta") { textDeltaFields(TEXT_HEAD) },
        responsesEvent("response.output_text.delta") { textDeltaFields(TEXT_TAIL) },
        responsesEvent("response.output_text.done") {
            textDeltaFields("")
            put("text", TEXT)
        },
        responsePartEvent("response.content_part.done", buildJsonObject {
            put("type", "output_text")
            put("text", TEXT)
        }),
        responseItemEvent("response.output_item.done", responseMessageItem(TEXT)),
        responsesCompleted(model, buildJsonArray { add(responseMessageItem(TEXT)) }),
    )

    private fun responsesReasoningFrames(model: String): List<SseFrame> = listOf(
        responsesEvent("response.created") {
            putJsonObject("response") {
                put("id", RESPONSE_ID)
                put("model", model)
                put("status", "in_progress")
                putJsonArray("output") { }
            }
        },
        responseItemEvent("response.output_item.added", responseReasoningItem(REASONING, includeSummary = false)),
        responsesEvent("response.reasoning_summary_part.added") {
            reasoningFields()
            putJsonObject("part") {
                put("type", "summary_text")
                put("text", "")
            }
        },
        responsesEvent("response.reasoning_summary_text.delta") {
            reasoningFields()
            put("delta", REASONING_HEAD)
        },
        responsesEvent("response.reasoning_summary_text.delta") {
            reasoningFields()
            put("delta", REASONING_TAIL)
        },
        responsesEvent("response.reasoning_summary_text.done") {
            reasoningFields()
            put("text", REASONING)
        },
        responseItemEvent("response.output_item.done", responseReasoningItem(REASONING, includeSummary = true)),
        responseItemEvent("response.output_item.added", responseMessageItem("")),
        responsePartEvent("response.content_part.added", buildJsonObject {
            put("type", "output_text")
            put("text", "")
        }),
        responsesEvent("response.output_text.delta") { textDeltaFields(TEXT) },
        responsePartEvent("response.content_part.done", buildJsonObject {
            put("type", "output_text")
            put("text", TEXT)
        }),
        responseItemEvent("response.output_item.done", responseMessageItem(TEXT)),
        responsesCompleted(
            model = model,
            output = buildJsonArray {
                add(responseReasoningItem(REASONING, includeSummary = true))
                add(responseMessageItem(TEXT))
            },
        ),
    )

    private fun responsesToolFrames(model: String): List<SseFrame> = listOf(
        responsesEvent("response.created") {
            putJsonObject("response") {
                put("id", RESPONSE_ID)
                put("model", model)
                put("status", "in_progress")
                putJsonArray("output") { }
            }
        },
        responseItemEvent("response.output_item.added", responseFunctionItem("")),
        responsesEvent("response.function_call_arguments.delta") {
            functionArgumentsFields()
            put("delta", TOOL_ARGUMENTS_HEAD)
        },
        responsesEvent("response.function_call_arguments.delta") {
            functionArgumentsFields()
            put("delta", TOOL_ARGUMENTS_TAIL)
        },
        responsesEvent("response.function_call_arguments.done") {
            functionArgumentsFields()
            put("arguments", TOOL_ARGUMENTS)
        },
        responseItemEvent("response.output_item.done", responseFunctionItem(TOOL_ARGUMENTS)),
        responsesCompleted(model, buildJsonArray { add(responseFunctionItem(TOOL_ARGUMENTS)) }),
    )

    private fun responsesEvent(type: String, block: JsonObjectBuilder.() -> Unit): SseFrame = SseFrame(
        event = type,
        data = buildJsonObject {
            put("type", type)
            block()
        }.toString(),
    )

    private fun responseItemEvent(type: String, item: JsonObject): SseFrame = responsesEvent(type) {
        put("output_index", 0)
        put("item", item)
    }

    private fun responsePartEvent(type: String, part: JsonObject): SseFrame = responsesEvent(type) {
        put("item_id", RESPONSE_ITEM_ID)
        put("output_index", 0)
        put("content_index", 0)
        put("part", part)
    }

    private fun JsonObjectBuilder.textDeltaFields(delta: String) {
        put("item_id", RESPONSE_ITEM_ID)
        put("output_index", 0)
        put("content_index", 0)
        put("delta", delta)
    }

    private fun JsonObjectBuilder.reasoningFields() {
        put("item_id", RESPONSE_REASONING_ITEM_ID)
        put("output_index", 0)
        put("summary_index", 0)
    }

    private fun JsonObjectBuilder.functionArgumentsFields() {
        put("item_id", RESPONSE_FUNCTION_ITEM_ID)
        put("output_index", 0)
        put("call_id", TOOL_CALL_ID)
    }

    private fun responseMessageItem(text: String): JsonObject = buildJsonObject {
        put("id", RESPONSE_ITEM_ID)
        put("type", "message")
        put("role", "assistant")
        putJsonArray("content") {
            if (text.isNotEmpty()) {
                addJsonObject {
                    put("type", "output_text")
                    put("text", text)
                }
            }
        }
    }

    private fun responseReasoningItem(text: String, includeSummary: Boolean): JsonObject = buildJsonObject {
        put("id", RESPONSE_REASONING_ITEM_ID)
        put("type", "reasoning")
        putJsonArray("summary") {
            if (includeSummary) {
                addJsonObject {
                    put("type", "summary_text")
                    put("text", text)
                }
            }
        }
    }

    private fun responseFunctionItem(arguments: String): JsonObject = buildJsonObject {
        put("id", RESPONSE_FUNCTION_ITEM_ID)
        put("type", "function_call")
        put("call_id", TOOL_CALL_ID)
        put("name", TOOL_NAME)
        put("arguments", arguments)
    }

    private fun responsesCompleted(model: String, output: JsonArray, withUsage: Boolean = true): SseFrame =
        responsesEvent("response.completed") {
            putJsonObject("response") {
                put("id", RESPONSE_ID)
                put("object", "response")
                put("model", model)
                put("status", "completed")
                put("output", output)
                if (withUsage) put("usage", responsesUsage())
            }
        }

    private fun responsesUsage(): JsonObject = buildJsonObject {
        put("input_tokens", PROMPT_TOKENS)
        put("output_tokens", COMPLETION_TOKENS)
        put("total_tokens", TOTAL_TOKENS)
        putJsonObject("input_tokens_details") { put("cached_tokens", CACHED_TOKENS) }
    }

    // ---- Anthropic Messages ----

    private fun claudeTextFrames(model: String, text: String? = null): List<SseFrame> = buildList {
        add(claudeMessageStart(model))
        add(
            claudeContentBlockStart(0, buildJsonObject {
                put("type", "text")
                put("text", "")
            })
        )
        // The default payload keeps the two-delta shape the existing assertions expect; the UTF-8
        // scenarios send one delta so the whole multi-byte character is inside one chunk list.
        if (text == null) {
            add(claudeTextDelta(0, TEXT_HEAD))
            add(claudeTextDelta(0, TEXT_TAIL))
        } else {
            add(claudeTextDelta(0, text))
        }
        add(claudeContentBlockStop(0))
        add(claudeMessageDelta(outputTokens = COMPLETION_TOKENS))
        add(claudeMessageStop())
    }

    private fun claudeReasoningFrames(model: String): List<SseFrame> = listOf(
        claudeMessageStart(model),
        claudeContentBlockStart(0, buildJsonObject {
            put("type", "thinking")
            put("thinking", "")
        }),
        claudeEvent("content_block_delta") {
            put("index", 0)
            putJsonObject("delta") {
                put("type", "thinking_delta")
                put("thinking", REASONING_HEAD)
            }
        },
        claudeEvent("content_block_delta") {
            put("index", 0)
            putJsonObject("delta") {
                put("type", "thinking_delta")
                put("thinking", REASONING_TAIL)
            }
        },
        claudeEvent("content_block_delta") {
            put("index", 0)
            putJsonObject("delta") {
                put("type", "signature_delta")
                put("signature", CLAUDE_SIGNATURE)
            }
        },
        claudeContentBlockStop(0),
        claudeContentBlockStart(1, buildJsonObject {
            put("type", "text")
            put("text", "")
        }),
        claudeTextDelta(1, TEXT),
        claudeContentBlockStop(1),
        claudeMessageDelta(outputTokens = COMPLETION_TOKENS),
        claudeMessageStop(),
    )

    private fun claudeToolFrames(model: String): List<SseFrame> = listOf(
        claudeMessageStart(model),
        claudeContentBlockStart(0, buildJsonObject {
            put("type", "tool_use")
            put("id", TOOL_CALL_ID)
            put("name", TOOL_NAME)
            putJsonObject("input") { }
        }),
        claudeEvent("content_block_delta") {
            put("index", 0)
            putJsonObject("delta") {
                put("type", "input_json_delta")
                put("partial_json", TOOL_ARGUMENTS_HEAD)
            }
        },
        claudeEvent("content_block_delta") {
            put("index", 0)
            putJsonObject("delta") {
                put("type", "input_json_delta")
                put("partial_json", TOOL_ARGUMENTS_TAIL)
            }
        },
        claudeContentBlockStop(0),
        claudeMessageDelta(outputTokens = COMPLETION_TOKENS, stopReason = "tool_use"),
        claudeMessageStop(),
    )

    private fun claudeEvent(type: String, block: JsonObjectBuilder.() -> Unit): SseFrame = SseFrame(
        event = type,
        data = buildJsonObject {
            put("type", type)
            block()
        }.toString(),
    )

    private fun claudeMessageStart(model: String): SseFrame = claudeEvent("message_start") {
        putJsonObject("message") {
            put("id", CLAUDE_MESSAGE_ID)
            put("type", "message")
            put("role", "assistant")
            put("model", model)
            putJsonArray("content") { }
            put("stop_reason", null as String?)
            put("stop_sequence", null as String?)
            put("usage", claudeUsage(outputTokens = 0))
        }
    }

    private fun claudeContentBlockStart(index: Int, contentBlock: JsonObject): SseFrame =
        claudeEvent("content_block_start") {
            put("index", index)
            put("content_block", contentBlock)
        }

    private fun claudeTextDelta(index: Int, text: String): SseFrame = claudeEvent("content_block_delta") {
        put("index", index)
        putJsonObject("delta") {
            put("type", "text_delta")
            put("text", text)
        }
    }

    private fun claudeContentBlockStop(index: Int): SseFrame = claudeEvent("content_block_stop") {
        put("index", index)
    }

    private fun claudeMessageDelta(outputTokens: Int, stopReason: String = "end_turn"): SseFrame =
        claudeEvent("message_delta") {
            putJsonObject("delta") {
                put("stop_reason", stopReason)
                put("stop_sequence", null as String?)
            }
            put("usage", claudeUsage(outputTokens = outputTokens))
        }

    private fun claudeMessageStop(): SseFrame = claudeEvent("message_stop") { }

    private fun claudeUsage(outputTokens: Int): JsonObject = buildJsonObject {
        put("input_tokens", PROMPT_TOKENS)
        put("output_tokens", outputTokens)
    }

    // ---- Gemini generateContent ----

    private fun geminiTextFrames(model: String): List<SseFrame> = listOf(
        geminiFrame(model, parts = buildJsonArray { add(geminiTextPart(TEXT_HEAD)) }),
        geminiFrame(model, parts = buildJsonArray { add(geminiTextPart(TEXT_TAIL)) }),
        geminiFinishFrame(model, withUsage = true),
    )

    private fun geminiReasoningFrames(model: String): List<SseFrame> = listOf(
        geminiFrame(model, parts = buildJsonArray { add(geminiTextPart(REASONING_HEAD, thought = true)) }),
        geminiFrame(model, parts = buildJsonArray { add(geminiTextPart(REASONING_TAIL, thought = true)) }),
        geminiFrame(model, parts = buildJsonArray { add(geminiTextPart(TEXT)) }),
        geminiFinishFrame(model, withUsage = true),
    )

    private fun geminiToolFrames(model: String): List<SseFrame> = listOf(
        geminiFrame(
            model = model,
            parts = buildJsonArray {
                addJsonObject {
                    putJsonObject("functionCall") {
                        put("id", TOOL_CALL_ID)
                        put("name", TOOL_NAME)
                        putJsonObject("args") { put("city", "Paris") }
                    }
                }
            },
        ),
        geminiFinishFrame(model, withUsage = true),
    )

    private fun geminiFrame(model: String, parts: JsonArray): SseFrame = SseFrame(
        data = buildJsonObject {
            put("responseId", GEMINI_RESPONSE_ID)
            put("modelVersion", model)
            putJsonArray("candidates") {
                addJsonObject {
                    putJsonObject("content") {
                        put("role", "model")
                        put("parts", parts)
                    }
                    put("index", 0)
                }
            }
        }.toString(),
    )

    private fun geminiFinishFrame(model: String, withUsage: Boolean): SseFrame = SseFrame(
        data = buildJsonObject {
            put("responseId", GEMINI_RESPONSE_ID)
            put("modelVersion", model)
            putJsonArray("candidates") {
                addJsonObject {
                    putJsonObject("content") {
                        put("role", "model")
                        putJsonArray("parts") { }
                    }
                    put("finishReason", "STOP")
                    put("index", 0)
                }
            }
            if (withUsage) put("usageMetadata", geminiUsage(candidatesTokens = COMPLETION_TOKENS))
        }.toString(),
    )

    private fun geminiTextPart(text: String, thought: Boolean = false): JsonObject = buildJsonObject {
        put("text", text)
        if (thought) put("thought", true)
    }

    private fun geminiUsage(candidatesTokens: Int): JsonObject = buildJsonObject {
        put("promptTokenCount", PROMPT_TOKENS)
        put("candidatesTokenCount", candidatesTokens)
        put("totalTokenCount", PROMPT_TOKENS + candidatesTokens)
        put("cachedContentTokenCount", CACHED_TOKENS)
    }
}
