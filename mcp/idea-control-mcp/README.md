# Pika MCP

`Pika MCP` is an independent local MCP server embedded in IntelliJ IDEA. It supports IDEA 2024.2
(`242.*`) through 2025.3 (`253.*`) and provides deterministic service control and changelist
operations without installing or enabling JetBrains MCP Server.

The plugin exposes a stateless Streamable HTTP endpoint on the IPv4 loopback interface:

```text
http://127.0.0.1:8765/mcp
```

Its health endpoint is `http://127.0.0.1:8765/health`. Requests from non-loopback hosts and
non-local browser origins are rejected.

## Tools

| Tool | Purpose |
| --- | --- |
| `idea_list_services` | List Run/Debug configurations and active executions. |
| `idea_start_service` | Start an exact configuration in `RUN` or `DEBUG` mode. |
| `idea_stop_service` | Stop an exact execution by `executionId`. |
| `idea_list_changelists` | List IDEA changelists, tracked changes, and unversioned paths. |
| `idea_move_changes_to_changelist` | Move explicit tracked paths into a changelist. |

The plugin never creates Git commits or pushes. The move tool does not include unversioned files
and defaults to all-or-nothing behavior.

Every tool accepts an optional `projectPath`. It can be omitted when exactly one IDEA project is
open. When multiple projects are open, use the exact project directory returned by the error.

## Connect Codex

Install `Pika-MCP-<version>.zip` through **Settings → Plugins → Install Plugin from Disk**, then
restart IDEA once. Register the independent endpoint with Codex:

```bash
codex mcp add pika --url http://127.0.0.1:8765/mcp
codex mcp list
```

JetBrains MCP Server may remain installed, disabled, or be removed; Pika MCP does not use it.

The default port is `8765`. To change it, set either the IDEA VM option
`-Dpika.mcp.port=<port>` or the `PIKA_MCP_PORT` environment variable before starting IDEA, then
register the matching URL in Codex.

## Build

The build requires JDK 21. The included Gradle Wrapper is pinned to Gradle 9.2.1. The default
local IDEA path targets 2024.2 and produces one cross-version package:

```bash
./gradlew clean test buildPlugin
```

Override the local IDEA path on another machine when necessary:

```bash
./gradlew buildPlugin \
  -PideaPath="/Applications/IntelliJ IDEA.app"
```

Compile and test the same source against IDEA 2025.3.2:

```bash
./gradlew clean test -PtargetIdeaVersion="2025.3.2"
```

The package is written to `build/distributions/Pika-MCP-<version>.zip`.
