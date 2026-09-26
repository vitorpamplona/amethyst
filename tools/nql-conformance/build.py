#!/usr/bin/env python3
"""Build and check NIP-FF's (Nostr Query Language) conformance vectors.

Signs the corpus in cases.json with fixed keys (alice = 1, bob = 2, ...), lays it
out as the NIP's `events` and `tags` sources in PostgreSQL and in SQLite, and runs
every case on both engines, each twice (rows inserted forward and in reverse):

- the declared column names and types must match what the engine returns;
- the stated rows must match (as a multiset unless the case is `ordered`); a case
  without stated rows takes PostgreSQL's, which SQLite must then reproduce;
- an `error` case must fail on the engine.

A case runs as written unless it carries a translation for an engine: the query
rewritten the way the NIP's implementation notes tell an implementation on that
engine to rewrite it. `"sqlite": null` marks a case SQLite cannot express (a
runtime error it has no way to raise). `invalid` cases are only emitted: no engine
can judge the language's own rules.

Usage:  build.py [--out FF-conformance.json] [--postgres "host=/tmp port=55432 user=postgres"]

Needs `pip install "psycopg[binary]"` and a PostgreSQL 16+ server; the script
creates (and drops) a database named nql_conformance with the C locale.
"""
import argparse, hashlib, json, math, os, re, sqlite3, sys

import psycopg

HERE = os.path.dirname(os.path.abspath(__file__))

# ---- BIP-340 Schnorr, the reference algorithm, with zero auxiliary randomness so signatures are stable.

P = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
G = (
    0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798,
    0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8,
)


def point_add(a, b):
    if a is None:
        return b
    if b is None:
        return a
    if a[0] == b[0] and a[1] != b[1]:
        return None
    if a == b:
        lam = (3 * a[0] * a[0] * pow(2 * a[1], P - 2, P)) % P
    else:
        lam = ((b[1] - a[1]) * pow(b[0] - a[0], P - 2, P)) % P
    x = (lam * lam - a[0] - b[0]) % P
    return x, (lam * (a[0] - x) - a[1]) % P


def point_mul(pt, k):
    r = None
    for i in range(256):
        if (k >> i) & 1:
            r = point_add(r, pt)
        pt = point_add(pt, pt)
    return r


def tagged_hash(tag, msg):
    t = hashlib.sha256(tag.encode()).digest()
    return hashlib.sha256(t + t + msg).digest()


def b32(x):
    return x.to_bytes(32, "big")


def pubkey(secret):
    return b32(point_mul(G, secret)[0]).hex()


def sign(msg, secret):
    pt = point_mul(G, secret)
    d = secret if pt[1] % 2 == 0 else N - secret
    t = bytes(a ^ b for a, b in zip(b32(d), tagged_hash("BIP0340/aux", bytes(32))))
    k0 = int.from_bytes(tagged_hash("BIP0340/nonce", t + b32(pt[0]) + msg), "big") % N
    r = point_mul(G, k0)
    k = k0 if r[1] % 2 == 0 else N - k0
    e = int.from_bytes(tagged_hash("BIP0340/challenge", b32(r[0]) + b32(pt[0]) + msg), "big") % N
    return (b32(r[0]) + b32((k + e * d) % N)).hex()


def event(secret, created_at, kind, tags, content):
    pk = pubkey(secret)
    serial = json.dumps([0, pk, created_at, kind, tags, content], separators=(",", ":"), ensure_ascii=False)
    eid = hashlib.sha256(serial.encode()).digest()
    return {"id": eid.hex(), "pubkey": pk, "created_at": created_at, "kind": kind, "tags": tags, "content": content, "sig": sign(eid, secret)}


# ---- The corpus.


def build_corpus(source):
    keys = {name: i + 1 for i, name in enumerate(source["authors"])}
    pubkeys = {name: pubkey(k) for name, k in keys.items()}
    ids = {}

    def resolve(s):
        def one(m):
            what, ref = m.group(1), m.group(2)
            return pubkeys[ref] if what == "pubkey" else ids[ref]

        return re.sub(r"\{(pubkey|id):([A-Za-z0-9-]+)\}", one, s)

    events = []
    for e in source["corpus"]:
        tags = [[resolve(v) for v in t] for t in e["tags"]]
        ev = event(keys[e["author"]], e["created_at"], e["kind"], tags, e["content"])
        ids[e["ref"]] = ev["id"]
        events.append(ev)
    return events, resolve


def tag_rows(events):
    for e in events:
        for idx, tag in enumerate(e["tags"]):
            if not tag:
                continue
            vals = (tag + [None] * 5)[:5]
            yield (e["id"], idx, *vals, e["created_at"], e["kind"], e["pubkey"])


