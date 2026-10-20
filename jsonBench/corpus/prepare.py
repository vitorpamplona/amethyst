#!/usr/bin/env python3
"""
Builds the jsonBench corpus: one NDJSON file per workload plus manifest.json.

Every harness reads the same files and must reproduce the digest recorded in the
manifest, so all parsers are proven to decode the same events identically before
their timings mean anything. See jsonBench/HARNESS.md for the digest definition.

    python3 jsonBench/corpus/prepare.py                       # quartz's real 2024 dump
    python3 jsonBench/corpus/prepare.py --source dump.ndjson  # any NDJSON / JSON array, .gz or plain
    python3 jsonBench/corpus/prepare.py --canonical           # re-serialize with NIP-01 escaping

Events are kept byte-for-byte as they appear in the source (the line a relay would
send) unless --canonical is given.
"""
import argparse
import gzip
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DEFAULT_SOURCE = os.path.join(ROOT, "quartz/src/commonTest/resources/nostr_vitor_startup_data.json.gz")
DEFAULT_OUT = os.path.join(os.path.dirname(HERE), ".corpus")

FIELDS = {"id", "pubkey", "created_at", "kind", "tags", "content", "sig"}

# name -> (description, predicate over the decoded event)
LIST_KINDS = {3, 10000, 10001, 10002, 10003, 10004, 10005, 10006, 10007, 10015, 10030, 30000, 30001, 30002, 30003}
WORKLOADS = [
    ("all", "every event in the corpus: the real kind mix a client downloads", lambda e: True),
    ("notes", "kind 1 text notes: short UTF-8 content, a few e/p tags", lambda e: e["kind"] == 1),
    ("reactions", "kind 7 reactions: tiny events, 3 tags, 1-char content", lambda e: e["kind"] == 7),
    ("dms", "kind 4 NIP-04 DMs: base64 ciphertext content", lambda e: e["kind"] == 4),
    ("giftwraps", "kind 1059 NIP-59 gift wraps: one long base64 NIP-44 payload", lambda e: e["kind"] == 1059),
    ("metadata", "kind 0 profiles: content is an escaped JSON object", lambda e: e["kind"] == 0),
    ("zaps", "kind 9735 zap receipts: escaped JSON zap request inside a tag", lambda e: e["kind"] == 9735),
    ("lists", "contact/mute/relay/bookmark lists (3, 1000x, 3000x): tag-heavy", lambda e: e["kind"] in LIST_KINDS),
]


def open_text(path):
    with open(path, "rb") as f:
        magic = f.read(2)
    if magic == b"\x1f\x8b":
        return gzip.open(path, "rt", encoding="utf-8")
    return open(path, "rt", encoding="utf-8")


def iter_raw(path):
    """Yields (raw_text, decoded) for every event, from a JSON array or NDJSON (events or
    ["EVENT", sub, event] relay messages)."""
    with open_text(path) as f:
        text = f.read()
    dec = json.JSONDecoder()
    stripped = text.lstrip()
    if stripped.startswith("[") and not stripped.startswith('["EVENT"'):
        # One big JSON array: walk it with raw_decode to keep each element's exact bytes.
        i = text.index("[") + 1
        n = len(text)
        while True:
            while i < n and text[i] in " \t\r\n,":
                i += 1
            if i >= n or text[i] == "]":
                return
            obj, end = dec.raw_decode(text, i)
            yield text[i:end], obj
            i = end
    else:
        for line in text.splitlines():
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            if isinstance(obj, list) and len(obj) >= 3 and obj[0] == "EVENT":
                # Keep the event's raw span: re-locate it after the second comma-separated element.
                obj = obj[2]
                yield None, obj
            else:
                yield line, obj


