package com.pika.idea.mcp

import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor

internal class PikaMcpHttpServer(
    private val requestedPort: Int,
    private val protocol: PikaMcpProtocol,
    private val pluginVersion: String,
    private val executor: Executor = AppExecutorUtil.getAppExecutorService(),
) {
    @Volatile
    private var server: HttpServer? = null

    val boundPort: Int?
        get() = server?.address?.port

    @Synchronized
    fun start(): Int {
        server?.let { return it.address.port }

        val created = HttpServer.create(
            InetSocketAddress(InetAddress.getByName(LOOPBACK_ADDRESS), requestedPort),
            0,
        )
        created.executor = executor
        created.createContext(MCP_PATH) { exchange -> handleMcp(exchange) }
        created.createContext(HEALTH_PATH) { exchange -> handleHealth(exchange) }
        created.start()
        server = created
        return created.address.port
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun handleMcp(exchange: HttpExchange) {
        try {
            if (exchange.requestURI.path != MCP_PATH) {
                send(exchange, 404)
                return
            }
            if (!isLocalRequest(exchange)) {
                send(exchange, 403, errorBody("Forbidden"))
                return
            }

            when (exchange.requestMethod.uppercase()) {
                "POST" -> handlePost(exchange)
                "GET" -> {
                    exchange.responseHeaders.set("Allow", "POST")
                    send(exchange, 405)
                }
                else -> {
                    exchange.responseHeaders.set("Allow", "POST")
                    send(exchange, 405)
                }
            }
        } catch (error: Throwable) {
            send(exchange, 500, errorBody(ToolSupport.messageOf(error)))
        } finally {
            exchange.close()
        }
    }

    private fun handlePost(exchange: HttpExchange) {
        val protocolVersion = exchange.requestHeaders.getFirst("MCP-Protocol-Version")
        if (protocolVersion != null && protocolVersion !in PikaMcpProtocol.SUPPORTED_PROTOCOL_VERSIONS) {
            send(exchange, 400, errorBody("Unsupported MCP-Protocol-Version '$protocolVersion'"))
            return
        }

        val contentType = exchange.requestHeaders.getFirst("Content-Type")
        if (contentType != null && !contentType.startsWith("application/json", ignoreCase = true)) {
            send(exchange, 415, errorBody("Content-Type must be application/json"))
            return
        }

        val bytes = exchange.requestBody.readNBytes(MAX_REQUEST_BYTES + 1)
        if (bytes.size > MAX_REQUEST_BYTES) {
            send(exchange, 413, errorBody("Request body is too large"))
            return
        }

        val reply = protocol.handle(String(bytes, StandardCharsets.UTF_8))
        send(exchange, reply.statusCode, reply.body?.toString())
    }

    private fun handleHealth(exchange: HttpExchange) {
        try {
            if (exchange.requestURI.path != HEALTH_PATH) {
                send(exchange, 404)
                return
            }
            if (!isLocalRequest(exchange)) {
                send(exchange, 403, errorBody("Forbidden"))
                return
            }
            if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
                exchange.responseHeaders.set("Allow", "GET")
                send(exchange, 405)
                return
            }

            send(
                exchange,
                200,
                ToolSupport.json(
                    mapOf(
                        "status" to "ok",
                        "name" to "Pika MCP",
                        "version" to pluginVersion,
                        "endpoint" to "http://$LOOPBACK_ADDRESS:${boundPort ?: requestedPort}$MCP_PATH",
                    ),
                ),
            )
        } finally {
            exchange.close()
        }
    }

    private fun isLocalRequest(exchange: HttpExchange): Boolean {
        if (exchange.remoteAddress?.address?.isLoopbackAddress != true) {
            return false
        }

        val host = exchange.requestHeaders.getFirst("Host")
        if (host != null && hostName(host) !in ALLOWED_HOSTS) {
            return false
        }

        val origin = exchange.requestHeaders.getFirst("Origin") ?: return true
        val originHost =
            try {
                URI(origin).host?.lowercase()
            } catch (_: IllegalArgumentException) {
                null
            }
        return originHost in ALLOWED_HOSTS
    }

    private fun hostName(value: String): String {
        val normalized = value.trim().lowercase()
        if (normalized.startsWith("[")) {
            return normalized.substringAfter("[").substringBefore("]")
        }
        return normalized.substringBefore(":")
    }

    private fun send(
        exchange: HttpExchange,
        statusCode: Int,
        body: String? = null,
    ) {
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        if (body == null) {
            exchange.sendResponseHeaders(statusCode, -1)
            return
        }

        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun errorBody(message: String): String =
        ToolSupport.json(
            mapOf(
                "jsonrpc" to "2.0",
                "id" to null,
                "error" to mapOf(
                    "code" to -32600,
                    "message" to message,
                ),
            ),
        )

    companion object {
        const val DEFAULT_PORT = 8765
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val MCP_PATH = "/mcp"
        const val HEALTH_PATH = "/health"
        const val MAX_REQUEST_BYTES = 1024 * 1024

        private val ALLOWED_HOSTS = setOf("127.0.0.1", "localhost", "::1")
    }
}
