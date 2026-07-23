package com.pika.idea.mcp.model

data object NoArgs

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
