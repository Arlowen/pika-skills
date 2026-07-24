package com.pika.idea.control

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

internal class ExecutionRegistry(project: Project) {
    private val records = ConcurrentHashMap<Long, ExecutionRecord>()

    internal val executionListener: ExecutionListener =
        object : ExecutionListener {
            override fun processStartScheduled(
                executorId: String,
                environment: ExecutionEnvironment,
            ) {
                register(environment)
            }

            override fun processStarting(
                executorId: String,
                environment: ExecutionEnvironment,
            ) {
                register(environment)
            }

            override fun processStarted(
                executorId: String,
                environment: ExecutionEnvironment,
                handler: com.intellij.execution.process.ProcessHandler,
            ) {
                register(environment)
            }

            override fun processNotStarted(
                executorId: String,
                environment: ExecutionEnvironment,
            ) {
                records.remove(environment.executionId)
            }

            override fun processNotStarted(
                executorId: String,
                environment: ExecutionEnvironment,
                cause: Throwable?,
            ) {
                records.remove(environment.executionId)
            }

            override fun processTerminated(
                executorId: String,
                environment: ExecutionEnvironment,
                handler: com.intellij.execution.process.ProcessHandler,
                exitCode: Int,
            ) {
                records.remove(environment.executionId)
            }
        }

    init {
        project.messageBus.connect(project).subscribe(
            ExecutionManager.EXECUTION_TOPIC,
            executionListener,
        )
    }

    fun register(environment: ExecutionEnvironment) {
        val settings = environment.runnerAndConfigurationSettings ?: return
        records[environment.executionId] = ExecutionRecord(
            configurationName = settings.name,
            configurationId = settings.uniqueID,
            mode = executorMode(environment.executor.id),
        )
    }

    fun find(executionId: Long): ExecutionRecord? = records[executionId]

    private fun executorMode(executorId: String): String =
        when (executorId) {
            DefaultDebugExecutor.EXECUTOR_ID -> "DEBUG"
            DefaultRunExecutor.EXECUTOR_ID -> "RUN"
            else -> executorId
        }

    data class ExecutionRecord(
        val configurationName: String,
        val configurationId: String,
        val mode: String,
    )
}
