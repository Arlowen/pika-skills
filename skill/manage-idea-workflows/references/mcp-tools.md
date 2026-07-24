# Pika MCP tools

## Tool map

| Tool | Arguments | Important result fields |
| --- | --- | --- |
| `idea_list_services` | none | `configurations`, `executions` |
| `idea_start_service` | `configName`, `mode`, `allowMultiple`, `startTimeoutSeconds` | `state`, `execution` |
| `idea_stop_service` | `executionId`, `waitForTermination`, `stopTimeoutSeconds` | `state`, `exitCode` |
| `idea_list_changelists` | none | `changelists`, `unversionedPaths` |
| `idea_move_changes_to_changelist` | `changelistName`, `paths`, `createIfMissing`, `allOrNothing` | `movedPaths`, `unmatchedPaths`, `created` |

## Service calls

Start in Debug mode:

```json
{
  "configName": "Console",
  "mode": "DEBUG",
  "allowMultiple": false,
  "startTimeoutSeconds": 30
}
```

Stop one exact execution:

```json
{
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
