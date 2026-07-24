package com.pika.idea.control

import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor

internal class PikaIdeaHttpServer(
    private val requestedPort: Int,
    private val api: PikaIdeaApi,
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
        created.createContext("/") { exchange -> handle(exchange) }
        created.start()
        server = created
        return created.address.port
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (!isLocalRequest(exchange)) {
                send(exchange, PikaIdeaApi.errorReply(403, "FORBIDDEN", "Forbidden"))
                return
            }

            if (exchange.requestURI.path == HEALTH_PATH) {
                handleHealth(exchange)
                return
            }

            val method = exchange.requestMethod.uppercase()
            if (method !in setOf("GET", "POST")) {
                send(
                    exchange,
                    PikaIdeaApi.errorReply(
                        405,
                        "METHOD_NOT_ALLOWED",
                        "Method $method is not allowed",
                    ),
                )
                return
            }

            val requestBody =
                if (method == "POST") {
                    val contentType = exchange.requestHeaders.getFirst("Content-Type")
                    if (contentType == null ||
                        !contentType.startsWith("application/json", ignoreCase = true)
                    ) {
                        send(
                            exchange,
                            PikaIdeaApi.errorReply(
                                415,
                                "UNSUPPORTED_MEDIA_TYPE",
                                "Content-Type must be application/json",
                            ),
                        )
                        return
                    }
                    val bytes = exchange.requestBody.readNBytes(MAX_REQUEST_BYTES + 1)
                    if (bytes.size > MAX_REQUEST_BYTES) {
                        send(
                            exchange,
                            PikaIdeaApi.errorReply(
                                413,
                                "PAYLOAD_TOO_LARGE",
                                "Request body is too large",
                            ),
                        )
                        return
                    }
                    String(bytes, StandardCharsets.UTF_8)
                } else {
                    null
                }

            val reply = api.handle(
                method = method,
                path = exchange.requestURI.path,
                query = parseQuery(exchange.requestURI.rawQuery),
                requestBody = requestBody,
            )
            send(exchange, reply)
        } catch (error: IllegalArgumentException) {
            send(
                exchange,
                PikaIdeaApi.errorReply(
                    400,
                    "INVALID_ARGUMENT",
                    error.message ?: "Invalid request",
                ),
            )
        } catch (error: Throwable) {
            send(
                exchange,
                PikaIdeaApi.errorReply(
                    500,
                    "INTERNAL_ERROR",
                    ControlSupport.messageOf(error),
                ),
            )
        } finally {
            exchange.close()
        }
    }

    private fun handleHealth(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            exchange.responseHeaders.set("Allow", "GET")
            send(
                exchange,
                PikaIdeaApi.errorReply(
                    405,
                    "METHOD_NOT_ALLOWED",
                    "Method ${exchange.requestMethod.uppercase()} is not allowed; use GET",
                ),
            )
            return
        }

        val port = boundPort ?: requestedPort
        send(
            exchange,
            ApiReply(
                200,
                ControlSupport.jsonObject(
                    mapOf(
                        "status" to "ok",
                        "name" to "Pika Control",
                        "version" to pluginVersion,
                        "apiBase" to "http://$LOOPBACK_ADDRESS:$port${PikaIdeaApi.API_BASE_PATH}",
                    ),
                ),
            ),
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, List<String>> {
        if (rawQuery.isNullOrEmpty()) {
            return emptyMap()
        }
        return rawQuery
            .split("&")
            .map { pair ->
                val rawName = pair.substringBefore("=")
                val rawValue = pair.substringAfter("=", "")
                decode(rawName) to decode(rawValue)
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

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

    private fun send(exchange: HttpExchange, reply: ApiReply) {
        val body = reply.body.toString()
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(reply.statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        const val DEFAULT_PORT = 8765
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val HEALTH_PATH = "/health"
        const val MAX_REQUEST_BYTES = 1024 * 1024

        private val ALLOWED_HOSTS = setOf("127.0.0.1", "localhost", "::1")
    }
}
