package com.pika.idea.control.model

data class StartServiceArgs(
    val configName: String,
    val mode: String = "DEBUG",
    val allowMultiple: Boolean = false,
    val startTimeoutSeconds: Int = 30,
)

data class StopServiceArgs(
    val executionId: Long,
    val waitForTermination: Boolean = true,
    val stopTimeoutSeconds: Int = 30,
)

data class MoveChangesArgs(
    val changelistName: String,
    val paths: List<String>,
    val createIfMissing: Boolean = true,
    val allOrNothing: Boolean = true,
)

data class DeleteChangelistArgs(
    val changelistId: String,
)

data class ProjectInfo(
    val name: String,
    val basePath: String?,
)

data class ProjectsResult(
    val projects: List<ProjectInfo>,
)

data class ConfigurationInfo(
    val name: String,
    val uniqueId: String,
    val typeId: String,
    val typeName: String,
    val temporary: Boolean,
    val supportsRun: Boolean,
    val supportsDebug: Boolean,
    val runningExecutionIds: List<Long>,
)

data class ExecutionInfo(
    val executionId: Long,
    val configurationName: String,
    val configurationId: String? = null,
    val displayName: String,
    val mode: String,
    val state: String,
    val exitCode: Int? = null,
)

data class ServicesResult(
    val configurations: List<ConfigurationInfo>,
    val executions: List<ExecutionInfo>,
)

data class StartServiceResult(
    val state: String,
    val execution: ExecutionInfo,
    val message: String? = null,
)

data class StopServiceResult(
    val state: String,
    val executionId: Long,
    val exitCode: Int? = null,
)

data class ChangeInfo(
    val type: String,
    val beforePath: String? = null,
    val afterPath: String? = null,
)

data class ChangelistInfo(
    val id: String,
    val name: String,
    val comment: String,
    val isDefault: Boolean,
    val isReadOnly: Boolean,
    val changes: List<ChangeInfo>,
)

data class ChangelistsResult(
    val changelists: List<ChangelistInfo>,
    val unversionedPaths: List<String>,
)

data class MoveChangesResult(
    val changelistName: String,
    val movedPaths: List<String>,
    val unmatchedPaths: List<String>,
    val created: Boolean,
)

data class DeleteChangelistResult(
    val deletedChangelistId: String,
    val deletedChangelistName: String,
    val movedToChangelistId: String,
    val movedToChangelistName: String,
    val movedPaths: List<String>,
    val deleted: Boolean,
)

data class ApiError(
    val code: String,
    val message: String,
)

data class ApiErrorResponse(
    val error: ApiError,
)
