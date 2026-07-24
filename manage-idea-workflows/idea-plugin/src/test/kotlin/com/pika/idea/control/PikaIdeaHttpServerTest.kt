package com.pika.idea.control

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

class PikaIdeaHttpServerTest {
    private lateinit var executor: ExecutorService
    private lateinit var server: PikaIdeaHttpServer
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        executor = Executors.newCachedThreadPool()
        server = PikaIdeaHttpServer(
            requestedPort = 0,
            api = PikaIdeaApi(),
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
    fun `health endpoint reports REST API base and actual bound port`() {
        val response = get("/health")

        assertEquals(200, response.statusCode())
        assertTrue(
            response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/json"),
        )
        val body = JsonParser.parseString(response.body()).asJsonObject
        assertEquals("ok", body.get("status").asString)
        assertEquals("Pika Control", body.get("name").asString)
        assertEquals("http-test", body.get("version").asString)
        assertEquals(
            "http://127.0.0.1:$port/api/v1",
            body.get("apiBase").asString,
        )
    }

    @Test
    fun `removed protocol path is not exposed`() {
        val response = get("/mcp")

        assertEquals(404, response.statusCode())
        assertEquals(
            "NOT_FOUND",
            JsonParser.parseString(response.body())
                .asJsonObject
                .getAsJsonObject("error")
                .get("code")
                .asString,
        )
    }

    @Test
    fun `unknown REST path returns structured error`() {
        val response = get("/api/v1/missing")

        assertEquals(404, response.statusCode())
        val error = JsonParser.parseString(response.body())
            .asJsonObject
            .getAsJsonObject("error")
        assertEquals("NOT_FOUND", error.get("code").asString)
        assertTrue(error.get("message").asString.contains("/api/v1/missing"))
    }

    @Test
    fun `non-local Origin is rejected`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/health"))
            .header("Origin", "https://attacker.example")
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(403, response.statusCode())
        assertEquals(
            "FORBIDDEN",
            JsonParser.parseString(response.body())
                .asJsonObject
                .getAsJsonObject("error")
                .get("code")
                .asString,
        )
    }

    @Test
    fun `POST requires JSON content type`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/api/v1/services/start"))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(415, response.statusCode())
        assertEquals(
            "UNSUPPORTED_MEDIA_TYPE",
            JsonParser.parseString(response.body())
                .asJsonObject
                .getAsJsonObject("error")
                .get("code")
                .asString,
        )
    }

    @Test
    fun `malformed JSON returns invalid argument without reaching IDEA`() {
        val response = post("/api/v1/services/start", "{")

        assertEquals(400, response.statusCode())
        assertEquals(
            "INVALID_ARGUMENT",
            JsonParser.parseString(response.body())
                .asJsonObject
                .getAsJsonObject("error")
                .get("code")
                .asString,
        )
    }

    @Test
    fun `wrong method returns method not allowed`() {
        val response = post("/api/v1/services", "{}")

        assertEquals(405, response.statusCode())
        assertEquals(
            "METHOD_NOT_ALLOWED",
            JsonParser.parseString(response.body())
                .asJsonObject
                .getAsJsonObject("error")
                .get("code")
                .asString,
        )
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port$path"))
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun post(path: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
