package com.pika.idea.control

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.pika.idea.control.model.ChangeInfo
import com.pika.idea.control.model.ChangelistInfo
import com.pika.idea.control.model.ChangelistsResult
import com.pika.idea.control.model.DeleteChangelistArgs
import com.pika.idea.control.model.DeleteChangelistResult
import com.pika.idea.control.model.MoveChangesArgs
import com.pika.idea.control.model.MoveChangesResult
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class ChangelistController {
    fun list(project: Project): ChangelistsResult {
        ensureProject(project)
        val manager = refreshedManager(project)
        return ChangelistsResult(
            changelists = manager.changeLists.map { list ->
                ChangelistInfo(
                    id = list.id ?: list.name,
                    name = list.name,
                    comment = list.comment.orEmpty(),
                    isDefault = list.isDefault,
                    isReadOnly = list.isReadOnly,
                    changes = list.changes
                        .map { it.toInfo(project) }
                        .sortedWith(compareBy({ it.afterPath ?: it.beforePath }, ChangeInfo::type)),
                )
            }.sortedWith(compareByDescending<ChangelistInfo> { it.isDefault }.thenBy { it.name }),
            unversionedPaths = manager.unversionedFilesPaths
                .map { projectRelative(project, it.path) }
                .sorted(),
        )
    }

    fun move(project: Project, args: MoveChangesArgs): MoveChangesResult {
        ensureProject(project)
        val targetName = args.changelistName.trim()
        require(targetName.isNotEmpty()) { "changelistName must not be blank" }
        require(args.paths.isNotEmpty()) { "paths must contain at least one tracked path" }

        val requested = args.paths
            .map { normalizeRequestedPath(project, it) }
            .distinct()
        val manager = refreshedManager(project)
        check(manager.areChangeListsEnabled()) { "IDEA changelists are not enabled for this project" }

        val requestedSet = requested.toSet()
        val matchedChanges = manager.allChanges
            .filter { change -> change.normalizedPaths(project).any(requestedSet::contains) }
            .distinct()
        val matchedPaths = matchedChanges
            .flatMap { it.normalizedPaths(project) }
            .filter(requestedSet::contains)
            .distinct()
        val unmatched = requested.filterNot(matchedPaths.toSet()::contains)

        if (args.allOrNothing && unmatched.isNotEmpty()) {
            throw IllegalArgumentException(
                "No tracked IDEA change found for: ${unmatched.joinToString()}; nothing was moved",
            )
        }
        require(matchedChanges.isNotEmpty()) {
            "None of the requested paths are tracked changes; unversioned files are intentionally not moved"
        }

        val existing = manager.findChangeList(targetName)
        if (existing == null && !args.createIfMissing) {
            throw IllegalArgumentException("Changelist '$targetName' does not exist")
        }
        if (existing?.isReadOnly == true) {
            throw IllegalArgumentException("Changelist '$targetName' is read-only")
        }

        val created = existing == null
        ControlSupport.onEdt {
            val target = manager.findChangeList(targetName)
                ?: manager.addChangeList(targetName, "")
            manager.moveChangesTo(target, matchedChanges)
        }

        return MoveChangesResult(
            changelistName = targetName,
            movedPaths = matchedPaths.sorted(),
            unmatchedPaths = unmatched,
            created = created,
        )
    }

    fun delete(project: Project, args: DeleteChangelistArgs): DeleteChangelistResult {
        ensureProject(project)
        val changelistId = args.changelistId.trim()
        require(changelistId.isNotEmpty()) { "changelistId must not be blank" }

        val manager = refreshedManager(project)
        val source = manager.changeLists.singleOrNull { list ->
            stableId(list) == changelistId
        } ?: throw IllegalArgumentException("Changelist ID '$changelistId' was not found")

        require(!source.isDefault) { "The default changelist can never be deleted" }
        require(!source.isReadOnly) { "Changelist '${source.name}' is read-only" }

        val default = manager.defaultChangeList
        val changes = source.changes.toList()
        val movedPaths = changes
            .flatMap { it.normalizedPaths(project) }
            .distinct()
            .sorted()

        ControlSupport.onEdt {
            if (changes.isNotEmpty()) {
                manager.moveChangesTo(default, changes)
            }
            manager.removeChangeList(source)
        }

        check(manager.changeLists.none { stableId(it) == changelistId }) {
            "IDEA did not delete changelist '${source.name}'"
        }

        return DeleteChangelistResult(
            deletedChangelistId = changelistId,
            deletedChangelistName = source.name,
            movedToChangelistId = stableId(default),
            movedToChangelistName = default.name,
            movedPaths = movedPaths,
            deleted = true,
        )
    }

    private fun refreshedManager(project: Project): ChangeListManager {
        val manager = ChangeListManager.getInstance(project)
        if (ApplicationManager.getApplication().isDispatchThread) {
            return manager
        }
        val updated = CompletableFuture<Unit>()
        manager.invokeAfterUpdate(false) {
            updated.complete(Unit)
        }
        updated.get(30, TimeUnit.SECONDS)
        return manager
    }

    private fun Change.toInfo(project: Project): ChangeInfo =
        ChangeInfo(
            type = type.name,
            beforePath = beforeRevision?.file?.path?.let { projectRelative(project, it) },
            afterPath = afterRevision?.file?.path?.let { projectRelative(project, it) },
        )

    private fun Change.normalizedPaths(project: Project): List<String> =
        listOfNotNull(
            beforeRevision?.file?.path,
            afterRevision?.file?.path,
        ).map { projectRelative(project, it) }.distinct()

    private fun normalizeRequestedPath(project: Project, rawPath: String): String {
        val value = rawPath.trim()
        require(value.isNotEmpty()) { "paths must not contain blank values" }
        val base = projectBase(project)
        val supplied = Path.of(value)
        val absolute = if (supplied.isAbsolute) supplied.normalize() else base.resolve(supplied).normalize()
        require(absolute.startsWith(base)) { "Path '$rawPath' is outside the current IDEA project" }
        return normalizeSeparators(base.relativize(absolute).toString())
    }

    private fun projectRelative(project: Project, rawPath: String): String {
        val base = projectBase(project)
        val absolute = Path.of(rawPath).toAbsolutePath().normalize()
        return if (absolute.startsWith(base)) {
            normalizeSeparators(base.relativize(absolute).toString())
        } else {
            normalizeSeparators(absolute.toString())
        }
    }

    private fun projectBase(project: Project): Path =
        project.basePath?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: throw IllegalStateException("The current IDEA project has no local base path")

    private fun normalizeSeparators(path: String): String = path.replace('\\', '/')

    private fun stableId(changelist: com.intellij.openapi.vcs.changes.LocalChangeList): String =
        changelist.id ?: changelist.name

    private fun ensureProject(project: Project) {
        check(!project.isDisposed) { "The current IDEA project is already disposed" }
    }
}
