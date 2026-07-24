package com.pika.idea.control

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId

internal class PikaIdeaServerService : Disposable {
    private val log = Logger.getInstance(PikaIdeaServerService::class.java)
    private val pluginVersion =
        PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "dev"

    @Volatile
    private var httpServer: PikaIdeaHttpServer? = null

    val boundPort: Int?
        get() = httpServer?.boundPort

    @Synchronized
    fun ensureStarted() {
        if (httpServer != null) {
            return
        }

        val port = configuredPort()
        val candidate = PikaIdeaHttpServer(
            requestedPort = port,
            api = PikaIdeaApi(),
            pluginVersion = pluginVersion,
        )
        try {
            val actualPort = candidate.start()
            httpServer = candidate
            log.info(
                "Pika Control listening on " +
                    "http://${PikaIdeaHttpServer.LOOPBACK_ADDRESS}:$actualPort${PikaIdeaApi.API_BASE_PATH}",
            )
        } catch (error: Throwable) {
            candidate.stop()
            log.warn("Unable to start Pika Control on port $port", error)
        }
    }

    override fun dispose() {
        httpServer?.stop()
        httpServer = null
    }

    private fun configuredPort(): Int {
        val configured = System.getProperty(PORT_PROPERTY)
            ?: System.getenv(PORT_ENVIRONMENT_VARIABLE)
            ?: return PikaIdeaHttpServer.DEFAULT_PORT
        val port = configured.toIntOrNull()
        if (port == null || port !in 0..65535) {
            log.warn(
                "Invalid Pika Control port '$configured'; using ${PikaIdeaHttpServer.DEFAULT_PORT}",
            )
            return PikaIdeaHttpServer.DEFAULT_PORT
        }
        return port
    }

    companion object {
        // Kept stable so upgrading an existing installation replaces the old plugin in place.
        const val PLUGIN_ID = "com.pika.ideaControlMcp"
        const val PORT_PROPERTY = "pika.idea.port"
        const val PORT_ENVIRONMENT_VARIABLE = "PIKA_IDEA_PORT"
    }
}
