package com.pika.idea.mcp

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.ide.mcp.Response

internal object ToolSupport {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun success(value: Any): Response = Response(status = gson.toJson(value))

    fun failure(message: String): Response = Response(error = message)

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
