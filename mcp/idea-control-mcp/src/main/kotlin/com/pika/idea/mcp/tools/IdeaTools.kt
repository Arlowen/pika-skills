package com.pika.idea.mcp.tools

import com.intellij.openapi.project.Project
import com.pika.idea.mcp.ChangelistController
import com.pika.idea.mcp.ServiceController
import com.pika.idea.mcp.ToolSupport
import com.pika.idea.mcp.model.MoveChangesArgs
import com.pika.idea.mcp.model.MoveChangesArgsSerializer
import com.pika.idea.mcp.model.StartServiceArgs
import com.pika.idea.mcp.model.StartServiceArgsSerializer
import com.pika.idea.mcp.model.StopServiceArgs
import com.pika.idea.mcp.model.StopServiceArgsSerializer
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

class IdeaListServicesTool :
    AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name: String = "idea_list_services"

    override val description: String =
        "List IDEA Run/Debug configurations and active executions. Returns stable executionId values; " +
            "use an executionId with idea_stop_service so same-name instances are not confused."

    override fun handle(project: Project, args: NoArgs): Response =
        runCatching { ToolSupport.success(ServiceController().list(project)) }
            .getOrElse { ToolSupport.failure(ToolSupport.messageOf(it)) }
}

class IdeaStartServiceTool :
    AbstractMcpTool<StartServiceArgs>(StartServiceArgsSerializer) {
    override val name: String = "idea_start_service"

    override val description: String =
        "Start one exact IDEA Run Configuration in RUN or DEBUG mode. DEBUG is the default. " +
            "Refuses duplicate instances unless allowMultiple=true and returns the executionId."

    override fun handle(project: Project, args: StartServiceArgs): Response =
        runCatching { ToolSupport.success(ServiceController().start(project, args)) }
            .getOrElse { ToolSupport.failure(ToolSupport.messageOf(it)) }
}

class IdeaStopServiceTool :
    AbstractMcpTool<StopServiceArgs>(StopServiceArgsSerializer) {
    override val name: String = "idea_stop_service"

    override val description: String =
        "Gracefully stop one IDEA Run/Debug execution by the executionId returned by " +
            "idea_list_services or idea_start_service. Can wait for confirmed termination."

    override fun handle(project: Project, args: StopServiceArgs): Response =
        runCatching { ToolSupport.success(ServiceController().stop(project, args)) }
            .getOrElse { ToolSupport.failure(ToolSupport.messageOf(it)) }
}

class IdeaListChangelistsTool :
    AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name: String = "idea_list_changelists"

    override val description: String =
        "List IDEA changelists, their tracked changes, and unversioned paths. " +
            "This tool does not create commits or modify files."

    override fun handle(project: Project, args: NoArgs): Response =
        runCatching { ToolSupport.success(ChangelistController().list(project)) }
            .getOrElse { ToolSupport.failure(ToolSupport.messageOf(it)) }
}

class IdeaMoveChangesToChangelistTool :
    AbstractMcpTool<MoveChangesArgs>(MoveChangesArgsSerializer) {
    override val name: String = "idea_move_changes_to_changelist"

    override val description: String =
        "Move tracked changes for explicit project-relative paths to an IDEA changelist. " +
            "Creates the changelist by default, is all-or-nothing by default, and never moves " +
            "unversioned files, commits, or pushes."

    override fun handle(project: Project, args: MoveChangesArgs): Response =
        runCatching { ToolSupport.success(ChangelistController().move(project, args)) }
            .getOrElse { ToolSupport.failure(ToolSupport.messageOf(it)) }
}
