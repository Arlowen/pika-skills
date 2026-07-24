#!/usr/bin/env python3
"""Call the loopback REST API exposed by the Pika Control plugin."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Sequence


DEFAULT_URL = "http://127.0.0.1:8765"
ALLOWED_HOSTS = {"127.0.0.1", "localhost", "::1"}


class IdeaApiError(RuntimeError):
    def __init__(self, status: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.code = code

    def __str__(self) -> str:
        return f"{self.code} (HTTP {self.status}): {super().__str__()}"


class IdeaClient:
    def __init__(self, base_url: str = DEFAULT_URL, timeout: float = 35.0) -> None:
        self.base_url = _validate_base_url(base_url)
        self.timeout = timeout

    def health(self) -> dict[str, Any]:
        return self._request("GET", "/health")

    def projects(self) -> dict[str, Any]:
        return self._request("GET", "/api/v1/projects")

    def services(self, project_path: str | None = None) -> dict[str, Any]:
        return self._request(
            "GET",
            "/api/v1/services",
            query=_project_query(project_path),
        )

    def start_service(
        self,
        config_name: str,
        *,
        project_path: str | None = None,
        mode: str = "DEBUG",
        allow_multiple: bool = False,
        start_timeout_seconds: int = 30,
    ) -> dict[str, Any]:
        return self._request(
            "POST",
            "/api/v1/services/start",
            body={
                **_project_body(project_path),
                "configName": config_name,
                "mode": mode,
                "allowMultiple": allow_multiple,
                "startTimeoutSeconds": start_timeout_seconds,
            },
        )

    def stop_service(
        self,
        execution_id: int,
        *,
        project_path: str | None = None,
        wait_for_termination: bool = True,
        stop_timeout_seconds: int = 30,
    ) -> dict[str, Any]:
        return self._request(
            "POST",
            "/api/v1/services/stop",
            body={
                **_project_body(project_path),
                "executionId": execution_id,
                "waitForTermination": wait_for_termination,
                "stopTimeoutSeconds": stop_timeout_seconds,
            },
        )

    def changelists(self, project_path: str | None = None) -> dict[str, Any]:
        return self._request(
            "GET",
            "/api/v1/changelists",
            query=_project_query(project_path),
        )

    def move_changes(
        self,
        changelist_name: str,
        paths: Sequence[str],
        *,
        project_path: str | None = None,
        create_if_missing: bool = True,
        all_or_nothing: bool = True,
    ) -> dict[str, Any]:
        return self._request(
            "POST",
            "/api/v1/changelists/move",
            body={
                **_project_body(project_path),
                "changelistName": changelist_name,
                "paths": list(paths),
                "createIfMissing": create_if_missing,
                "allOrNothing": all_or_nothing,
            },
        )

    def delete_changelist(
        self,
        changelist_id: str,
        *,
        project_path: str | None = None,
    ) -> dict[str, Any]:
        return self._request(
            "POST",
            "/api/v1/changelists/delete",
            body={
                **_project_body(project_path),
                "changelistId": changelist_id,
            },
        )

    def _request(
        self,
        method: str,
        path: str,
        *,
        query: dict[str, str] | None = None,
        body: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        url = f"{self.base_url}{path}"
        if query:
            url = f"{url}?{urllib.parse.urlencode(query)}"

        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body, separators=(",", ":")).encode("utf-8")
            headers["Content-Type"] = "application/json"

        request = urllib.request.Request(
            url,
            data=data,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return _decode_json(response.read(), response.status)
        except urllib.error.HTTPError as error:
            payload = _decode_error(error.read(), error.code)
            raise IdeaApiError(error.code, payload[0], payload[1]) from error
        except urllib.error.URLError as error:
            reason = getattr(error, "reason", error)
            raise ConnectionError(
                f"Cannot reach Pika Control at {self.base_url}: {reason}"
            ) from error


def _validate_base_url(value: str) -> str:
    parsed = urllib.parse.urlsplit(value.rstrip("/"))
    if parsed.scheme != "http" or parsed.hostname not in ALLOWED_HOSTS:
        raise ValueError(
            "IDEA URL must be a loopback http URL on 127.0.0.1, localhost, or ::1"
        )
    if parsed.username is not None or parsed.password is not None:
        raise ValueError("IDEA URL must not contain credentials")
    if parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise ValueError("IDEA URL must contain only scheme, loopback host, and optional port")
    try:
        parsed.port
    except ValueError as error:
        raise ValueError("IDEA URL contains an invalid port") from error
    return value.rstrip("/")


def _decode_json(data: bytes, status: int) -> dict[str, Any]:
    try:
        value = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RuntimeError(f"IDEA returned invalid JSON with HTTP {status}") from error
    if not isinstance(value, dict):
        raise RuntimeError(f"IDEA returned a non-object JSON response with HTTP {status}")
    return value


def _decode_error(data: bytes, status: int) -> tuple[str, str]:
    try:
        value = _decode_json(data, status)
        error = value.get("error")
        if isinstance(error, dict):
            code = error.get("code")
            message = error.get("message")
            if isinstance(code, str) and isinstance(message, str):
                return code, message
    except RuntimeError:
        pass
    return "HTTP_ERROR", f"IDEA returned HTTP {status}"


def _project_query(project_path: str | None) -> dict[str, str]:
    return {"projectPath": project_path} if project_path else {}


def _project_body(project_path: str | None) -> dict[str, str]:
    return {"projectPath": project_path} if project_path else {}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Call the local Pika Control plugin REST API.",
    )
    parser.add_argument(
        "--url",
        default=os.environ.get("PIKA_IDEA_URL", DEFAULT_URL),
        help="Plugin URL (default: %(default)s; env: PIKA_IDEA_URL)",
    )
    parser.add_argument(
        "--http-timeout",
        type=float,
        default=35.0,
        help="HTTP timeout in seconds (default: %(default)s)",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("health", help="Check whether the IDEA plugin is reachable")
    subparsers.add_parser("projects", help="List open IDEA projects")

    services = subparsers.add_parser("services", help="List run configurations and executions")
    _add_project_argument(services)

    start = subparsers.add_parser("start", help="Start one exact IDEA run configuration")
    _add_project_argument(start)
    start.add_argument("--name", required=True, help="Exact run configuration name")
    start.add_argument("--mode", choices=("RUN", "DEBUG"), default="DEBUG")
    start.add_argument("--allow-multiple", action="store_true")
    start.add_argument("--start-timeout", type=int, default=30, metavar="SECONDS")

    stop = subparsers.add_parser("stop", help="Stop one exact IDEA execution")
    _add_project_argument(stop)
    stop.add_argument("--execution-id", type=int, required=True)
    stop.add_argument("--no-wait", action="store_true")
    stop.add_argument("--stop-timeout", type=int, default=30, metavar="SECONDS")

    changelists = subparsers.add_parser("changelists", help="List IDEA changelists")
    _add_project_argument(changelists)

    move = subparsers.add_parser("move", help="Move tracked paths to an IDEA changelist")
    _add_project_argument(move)
    move.add_argument("--name", required=True, help="Target changelist name")
    move.add_argument(
        "--path",
        action="append",
        required=True,
        dest="paths",
        help="Project-relative or in-project absolute path; repeat for multiple paths",
    )
    move.add_argument("--no-create", action="store_true")
    move.add_argument(
        "--partial",
        action="store_true",
        help="Move matched paths even when another requested path is unmatched",
    )

    delete = subparsers.add_parser("delete", help="Delete a non-default IDEA changelist")
    _add_project_argument(delete)
    delete.add_argument("--id", required=True, dest="changelist_id")

    return parser


def _add_project_argument(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--project",
        help="Exact absolute path of an open IDEA project; optional when only one is open",
    )


def run(arguments: argparse.Namespace) -> dict[str, Any]:
    client = IdeaClient(arguments.url, arguments.http_timeout)
    command = arguments.command
    if command == "health":
        return client.health()
    if command == "projects":
        return client.projects()
    if command == "services":
        return client.services(arguments.project)
    if command == "start":
        return client.start_service(
            arguments.name,
            project_path=arguments.project,
            mode=arguments.mode,
            allow_multiple=arguments.allow_multiple,
            start_timeout_seconds=arguments.start_timeout,
        )
    if command == "stop":
        return client.stop_service(
            arguments.execution_id,
            project_path=arguments.project,
            wait_for_termination=not arguments.no_wait,
            stop_timeout_seconds=arguments.stop_timeout,
        )
    if command == "changelists":
        return client.changelists(arguments.project)
    if command == "move":
        return client.move_changes(
            arguments.name,
            arguments.paths,
            project_path=arguments.project,
            create_if_missing=not arguments.no_create,
            all_or_nothing=not arguments.partial,
        )
    if command == "delete":
        return client.delete_changelist(
            arguments.changelist_id,
            project_path=arguments.project,
        )
    raise AssertionError(f"Unhandled command: {command}")


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    try:
        result = run(parser.parse_args(argv))
    except (ConnectionError, IdeaApiError, RuntimeError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
