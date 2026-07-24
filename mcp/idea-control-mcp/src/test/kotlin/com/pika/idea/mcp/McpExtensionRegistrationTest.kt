package com.pika.idea.mcp

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.ide.mcp.MCPService
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

class McpExtensionRegistrationTest : BasePlatformTestCase() {
    fun testAllIdeaControlToolsAreLoadedByMcpServerExtensionPoint() {
        val toolNames = ideaControlTools()
            .filter { it.javaClass.name.startsWith("com.pika.idea.mcp.tools.") }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf(
                "idea_list_services",
                "idea_start_service",
                "idea_stop_service",
                "idea_list_changelists",
                "idea_move_changes_to_changelist",
            ),
            toolNames,
        )
        assertNotNull(project.getService(ExecutionRegistry::class.java))
    }

    fun testMcpServerCanGenerateSchemasForAllIdeaControlTools() {
        val schemaMethod = MCPService::class.java
            .getDeclaredMethod("schemaFromDataClass", KClass::class.java)
            .apply { isAccessible = true }
        val mcpService = MCPService()

        ideaControlTools().forEach { tool ->
            assertNotNull(
                "MCP Server failed to generate the argument schema for ${tool.name}",
                schemaMethod.invoke(mcpService, tool.argKlass),
            )
        }
    }

    fun testExecutionRegistryAcceptsNullProcessNotStartedCause() {
        val registry = project.getService(ExecutionRegistry::class.java)
        val environment = ExecutionEnvironment().apply {
            assignNewExecutionId()
        }

        registry.executionListener.processNotStarted("Debug", environment, null)
    }

    fun testStartCallbackAcceptsNullProcessStartedDescriptor() {
        val started = CompletableFuture<RunContentDescriptor?>()

        StartCallback("DmAloneLauncher", started).processStarted(null)

        assertTrue(started.isDone)
        assertNull(started.get())
    }

    private fun ideaControlTools(): List<AbstractMcpTool<*>> =
        ExtensionPointName
            .create<AbstractMcpTool<*>>("com.intellij.mcpServer.mcpTool")
            .extensionList
            .filter { it.javaClass.name.startsWith("com.pika.idea.mcp.tools.") }
}
