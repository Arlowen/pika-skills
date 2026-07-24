package com.pika.idea.control

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager

internal object ControlSupport {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun json(value: Any): String = gson.toJson(value)

    fun jsonElement(value: Any): JsonElement = gson.toJsonTree(value)

    fun jsonObject(value: Any): JsonObject =
        jsonElement(value).takeIf { it.isJsonObject }?.asJsonObject
            ?: error("Expected an object result but got ${value::class.simpleName}")

    fun messageOf(error: Throwable): String =
        when (error) {
            is IllegalArgumentException -> error.message ?: "Invalid arguments"
            is IllegalStateException -> error.message ?: "IDE state does not allow this operation"
            else -> "${error::class.simpleName}: ${error.message ?: "Unexpected error"}"
        }

    fun <T> onEdt(action: () -> T): T {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            return action()
        }

        var result: Result<T>? = null
        application.invokeAndWait {
            result = runCatching(action)
        }
        return requireNotNull(result).getOrThrow()
    }
}
