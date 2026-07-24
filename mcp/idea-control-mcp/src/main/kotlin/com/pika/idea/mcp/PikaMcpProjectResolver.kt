package com.pika.idea.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.InvalidPathException
import java.nio.file.Paths

internal object PikaMcpProjectResolver {
    fun resolve(projectPath: String?): Project {
        val projects = ProjectManager.getInstance().openProjects
            .filterNot { it.isDisposed }

        if (projectPath.isNullOrBlank()) {
            return when (projects.size) {
                0 -> throw IllegalStateException("No IDEA project is currently open")
                1 -> projects.single()
                else -> throw IllegalArgumentException(
                    "Multiple IDEA projects are open; pass projectPath explicitly. Open projects: " +
                        projects.joinToString { it.basePath ?: it.name },
                )
            }
        }

        val requestedPath = normalize(projectPath)
        return projects.firstOrNull { project ->
            project.basePath?.let { FileUtil.pathsEqual(normalize(it), requestedPath) } == true
        } ?: throw IllegalArgumentException(
            "No open IDEA project matches projectPath '$projectPath'. Open projects: " +
                projects.joinToString { it.basePath ?: it.name },
        )
    }

    private fun normalize(path: String): String =
        try {
            Paths.get(path).toAbsolutePath().normalize().toString()
        } catch (error: InvalidPathException) {
            throw IllegalArgumentException("Invalid projectPath '$path'", error)
        }
}