# ---- Engines.


class Failure(Exception):
    pass


def lower_names(names):
    return [n.lower() for n in names]


class Postgres:
    TYPES = {20: "INTEGER", 21: "INTEGER", 23: "INTEGER", 701: "REAL", 25: "TEXT", 1043: "TEXT", 705: "TEXT", 16: "BOOLEAN"}

    def __init__(self, dsn, events):
        admin = psycopg.connect(dsn + " dbname=postgres", autocommit=True)
        admin.execute("DROP DATABASE IF EXISTS nql_conformance")
        admin.execute("CREATE DATABASE nql_conformance TEMPLATE template0 ENCODING 'UTF8' LOCALE 'C'")
        admin.close()
        self.conn = psycopg.connect(dsn + " dbname=nql_conformance", autocommit=True)
        with open(os.path.join(HERE, "postgres.sql")) as f:
            self.conn.execute(f.read())
        for schema, order in (("fwd", events), ("rev", list(reversed(events)))):
            self.conn.execute(f"CREATE SCHEMA {schema}")
            self.conn.execute(f"CREATE TABLE {schema}.events (id text, pubkey text, created_at int8, kind int8, content text, sig text)")
            self.conn.execute(
                f"CREATE TABLE {schema}.tags (event_id text, idx int8, t0 text, t1 text, t2 text, t3 text, t4 text, created_at int8, kind int8, pubkey text)"
            )
            with self.conn.cursor() as cur:
                cur.executemany(f"INSERT INTO {schema}.events VALUES (%s, %s, %s, %s, %s, %s)", [(e["id"], e["pubkey"], e["created_at"], e["kind"], e["content"], e["sig"]) for e in order])
                cur.executemany(f"INSERT INTO {schema}.tags VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)", list(tag_rows(order)))

    @staticmethod
    def translate(query):
        """The literal-level rewrites every query needs: 64-bit integers, binary64 reals, `?` placeholders."""
        out, i = [], 0
        for m in re.finditer(r"'(?:[^']|'')*'|(?<![\w.])(-9223372036854775808)(?![\w.])|(?<![\w.])(\d+\.\d+(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+|\d+)(?![\w.])|\?|%|\bAS\s+(INTEGER|REAL)\b", query, re.I):
            out.append(query[i : m.start()])
            tok = m.group(0)
            if tok.startswith("'"):
                out.append(tok.replace("%", "%%"))
            elif tok == "?":
                out.append("%s")
            elif tok == "%":
                out.append("%%")
            elif m.group(1):
                out.append("(-9223372036854775807::int8 - 1)")
            elif m.group(3):
                out.append("AS int8" if m.group(3).upper() == "INTEGER" else "AS float8")
            elif re.fullmatch(r"\d+", tok):
                out.append(f"{tok}::int8" if int(tok) < 2**63 else tok)
            else:
                out.append(f"{tok}::float8")
            i = m.end()
        out.append(query[i:])
        return "".join(out)

    def run(self, schema, query, params):
        self.conn.execute(f"SET search_path = {schema}, public")
        try:
            cur = self.conn.execute(self.translate(query), params or [])
        except psycopg.Error as e:
            raise Failure(str(e).splitlines()[0])
        names = lower_names([d.name for d in cur.description])
        types = [self.TYPES.get(d.type_code, f"oid {d.type_code}") for d in cur.description]
        return names, types, [list(r) for r in cur.fetchall()]


class SQLite:
    def __init__(self, events):
        self.dbs = {}
        for schema, order in (("fwd", events), ("rev", list(reversed(events)))):
            db = sqlite3.connect(":memory:")
            db.execute("PRAGMA case_sensitive_like = ON")
            db.execute("CREATE TABLE events (id TEXT, pubkey TEXT, created_at INTEGER, kind INTEGER, content TEXT, sig TEXT)")
            db.execute("CREATE TABLE tags (event_id TEXT, idx INTEGER, t0 TEXT, t1 TEXT, t2 TEXT, t3 TEXT, t4 TEXT, created_at INTEGER, kind INTEGER, pubkey TEXT)")
            db.executemany("INSERT INTO events VALUES (?, ?, ?, ?, ?, ?)", [(e["id"], e["pubkey"], e["created_at"], e["kind"], e["content"], e["sig"]) for e in order])
            db.executemany("INSERT INTO tags VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", list(tag_rows(order)))
            self.dbs[schema] = db

    def run(self, schema, query, params):
        try:
            cur = self.dbs[schema].execute(query, params or [])
            rows = [list(r) for r in cur.fetchall()]
        except sqlite3.Error as e:
            raise Failure(str(e))
        names = lower_names([d[0] for d in cur.description])
        return names, None, rows


