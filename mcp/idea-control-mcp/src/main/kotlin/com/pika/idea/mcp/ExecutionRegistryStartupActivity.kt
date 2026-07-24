package com.pika.idea.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

internal class ExecutionRegistryStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        project.getService(ExecutionRegistry::class.java)
        ApplicationManager.getApplication()
            .getService(PikaMcpServerService::class.java)
            .ensureStarted()
    }
}
