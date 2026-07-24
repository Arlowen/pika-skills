from __future__ import annotations

import json
import sys
import threading
import unittest
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

SKILL_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SKILL_DIR))

from scripts.pika_idea import IdeaApiError, IdeaClient


class FakeIdeaHandler(BaseHTTPRequestHandler):
    requests: list[dict[str, Any]] = []

    def do_GET(self) -> None:
        self._handle()

    def do_POST(self) -> None:
        self._handle()

    def log_message(self, _format: str, *_args: Any) -> None:
        pass

    def _handle(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(length)
        body = json.loads(raw_body) if raw_body else None
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        self.requests.append(
            {
                "method": self.command,
                "path": parsed.path,
                "query": query,
                "body": body,
                "content_type": self.headers.get("Content-Type"),
            }
        )

        if parsed.path == "/api/v1/failure":
            self._json(
                409,
                {
                    "error": {
                        "code": "INVALID_STATE",
                        "message": "IDEA is busy",
                    }
                },
            )
            return
        self._json(200, {"ok": True, "path": parsed.path})

    def _json(self, status: int, value: dict[str, Any]) -> None:
        encoded = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


class IdeaClientTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        FakeIdeaHandler.requests = []
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeIdeaHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base_url = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)

    def setUp(self) -> None:
        FakeIdeaHandler.requests.clear()
        self.client = IdeaClient(self.base_url)

    def test_health_uses_get(self) -> None:
        result = self.client.health()

        self.assertTrue(result["ok"])
        self.assertEqual(
            {
                "method": "GET",
                "path": "/health",
                "query": {},
                "body": None,
                "content_type": None,
            },
            FakeIdeaHandler.requests[-1],
        )

    def test_services_encodes_project_path(self) -> None:
        self.client.services("/tmp/project with spaces")

        self.assertEqual(
            {"projectPath": ["/tmp/project with spaces"]},
            FakeIdeaHandler.requests[-1]["query"],
        )

    def test_start_posts_exact_json_contract(self) -> None:
        self.client.start_service(
            "Backend",
            project_path="/workspace/app",
            mode="RUN",
            allow_multiple=True,
            start_timeout_seconds=12,
        )

        request = FakeIdeaHandler.requests[-1]
        self.assertEqual("POST", request["method"])
        self.assertEqual("/api/v1/services/start", request["path"])
        self.assertEqual("application/json", request["content_type"])
        self.assertEqual(
            {
                "projectPath": "/workspace/app",
                "configName": "Backend",
                "mode": "RUN",
                "allowMultiple": True,
                "startTimeoutSeconds": 12,
            },
            request["body"],
        )

    def test_move_defaults_to_atomic_and_create(self) -> None:
        self.client.move_changes("Backend", ["src/App.kt", "src/AppTest.kt"])

        self.assertEqual(
            {
                "changelistName": "Backend",
                "paths": ["src/App.kt", "src/AppTest.kt"],
                "createIfMissing": True,
                "allOrNothing": True,
            },
            FakeIdeaHandler.requests[-1]["body"],
        )

    def test_structured_api_error_is_preserved(self) -> None:
        with self.assertRaises(IdeaApiError) as raised:
            self.client._request("GET", "/api/v1/failure")

        self.assertEqual(409, raised.exception.status)
        self.assertEqual("INVALID_STATE", raised.exception.code)
        self.assertIn("IDEA is busy", str(raised.exception))

    def test_remote_url_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "loopback"):
            IdeaClient("https://example.com")


if __name__ == "__main__":
    unittest.main()
