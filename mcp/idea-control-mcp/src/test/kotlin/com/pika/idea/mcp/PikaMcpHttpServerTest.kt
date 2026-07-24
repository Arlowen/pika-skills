package com.pika.idea.mcp

import com.google.gson.JsonParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PikaMcpHttpServerTest {
    private lateinit var executor: ExecutorService
    private lateinit var server: PikaMcpHttpServer
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        executor = Executors.newCachedThreadPool()
        server = PikaMcpHttpServer(
            requestedPort = 0,
            protocol = PikaMcpProtocol("http-test"),
            pluginVersion = "http-test",
            executor = executor,
        )
        port = server.start()
        client = HttpClient.newBuilder().build()
    }

    @AfterEach
    fun stopServer() {
        server.stop()
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    @Test
    fun `streamable HTTP initialize returns JSON response`() {
        val response = post(
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "initialize",
              "params": {"protocolVersion": "2025-11-25", "capabilities": {}}
            }
            """.trimIndent(),
        )

        assertEquals(200, response.statusCode())
        assertTrue(
            response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/json"),
        )
        val result = JsonParser.parseString(response.body())
            .asJsonObject
            .getAsJsonObject("result")
        assertEquals("2025-11-25", result.get("protocolVersion").asString)
        assertEquals("http-test", result.getAsJsonObject("serverInfo").get("version").asString)
    }

    @Test
    fun `HTTP tools list exposes the Pika tools`() {
        val response = post(
            """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""",
            protocolVersion = "2025-11-25",
        )

        assertEquals(200, response.statusCode())
        assertEquals(
            5,
            JsonParser.parseString(response.body())
                .asJsonObject
                .getAsJsonObject("result")
                .getAsJsonArray("tools")
                .size(),
        )
    }

    @Test
    fun `GET on MCP endpoint reports that SSE is not offered`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/mcp"))
            .GET()
            .build()

        assertEquals(405, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
    }

    @Test
    fun `health endpoint reports the actual bound port`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/health"))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertEquals(
            "http://127.0.0.1:$port/mcp",
            JsonParser.parseString(response.body()).asJsonObject.get("endpoint").asString,
        )
    }

    @Test
    fun `non-local Origin is rejected`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("Origin", "https://attacker.example")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """{"jsonrpc":"2.0","id":1,"method":"ping","params":{}}""",
                ),
            )
            .build()

        assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
    }

    @Test
    fun `unsupported protocol version is rejected`() {
        val response = post(
            """{"jsonrpc":"2.0","id":1,"method":"ping","params":{}}""",
            protocolVersion = "2099-01-01",
        )

        assertEquals(400, response.statusCode())
    }

    private fun post(
        body: String,
        protocolVersion: String? = null,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        protocolVersion?.let { builder.header("MCP-Protocol-Version", it) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
}
