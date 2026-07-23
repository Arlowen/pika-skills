---
name: notify-feishu-completion
description: Send required Feishu completion notifications for Codex work. Use after every completed chat turn when project rules require notification, and after completing and pushing or otherwise finishing any code, asset, UI, documentation, or project change when required. Use the Feishu application bot with FEISHU_APP_ID, FEISHU_APP_SECRET, FEISHU_RECEIVE_ID_TYPE, and FEISHU_RECEIVE_ID. Use when AGENTS.md or user instructions mention Feishu completion messages, completion notifications, every-chat completion notices, or the exact format "【项目名】【chat 名称】【会话名】变更已完成，【完成时间】"; do not use Slack for this notification.
---

# Notify Feishu Completion

## Overview

Send the required Feishu application-bot completion message after a chat turn or qualifying Codex change is finished. Use the bundled script so token retrieval, message shape, and API calls stay consistent.

## Workflow

1. Confirm whether the active project rules require notification after every completed chat turn. If they do, send even for question-only turns. Otherwise, send only after completing and pushing, or otherwise finishing, a code, asset, UI, documentation, or project change.
2. Determine the message fields:
   - Project: current repository or workspace name. If it is `codex`, render it as `Codex`.
   - Chat: current chat/thread title. If unavailable, use the visible workspace/chat directory name.
   - Session: current task or conversation name. If unavailable, use a concise name derived from the user's task.
   - Time: local completion time.
3. Run `scripts/send_feishu_completion.py` from this skill with explicit `--project`, `--chat`, and `--session` when you know them.
4. If the script reports missing Feishu environment variables, tell the user the notification could not be sent for that concrete reason. Do not fall back to Slack.
5. If Feishu returns an API error, retry once only when the failure looks transient. Otherwise report the failure and continue the final response.

## Script Usage

Use:

```bash
python3 /Users/pika/.codex/skills/notify-feishu-completion/scripts/send_feishu_completion.py \
  --project "ProjectName" \
  --chat "ChatName" \
  --session "SessionName"
```

Use `--dry-run` to validate the generated message without calling Feishu:

```bash
python3 /Users/pika/.codex/skills/notify-feishu-completion/scripts/send_feishu_completion.py \
  --project "Codex" \
  --chat "new-chat" \
  --session "Create Feishu notification skill" \
  --dry-run
```

The script sends this exact message shape:

```text
【项目名】【chat 名称】【会话名】变更已完成，【完成时间】
```

The script reads these environment variables when not in dry-run mode:

```text
FEISHU_APP_ID
FEISHU_APP_SECRET
FEISHU_RECEIVE_ID_TYPE
FEISHU_RECEIVE_ID
```

## Notes

- Send through Feishu only; never use Slack for this completion notification.
- Keep the final user response clear if the notification was sent or could not be sent.
- Prefer passing explicit field values over relying on script inference when the current chat/session names are known.
