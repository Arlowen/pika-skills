package com.pika.idea.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal class PikaMcpProtocol(
    private val pluginVersion: String,
    private val tools: PikaMcpToolRegistry = PikaMcpToolRegistry(),
) {
    fun handle(body: String): ProtocolReply {
        val message =
            try {
                JsonParser.parseString(body)
            } catch (_: JsonParseException) {
                return ProtocolReply(400, errorResponse(null, -32700, "Parse error"))
            }

        if (!message.isJsonObject) {
            return ProtocolReply(400, errorResponse(null, -32600, "Invalid Request"))
        }

        val request = message.asJsonObject
        val jsonRpcVersion = request.get("jsonrpc")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        if (jsonRpcVersion != "2.0") {
            return ProtocolReply(400, errorResponse(request.get("id"), -32600, "Invalid Request"))
        }

        val methodElement = request.get("method")
        if (methodElement == null) {
            return ProtocolReply(202)
        }
        if (!methodElement.isJsonPrimitive || !methodElement.asJsonPrimitive.isString) {
            return ProtocolReply(400, errorResponse(request.get("id"), -32600, "Invalid Request"))
        }

        val id = request.get("id")
        if (id == null || id is JsonNull) {
            return ProtocolReply(202)
        }

        val params = request.get("params")?.let {
            if (!it.isJsonObject) {
                return ProtocolReply(
                    200,
                    errorResponse(id, -32602, "params must be an object"),
                )
            }
            it.asJsonObject
        } ?: JsonObject()

        return when (val method = methodElement.asString) {
            "initialize" -> ProtocolReply(200, successResponse(id, initialize(params)))
            "ping" -> ProtocolReply(200, successResponse(id, JsonObject()))
            "tools/list" ->
                ProtocolReply(
                    200,
                    successResponse(
                        id,
                        JsonObject().apply {
                            add("tools", tools.definitionsJson())
                        },
                    ),
                )

            "tools/call" -> callTool(id, params)
            else -> ProtocolReply(200, errorResponse(id, -32601, "Method not found: $method"))
        }
    }

    private fun initialize(params: JsonObject): JsonObject {
        val requestedVersion = params.get("protocolVersion")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        val negotiatedVersion =
            requestedVersion?.takeIf { it in SUPPORTED_PROTOCOL_VERSIONS }
                ?: LATEST_PROTOCOL_VERSION

        return JsonObject().apply {
            addProperty("protocolVersion", negotiatedVersion)
            add(
                "capabilities",
                JsonObject().apply {
                    add(
                        "tools",
                        JsonObject().apply {
                            addProperty("listChanged", false)
                        },
                    )
                },
            )
            add(
                "serverInfo",
                JsonObject().apply {
                    addProperty("name", "pika-mcp")
                    addProperty("title", "Pika MCP")
                    addProperty("version", pluginVersion)
                },
            )
            addProperty(
                "instructions",
                "Controls Run/Debug executions and IDEA changelists for an open IntelliJ IDEA project. " +
                    "Pass projectPath when multiple projects are open.",
            )
        }
    }

    private fun callTool(id: JsonElement, params: JsonObject): ProtocolReply {
        val name = params.get("name")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?: return ProtocolReply(200, errorResponse(id, -32602, "Tool name is required"))
        val arguments = params.get("arguments")?.let {
            if (!it.isJsonObject) {
                return ProtocolReply(200, errorResponse(id, -32602, "Tool arguments must be an object"))
            }
            it.asJsonObject
        } ?: JsonObject()

        return try {
            val structured = tools.call(name, arguments)
            ProtocolReply(
                200,
                successResponse(
                    id,
                    JsonObject().apply {
                        add(
                            "content",
                            textContent(ToolSupport.json(structured)),
                        )
                        add("structuredContent", structured)
                        addProperty("isError", false)
                    },
                ),
            )
        } catch (error: UnknownMcpToolException) {
            ProtocolReply(200, errorResponse(id, -32602, error.message ?: "Unknown tool"))
        } catch (error: Throwable) {
            val message = ToolSupport.messageOf(error)
            ProtocolReply(
                200,
                successResponse(
                    id,
                    JsonObject().apply {
                        add("content", textContent(message))
                        addProperty("isError", true)
                    },
                ),
            )
        }
    }

    private fun textContent(text: String) =
        com.google.gson.JsonArray().apply {
            add(
                JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text)
                },
            )
        }

    private fun successResponse(id: JsonElement, result: JsonObject): JsonObject =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id.deepCopy())
            add("result", result)
        }

    private fun errorResponse(
        id: JsonElement?,
        code: Int,
        message: String,
    ): JsonObject =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id?.deepCopy() ?: JsonNull.INSTANCE)
            add(
                "error",
                JsonObject().apply {
                    addProperty("code", code)
                    addProperty("message", message)
                },
            )
        }

    companion object {
        const val LATEST_PROTOCOL_VERSION = "2025-11-25"
        val SUPPORTED_PROTOCOL_VERSIONS =
            setOf(
                "2025-11-25",
                "2025-06-18",
                "2025-03-26",
            )
    }
}

internal data class ProtocolReply(
    val statusCode: Int,
    val body: JsonObject? = null,
)
