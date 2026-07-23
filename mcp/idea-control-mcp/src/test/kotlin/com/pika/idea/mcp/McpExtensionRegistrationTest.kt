package com.pika.idea.mcp

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.mcpserverplugin.AbstractMcpTool

class McpExtensionRegistrationTest : BasePlatformTestCase() {
    fun testAllIdeaControlToolsAreLoadedByMcpServerExtensionPoint() {
        val extensionPoint =
            ExtensionPointName.create<AbstractMcpTool<*>>("com.intellij.mcpServer.mcpTool")
        val toolNames = extensionPoint.extensionList
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
}
