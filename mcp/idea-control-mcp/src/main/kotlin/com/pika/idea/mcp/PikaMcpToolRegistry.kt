package com.pika.idea.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pika.idea.mcp.model.MoveChangesArgs
import com.pika.idea.mcp.model.StartServiceArgs
import com.pika.idea.mcp.model.StopServiceArgs

internal class PikaMcpToolRegistry {
    private val definitions: List<ToolDefinition> =
        listOf(
            ToolDefinition(
                name = "idea_list_services",
                description = "List IDEA Run/Debug configurations and active executions. Returns stable " +
                    "executionId values; use an executionId with idea_stop_service so same-name instances " +
                    "are not confused.",
                properties = projectPathProperty(),
                readOnly = true,
                idempotent = true,
            ),
            ToolDefinition(
                name = "idea_start_service",
                description = "Start one exact IDEA Run Configuration in RUN or DEBUG mode. DEBUG is the " +
                    "default. Refuses duplicate instances unless allowMultiple=true and returns the executionId.",
                properties = projectPathProperty() + mapOf(
                    "configName" to stringProperty("Exact IDEA Run Configuration name."),
                    "mode" to stringProperty("Execution mode.", listOf("RUN", "DEBUG"), "DEBUG"),
                    "allowMultiple" to booleanProperty(
                        "Whether to permit another active instance of the same configuration.",
                        false,
                    ),
                    "startTimeoutSeconds" to integerProperty(
                        "Seconds to wait for IDEA to expose a process descriptor.",
                        1,
                        120,
                        30,
                    ),
                ),
                required = setOf("configName"),
            ),
            ToolDefinition(
                name = "idea_stop_service",
                description = "Gracefully stop one IDEA Run/Debug execution by the executionId returned by " +
                    "idea_list_services or idea_start_service. Can wait for confirmed termination.",
                properties = projectPathProperty() + mapOf(
                    "executionId" to integerProperty(
                        "Exact execution identifier returned by a Pika MCP service tool.",
                        minimum = 1,
                    ),
                    "waitForTermination" to booleanProperty(
                        "Whether to wait until process termination is confirmed.",
                        true,
                    ),
                    "stopTimeoutSeconds" to integerProperty(
                        "Seconds to wait for confirmed process termination.",
                        1,
                        120,
                        30,
                    ),
                ),
                required = setOf("executionId"),
                destructive = true,
            ),
            ToolDefinition(
                name = "idea_list_changelists",
                description = "List IDEA changelists, their tracked changes, and unversioned paths. " +
                    "This tool does not create commits or modify files.",
                properties = projectPathProperty(),
                readOnly = true,
                idempotent = true,
            ),
            ToolDefinition(
                name = "idea_move_changes_to_changelist",
                description = "Move tracked changes for explicit project-relative paths to an IDEA changelist. " +
                    "Creates the changelist by default, is all-or-nothing by default, and never moves " +
                    "unversioned files, commits, or pushes.",
                properties = projectPathProperty() + mapOf(
                    "changelistName" to stringProperty("Target IDEA changelist name."),
                    "paths" to stringArrayProperty(
                        "Explicit project-relative or absolute paths inside the project.",
                    ),
                    "createIfMissing" to booleanProperty(
                        "Whether to create the changelist when it does not exist.",
                        true,
                    ),
                    "allOrNothing" to booleanProperty(
                        "Whether any unmatched path should prevent all moves.",
                        true,
                    ),
                ),
                required = setOf("changelistName", "paths"),
                idempotent = true,
            ),
        )

    fun definitionsJson(): JsonArray =
        JsonArray().apply {
            definitions.forEach { add(it.toJson()) }
        }

    fun call(name: String, arguments: JsonObject): JsonObject {
        val definition = definitions.firstOrNull { it.name == name }
            ?: throw UnknownMcpToolException(name)
        rejectUnknownArguments(arguments, definition.properties.keys)

        val project = PikaMcpProjectResolver.resolve(optionalString(arguments, "projectPath"))
        val result: Any =
            when (name) {
                "idea_list_services" -> ServiceController().list(project)
                "idea_start_service" ->
                    ServiceController().start(
                        project,
                        StartServiceArgs(
                            configName = requiredString(arguments, "configName"),
                            mode = optionalString(arguments, "mode") ?: "DEBUG",
                            allowMultiple = optionalBoolean(arguments, "allowMultiple") ?: false,
                            startTimeoutSeconds = optionalInt(arguments, "startTimeoutSeconds") ?: 30,
                        ),
                    )

                "idea_stop_service" ->
                    ServiceController().stop(
                        project,
                        StopServiceArgs(
                            executionId = requiredLong(arguments, "executionId"),
                            waitForTermination = optionalBoolean(arguments, "waitForTermination") ?: true,
                            stopTimeoutSeconds = optionalInt(arguments, "stopTimeoutSeconds") ?: 30,
                        ),
                    )

                "idea_list_changelists" -> ChangelistController().list(project)
                "idea_move_changes_to_changelist" ->
                    ChangelistController().move(
                        project,
                        MoveChangesArgs(
                            changelistName = requiredString(arguments, "changelistName"),
                            paths = requiredStringList(arguments, "paths"),
                            createIfMissing = optionalBoolean(arguments, "createIfMissing") ?: true,
                            allOrNothing = optionalBoolean(arguments, "allOrNothing") ?: true,
                        ),
                    )

                else -> throw UnknownMcpToolException(name)
            }

        return ToolSupport.jsonObject(result)
    }

