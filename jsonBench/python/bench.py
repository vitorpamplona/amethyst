"""jsonBench Python harness. Contract: ../HARNESS.md. Run through ./run.sh.

Without --child the script is the orchestrator: it re-runs itself once per parser in a
fresh interpreter (same isolation rule as the JIT harnesses: a parser's heap and the
allocator state it leaves behind don't tax the next one) and merges the rows.
"""

from __future__ import annotations

import argparse
import gc
import hashlib
import json
import os
import platform
import subprocess
import sys
import time
import tracemalloc
from importlib.metadata import version as dist_version
from pathlib import Path

HERE = Path(__file__).resolve().parent
BENCH = HERE.parent

US, RS, GS, FS = b"\x1f", b"\x1e", b"\x1d", b"\x1c"

# ------------------------------------------------------------------ parsers
#
# Every parser starts from what a WebSocket text frame is in Python: a `str` (websockets
# recv(), aiohttp WSMessage.data). `input` names the path (HARNESS.md vocabulary):
# "string" (the parser takes the str itself) or "string→bytes" (the parser only reads
# bytes, so `s.encode()` runs inside the timed loop, as part of the parse function).
# `access` picks the timed-loop fold: "dict" (e["kind"]...), "attr" (e.kind...), "dom"
# (kind + tag count from a lazy proxy only), "bytes" (baseline: encoded length). `load()`
# returns the parse function, `to_py` (optional) converts a lazy result into plain
# objects for the digest. `retained=False` marks results that cannot be kept (views into
# a reused parser document). Licenses were read from each wheel's METADATA / LICENSE
# file (see README.md).

PY = f"CPython {platform.python_version()}"


def _stdlib(loader):
    return lambda: loader


def _load_json_str():
    return json.loads


def _load_orjson():
    import orjson

    return orjson.loads


def _load_ujson():
    import ujson

    return ujson.loads


def _load_msgspec_typed():
    import msgspec

    class Event(msgspec.Struct):
        id: str
        pubkey: str
        created_at: int
        kind: int
        tags: list[list[str]]
        content: str
        sig: str

    return msgspec.json.Decoder(Event).decode


def _load_msgspec_generic():
    import msgspec

    return msgspec.json.Decoder().decode


def _load_pysimdjson_dom():
    import simdjson

    return simdjson.Parser().parse


def _load_pysimdjson_recursive():
    import simdjson

    p = simdjson.Parser()
    return lambda s: p.parse(s, True)


def _load_cysimdjson():
    import cysimdjson

    return cysimdjson.JSONParser().parse_string


def _load_rapidjson():
    import rapidjson

    return rapidjson.loads


def _load_jiter():
    import jiter

    f = jiter.from_json
    return lambda s: f(s.encode())


def _load_simplejson():
    import simplejson
    import simplejson.decoder

    if simplejson.decoder.c_scanstring is None:
        raise RuntimeError("simplejson C speedups not available")
    return simplejson.loads


def _load_yyjson():
    import yyjson

    f = yyjson.loads
    return lambda s: f(s.encode())


def _load_encode():
    return str.encode


