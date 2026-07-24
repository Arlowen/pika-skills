package com.pika.idea.mcp

import com.pika.idea.mcp.tools.IdeaListChangelistsTool
import com.pika.idea.mcp.tools.IdeaListServicesTool
import com.pika.idea.mcp.tools.IdeaMoveChangesToChangelistTool
import com.pika.idea.mcp.tools.IdeaStartServiceTool
import com.pika.idea.mcp.tools.IdeaStopServiceTool
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.ide.mcp.NoArgs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class ToolRegistrationTest {
    @Test
    fun `all public tool names are stable and unique`() {
        val tools = listOf(
            IdeaListServicesTool(),
            IdeaStartServiceTool(),
            IdeaStopServiceTool(),
            IdeaListChangelistsTool(),
            IdeaMoveChangesToChangelistTool(),
        )

        assertEquals(
            setOf(
                "idea_list_services",
                "idea_start_service",
                "idea_stop_service",
                "idea_list_changelists",
                "idea_move_changes_to_changelist",
            ),
            tools.map { it.name }.toSet(),
        )
        assertEquals(tools.size, tools.map { it.name }.distinct().size)
        assertTrue(tools.all { it.description.isNotBlank() })
    }

    @Test
    fun `no argument tools use MCP server NoArgs for schema compatibility`() {
        val tools = listOf(
            IdeaListServicesTool(),
            IdeaListChangelistsTool(),
        )

        assertTrue(tools.all { it.argKlass == NoArgs::class })
    }

    @Test
    fun `start schema requires only configuration name`() {
        val descriptor = IdeaStartServiceTool().serializer.descriptor

        assertEquals(
            listOf("configName", "mode", "allowMultiple", "startTimeoutSeconds"),
            (0 until descriptor.elementsCount).map(descriptor::getElementName),
        )
        assertEquals(false, descriptor.isElementOptional(0))
        assertEquals(true, descriptor.isElementOptional(1))
        assertEquals(true, descriptor.isElementOptional(2))
        assertEquals(true, descriptor.isElementOptional(3))
    }

    @Test
    fun `move schema exposes string array and safe optional switches`() {
        val descriptor = IdeaMoveChangesToChangelistTool().serializer.descriptor

        assertEquals(
            listOf("changelistName", "paths", "createIfMissing", "allOrNothing"),
            (0 until descriptor.elementsCount).map(descriptor::getElementName),
        )
        assertEquals("kotlin.collections.ArrayList", descriptor.getElementDescriptor(1).serialName)
        assertEquals(false, descriptor.isElementOptional(0))
        assertEquals(false, descriptor.isElementOptional(1))
        assertEquals(true, descriptor.isElementOptional(2))
        assertEquals(true, descriptor.isElementOptional(3))
    }
}
