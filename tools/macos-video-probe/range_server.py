# A loopback server answering Range like MediaRelay does with this CDN's answers: 206 with
# Content-Type, Content-Range and Content-Length, and no Accept-Ranges on a 206.
import os, re, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = sys.argv[2]

class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[server] %s %s range=%s\n" % (self.command, self.path, self.headers.get("Range")))

    def _serve(self, body):
        path = os.path.join(ROOT, os.path.basename(self.path))
        if not os.path.isfile(path):
            self.send_response(404); self.send_header("Content-Length", "0"); self.end_headers(); return
        total = os.path.getsize(path)
        rng = self.headers.get("Range")
        start, end = 0, total - 1
        m = re.match(r"bytes=(\d*)-(\d*)$", rng or "")
        if m:
            if m.group(1) == "":
                start = max(0, total - int(m.group(2)))
            else:
                start = int(m.group(1))
                if m.group(2): end = min(int(m.group(2)), total - 1)
            self.send_response(206)
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, total))
        else:
            self.send_response(200)
            self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        if not body: return
        try:
            with open(path, "rb") as f:
                f.seek(start)
                left = end - start + 1
                while left > 0:
                    chunk = f.read(min(65536, left))
                    if not chunk: break
                    self.wfile.write(chunk); left -= len(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self): self._serve(True)
    def do_HEAD(self): self._serve(False)

ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
