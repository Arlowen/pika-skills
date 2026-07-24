package com.pika.idea.mcp

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId

internal class PikaMcpServerService : Disposable {
    private val log = Logger.getInstance(PikaMcpServerService::class.java)
    private val pluginVersion =
        PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "dev"

    @Volatile
    private var httpServer: PikaMcpHttpServer? = null

    val boundPort: Int?
        get() = httpServer?.boundPort

    @Synchronized
    fun ensureStarted() {
        if (httpServer != null) {
            return
        }

        val port = configuredPort()
        val candidate = PikaMcpHttpServer(
            requestedPort = port,
            protocol = PikaMcpProtocol(pluginVersion),
            pluginVersion = pluginVersion,
        )
        try {
            val actualPort = candidate.start()
            httpServer = candidate
            log.info("Pika MCP listening on http://${PikaMcpHttpServer.LOOPBACK_ADDRESS}:$actualPort/mcp")
        } catch (error: Throwable) {
            candidate.stop()
            log.warn("Unable to start Pika MCP on port $port", error)
        }
    }

    override fun dispose() {
        httpServer?.stop()
        httpServer = null
    }

    private fun configuredPort(): Int {
        val configured = System.getProperty(PORT_PROPERTY)
            ?: System.getenv(PORT_ENVIRONMENT_VARIABLE)
            ?: return PikaMcpHttpServer.DEFAULT_PORT
        val port = configured.toIntOrNull()
        if (port == null || port !in 0..65535) {
            log.warn(
                "Invalid Pika MCP port '$configured'; using ${PikaMcpHttpServer.DEFAULT_PORT}",
            )
            return PikaMcpHttpServer.DEFAULT_PORT
        }
        return port
    }

    companion object {
        const val PLUGIN_ID = "com.pika.ideaControlMcp"
        const val PORT_PROPERTY = "pika.mcp.port"
        const val PORT_ENVIRONMENT_VARIABLE = "PIKA_MCP_PORT"
    }
}
