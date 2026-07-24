# Pika Control interface

The skill helper uses `http://127.0.0.1:8765` by default. Override it with global
`--url http://127.0.0.1:<port>` or `PIKA_IDEA_URL`. Only loopback HTTP URLs are accepted.

Resolve `<helper>` to the absolute path of `scripts/pika_idea.py` beside the active `SKILL.md`:

```bash
python3 <helper> health
python3 <helper> projects
```

`--project <absolute-path>` is optional when exactly one IDEA project is open and required when
several are open.

## Command map

| Command | Important options | Important result fields |
| --- | --- | --- |
| `projects` | none | `projects[].name`, `projects[].basePath` |
| `services` | `--project` | `configurations`, `executions` |
| `start` | `--name`, `--mode`, `--allow-multiple`, `--start-timeout`, `--project` | `state`, `execution` |
| `stop` | `--execution-id`, `--no-wait`, `--stop-timeout`, `--project` | `state`, `exitCode` |
| `changelists` | `--project` | `changelists`, `unversionedPaths` |
| `move` | `--name`, repeated `--path`, `--no-create`, `--partial`, `--project` | `movedPaths`, `unmatchedPaths`, `created` |
| `delete` | `--id`, `--project` | `deletedChangelistId`, `movedToChangelistId`, `movedPaths`, `deleted` |

Global options must precede the command:

```bash
python3 <helper> --url http://127.0.0.1:8877 --http-timeout 45 services
```

## Service examples

```bash
python3 <helper> start \
  --project /absolute/path/to/project \
  --name Console \
  --mode DEBUG

python3 <helper> stop \
  --project /absolute/path/to/project \
  --execution-id 42
```

Start states:

- `RUNNING`: IDEA supplied a live process descriptor.
- `STARTING`: IDEA accepted the launch; poll `services`.
- `ALREADY_RUNNING`: duplicate protection found an active instance.

Stop states:

- `TERMINATED`: process exit was confirmed.
- `STOPPING`: termination was requested; poll `services`.
- `NO_PROCESS`: the execution descriptor had no process handler.

## Changelist examples

```bash
python3 <helper> move \
  --project /absolute/path/to/project \
  --name "Backend API" \
  --path server/src/main/kotlin/example/Controller.kt \
  --path server/src/test/kotlin/example/ControllerTest.kt

python3 <helper> delete \
  --project /absolute/path/to/project \
  --id 4f37bba1-64d2-4d58-aea3-58a90649cd58
```

Paths can be project-relative or absolute paths inside the project. Renames match either the
before or after path. Unversioned files are listed but cannot be moved.

Movement is atomic by default: one unmatched path prevents the entire operation. `--partial`
opts into partial movement.

Deletion resolves the exact ID, moves every tracked change to the current default changelist,
then removes the source. The default and read-only changelists are rejected.

## REST map

The Python helper is the stable skill interface. The underlying endpoints are:

| Method | Path |
| --- | --- |
| `GET` | `/health` |
| `GET` | `/api/v1/projects` |
| `GET` | `/api/v1/services?projectPath=...` |
| `POST` | `/api/v1/services/start` |
| `POST` | `/api/v1/services/stop` |
| `GET` | `/api/v1/changelists?projectPath=...` |
| `POST` | `/api/v1/changelists/move` |
| `POST` | `/api/v1/changelists/delete` |

Errors use:

```json
{
  "error": {
    "code": "INVALID_ARGUMENT",
    "message": "Human-readable detail"
  }
}
```
