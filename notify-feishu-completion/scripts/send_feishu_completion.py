#!/usr/bin/env python3
"""Send the required Codex completion notice through Feishu."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request


TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"
MESSAGE_URL = "https://open.feishu.cn/open-apis/im/v1/messages"
REQUIRED_ENV = (
    "FEISHU_APP_ID",
    "FEISHU_APP_SECRET",
    "FEISHU_RECEIVE_ID_TYPE",
    "FEISHU_RECEIVE_ID",
)


class FeishuError(RuntimeError):
    """Raised for Feishu request or response failures."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Send a Feishu completion notice using local environment variables."
    )
    parser.add_argument("--project", help="Project/repository/workspace name.")
    parser.add_argument("--chat", help="Current chat/thread name.")
    parser.add_argument("--session", help="Current task/conversation name.")
    parser.add_argument("--time", help="Completion time string. Defaults to local now.")
    parser.add_argument("--cwd", default=os.getcwd(), help="Directory for project inference.")
    parser.add_argument("--dry-run", action="store_true", help="Print payload without sending.")
    parser.add_argument("--timeout", type=float, default=20.0, help="HTTP timeout in seconds.")
    return parser.parse_args()


def infer_project(cwd: str) -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=cwd,
            check=True,
            capture_output=True,
            text=True,
            timeout=3,
        )
        name = pathlib.Path(result.stdout.strip()).name
    except Exception:
        name = pathlib.Path(cwd).resolve().name

    if name.lower() == "codex":
        return "Codex"
    return name


def first_env(*names: str) -> str | None:
    for name in names:
        value = os.environ.get(name)
        if value:
            return value
    return None


def local_time() -> str:
    return dt.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %Z")


def completion_message(project: str, chat: str, session: str, completed_at: str) -> str:
    return f"【{project}】【{chat}】【{session}】变更已完成，{completed_at}"


def post_json(
    url: str,
    payload: dict[str, object],
    headers: dict[str, str] | None = None,
    timeout: float = 20.0,
) -> dict[str, object]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json", **(headers or {})},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response_body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise FeishuError(f"HTTP {exc.code} from {url}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise FeishuError(f"Request failed for {url}: {exc}") from exc

    try:
        data = json.loads(response_body)
    except json.JSONDecodeError as exc:
        raise FeishuError(f"Invalid JSON response from {url}: {response_body}") from exc

    if not isinstance(data, dict):
        raise FeishuError(f"Unexpected JSON response from {url}: {data!r}")
    return data


def require_success(data: dict[str, object], operation: str) -> None:
    code = data.get("code")
    if code not in (0, "0", None):
        message = data.get("msg") or data.get("message") or data
        raise FeishuError(f"{operation} failed: code={code}, message={message}")


def send_message(message: str, timeout: float) -> dict[str, object]:
    missing = [name for name in REQUIRED_ENV if not os.environ.get(name)]
    if missing:
        raise FeishuError(f"Missing required environment variables: {', '.join(missing)}")

    token_response = post_json(
        TOKEN_URL,
        {
            "app_id": os.environ["FEISHU_APP_ID"],
            "app_secret": os.environ["FEISHU_APP_SECRET"],
        },
        timeout=timeout,
    )
    require_success(token_response, "tenant_access_token request")
    token = token_response.get("tenant_access_token")
    if not isinstance(token, str) or not token:
        raise FeishuError(f"tenant_access_token missing in response: {token_response}")

    content = json.dumps({"text": message}, ensure_ascii=False)
    receive_id_type = os.environ["FEISHU_RECEIVE_ID_TYPE"]
    message_response = post_json(
        f"{MESSAGE_URL}?receive_id_type={receive_id_type}",
        {
            "receive_id": os.environ["FEISHU_RECEIVE_ID"],
            "msg_type": "text",
            "content": content,
        },
        headers={"Authorization": f"Bearer {token}"},
        timeout=timeout,
    )
    require_success(message_response, "message send")
    return message_response


def main() -> int:
    args = parse_args()
    project = args.project or infer_project(args.cwd)
    if project.lower() == "codex":
        project = "Codex"

    default_name = pathlib.Path(args.cwd).resolve().name
    chat = args.chat or first_env("CODEX_CHAT_TITLE", "FEISHU_CHAT_NAME", "CHAT_NAME") or default_name
    session = (
        args.session
        or first_env("CODEX_SESSION_NAME", "FEISHU_SESSION_NAME", "TASK_NAME")
        or chat
    )
    completed_at = args.time or local_time()
    message = completion_message(project, chat, session, completed_at)

    if args.dry_run:
        print(
            json.dumps(
                {
                    "dry_run": True,
                    "message": message,
                    "content": json.dumps({"text": message}, ensure_ascii=False),
                    "missing_env": [name for name in REQUIRED_ENV if not os.environ.get(name)],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return 0

    try:
        response = send_message(message, timeout=args.timeout)
    except FeishuError as exc:
        print(f"Feishu completion notification failed: {exc}", file=sys.stderr)
        return 1

    print(json.dumps({"sent": True, "message": message, "response": response}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
