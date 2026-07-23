package com.pika.idea.mcp

import com.pika.idea.mcp.model.MoveChangesArgs
import com.pika.idea.mcp.model.MoveChangesArgsSerializer
import com.pika.idea.mcp.model.StartServiceArgs
import com.pika.idea.mcp.model.StartServiceArgsSerializer
import com.pika.idea.mcp.model.StopServiceArgs
import com.pika.idea.mcp.model.StopServiceArgsSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolModelSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = false
    }

    @Test
    fun `start args default to debug and single instance`() {
        val args = json.decodeFromString(
            StartServiceArgsSerializer,
            """{"configName":"Console"}""",
        )

        assertEquals("DEBUG", args.mode)
        assertEquals(false, args.allowMultiple)
        assertEquals(30, args.startTimeoutSeconds)
    }

    @Test
    fun `stop args wait for termination by default`() {
        val args = json.decodeFromString(
            StopServiceArgsSerializer,
            """{"executionId":42}""",
        )

        assertEquals(42, args.executionId)
        assertEquals(true, args.waitForTermination)
        assertEquals(30, args.stopTimeoutSeconds)
    }

    @Test
    fun `move args are safe by default`() {
        val args = json.decodeFromString(
            MoveChangesArgsSerializer,
            """{"changelistName":"Backend","paths":["server/App.kt"]}""",
        )

        assertEquals(true, args.createIfMissing)
        assertEquals(true, args.allOrNothing)
        assertEquals(listOf("server/App.kt"), args.paths)
    }
}