    private fun rejectUnknownArguments(arguments: JsonObject, allowed: Set<String>) {
        val unknown = arguments.keySet() - allowed
        require(unknown.isEmpty()) {
            "Unknown arguments: ${unknown.sorted().joinToString()}"
        }
    }

    private fun requiredString(arguments: JsonObject, name: String): String =
        optionalString(arguments, name)
            ?: throw IllegalArgumentException("$name is required")

    private fun optionalString(arguments: JsonObject, name: String): String? {
        val value = arguments.get(name) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "$name must be a string"
        }
        return value.asString
    }

    private fun optionalBoolean(arguments: JsonObject, name: String): Boolean? {
        val value = arguments.get(name) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
            "$name must be a boolean"
        }
        return value.asBoolean
    }

    private fun optionalInt(arguments: JsonObject, name: String): Int? {
        val value = arguments.get(name) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "$name must be an integer"
        }
        return value.asInt
    }

    private fun requiredLong(arguments: JsonObject, name: String): Long {
        val value = arguments.get(name)
            ?: throw IllegalArgumentException("$name is required")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "$name must be an integer"
        }
        return value.asLong
    }

    private fun requiredStringList(arguments: JsonObject, name: String): List<String> {
        val value = arguments.get(name)
            ?: throw IllegalArgumentException("$name is required")
        require(value.isJsonArray) {
            "$name must be an array of strings"
        }
        return value.asJsonArray.mapIndexed { index, item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                "$name[$index] must be a string"
            }
            item.asString
        }
    }

    private data class ToolDefinition(
        val name: String,
        val description: String,
        val properties: Map<String, JsonObject>,
        val required: Set<String> = emptySet(),
        val readOnly: Boolean = false,
        val destructive: Boolean = false,
        val idempotent: Boolean = false,
    ) {
        fun toJson(): JsonObject =
            JsonObject().apply {
                addProperty("name", name)
                addProperty("title", name)
                addProperty("description", description)
                add(
                    "inputSchema",
                    JsonObject().apply {
                        addProperty("type", "object")
                        add(
                            "properties",
                            JsonObject().apply {
                                properties.forEach { (propertyName, propertySchema) ->
                                    add(propertyName, propertySchema)
                                }
                            },
                        )
                        add(
                            "required",
                            JsonArray().apply {
                                required.sorted().forEach { add(it) }
                            },
                        )
                        addProperty("additionalProperties", false)
                    },
                )
                add(
                    "annotations",
                    JsonObject().apply {
                        addProperty("readOnlyHint", readOnly)
                        addProperty("destructiveHint", destructive)
                        addProperty("idempotentHint", idempotent)
                        addProperty("openWorldHint", false)
                    },
                )
            }
    }

    private companion object {
        fun projectPathProperty(): Map<String, JsonObject> =
            mapOf(
                "projectPath" to stringProperty(
                    "Absolute path of the target open IDEA project. Optional when exactly one project is open.",
                ),
            )

        fun stringProperty(
            description: String,
            values: List<String> = emptyList(),
            defaultValue: String? = null,
        ): JsonObject =
            JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", description)
                if (values.isNotEmpty()) {
                    add(
                        "enum",
                        JsonArray().apply {
                            values.forEach { add(it) }
                        },
                    )
                }
                defaultValue?.let { addProperty("default", it) }
            }

        fun booleanProperty(description: String, defaultValue: Boolean): JsonObject =
            JsonObject().apply {
                addProperty("type", "boolean")
                addProperty("description", description)
                addProperty("default", defaultValue)
            }

        fun integerProperty(
            description: String,
            minimum: Int? = null,
            maximum: Int? = null,
            defaultValue: Int? = null,
        ): JsonObject =
            JsonObject().apply {
                addProperty("type", "integer")
                addProperty("description", description)
                minimum?.let { addProperty("minimum", it) }
                maximum?.let { addProperty("maximum", it) }
                defaultValue?.let { addProperty("default", it) }
            }

        fun stringArrayProperty(description: String): JsonObject =
            JsonObject().apply {
                addProperty("type", "array")
                addProperty("description", description)
                add(
                    "items",
                    JsonObject().apply {
                        addProperty("type", "string")
                    },
                )
                addProperty("minItems", 1)
            }
    }
}

internal class UnknownMcpToolException(
    toolName: String,
) : IllegalArgumentException("Unknown tool '$toolName'")