def is_clean(e):
    """Only events every parser in every language can represent identically."""
    if not isinstance(e, dict) or set(e.keys()) != FIELDS:
        return False
    for k in ("id", "pubkey", "sig", "content"):
        if not isinstance(e[k], str):
            return False
    for k in ("created_at", "kind"):
        v = e[k]
        if isinstance(v, bool) or not isinstance(v, int):
            return False
    if not (0 <= e["kind"] < 2**31 and 0 <= e["created_at"] < 2**53):
        return False
    tags = e["tags"]
    if not isinstance(tags, list):
        return False
    strings = [e["id"], e["pubkey"], e["sig"], e["content"]]
    for t in tags:
        if not isinstance(t, list):
            return False
        for v in t:
            if not isinstance(v, str):
                return False
            strings.append(v)
    try:
        for s in strings:
            s.encode("utf-8")  # rejects lone surrogates ("\ud83d" without its pair)
    except UnicodeEncodeError:
        return False
    return True


def canonical(e):
    """NIP-01 serialization of the full event: raw UTF-8, only the mandatory escapes."""
    return json.dumps(
        {k: e[k] for k in ("id", "pubkey", "created_at", "kind", "tags", "content", "sig")},
        ensure_ascii=False,
        separators=(",", ":"),
    )


US, RS, GS, FS = b"\x1f", b"\x1e", b"\x1d", b"\x1c"


def digest_event(h, e):
    """The cross-language digest. Keep in sync with HARNESS.md."""
    h.update(e["id"].encode())
    h.update(US)
    h.update(e["pubkey"].encode())
    h.update(US)
    h.update(str(e["created_at"]).encode())
    h.update(US)
    h.update(str(e["kind"]).encode())
    h.update(US)
    for t in e["tags"]:
        for v in t:
            h.update(v.encode())
            h.update(US)
        h.update(RS)
    h.update(GS)
    h.update(e["content"].encode())
    h.update(US)
    h.update(e["sig"].encode())
    h.update(US)
    h.update(FS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default=DEFAULT_SOURCE)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--canonical", action="store_true", help="re-serialize every event with NIP-01 escaping")
    ap.add_argument("--limit", type=int, default=0, help="keep at most N events (0 = all)")
    args = ap.parse_args()

    seen = set()
    events = []  # (raw_bytes, decoded)
    dropped_dup = dropped_dirty = 0
    for raw, e in iter_raw(args.source):
        if not isinstance(e, dict) or e.get("id") in seen:
            dropped_dup += 1
            continue
        seen.add(e.get("id"))
        if not is_clean(e):
            dropped_dirty += 1
            continue
        if args.canonical or raw is None or "\n" in raw or "\r" in raw:
            raw = canonical(e)
        events.append((raw.strip().encode("utf-8"), e))
        if args.limit and len(events) >= args.limit:
            break

    os.makedirs(args.out, exist_ok=True)
    fingerprint = hashlib.sha256()
    for raw, _ in events:
        fingerprint.update(raw)
        fingerprint.update(b"\n")

    workloads = []
    for name, desc, pred in WORKLOADS:
        chosen = [(raw, e) for raw, e in events if pred(e)]
        if not chosen:
            print(f"skip workload {name}: no events", file=sys.stderr)
            continue
        file = f"{name}.ndjson"
        h = hashlib.sha256()
        size = 0
        tags = 0
        with open(os.path.join(args.out, file), "wb") as f:
            for raw, e in chosen:
                f.write(raw)
                f.write(b"\n")
                size += len(raw)
                tags += len(e["tags"])
                digest_event(h, e)
        workloads.append(
            {
                "name": name,
                "file": file,
                "description": desc,
                "events": len(chosen),
                "bytes": size,  # sum of event lengths, newlines excluded
                "avg_bytes": round(size / len(chosen)),
                "avg_tags": round(tags / len(chosen), 1),
                "digest": h.hexdigest(),
            }
        )
        print(f"{name:10s} {len(chosen):6d} events {size / 1e6:8.2f} MB  avg {size // len(chosen)} B")

    manifest = {
        "version": 1,
        "source": os.path.relpath(os.path.abspath(args.source), ROOT),
        "canonical": args.canonical,
        "fingerprint": fingerprint.hexdigest(),
        "events": len(events),
        "dropped_duplicates": dropped_dup,
        "dropped_unrepresentable": dropped_dirty,
        "workloads": workloads,
    }
    with open(os.path.join(args.out, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print(f"{len(events)} events ({dropped_dup} duplicates, {dropped_dirty} unrepresentable dropped) -> {args.out}")


if __name__ == "__main__":
    main()
