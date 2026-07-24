---
name: manage-idea-workflows
description: Control IntelliJ IDEA Run/Debug services and organize tracked changes into logical IDEA changelists through the IDEA Control MCP extension. Use when the user asks Codex to list, start, stop, or restart services configured in IDEA; prefers DEBUG mode; needs exact multi-instance process control; asks to split current changes into commit groups or changelists; or wants files grouped for later atomic commits without committing or pushing.
---

# Manage IDEA Workflows

Use the IDEA Control MCP tools for deterministic IDE operations. Keep semantic decisions—service
intent, dependency order, and commit grouping—in the agent.

Read [references/mcp-tools.md](references/mcp-tools.md) before the first MCP call in a task when
tool arguments or response states are not already known.

## Preconditions

1. Confirm the active IDEA project is the intended project.
2. Require the `Pika MCP` plugin and JetBrains `MCP Server` plugin to be
   enabled.
3. Use the five `idea_*` tools. Do not replace unavailable tools with coordinate-based IDEA UI
   automation unless the user explicitly accepts that fallback.
4. Never create a Git commit or push merely because the user asked for “commit grouping.” Treat
   that phrase as IDEA changelist grouping unless the user explicitly requests commits.

## Control services

### List

1. Call `idea_list_services`.
2. Resolve the requested configuration by exact `name`.
3. If duplicate configuration names exist, stop and report their `uniqueId` values; do not guess.
4. Use returned `executionId` values as the identity of running instances.

### Start

1. List services before starting.
2. Default to `DEBUG` unless the user explicitly requests `RUN`.
3. Leave `allowMultiple=false`. Set it to `true` only when the user clearly wants another instance.
4. Call `idea_start_service` with the exact configuration name.
5. Accept `RUNNING` as confirmed. For `STARTING`, poll `idea_list_services` until the execution
   becomes `RUNNING` or a relevant failure is visible.
6. Report the returned `executionId`.

### Stop

1. List services and select the exact `executionId`.
2. If multiple instances share a configuration name, identify the target from the user's context;
   do not stop every instance by assumption.
3. Call `idea_stop_service` with `waitForTermination=true`.
4. Treat `TERMINATED` as confirmed. For `STOPPING`, poll `idea_list_services` before claiming
   completion.

### Restart

Perform restart as a controlled composition:

1. Stop the exact existing `executionId`.
2. Confirm termination.
3. Start the exact configuration in its requested mode.
4. Confirm the new execution and report its new `executionId`.

## Group changes

1. Inspect `git status`, staged state, and the complete relevant diff before proposing groups.
2. Form independently reviewable, buildable groups by behavior and dependency—not merely by
   directory. Keep an implementation with its directly corresponding tests.
3. Keep unrelated generated files, migrations, documentation, frontend, and backend changes
   separate when they can stand alone. Keep a required schema/API change with its consumer when
   separating them would break the build or behavior.
4. Preserve existing changelist assignments unless the requested regrouping requires moving them.
5. Call `idea_list_changelists` and reconcile every tracked changed path with the plan.
6. Call `idea_move_changes_to_changelist` once per group with explicit project-relative paths.
   Keep `allOrNothing=true` so a typo cannot cause a partial grouping.
7. Do not include unversioned files. Report them separately and ask before adding or otherwise
   handling them.
8. List changelists again. Verify every requested tracked path is in exactly the intended group and
   report anything left in the default changelist.
9. Do not stage, commit, rename branches, or push unless separately and explicitly requested.

## Safety rules

- Prefer exact identifiers over display names whenever an identifier is available.
- Do not stop unrelated services as cleanup.
- Do not enable duplicate instances to work around an already-running response.
- Do not claim a start, stop, or grouping succeeded without a post-operation state check.
- Preserve user-created changelist comments and untouched assignments.
- Surface MCP errors verbatim enough to identify the configuration, execution, or path involved.
