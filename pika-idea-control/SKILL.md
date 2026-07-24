---
name: pika-idea-control
description: Control IntelliJ IDEA Run/Debug services and organize tracked changes into logical IDEA changelists through the local Pika Control plugin and its bundled Python helper. Requires the Pika Control IDEA plugin to be installed, enabled, and running before any operation. Use when the user asks Codex to list, start, stop, or restart IDEA services; needs exact multi-instance process control; asks to split tracked changes into IDEA changelists; or wants to list, move, or delete changelists without committing or pushing.
---

# Pika IDEA Control

This Skill is not a standalone IDEA integration. Require the separately installed `Pika Control`
IDEA plugin before using any workflow. `pika-idea-control` is the Codex Skill; `Pika Control`
is its IDEA-side dependency.

Use the bundled `scripts/pika_idea.py` helper for deterministic IDEA operations. Resolve its
absolute path relative to this `SKILL.md`; do not assume the current working directory is the
skill directory.

Read [references/idea-rest-api.md](references/idea-rest-api.md) when command arguments, result
states, or endpoint behavior are needed.

For installation or upgrade instructions, follow
[references/installation.md](references/installation.md).

## Install the required IDEA plugin

Before the first invocation:

1. Obtain `Pika-IDEA-Control-<version>.zip`:
   - Download it from [GitHub Releases](https://github.com/Arlowen/pika-skills/releases) when a
     release asset is available.
   - Otherwise, use JDK 21 to run `./gradlew buildPlugin` in `<skill-dir>/idea-plugin`; take the
     ZIP from `build/distributions/`.
2. Open `Settings/Preferences | Plugins` in IntelliJ IDEA.
3. Choose `Install Plugin from Disk`, select the ZIP without extracting it, and enable
   `Pika Control`.
4. Restart IDEA.
5. Run `python3 <skill-dir>/scripts/pika_idea.py health` and require a successful response.

Use [references/installation.md](references/installation.md) for full installation,
configuration, and troubleshooting details.

## Preconditions

1. Do not operate or restart the IDEA UI unless the user explicitly asks.
2. Run `python3 <skill-dir>/scripts/pika_idea.py health`.
3. If health fails, stop before running any other Skill command. Report the connection error,
   explain that the required `Pika Control` IDEA plugin may be missing, disabled, or not running,
   and provide the installation steps above. Do not fall back to UI automation.
4. Run `projects`; pass `--project <absolute-path>` to every project-scoped command when multiple
   IDEA projects are open.
5. Treat “group commits” as IDEA Changelist grouping unless the user explicitly requests Git
   staging, commits, or pushes.

## Control services

### List and resolve

1. Run `services`.
2. Resolve a Run Configuration by exact `name`.
3. If duplicate names exist, report their `uniqueId` values and do not guess.
4. Treat `executionId` as the identity of a running instance.

### Start

1. List services first.
2. Default to `DEBUG`; use `RUN` only when requested.
3. Keep duplicate protection enabled. Use `--allow-multiple` only for an explicitly requested
   additional instance.
4. Run `start --name <exact-name> --mode DEBUG`.
5. Accept `RUNNING` as confirmed. For `STARTING`, poll `services` until the execution is running
   or a relevant failure is visible.
6. Report the returned `executionId`.

### Stop or restart

1. List services and select the exact `executionId`; never stop all same-name instances by
   assumption.
2. Run `stop --execution-id <id>` and require `TERMINATED`. Poll `services` for `STOPPING`.
3. For restart, confirm termination before starting the exact configuration again. Report the new
   `executionId`.

## Organize changes

1. Inspect Git status, staged state, and the complete relevant diff before planning groups.
2. Group independently reviewable behavior with its tests. Preserve unrelated existing
   assignments.
3. Run `changelists` and reconcile every tracked changed path.
4. Run one atomic command per group:

   ```bash
   python3 <skill-dir>/scripts/pika_idea.py move \
     --name "Logical group" \
     --path path/to/source \
     --path path/to/test
   ```

5. Do not pass `--partial` unless the user explicitly accepts partial movement.
6. Unversioned files are listed but intentionally not moved. Report them separately.
7. List changelists again and verify every requested path.
8. Do not stage, commit, rename branches, or push unless separately requested.

## Delete a changelist

1. Run `changelists` and resolve the target by exact `id`.
2. Never attempt to delete the default or a read-only changelist.
3. Run `delete --id <exact-id>`.
4. The plugin moves every tracked change in the target into the current default changelist before
   deleting it.
5. List changelists again. Confirm that the target ID is gone and moved changes are in the default
   changelist.

## Safety

- Prefer exact identifiers over display names.
- Do not stop unrelated executions or enable duplicates as a workaround.
- Do not claim success without a post-operation state check.
- Preserve user-created changelist comments and untouched assignments.
- Surface structured plugin errors with their code and message.