PARSERS = [
    dict(id="json-stdlib", library="json (stdlib)", dist=None, license="PSF-2.0", mode="typed", input="string",
         access="dict", load=_load_json_str,
         notes="json.loads(str), C scanner reading the str's own code units; dict result"),
    dict(id="baseline-utf8-encode", library="str.encode", dist=None, license="PSF-2.0", mode="baseline",
         input="string→bytes", access="bytes", load=_load_encode,
         notes="s.encode() only (no parse) on a fresh str: the str -> UTF-8 bytes step the string→bytes rows pay"),
    dict(id="orjson", library="orjson", dist="orjson", license="MPL-2.0 AND (Apache-2.0 OR MIT)", mode="typed",
         input="string", access="dict", load=_load_orjson,
         notes="orjson.loads(str): reads the str's UTF-8 through the C API (free for an ASCII str, an encode cached on "
               "the str otherwise); Rust, caches short dict keys"),
    dict(id="ujson", library="ujson", dist="ujson", license="BSD-3-Clause AND TCL", mode="typed", input="string",
         access="dict", load=_load_ujson, notes="ujson.loads(str)"),
    dict(id="msgspec-typed", library="msgspec", dist="msgspec", license="BSD-3-Clause", mode="typed", input="string",
         access="attr", load=_load_msgspec_typed,
         notes="msgspec.json.Decoder(Event).decode(str), Event is a msgspec.Struct (validated types), decoder reused"),
    dict(id="msgspec-generic", library="msgspec", dist="msgspec", license="BSD-3-Clause", mode="typed",
         input="string", access="dict", load=_load_msgspec_generic,
         notes="msgspec.json.Decoder().decode(str) -> dict, decoder reused"),
    dict(id="pysimdjson", library="pysimdjson", dist="pysimdjson", license="MIT", mode="dom", input="string",
         access="dom", load=_load_pysimdjson_dom, to_py=lambda o: o.as_dict(), retained=False,
         notes="simdjson.Parser().parse(str) -> lazy Object proxy (parse takes the str's UTF-8 itself); one parser "
               "reused, so each document is only valid until the next parse (borrowed-ish), and retained is null; "
               "loop reads kind + len(tags). Bundles simdjson (Apache-2.0)"),
    dict(id="pysimdjson-recursive", library="pysimdjson", dist="pysimdjson", license="MIT", mode="typed",
         input="string", access="dict", load=_load_pysimdjson_recursive,
         notes="simdjson.Parser().parse(str, recursive=True) -> plain dict; parser reused"),
    dict(id="cysimdjson", library="cysimdjson", dist="cysimdjson", license="Apache-2.0", mode="dom", input="string",
         access="dom", load=_load_cysimdjson, to_py=lambda o: o.export(), retained=False,
         notes="cysimdjson.JSONParser().parse_string(str) (its str entry point: PyUnicode_AsUTF8AndSize) -> lazy "
               "JSONElement; parser reused, documents valid until the next parse, so retained is null; loop reads "
               "kind + len(tags)"),
    dict(id="python-rapidjson", library="python-rapidjson", dist="python-rapidjson", license="MIT", mode="typed",
         input="string", access="dict", load=_load_rapidjson, notes="rapidjson.loads(str)"),
    dict(id="jiter", library="jiter", dist="jiter", license="MIT", mode="typed", input="string→bytes",
         access="dict", load=_load_jiter,
         notes="jiter.from_json(s.encode()) (pydantic's parser; from_json only takes bytes, the encode is timed), "
               "default cache_mode='all' string cache"),
    dict(id="simplejson", library="simplejson", dist="simplejson", license="MIT OR AFL-2.1", mode="typed",
         input="string", access="dict", load=_load_simplejson, notes="simplejson.loads(str) with the C speedups"),
    dict(id="yyjson", library="yyjson (py_yyjson)", dist="yyjson", license="MIT", mode="typed",
         input="string→bytes", access="dict", load=_load_yyjson,
         notes="yyjson.loads(s.encode()) -> dict (encode timed). loads(str) is not used: it returns mojibake for "
               "non-ASCII (checked in 4.0.6: '{\"a\":\"é\"}' -> 'Ã©'). Bundles yyjson (MIT)"),
]
BY_ID = {p["id"]: p for p in PARSERS}


def parser_version(p):
    return dist_version(p["dist"]) if p["dist"] else PY


# ------------------------------------------------------------------ digest

def _int(n, name):
    if type(n) is not int or n < 0:
        raise ValueError(f"{name} is not a non-negative int: {n!r}")
    return str(n).encode()


def _str(s, name):
    if type(s) is not str:
        raise ValueError(f"{name} is not a str: {type(s).__name__}")
    return s.encode()


