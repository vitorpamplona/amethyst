#!/usr/bin/env python3
"""A stand-in for an OpenAI-compatible AI provider, for the amy agent harness.

Serves GET <root>/models on loopback: 200 with a two-model list when the request carries
`Authorization: Bearer <the key in KEY_FILE>`, 401 otherwise. Anything else is 404. It never
logs a header, so no key reaches its log.

Usage: stub-provider.py PORT KEY_FILE
"""
import http.server
import json
import sys

PORT = int(sys.argv[1])
with open(sys.argv[2], encoding="utf-8") as f:
    GOOD = f.read().strip()


class Handler(http.server.BaseHTTPRequestHandler):
    def _send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if not self.path.rstrip("/").endswith("/models"):
            self._send(404, {"error": "not found"})
        elif self.headers.get("Authorization") == "Bearer " + GOOD:
            self._send(200, {"object": "list", "data": [{"id": "stub-small"}, {"id": "stub-large"}]})
        else:
            self._send(401, {"error": {"message": "invalid api key"}})

    def log_message(self, fmt, *args):
        sys.stderr.write("stub-provider: %s %s\n" % (self.command, self.path))


http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
