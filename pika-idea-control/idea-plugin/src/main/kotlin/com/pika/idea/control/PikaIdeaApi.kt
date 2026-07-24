package com.pika.idea.control

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.pika.idea.control.model.ApiError
import com.pika.idea.control.model.ApiErrorResponse
import com.pika.idea.control.model.DeleteChangelistArgs
import com.pika.idea.control.model.MoveChangesArgs
import com.pika.idea.control.model.StartServiceArgs
import com.pika.idea.control.model.StopServiceArgs

internal data class ApiReply(
    val statusCode: Int,
    val body: JsonObject,
)

internal class PikaIdeaApi {
    fun handle(
        method: String,
        path: String,
        query: Map<String, List<String>>,
        requestBody: String?,
    ): ApiReply =
        try {
            route(method.uppercase(), path, query, requestBody)
        } catch (error: MethodNotAllowedException) {
            errorReply(405, "METHOD_NOT_ALLOWED", error.message ?: "Method is not allowed")
        } catch (error: JsonParseException) {
            errorReply(400, "INVALID_ARGUMENT", "Request body must be valid JSON")
        } catch (error: IllegalArgumentException) {
            errorReply(400, "INVALID_ARGUMENT", error.message ?: "Invalid request")
        } catch (error: IllegalStateException) {
            errorReply(409, "INVALID_STATE", error.message ?: "IDE state does not allow this operation")
        } catch (error: Throwable) {
            errorReply(500, "INTERNAL_ERROR", ControlSupport.messageOf(error))
        }

    private fun route(
        method: String,
        path: String,
        query: Map<String, List<String>>,
        requestBody: String?,
    ): ApiReply {
        val result: Any =
            when (path) {
                PROJECTS_PATH -> {
                    requireMethod(method, "GET")
                    requireNoBody(requestBody)
                    rejectUnknownQuery(query, emptySet())
                    PikaProjectResolver.list()
                }

                SERVICES_PATH -> {
                    requireMethod(method, "GET")
                    requireNoBody(requestBody)
                    rejectUnknownQuery(query, PROJECT_QUERY)
                    ServiceController().list(PikaProjectResolver.resolve(optionalQuery(query, "projectPath")))
                }

                START_SERVICE_PATH -> {
                    requireMethod(method, "POST")
                    rejectUnknownQuery(query, emptySet())
                    val body = parseBody(requestBody)
                    rejectUnknownFields(
                        body,
                        setOf(
                            "projectPath",
                            "configName",
                            "mode",
                            "allowMultiple",
                            "startTimeoutSeconds",
                        ),
                    )
                    ServiceController().start(
                        PikaProjectResolver.resolve(optionalString(body, "projectPath")),
                        StartServiceArgs(
                            configName = requiredString(body, "configName"),
                            mode = optionalString(body, "mode") ?: "DEBUG",
                            allowMultiple = optionalBoolean(body, "allowMultiple") ?: false,
                            startTimeoutSeconds = optionalInt(body, "startTimeoutSeconds") ?: 30,
                        ),
                    )
                }

                STOP_SERVICE_PATH -> {
                    requireMethod(method, "POST")
                    rejectUnknownQuery(query, emptySet())
                    val body = parseBody(requestBody)
                    rejectUnknownFields(
                        body,
                        setOf(
                            "projectPath",
                            "executionId",
                            "waitForTermination",
                            "stopTimeoutSeconds",
                        ),
                    )
                    ServiceController().stop(
                        PikaProjectResolver.resolve(optionalString(body, "projectPath")),
                        StopServiceArgs(
                            executionId = requiredLong(body, "executionId"),
                            waitForTermination = optionalBoolean(body, "waitForTermination") ?: true,
                            stopTimeoutSeconds = optionalInt(body, "stopTimeoutSeconds") ?: 30,
                        ),
                    )
                }

                CHANGELISTS_PATH -> {
                    requireMethod(method, "GET")
                    requireNoBody(requestBody)
                    rejectUnknownQuery(query, PROJECT_QUERY)
                    ChangelistController().list(
                        PikaProjectResolver.resolve(optionalQuery(query, "projectPath")),
                    )
                }

                MOVE_CHANGES_PATH -> {
                    requireMethod(method, "POST")
                    rejectUnknownQuery(query, emptySet())
                    val body = parseBody(requestBody)
                    rejectUnknownFields(
                        body,
                        setOf(
                            "projectPath",
                            "changelistName",
                            "paths",
                            "createIfMissing",
                            "allOrNothing",
                        ),
                    )
                    ChangelistController().move(
                        PikaProjectResolver.resolve(optionalString(body, "projectPath")),
                        MoveChangesArgs(
                            changelistName = requiredString(body, "changelistName"),
                            paths = requiredStringList(body, "paths"),
                            createIfMissing = optionalBoolean(body, "createIfMissing") ?: true,
                            allOrNothing = optionalBoolean(body, "allOrNothing") ?: true,
                        ),
                    )
                }

                DELETE_CHANGELIST_PATH -> {
                    requireMethod(method, "POST")
                    rejectUnknownQuery(query, emptySet())
                    val body = parseBody(requestBody)
                    rejectUnknownFields(body, setOf("projectPath", "changelistId"))
                    ChangelistController().delete(
                        PikaProjectResolver.resolve(optionalString(body, "projectPath")),
                        DeleteChangelistArgs(
                            changelistId = requiredString(body, "changelistId"),
                        ),
                    )
                }

                else -> return errorReply(404, "NOT_FOUND", "Unknown API path '$path'")
            }

        return ApiReply(200, ControlSupport.jsonObject(result))
    }