def digest_event(h, e):
    """Same byte stream as corpus/prepare.py digest_event, with type checks."""
    if not isinstance(e, dict):
        e = {k: getattr(e, k) for k in ("id", "pubkey", "created_at", "kind", "tags", "content", "sig")}
    parts = [_str(e["id"], "id"), US, _str(e["pubkey"], "pubkey"), US, _int(e["created_at"], "created_at"), US,
             _int(e["kind"], "kind"), US]
    tags = e["tags"]
    if not isinstance(tags, list):
        raise ValueError("tags is not a list")
    for t in tags:
        if not isinstance(t, list):
            raise ValueError("tag is not a list")
        for v in t:
            parts.append(_str(v, "tag element"))
            parts.append(US)
        parts.append(RS)
    parts += [GS, _str(e["content"], "content"), US, _str(e["sig"], "sig"), US, FS]
    h.update(b"".join(parts))


# ------------------------------------------------------------------ timed rounds

def make_round(parse, access):
    # One closure per access style so the loop body has no extra call for the fold. A
    # round takes its inputs as an argument because every round gets fresh str objects.
    if access == "dict":
        def rnd(inputs):
            s = 0
            for x in inputs:
                e = parse(x)
                s += e["kind"] + len(e["tags"]) + len(e["content"])
            return s
    elif access == "attr":
        def rnd(inputs):
            s = 0
            for x in inputs:
                e = parse(x)
                s += e.kind + len(e.tags) + len(e.content)
            return s
    elif access == "dom":
        def rnd(inputs):
            s = 0
            for x in inputs:
                e = parse(x)
                s += e["kind"] + len(e["tags"])
                del e  # a reused simdjson parser refuses to parse while proxies are alive
            return s
    elif access == "bytes":
        def rnd(inputs):
            s = 0
            for x in inputs:
                s += len(parse(x))
            return s
    else:
        raise ValueError(access)
    return rnd


def fresh_frames(raw):
    """New str objects for every line, as a socket hands over a new str per frame.

    CPython caches a str's UTF-8 form on the str the first time the C API asks for it
    (PyUnicode_AsUTF8AndSize: orjson, msgspec, rapidjson, pysimdjson and cysimdjson do;
    the stdlib json, ujson and simplejson read the code units and do not). Rounds over the
    same str objects would get that encode for free from round 2 on, so every round,
    warm-up included, runs on a fresh list built outside the timer. ASCII-only strs have
    no cache: their UTF-8 is their storage.
    """
    return [b.decode("utf-8") for b in raw]


def measure_retained(parse, raw):
    """tracemalloc'd bytes still held after keeping every parse result, per event.

    Frames are fresh strs created before tracing starts and dropped one by one, so the
    frame (and any UTF-8 cache a parser hangs on it) is not counted, as in a client that
    drops the frame once parsed. The result list is allocated before tracing starts, so
    the container is excluded. Shared objects (small ints, interned or cached keys)
    already alive before the pass are free, as they are in a client.
    """
    n = len(raw)
    frames = fresh_frames(raw)
    keep = [None] * n
    gc.collect()
    tracemalloc.start()
    try:
        for i in range(n):
            keep[i] = parse(frames[i])
            frames[i] = None
        gc.collect()
        current, _peak = tracemalloc.get_traced_memory()
    finally:
        tracemalloc.stop()
    del keep
    return current / n


class GcTimer:
    """Counts cyclic-GC collections and their time through gc.callbacks."""

    def __init__(self):
        self.active = False
        self.count = 0
        self.ns = 0
        self._t = 0
        gc.callbacks.append(self._cb)

    def _cb(self, phase, info):
        if not self.active:
            return
        if phase == "start":
            self._t = time.perf_counter_ns()
        else:
            self.count += 1
            self.ns += time.perf_counter_ns() - self._t


# ------------------------------------------------------------------ child

def load_lines(corpus: Path, w):
    with open(corpus / w["file"], "rb") as f:
        return [line.rstrip(b"\n") for line in f if line.strip()]


