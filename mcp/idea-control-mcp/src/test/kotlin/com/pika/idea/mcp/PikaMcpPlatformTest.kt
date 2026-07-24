package com.pika.idea.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class PikaMcpPlatformTest : BasePlatformTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun testPluginDescriptorHasNoJetBrainsMcpDependency() {
        val descriptor = javaClass.getResourceAsStream("/META-INF/plugin.xml")
            ?.bufferedReader()
            ?.use { it.readText() }

        assertNotNull(descriptor)
        assertFalse(descriptor!!.contains("com.intellij.mcpServer"))
        assertTrue(descriptor.contains(PikaMcpServerService::class.java.name))
    }

    fun testExecutionRegistryProjectServiceIsAvailable() {
        assertNotNull(project.getService(ExecutionRegistry::class.java))
    }

    fun testListServicesToolRunsAgainstTheOpenTestProject() {
        val arguments = JsonObject().apply {
            project.basePath?.let { addProperty("projectPath", it) }
        }

        val result = PikaMcpToolRegistry().call("idea_list_services", arguments)

        assertTrue(result.has("configurations"))
        assertTrue(result.has("executions"))
    }

    fun testIndependentMcpApplicationServiceStartsOnLoopback() {
        val service = ApplicationManager.getApplication()
            .getService(PikaMcpServerService::class.java)

        service.ensureStarted()

        assertNotNull(service.boundPort)
        assertTrue(service.boundPort!! > 0)
    }

    fun testHttpToolCallReachesIdeaProjectServiceEndToEnd() {
        val service = ApplicationManager.getApplication()
            .getService(PikaMcpServerService::class.java)
        service.ensureStarted()
        val port = requireNotNull(service.boundPort)
        val arguments = JsonObject().apply {
            project.basePath?.let { addProperty("projectPath", it) }
        }
        val requestBody = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 7)
            addProperty("method", "tools/call")
            add(
                "params",
                JsonObject().apply {
                    addProperty("name", "idea_list_services")
                    add("arguments", arguments)
                },
            )
        }
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PikaMcpProtocol.LATEST_PROTOCOL_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build()

        val response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())
        val result = JsonParser.parseString(response.body())
            .asJsonObject
            .getAsJsonObject("result")

        assertEquals(200, response.statusCode())
        assertFalse(result.get("isError").asBoolean)
        assertTrue(result.getAsJsonObject("structuredContent").has("configurations"))
        assertTrue(result.getAsJsonObject("structuredContent").has("executions"))
    }
}