    private fun requireMethod(actual: String, expected: String) {
        if (actual != expected) {
            throw MethodNotAllowedException("Method $actual is not allowed; use $expected")
        }
    }

    private fun requireNoBody(requestBody: String?) {
        require(requestBody.isNullOrBlank()) { "GET requests must not include a body" }
    }

    private fun parseBody(requestBody: String?): JsonObject {
        require(!requestBody.isNullOrBlank()) { "Request body is required" }
        val parsed = JsonParser.parseString(requestBody)
        require(parsed.isJsonObject) { "Request body must be a JSON object" }
        return parsed.asJsonObject
    }

    private fun rejectUnknownFields(body: JsonObject, allowed: Set<String>) {
        val unknown = body.keySet() - allowed
        require(unknown.isEmpty()) { "Unknown fields: ${unknown.sorted().joinToString()}" }
    }

    private fun rejectUnknownQuery(query: Map<String, List<String>>, allowed: Set<String>) {
        val unknown = query.keys - allowed
        require(unknown.isEmpty()) { "Unknown query parameters: ${unknown.sorted().joinToString()}" }
    }

    private fun optionalQuery(query: Map<String, List<String>>, name: String): String? {
        val values = query[name] ?: return null
        require(values.size == 1) { "$name must be provided exactly once" }
        return values.single()
    }

    private fun requiredString(body: JsonObject, name: String): String =
        optionalString(body, name)
            ?: throw IllegalArgumentException("$name is required")

    private fun optionalString(body: JsonObject, name: String): String? {
        val value = body.get(name) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "$name must be a string"
        }
        return value.asString
    }

    private fun optionalBoolean(body: JsonObject, name: String): Boolean? {
        val value = body.get(name) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
            "$name must be a boolean"
        }
        return value.asBoolean
    }

    private fun optionalInt(body: JsonObject, name: String): Int? {
        val value = body.get(name) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "$name must be an integer"
        }
        val raw = value.asJsonPrimitive.asString
        requireInteger(raw, name)
        val parsed = raw.toLongOrNull()
        require(parsed != null && parsed in Int.MIN_VALUE..Int.MAX_VALUE) {
            "$name is outside the supported integer range"
        }
        return parsed.toInt()
    }

    private fun requiredLong(body: JsonObject, name: String): Long {
        val value = body.get(name)
            ?: throw IllegalArgumentException("$name is required")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "$name must be an integer"
        }
        val raw = value.asJsonPrimitive.asString
        requireInteger(raw, name)
        return raw.toLongOrNull()
            ?: throw IllegalArgumentException("$name is outside the supported integer range")
    }

    private fun requireInteger(raw: String, name: String) {
        require(INTEGER_PATTERN.matches(raw)) { "$name must be an integer" }
    }

    private fun requiredStringList(body: JsonObject, name: String): List<String> {
        val value = body.get(name)
            ?: throw IllegalArgumentException("$name is required")
        require(value.isJsonArray) { "$name must be an array of strings" }
        return value.asJsonArray.mapIndexed { index, item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                "$name[$index] must be a string"
            }
            item.asString
        }
    }

    private class MethodNotAllowedException(message: String) : IllegalArgumentException(message)

    companion object {
        const val API_BASE_PATH = "/api/v1"
        const val PROJECTS_PATH = "$API_BASE_PATH/projects"
        const val SERVICES_PATH = "$API_BASE_PATH/services"
        const val START_SERVICE_PATH = "$API_BASE_PATH/services/start"
        const val STOP_SERVICE_PATH = "$API_BASE_PATH/services/stop"
        const val CHANGELISTS_PATH = "$API_BASE_PATH/changelists"
        const val MOVE_CHANGES_PATH = "$API_BASE_PATH/changelists/move"
        const val DELETE_CHANGELIST_PATH = "$API_BASE_PATH/changelists/delete"

        private val PROJECT_QUERY = setOf("projectPath")
        private val INTEGER_PATTERN = Regex("-?(?:0|[1-9][0-9]*)")

        fun errorReply(statusCode: Int, code: String, message: String): ApiReply =
            ApiReply(
                statusCode,
                ControlSupport.jsonObject(ApiErrorResponse(ApiError(code, message))),
            )
    }
}
