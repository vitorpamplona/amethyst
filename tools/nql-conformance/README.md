# NIP-FF conformance vectors

`build.py` builds `FF-conformance.json`, the test vectors of NIP-FF (Nostr Query
Language), from `cases.json`, and checks every case against two real engines
before writing anything:

- **PostgreSQL 16+**, with the helper functions in `postgres.sql`. Every case with
  rows or a runtime `error` must hold on PostgreSQL, so PostgreSQL is the reference.
- **SQLite** (Python's bundled one). Cases that SQLite cannot express, such as the
  runtime errors it turns into NULL, carry `"sqlite": null` and are listed as
  skipped.

Each engine loads the corpus twice, forward and reversed, so a case whose rows
depend on insertion order fails.

A case runs as written, unless it carries a `postgres` or `sqlite` query. That
query is the case rewritten the way the NIP's implementation notes tell an
implementation on that engine to rewrite it. The published vectors keep only the
NQL query. `invalid` cases are emitted unchecked: they test the language's own
rules, which no engine enforces.

The corpus is signed with fixed keys (alice = 1, bob = 2, …) and BIP-340's zero
auxiliary randomness, so the output is byte-for-byte stable.

```bash
pip install "psycopg[binary]"
# a throwaway server, if none is running
/usr/lib/postgresql/16/bin/initdb -D /tmp/nqlpg -A trust -U postgres
/usr/lib/postgresql/16/bin/pg_ctl -D /tmp/nqlpg -o '-p 55432 -k /tmp' start

./build.py --out ../../../nips/FF-conformance.json --postgres "host=/tmp port=55432 user=postgres"
```
