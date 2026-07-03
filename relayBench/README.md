# relayBench

Head-to-head benchmark for Nostr relay implementations. Boots each relay as a
real external process on loopback under an equivalent setup — persistent
storage, signature verification on, no auth, stock limits — replays the same
event corpus into each, and renders a side-by-side report.

```bash
./relayBench/run.sh                 # geode vs strfry, 10k-event synthetic corpus
./relayBench/run.sh --quick         # 2k-event smoke run
./relayBench/run.sh --real          # replay the checked-in real-event dump (2024, ~30k events)
```

`run.sh` builds `:geode:installDist` and the harness, and resolves strfry from
`$STRFRY_BIN`, the `PATH`, or by building it from source into
`relayBench/.cache/` (first run only). `SKIP_STRFRY=1` / `SKIP_GEODE=1` skip a
side.

## What is measured

**Ingest — receipt ➜ queryable.** One connection publishes an event; from the
same instant a second connection hammer-polls `REQ {"ids":[id]}` until the
event comes back. This measures exactly "how long after the relay receives an
event can a REQ return it", which is not the same thing as the OK ack — the
report also shows OK latency and what fraction of events were already
queryable when their OK arrived.

**Ingest — throughput.** The corpus is replayed over N connections (default 4)
with a bounded number of unacked EVENTs in flight, wall-clocked from first
send to last OK. Accepted/rejected counts come from the OKs.

**Queries.** Filters modeled on what real clients send, derived from the
corpus itself so they hit meaningful data: global feed, profile hydration,
home feed (150 follows), hottest thread, notifications for the most-mentioned
pubkey, hashtag feed, 100-id batch fetch, recent time window. Each runs
warmup + measured rounds on one connection (time-to-first-event / time-to-EOSE
percentiles) and then from 8 connections at once (aggregate events/second).
All filters stay inside strfry's default limits, and the number of events each
relay returns is cross-checked — a ⚠ in the report means the relays disagree
about the result set, which invalidates the speed comparison for that row.

**NIP-77 negentropy sync — every pair of relays.** Both sides get an 80% slice
of the corpus (60% overlap); the harness plays the `strfry sync` role with one
side's dataset as its local set and measures: initial reconciliation against
each relay as server (time, NEG-MSG rounds, wire bytes), the delta transfer to
convergence, and the steady-state reconcile of identical sets. Convergence is
verified, so this doubles as an interop test.

**Storage.** On-disk footprint after full ingest (LMDB vs SQLite vs whatever).

## Corpora

The corpus is the controlled variable: every relay sees the same events in
the same order, and reports carry a `fingerprint` (sha256 over event ids) so
two runs are comparable only when fingerprints match.

| source | flag | notes |
|---|---|---|
| **synthetic** (default) | `--events N --seed S` | Deterministic to the byte: seeded keys, fixed timestamps, seed-derived BIP-340 nonces. Same spec ⇒ identical NDJSON on any machine. Zipf-popularity authors, threads, reactions, reposts, zap pairs, hashtags. |
| **real dump** | `--real` | The `quartz` test fixture `nostr_vitor_startup_data.json.gz` — ~31k unique real events from 2024 with a rich kind mix (notes, chats, DMs, zaps, reports, communities). |
| **contact lists** | `--corpus contact-lists.gz --limit 100000 --max-event-bytes 1048576 --max-tags 20000` | 2.1M real kind-3 contact lists (heavy events, ~1.3 kB avg, up to 100+ kB). Grab it with `pip install gdown && gdown 1yyC93xY9sDsEsa351ZAMhtAXwBUh3LYT`. Raising the size/tag caps reconfigures strfry to match, so both relays still accept the full stream. |
| **any dump** | `--corpus FILE` | NDJSON or a single JSON array, gzipped or plain (sniffed by magic bytes). |
| **fresh download** | `--download [urls]` | Pages recent events out of public relays (damus/nos.lol/primal by default). |

Every source goes through the same preparation: dedup by id, drop unsigned
events (NIP-17 rumors), kind-5 deletions and ephemerals (order-dependent or
unqueryable — they would make relays disagree for reasons unrelated to
performance), drop events over the size/tag caps, verify every Schnorr
signature in parallel, sort chronologically. The prepared corpus is cached in
`relayBench/.corpus-cache/` as NDJSON next to a `manifest.json` with the
fingerprint and kind histogram — that file pair is a shareable, citable
benchmark artifact.

> **Why this corpus matters:** there is no de-facto community benchmark corpus
> today. Existing relay benchmarks (rnostr's and privkeyio's nostr-bench,
> mattn's scripts) each synthesize their own events with unspecified
> distributions, so published numbers aren't reproducible corpus-controlled;
> the only shared dataset (Wellorder's early-1m) is frozen in January 2023.
> relayBench's synthetic spec ("seed 1, n=10000, v1" ⇒ byte-identical corpus)
> and manifest/fingerprint convention are designed so other relay authors can
> run the exact same workload and publish comparable numbers.

## Feature parity: NIP-50 search

geode maintains a NIP-50 full-text index by default; strfry has no search
at all. That skews the *ingest* comparison — geode tokenizes every
searchable event into the FTS index (~a quarter of its write cost) for a
feature strfry isn't providing. `--geode-no-search` runs geode with
`--no-search` (no FTS index, NIP-11 stops advertising 50) for the
apples-to-apples write path:

```bash
./relayBench/run.sh --geode-no-search          # geode(no NIP-50) vs strfry
```

To see the price of search inline, run both geode flavors side by side:

```bash
GEODE=geode/build/install/geode/bin/geode
./relayBench/run.sh --geode-no-search \
    --relay "geode-fts=$GEODE --host 127.0.0.1 --port {port} --db {dir}/geode.sqlite"
```

## Adding another relay

No code needed if the relay can be launched from a command line:

```bash
./relayBench/run.sh --relay 'nostr-rs-relay=/usr/bin/nostr-rs-relay --db {dir} --port {port}'
```

`{port}` and `{dir}` are substituted at launch; the process must listen on
`127.0.0.1:{port}` with persistent storage under `{dir}`, verification on and
no auth. If the relay needs a config file, point the template at a small
wrapper script that writes one (see `StrfryRelay` in
`relays/RelayUnderTest.kt` for the pattern — adding a first-class subclass is
~20 lines).

## Output

The terminal report shows each metric as name / bar / value rows with the
winner starred, followed by a head-to-head summary. Every run also writes:

- `relayBench/results/<timestamp>/report.md` — shareable Markdown
- `relayBench/results/<timestamp>/results.json` — raw numbers for tooling

## Direct harness invocation

`run.sh` is a thin wrapper; the harness itself is
`relayBench/build/install/relaybench/bin/relaybench` — see `--help` for all
options (`--samples`, `--publishers`, `--window`, `--query-rounds`,
`--query-conns`, `--no-sync`, `--out`, `--keep-data`, …).