def run_child(opts, workloads):
    p = BY_ID[opts.child]
    row_base = dict(parser=p["id"], library=p["library"], version=None, license=p["license"], mode=p["mode"],
                    input=p["input"], notes=p["notes"])
    load_error = None
    parse = None
    try:
        row_base["version"] = parser_version(p)
        parse = p["load"]()
    except Exception as ex:  # noqa: BLE001 - reported in the row
        load_error = f"load failed: {ex!r}"
    to_py = p.get("to_py")
    gct = GcTimer()
    rows = []
    sink_all = 0
    for w in workloads:
        row = dict(row_base, workload=w["name"], events=w["events"], bytes=w["bytes"], digest_ok=False, errors=0,
                   error=None, rounds=0, median_ns=None, min_ns=None, p90_ns=None, ns_per_event=None,
                   mb_per_s=None, events_per_s=None, alloc_bytes_per_event=None, allocs_per_event=None,
                   gc_count=None, gc_ms=None, retained_bytes_per_event=None)
        rows.append(row)
        if load_error:
            row["error"] = load_error
            continue
        raw = load_lines(opts.corpus, w)
        if len(raw) != w["events"]:
            row["error"] = f"line count {len(raw)} != manifest events {w['events']}"
            continue

        # verify (untimed)
        h = hashlib.sha256()
        for i, x in enumerate(fresh_frames(raw)):
            try:
                r = parse(x)
                if p["access"] == "bytes":
                    r = json.loads(r)
                elif to_py:
                    r = to_py(r)
                digest_event(h, r)
                del r
            except Exception as ex:  # noqa: BLE001
                row["errors"] += 1
                if row["error"] is None:
                    row["error"] = f"event {i}: {ex!r}"
        digest = h.hexdigest()
        row["digest_ok"] = row["errors"] == 0 and digest == w["digest"]
        if not row["digest_ok"]:
            if row["error"] is None:
                row["error"] = f"digest mismatch: {digest} != {w['digest']}"
            continue

        rnd = make_round(parse, p["access"])
        clock = time.perf_counter_ns
        sink = 0
        end = clock() + opts.warmup_ms * 1_000_000
        while True:
            sink += rnd(fresh_frames(raw))
            if clock() >= end:
                break

        # Each round is timed on its own, with a fresh str list built (and the previous one
        # freed) between rounds, outside the clock; min-time counts timed rounds only.
        times = []
        gct.count = gct.ns = 0
        timed = 0
        min_ns = opts.min_time_ms * 1_000_000
        inputs = None
        while len(times) < 10_000:
            inputs = None
            inputs = fresh_frames(raw)
            gct.active = True
            t0 = clock()
            sink += rnd(inputs)
            t = clock() - t0
            gct.active = False
            times.append(t)
            timed += t
            if len(times) >= opts.min_rounds and timed >= min_ns:
                break
        del inputs
        sink_all += sink

        times.sort()
        n = len(times)
        med = times[(n - 1) // 2] if n % 2 else (times[n // 2 - 1] + times[n // 2]) / 2
        p90 = times[max(0, -(-9 * n // 10) - 1)]
        row.update(rounds=n, median_ns=round(med), min_ns=times[0], p90_ns=p90,
                   ns_per_event=med / w["events"], mb_per_s=w["bytes"] / (med / 1e9) / 1e6,
                   events_per_s=w["events"] / (med / 1e9), gc_count=gct.count, gc_ms=gct.ns / 1e6)
        # retained heap (untimed), not for baselines or views into a reused parser document
        if p["mode"] != "baseline" and p.get("retained", True):
            row["retained_bytes_per_event"] = measure_retained(parse, raw)
        ret = row["retained_bytes_per_event"]
        print(f"  python {p['id']:<22} {w['name']:<10} {row['mb_per_s']:8.1f} MB/s "
              f"{row['ns_per_event']:7.0f} ns/ev  rounds={n} gc={gct.count}"
              f"{'' if ret is None else f' retained={ret:.0f} B/ev'} sink={sink}", file=sys.stderr)
    print("@@ROWS " + json.dumps(rows))
    print(f"  (sink {sink_all})", file=sys.stderr)


# ------------------------------------------------------------------ orchestrator

def host_info():
    cpu = platform.processor() or "unknown"
    try:
        for line in open("/proc/cpuinfo", encoding="utf-8"):
            if line.startswith("model name"):
                cpu = line.split(":", 1)[1].strip()
                break
    except OSError:
        pass
    return {"cpu": cpu, "cores": os.cpu_count(), "os": f"{platform.system()} {platform.release()}"}


def parse_args(argv):
    ap = argparse.ArgumentParser(description="jsonBench Python harness")
    ap.add_argument("--corpus", type=Path, default=BENCH / ".corpus")
    ap.add_argument("--out", type=Path, default=BENCH / "results" / "python.json")
    ap.add_argument("--workloads")
    ap.add_argument("--parsers")
    ap.add_argument("--warmup-ms", type=int, default=1000)
    ap.add_argument("--min-time-ms", type=int, default=2000)
    ap.add_argument("--min-rounds", type=int, default=5)
    ap.add_argument("--quick", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--child")
    o = ap.parse_args(argv)
    if o.quick:
        o.warmup_ms, o.min_time_ms, o.min_rounds = 200, 300, 3
    o.corpus = o.corpus.resolve()
    o.out = o.out.resolve()
    return o


def main(argv):
    opts = parse_args(argv)
    if opts.list:
        for p in PARSERS:
            print(p["id"])
        return 0
    mpath = opts.corpus / "manifest.json"
    if not mpath.exists():
        sys.exit(f"no corpus at {opts.corpus}: run python3 jsonBench/corpus/prepare.py")
    manifest = json.loads(mpath.read_text(encoding="utf-8"))
    workloads = manifest["workloads"]
    if opts.workloads:
        names = opts.workloads.split(",")
        unknown = [n for n in names if n not in {w["name"] for w in workloads}]
        if unknown:
            sys.exit(f"unknown workload(s): {unknown}")
        workloads = [w for w in workloads if w["name"] in names]

    if opts.child:
        if opts.child not in BY_ID:
            sys.exit(f"unknown parser {opts.child}")
        run_child(opts, workloads)
        return 0

    parsers = PARSERS
    if opts.parsers:
        names = opts.parsers.split(",")
        unknown = [n for n in names if n not in BY_ID]
        if unknown:
            sys.exit(f"unknown parser(s): {unknown}")
        parsers = [p for p in PARSERS if p["id"] in names]

    pass_args = ["--corpus", str(opts.corpus), "--workloads", ",".join(w["name"] for w in workloads),
                 "--warmup-ms", str(opts.warmup_ms), "--min-time-ms", str(opts.min_time_ms),
                 "--min-rounds", str(opts.min_rounds)]
    results = []
    for p in parsers:
        print(f"[python] {p['id']}", file=sys.stderr)
        r = subprocess.run([sys.executable, __file__, *pass_args, "--child", p["id"]], stdout=subprocess.PIPE,
                           stderr=None, text=True, check=False)
        line = next((l for l in r.stdout.splitlines() if l.startswith("@@ROWS ")), None)
        if r.returncode == 0 and line:
            results.extend(json.loads(line[7:]))
            continue
        why = f"child exited with {r.returncode}"
        print(f"  {why}", file=sys.stderr)
        for w in workloads:
            results.append(dict(parser=p["id"], library=p["library"], version=None, license=p["license"],
                                mode=p["mode"], input=p["input"], notes=p["notes"], workload=w["name"],
                                events=w["events"], bytes=w["bytes"], digest_ok=False, errors=0, error=why,
                                rounds=0, median_ns=None, min_ns=None, p90_ns=None, ns_per_event=None,
                                mb_per_s=None, events_per_s=None, alloc_bytes_per_event=None,
                                allocs_per_event=None, gc_count=None, gc_ms=None,
                                retained_bytes_per_event=None))

    doc = {
        "harness": "python",
        "language": "Python",
        "runtime": f"{PY} ({platform.python_implementation()}, {platform.python_compiler()})",
        "host": host_info(),
        "settings": {"warmup_ms": opts.warmup_ms, "min_time_ms": opts.min_time_ms, "min_rounds": opts.min_rounds},
        "corpus_fingerprint": manifest["fingerprint"],
        "results": results,
    }
    opts.out.parent.mkdir(parents=True, exist_ok=True)
    opts.out.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {opts.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
