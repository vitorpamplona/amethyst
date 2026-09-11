#!/usr/bin/env python3
"""A throwaway Blossom server for the Marmot interop harness.

Enough of BUD-01/BUD-02 for both implementations to store and fetch an
encrypted attachment: `PUT /upload` stores the body under its SHA-256 and
returns the blob descriptor, `GET /<sha256>` serves it back, `HEAD` answers
existence checks.

Deliberately unauthenticated. Real Blossom servers verify a kind-24242
authorization event; this one runs on loopback for the duration of a test run
and holds nothing but ciphertext the group already encrypted. Checking the
signature would test the harness, not the protocol.
"""

import argparse
import hashlib
import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    blob_dir = "."
    base_url = ""

    def _blob_path(self, sha):
        return os.path.join(self.blob_dir, sha)

    def _sha_from_path(self):
        # BUD-01 allows an optional extension: `/<sha256>` or `/<sha256>.bin`.
        name = self.path.lstrip("/").split("?")[0]
        sha = name.split(".")[0]
        if len(sha) != 64 or any(c not in "0123456789abcdef" for c in sha.lower()):
            return None
        return sha.lower()

    def _send_json(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_PUT(self):
        if not self.path.startswith("/upload"):
            self._send_json(404, {"message": "not found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        sha = hashlib.sha256(body).hexdigest()
        with open(self._blob_path(sha), "wb") as handle:
            handle.write(body)
        self._send_json(
            200,
            {
                "sha256": sha,
                "size": len(body),
                "type": self.headers.get("Content-Type", "application/octet-stream"),
                "uploaded": int(time.time()),
                "url": f"{self.base_url}/{sha}",
            },
        )

    def do_GET(self):
        sha = self._sha_from_path()
        if sha is None or not os.path.exists(self._blob_path(sha)):
            self._send_json(404, {"message": "blob not found"})
            return
        with open(self._blob_path(sha), "rb") as handle:
            body = handle.read()
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_HEAD(self):
        sha = self._sha_from_path()
        exists = sha is not None and os.path.exists(self._blob_path(sha))
        self.send_response(200 if exists else 404)
        self.send_header("Content-Type", "application/octet-stream")
        self.end_headers()

    def log_message(self, fmt, *args):
        # The harness captures stdout; one line per request is useful when a
        # media test fails and useless otherwise.
        print("blossom %s - %s" % (self.address_string(), fmt % args), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8081)
    parser.add_argument("--dir", required=True)
    args = parser.parse_args()

    os.makedirs(args.dir, exist_ok=True)
    Handler.blob_dir = args.dir
    Handler.base_url = f"http://{args.host}:{args.port}"

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(json.dumps({"ready": True, "base_url": Handler.base_url}), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
