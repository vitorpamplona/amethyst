#!/usr/bin/env python3
"""ws-probe.py: a stdlib-only, UNAUTHENTICATED Nostr websocket probe.

Opens one socket to a relay, never answers AUTH, and prints every frame it
receives as one JSON line. Used by buzz-relay-interop.sh to pin what a cold
socket gets from Buzz (the challenge, then `auth-required:` on CLOSED and OK),
which is the answer amy's publish/drain retry logic keys on.

usage: ws-probe.py ws://HOST:PORT [MESSAGE-JSON ...]
Each MESSAGE-JSON is sent after the first second of reads; reads go on for
--wait seconds (default 3) after the last send.
"""

import base64
import json
import os
import socket
import sys
import time
from urllib.parse import urlparse


def send_frame(sock, text):
    payload = text.encode()
    header = bytearray([0x81])
    n = len(payload)
    if n < 126:
        header.append(0x80 | n)
    elif n < 65536:
        header.append(0x80 | 126)
        header += n.to_bytes(2, "big")
    else:
        header.append(0x80 | 127)
        header += n.to_bytes(8, "big")
    mask = os.urandom(4)
    header += mask
    sock.sendall(bytes(header) + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))


def recv_exact(sock, n, buf):
    while len(buf) < n:
        chunk = sock.recv(65536)
        if not chunk:
            raise EOFError
        buf += chunk
    out, rest = buf[:n], buf[n:]
    return out, rest


def read_frames(sock, seconds, buf):
    deadline = time.time() + seconds
    while time.time() < deadline:
        sock.settimeout(max(0.05, deadline - time.time()))
        try:
            head, buf = recv_exact(sock, 2, buf)
        except (socket.timeout, TimeoutError):
            return buf
        except EOFError:
            print(json.dumps({"probe": "closed"}))
            return buf
        sock.settimeout(5)
        opcode = head[0] & 0x0F
        n = head[1] & 0x7F
        if n == 126:
            ext, buf = recv_exact(sock, 2, buf)
            n = int.from_bytes(ext, "big")
        elif n == 127:
            ext, buf = recv_exact(sock, 8, buf)
            n = int.from_bytes(ext, "big")
        data, buf = recv_exact(sock, n, buf)
        if opcode == 1:
            print(data.decode(), flush=True)
        elif opcode == 8:
            print(json.dumps({"probe": "close-frame"}))
            return buf
    return buf


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--wait=")]
    wait = next((float(a.split("=", 1)[1]) for a in sys.argv[1:] if a.startswith("--wait=")), 3.0)
    url = urlparse(args[0])
    host, port = url.hostname, url.port or 80
    sock = socket.create_connection((host, port), timeout=5)
    key = base64.b64encode(os.urandom(16)).decode()
    req = (
        f"GET {url.path or '/'} HTTP/1.1\r\nHost: {host}:{port}\r\nUpgrade: websocket\r\n"
        f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
    )
    sock.sendall(req.encode())
    buf = b""
    while b"\r\n\r\n" not in buf:
        buf += sock.recv(4096)
    status, _, buf = buf.partition(b"\r\n\r\n")
    if b" 101 " not in status.split(b"\r\n")[0]:
        print(json.dumps({"probe": "no-upgrade", "status": status.split(b"\r\n")[0].decode()}))
        return 1
    buf = read_frames(sock, 1.0, buf)
    for msg in args[1:]:
        send_frame(sock, msg)
        buf = read_frames(sock, 0.5, buf)
    read_frames(sock, wait, buf)
    sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