def conform(rows, columns, engine):
    """The engine's values in the NIP's JSON form, checking each against its declared type."""
    out = []
    for row in rows:
        conv = []
        for (name, typ), v in zip(columns, row):
            if v is None:
                conv.append(None)
                continue
            ok = {
                "INTEGER": isinstance(v, int) and not isinstance(v, bool),
                "REAL": isinstance(v, float) and math.isfinite(v),
                "TEXT": isinstance(v, str),
                "BOOLEAN": isinstance(v, bool) or (engine == "sqlite" and v in (0, 1) and not isinstance(v, float)),
            }[typ]
            if not ok:
                raise Failure(f"column {name} is {typ}, the engine returned {v!r}")
            conv.append(bool(v) if typ == "BOOLEAN" else v)
        out.append(conv)
    return out


def same_value(a, b, tol):
    if a is None or b is None:
        return a is None and b is None
    if isinstance(a, bool) or isinstance(b, bool):
        return type(a) is type(b) and a == b
    if isinstance(a, str) or isinstance(b, str):
        return a == b
    if tol is None:
        return a == b
    return abs(a - b) <= tol * max(1.0, abs(a), abs(b))


def same_rows(a, b, ordered, tol):
    if len(a) != len(b):
        return False
    eq = lambda x, y: len(x) == len(y) and all(same_value(p, q, tol) for p, q in zip(x, y))
    if ordered:
        return all(eq(x, y) for x, y in zip(a, b))
    left = list(b)
    for row in a:
        for i, other in enumerate(left):
            if eq(row, other):
                del left[i]
                break
        else:
            return False
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out")
    ap.add_argument("--postgres", default="host=/tmp port=55432 user=postgres")
    args = ap.parse_args()

    with open(os.path.join(HERE, "cases.json")) as f:
        source = json.load(f)
    events, resolve = build_corpus(source)
    pg = Postgres(args.postgres, events)
    lite = SQLite(events)

    problems, cases, skipped = [], [], []
    for c in source["cases"]:
        name = c["name"]
        params = [resolve(p) if isinstance(p, str) else p for p in c["params"]] if "params" in c else None
        out = {"name": name, "query": resolve(c["query"])}
        if params is not None:
            out["params"] = params

        if "error" in c:
            out["error"] = c["error"]
            if c["error"] == "error":
                for label, engine, query in (("postgres", pg, c.get("postgres", c["query"])), ("sqlite", lite, c.get("sqlite", c["query"]))):
                    if query is None:
                        skipped.append(f"{name} ({label})")
                        continue
                    try:
                        engine.run("fwd", resolve(query), params)
                        problems.append(f"{name}: {label} succeeded, expected an evaluation error")
                    except Failure:
                        pass
            cases.append(out)
            continue

        columns = c["columns"]
        ordered = c.get("ordered", False)
        tol = c.get("tolerance")
        expected = c.get("rows")
        for label, engine in (("postgres", pg), ("sqlite", lite)):
            query = c.get(label, c["query"])
            if query is None:
                skipped.append(f"{name} ({label})")
                continue
            try:
                results = []
                for schema in ("fwd", "rev"):
                    names, types, rows = engine.run(schema, resolve(query), params)
                    if names != [n for n, _ in columns]:
                        raise Failure(f"names {names}, declared {[n for n, _ in columns]}")
                    if types is not None and types != [t for _, t in columns]:
                        raise Failure(f"types {types}, declared {[t for _, t in columns]}")
                    results.append(conform(rows, columns, label))
                if not same_rows(results[0], results[1], ordered, tol):
                    raise Failure(f"rows depend on insertion order: {results[0]} vs {results[1]}")
                if expected is None:
                    expected = results[0]
                elif not same_rows(results[0], expected, ordered, tol):
                    raise Failure(f"stated {json.dumps(expected, ensure_ascii=False)}, got {json.dumps(results[0], ensure_ascii=False)}")
            except Failure as e:
                problems.append(f"{name} ({label}): {e}")
        out["columns"] = columns
        out["rows"] = expected
        if ordered:
            out["ordered"] = True
        if tol is not None:
            out["tolerance"] = tol
        cases.append(out)

    print(f"{len(cases)} cases; not expressible on an engine: {len(skipped)}")
    for s in skipped:
        print("  skipped", s)
    if problems:
        print(f"{len(problems)} problem(s):")
        for p in problems:
            print("  " + p)
        sys.exit(1)
    if args.out:
        with open(args.out, "w") as f:
            json.dump({"nip": "FF", "events": events, "cases": cases}, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print("wrote", args.out)


if __name__ == "__main__":
    main()
