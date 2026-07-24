# Pika MCP tools

## Tool map

| Tool | Arguments | Important result fields |
| --- | --- | --- |
| `idea_list_services` | `projectPath?` | `configurations`, `executions` |
| `idea_start_service` | `projectPath?`, `configName`, `mode`, `allowMultiple`, `startTimeoutSeconds` | `state`, `execution` |
| `idea_stop_service` | `projectPath?`, `executionId`, `waitForTermination`, `stopTimeoutSeconds` | `state`, `exitCode` |
| `idea_list_changelists` | `projectPath?` | `changelists`, `unversionedPaths` |
| `idea_move_changes_to_changelist` | `projectPath?`, `changelistName`, `paths`, `createIfMissing`, `allOrNothing` | `movedPaths`, `unmatchedPaths`, `created` |

Pika MCP runs independently at `http://127.0.0.1:8765/mcp` by default. Register it with Codex:

```bash
codex mcp add pika --url http://127.0.0.1:8765/mcp
```

`projectPath` can be omitted when exactly one IDEA project is open. If several projects are open,
pass the exact project directory to every call.

## Service calls

Start in Debug mode:

```json
{
  "projectPath": "/absolute/path/to/project",
  "configName": "Console",
  "mode": "DEBUG",
  "allowMultiple": false,
  "startTimeoutSeconds": 30
}
```

Stop one exact execution:

```json
{
  "projectPath": "/absolute/path/to/project",
  "executionId": 42,
  "waitForTermination": true,
  "stopTimeoutSeconds": 30
}
```

Start states:

- `RUNNING`: IDEA supplied a live process descriptor.
- `STARTING`: IDEA accepted the launch, but the descriptor did not appear before the timeout.
- `ALREADY_RUNNING`: duplicate protection found an active instance.

Stop states:

- `TERMINATED`: process exit was confirmed.
- `STOPPING`: termination was requested but not yet confirmed.
- `NO_PROCESS`: the execution descriptor had no process handler.

## Changelist calls

Move tracked paths atomically:

```json
{
  "projectPath": "/absolute/path/to/project",
  "changelistName": "Backend API",
  "paths": [
    "server/src/main/kotlin/example/Controller.kt",
    "server/src/test/kotlin/example/ControllerTest.kt"
  ],
  "createIfMissing": true,
  "allOrNothing": true
}
```

Paths can be project-relative or absolute paths inside the active project. The tool rejects paths
outside the project. Renames match either the before or after path. Unversioned files are listed
but intentionally cannot be moved by this tool.

With `allOrNothing=true`, any unmatched path prevents every move in that call. With
`allOrNothing=false`, matched tracked changes move and `unmatchedPaths` reports the remainder.
Prefer the safe default.
