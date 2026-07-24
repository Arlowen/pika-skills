package com.pika.idea.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PikaMcpProtocolTest {
    private val protocol = PikaMcpProtocol("test-version")

    @Test
    fun `initialize negotiates supported protocol and advertises tools`() {
        val reply = protocol.handle(
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "initialize",
              "params": {
                "protocolVersion": "2025-06-18",
                "capabilities": {},
                "clientInfo": {"name": "test", "version": "1"}
              }
            }
            """.trimIndent(),
        )

        assertEquals(200, reply.statusCode)
        val result = requireNotNull(reply.body).getAsJsonObject("result")
        assertEquals("2025-06-18", result.get("protocolVersion").asString)
        assertEquals("pika-mcp", result.getAsJsonObject("serverInfo").get("name").asString)
        assertEquals("test-version", result.getAsJsonObject("serverInfo").get("version").asString)
        assertFalse(
            result.getAsJsonObject("capabilities")
                .getAsJsonObject("tools")
                .get("listChanged")
                .asBoolean,
        )
    }

    @Test
    fun `tools list exposes five independent Pika MCP tools with schemas`() {
        val reply = protocol.handle(
            """{"jsonrpc":"2.0","id":"tools","method":"tools/list","params":{}}""",
        )

        assertEquals(200, reply.statusCode)
        val tools = requireNotNull(reply.body)
            .getAsJsonObject("result")
            .getAsJsonArray("tools")
        assertEquals(5, tools.size())
        assertEquals(
            setOf(
                "idea_list_services",
                "idea_start_service",
                "idea_stop_service",
                "idea_list_changelists",
                "idea_move_changes_to_changelist",
            ),
            tools.map { it.asJsonObject.get("name").asString }.toSet(),
        )
        tools.forEach { item ->
            val tool = item.asJsonObject
            assertTrue(tool.get("description").asString.isNotBlank())
            assertEquals("object", tool.getAsJsonObject("inputSchema").get("type").asString)
            assertFalse(tool.getAsJsonObject("inputSchema").get("additionalProperties").asBoolean)
        }
    }

    @Test
    fun `notifications are accepted without a JSON RPC response body`() {
        val reply = protocol.handle(
            """{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""",
        )

        assertEquals(202, reply.statusCode)
        assertEquals(null, reply.body)
    }

    @Test
    fun `unknown tool is a protocol level invalid params error`() {
        val reply = protocol.handle(
            """
            {
              "jsonrpc": "2.0",
              "id": 4,
              "method": "tools/call",
              "params": {"name": "idea_missing", "arguments": {}}
            }
            """.trimIndent(),
        )

        assertEquals(200, reply.statusCode)
        val error = requireNotNull(reply.body).getAsJsonObject("error")
        assertEquals(-32602, error.get("code").asInt)
        assertTrue(error.get("message").asString.contains("Unknown tool"))
    }

    @Test
    fun `malformed JSON produces a parse error`() {
        val reply = protocol.handle("{")

        assertEquals(400, reply.statusCode)
        assertEquals(
            -32700,
            requireNotNull(reply.body).getAsJsonObject("error").get("code").asInt,
        )
    }

    @Test
    fun `unsupported method produces method not found`() {
        val reply = protocol.handle(
            """{"jsonrpc":"2.0","id":9,"method":"resources/list","params":{}}""",
        )

        assertEquals(200, reply.statusCode)
        assertEquals(
            -32601,
            requireNotNull(reply.body).getAsJsonObject("error").get("code").asInt,
        )
    }
}
