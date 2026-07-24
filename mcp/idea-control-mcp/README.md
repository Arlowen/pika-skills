# Pika MCP

`Pika MCP` is a lightweight IDEA extension for JetBrains MCP Server 1.0.30. It targets
IntelliJ IDEA 2024.2 and adds deterministic service control and changelist operations without
requiring an IDEA upgrade.

The build requires JDK 21. The included Gradle Wrapper is pinned to Gradle 9.2.1.

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

## Build

The default paths match the local IDEA 2024.2 installation:

```bash
./gradlew buildPlugin
```

Override them on another machine when necessary:

```bash
./gradlew buildPlugin \
  -PideaPath="/Applications/IntelliJ IDEA.app" \
  -PmcpPluginPath="$HOME/Library/Application Support/JetBrains/IntelliJIdea2024.2/plugins/mcp-server-plugin"
```

Install the ZIP from `build/distributions/` through
**Settings → Plugins → Install Plugin from Disk**, then restart IDEA. JetBrains MCP Server
1.0.30 must remain installed and enabled.
