package com.pika.idea.mcp

import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.pika.idea.mcp.model.ConfigurationInfo
import com.pika.idea.mcp.model.ExecutionInfo
import com.pika.idea.mcp.model.ServicesResult
import com.pika.idea.mcp.model.StartServiceArgs
import com.pika.idea.mcp.model.StartServiceResult
import com.pika.idea.mcp.model.StopServiceArgs
import com.pika.idea.mcp.model.StopServiceResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class ServiceController {
    fun list(project: Project): ServicesResult {
        ensureProject(project)
        val runManager = RunManager.getInstance(project)
        val registry = registry(project)
        val executions = activeDescriptors(project)
            .map { descriptor -> descriptorMapping(descriptor, runManager, registry) }
            .map { it.toExecutionInfo() }
            .sortedBy { it.executionId }

        val runningByConfig = executions
            .filter { it.configurationId != null }
            .groupBy { it.configurationId }
            .mapValues { (_, values) -> values.map { it.executionId }.sorted() }

        val runExecutor = DefaultRunExecutor.getRunExecutorInstance()
        val debugExecutor = DefaultDebugExecutor.getDebugExecutorInstance()
        val configurations = runManager.allSettings
            .filterNot { it.isTemplate }
            .map { settings ->
                ConfigurationInfo(
                    name = settings.name,
                    uniqueId = settings.uniqueID,
                    typeId = settings.type.id,
                    typeName = settings.type.displayName,
                    temporary = settings.isTemporary,
                    supportsRun = runnerFor(runExecutor, settings) != null,
                    supportsDebug = runnerFor(debugExecutor, settings) != null,
                    runningExecutionIds = runningByConfig[settings.uniqueID].orEmpty(),
                )
            }
            .sortedWith(compareBy(ConfigurationInfo::name, ConfigurationInfo::uniqueId))

        return ServicesResult(configurations, executions)
    }

    fun start(project: Project, args: StartServiceArgs): StartServiceResult {
        ensureProject(project)
        val configName = args.configName.trim()
        require(configName.isNotEmpty()) { "configName must not be blank" }
        require(args.startTimeoutSeconds in 1..120) {
            "startTimeoutSeconds must be between 1 and 120"
        }

        val runManager = RunManager.getInstance(project)
        val matches = runManager.allSettings.filter { it.name == configName && !it.isTemplate }
        require(matches.isNotEmpty()) { "Run configuration '$configName' was not found" }
        require(matches.size == 1) {
            "Run configuration name '$configName' is ambiguous; ${matches.size} configurations use it"
        }
        val settings = matches.single()
        val registry = registry(project)
        val current = activeDescriptors(project)
            .map { descriptor -> descriptorMapping(descriptor, runManager, registry) }
            .filter { it.settings?.uniqueID == settings.uniqueID }

        if (!args.allowMultiple && current.isNotEmpty()) {
            val mapping = current.minBy { it.descriptor.executionId }
            return StartServiceResult(
                state = "ALREADY_RUNNING",
                execution = mapping.toExecutionInfo(),
                message = "Set allowMultiple=true only when another instance is intentional",
            )
        }

        val executor = executor(args.mode)
        settings.checkSettings(executor)
        val runner = runnerFor(executor, settings)
            ?: throw IllegalArgumentException(
                "Run configuration '$configName' does not support ${executorMode(executor)} mode",
            )

        val started = CompletableFuture<RunContentDescriptor>()
        val callback = object : ProgramRunner.Callback {
            override fun processStarted(descriptor: RunContentDescriptor) {
                started.complete(descriptor)
            }

            override fun processNotStarted(error: Throwable?) {
                started.completeExceptionally(
                    error ?: IllegalStateException("IDEA did not start '$configName'"),
                )
            }
        }

        val environment = ToolSupport.onEdt {
            val built = ExecutionEnvironmentBuilder.create(executor, settings).build(callback)
            if (built.executionId == 0L) {
                built.assignNewExecutionId()
            }
            registry.register(built)
            runner.execute(built)
            built
        }

        return try {
            val descriptor = started.get(args.startTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            StartServiceResult(
                state = "RUNNING",
                execution = DescriptorMapping(
                    descriptor = descriptor,
                    settings = settings,
                    mode = executorMode(executor),
                ).toExecutionInfo(),
            )
        } catch (_: TimeoutException) {
            StartServiceResult(
                state = "STARTING",
                execution = environment.toExecutionInfo(settings),
                message = "IDEA accepted the start request but no process descriptor appeared before the timeout",
            )
        }
    }

    fun stop(project: Project, args: StopServiceArgs): StopServiceResult {
        ensureProject(project)
        require(args.executionId > 0) { "executionId must be positive" }
        require(args.stopTimeoutSeconds in 1..120) {
            "stopTimeoutSeconds must be between 1 and 120"
        }

        val descriptor = allDescriptors(project)
            .firstOrNull { it.executionId == args.executionId }
            ?: throw IllegalArgumentException(
                "Execution ${args.executionId} is not running in the current project",
            )
        val handler = descriptor.processHandler
            ?: return StopServiceResult("NO_PROCESS", args.executionId)

        if (handler.isProcessTerminated) {
            return StopServiceResult("TERMINATED", args.executionId, handler.exitCode)
        }

        if (!handler.isProcessTerminating) {
            ToolSupport.onEdt {
                handler.destroyProcess()
            }
        }

        if (!args.waitForTermination) {
            return StopServiceResult("STOPPING", args.executionId, handler.exitCode)
        }

        val stopped = handler.waitFor(TimeUnit.SECONDS.toMillis(args.stopTimeoutSeconds.toLong()))
        return StopServiceResult(
            state = if (stopped) "TERMINATED" else "STOPPING",
            executionId = args.executionId,
            exitCode = handler.exitCode,
        )
    }

    private fun descriptorMapping(
        descriptor: RunContentDescriptor,
        runManager: RunManager,
        registry: ExecutionRegistry,
    ): DescriptorMapping {
        val record = registry.find(descriptor.executionId)
        val settings = record?.configurationId?.let { configurationId ->
            runManager.allSettings.singleOrNull { it.uniqueID == configurationId }
        } ?: settingsForDisplayName(runManager, descriptor.displayName)
        return DescriptorMapping(
            descriptor = descriptor,
            settings = settings,
            mode = record?.mode ?: descriptorMode(descriptor),
        )
    }

    private fun activeDescriptors(project: Project): List<RunContentDescriptor> =
        allDescriptors(project)
            .filterNot { it.processHandler?.isProcessTerminated == true }

    private fun allDescriptors(project: Project): List<RunContentDescriptor> =
        ToolSupport.onEdt {
            RunContentManager.getInstance(project).allDescriptors.toList()
        }

    private fun settingsForDisplayName(
        runManager: RunManager,
        displayName: String,
    ): RunnerAndConfigurationSettings? {
        val exact = runManager.allSettings.filter { it.name == displayName }
        if (exact.size == 1) {
            return exact.single()
        }
        val numberedBase = displayName.replace(Regex("""\s+(?:\(\d+\)|\[\d+])$"""), "")
        return runManager.allSettings.filter { it.name == numberedBase }.singleOrNull()
    }

    private fun runnerFor(
        executor: Executor,
        settings: RunnerAndConfigurationSettings,
    ): ProgramRunner<*>? = ProgramRunner.getRunner(executor.id, settings.configuration)

    private fun executor(mode: String): Executor =
        when (mode.trim().uppercase()) {
            "RUN" -> DefaultRunExecutor.getRunExecutorInstance()
            "DEBUG" -> DefaultDebugExecutor.getDebugExecutorInstance()
            else -> throw IllegalArgumentException("mode must be RUN or DEBUG")
        }

    private fun executorMode(executor: Executor): String =
        when (executor.id) {
            DefaultDebugExecutor.EXECUTOR_ID -> "DEBUG"
            DefaultRunExecutor.EXECUTOR_ID -> "RUN"
            else -> executor.id
        }

    private fun descriptorMode(descriptor: RunContentDescriptor): String =
        when (descriptor.contentToolWindowId) {
            ToolWindowId.DEBUG -> "DEBUG"
            ToolWindowId.RUN -> "RUN"
            else -> "UNKNOWN"
        }

    private fun DescriptorMapping.toExecutionInfo(): ExecutionInfo {
        val handler = descriptor.processHandler
        val state = when {
            handler == null -> "STARTING"
            handler.isProcessTerminated -> "TERMINATED"
            handler.isProcessTerminating -> "STOPPING"
            else -> "RUNNING"
        }
        return ExecutionInfo(
            executionId = descriptor.executionId,
            configurationName = settings?.name ?: descriptor.displayName,
            configurationId = settings?.uniqueID,
            displayName = descriptor.displayName,
            mode = mode,
            state = state,
            exitCode = handler?.exitCode,
        )
    }

    private fun ExecutionEnvironment.toExecutionInfo(
        settings: RunnerAndConfigurationSettings,
    ): ExecutionInfo =
        ExecutionInfo(
            executionId = executionId,
            configurationName = settings.name,
            configurationId = settings.uniqueID,
            displayName = settings.name,
            mode = executorMode(executor),
            state = "STARTING",
        )

    private fun ensureProject(project: Project) {
        check(!project.isDisposed) { "The current IDEA project is already disposed" }
    }

    private fun registry(project: Project): ExecutionRegistry =
        project.getService(ExecutionRegistry::class.java)

    private data class DescriptorMapping(
        val descriptor: RunContentDescriptor,
        val settings: RunnerAndConfigurationSettings?,
        val mode: String,
    )
}
