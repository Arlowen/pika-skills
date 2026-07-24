# Install pika-idea-control

The Codex Skill and the Pika Control IDEA plugin are installed separately. No MCP server
registration is required. Installing the Skill alone is not sufficient: install, enable, and
start the Pika Control plugin in IntelliJ IDEA before invoking any Skill workflow.

## Install the Codex Skill

### Recommended: ask Codex to install it

Start a Codex task and send:

```text
Use $skill-installer to install:
https://github.com/Arlowen/pika-skills/tree/main/pika-idea-control
```

The Skill will be installed as:

```text
${CODEX_HOME:-$HOME/.codex}/skills/pika-idea-control
```

Use it in a new task with:

```text
Use $pika-idea-control to list the open IDEA projects.
```

### Manual installation

For a new installation:

```bash
git clone --depth 1 https://github.com/Arlowen/pika-skills.git
mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills"
cp -R \
  pika-skills/pika-idea-control \
  "${CODEX_HOME:-$HOME/.codex}/skills/pika-idea-control"
```

Verify the installed Skill:

```bash
test -f "${CODEX_HOME:-$HOME/.codex}/skills/pika-idea-control/SKILL.md"
python3 \
  "${CODEX_HOME:-$HOME/.codex}/skills/pika-idea-control/scripts/pika_idea.py" \
  --help
```

Open a new Codex task after installation. If the Skill is not discovered, restart Codex once.

## Install the Pika Control IDEA plugin

First obtain the plugin ZIP. If a `Pika-IDEA-Control-<version>.zip` asset is available on
[GitHub Releases](https://github.com/Arlowen/pika-skills/releases), download it without
extracting it.

If no release asset is available, build the ZIP from the plugin source with JDK 21:

```bash
cd "${CODEX_HOME:-$HOME/.codex}/skills/pika-idea-control/idea-plugin"
./gradlew buildPlugin
```

The ZIP is created under `build/distributions/`. When building from a repository checkout
instead of an installed Skill, run the same command from
`pika-idea-control/idea-plugin`.

Then install it in IDEA:

1. Open `Settings/Preferences | Plugins`.
2. Open the Plugins settings menu and choose `Install Plugin from Disk`.
3. Select the `Pika-IDEA-Control-<version>.zip` file without extracting it.
4. Confirm the installation, enable `Pika Control`, and restart IDEA.

The plugin defaults to `http://127.0.0.1:8765`. Verify it from the installed Skill:

```bash
python3 \
  "${CODEX_HOME:-$HOME/.codex}/skills/pika-idea-control/scripts/pika_idea.py" \
  health
```

Do not use the Skill until this health check succeeds. If it fails:

1. Confirm that IDEA is running and `Pika Control` is installed and enabled.
2. Restart IDEA after installing or enabling the plugin.
3. Confirm that the plugin and helper use the same port.

If the plugin uses another port, set:

```bash
export PIKA_IDEA_URL="http://127.0.0.1:<port>"
```

The Skill communicates directly with the plugin's loopback REST API. Do not add an MCP
configuration to Codex or enable JetBrains MCP Server for this workflow.
