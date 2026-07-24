package com.pika.idea.control

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.pika.idea.control.model.DeleteChangelistArgs
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class PikaIdeaPlatformTest : BasePlatformTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun testPluginDescriptorRegistersIndependentRestService() {
        val descriptor = javaClass.getResourceAsStream("/META-INF/plugin.xml")
            ?.bufferedReader()
            ?.use { it.readText() }

        assertNotNull(descriptor)
        assertTrue(descriptor!!.contains("<name>Pika Control</name>"))
        assertTrue(descriptor.contains(PikaIdeaServerService::class.java.name))
        assertTrue(descriptor.contains(ExecutionRegistry::class.java.name))
    }

    fun testExecutionRegistryProjectServiceIsAvailable() {
        assertNotNull(project.getService(ExecutionRegistry::class.java))
    }

    fun testListServicesRunsAgainstTheOpenTestProject() {
        val result = ServiceController().list(project)

        assertNotNull(result.configurations)
        assertNotNull(result.executions)
    }

    fun testApplicationServiceStartsOnLoopback() {
        val service = ApplicationManager.getApplication()
            .getService(PikaIdeaServerService::class.java)

        service.ensureStarted()

        assertNotNull(service.boundPort)
        assertTrue(service.boundPort!! > 0)
    }

    fun testRestRequestReachesIdeaProjectServiceEndToEnd() {
        val service = ApplicationManager.getApplication()
            .getService(PikaIdeaServerService::class.java)
        service.ensureStarted()
        val port = requireNotNull(service.boundPort)
        val projectPath = URLEncoder.encode(requireNotNull(project.basePath), StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/api/v1/services?projectPath=$projectPath"))
            .GET()
            .build()

        val response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())
        val result = JsonParser.parseString(response.body()).asJsonObject

        assertEquals(200, response.statusCode())
        assertTrue(result.has("configurations"))
        assertTrue(result.has("executions"))
    }

    fun testProjectsEndpointListsTheOpenTestProject() {
        val service = ApplicationManager.getApplication()
            .getService(PikaIdeaServerService::class.java)
        service.ensureStarted()
        val port = requireNotNull(service.boundPort)
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/api/v1/projects"))
            .GET()
            .build()

        val response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())
        val projects = JsonParser.parseString(response.body())
            .asJsonObject
            .getAsJsonArray("projects")

        assertEquals(200, response.statusCode())
        assertTrue(
            projects.any { item ->
                item.asJsonObject.get("basePath").asString == project.basePath
            },
        )
    }

    fun testDefaultChangelistCanNeverBeDeleted() {
        val manager = ChangeListManager.getInstance(project)
        val default = manager.defaultChangeList
        val defaultId = default.id ?: default.name

        try {
            ChangelistController().delete(project, DeleteChangelistArgs(defaultId))
            fail("Deleting the default changelist should fail")
        } catch (error: IllegalArgumentException) {
            assertEquals("The default changelist can never be deleted", error.message)
        }

        assertTrue(manager.defaultChangeList.isDefault)
        assertEquals(defaultId, manager.defaultChangeList.id ?: manager.defaultChangeList.name)
    }

    fun testEmptyNonDefaultChangelistIsDeletedById() {
        val manager = ChangeListManager.getInstance(project)
        val created = ControlSupport.onEdt {
            manager.addChangeList("Pika disposable test list", "")
        }
        val createdId = created.id ?: created.name

        val result = ChangelistController().delete(
            project,
            DeleteChangelistArgs(createdId),
        )

        assertTrue(result.deleted)
        assertEquals(createdId, result.deletedChangelistId)
        assertEquals(manager.defaultChangeList.name, result.movedToChangelistName)
        assertEmpty(result.movedPaths)
        assertNull(manager.changeLists.firstOrNull { (it.id ?: it.name) == createdId })
    }
}
