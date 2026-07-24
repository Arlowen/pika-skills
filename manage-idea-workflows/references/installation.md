# Install manage-idea-workflows

The Codex Skill and the Pika Control IDEA plugin are installed separately. No MCP server
registration is required.

## Install the Codex Skill

### Recommended: ask Codex to install it

Start a Codex task and send:

```text
Use $skill-installer to install:
https://github.com/Arlowen/pika-skills/tree/main/manage-idea-workflows
```

The Skill will be installed as:

```text
${CODEX_HOME:-$HOME/.codex}/skills/manage-idea-workflows
```

Use it in a new task with:

```text
Use $manage-idea-workflows to list the open IDEA projects.
```

### Manual installation

For a new installation:

```bash
git clone --depth 1 https://github.com/Arlowen/pika-skills.git
mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills"
cp -R \
  pika-skills/manage-idea-workflows \
  "${CODEX_HOME:-$HOME/.codex}/skills/manage-idea-workflows"
```

Verify the installed Skill:

```bash
test -f "${CODEX_HOME:-$HOME/.codex}/skills/manage-idea-workflows/SKILL.md"
python3 \
  "${CODEX_HOME:-$HOME/.codex}/skills/manage-idea-workflows/scripts/pika_idea.py" \
  --help
```

Open a new Codex task after installation. If the Skill is not discovered, restart Codex once.

## Install the Pika Control IDEA plugin

1. Download `Pika-IDEA-Control-<version>.zip` from the repository's GitHub Releases.
2. In IntelliJ IDEA, open `Settings | Plugins`.
3. Choose `Install Plugin from Disk` and select the ZIP without extracting it.
4. Enable `Pika Control` and restart IDEA.

The plugin defaults to `http://127.0.0.1:8765`. Verify it from the installed Skill:

```bash
python3 \
  "${CODEX_HOME:-$HOME/.codex}/skills/manage-idea-workflows/scripts/pika_idea.py" \
  health
```

If the plugin uses another port, set:

```bash
export PIKA_IDEA_URL="http://127.0.0.1:<port>"
```

The Skill communicates directly with the plugin's loopback REST API. Do not add an MCP
configuration to Codex or enable JetBrains MCP Server for this workflow.
