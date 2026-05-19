# Local Bitcoin headers explorer for NIP-03 OTS

**Date:** 2026-05-08 (revised 2026-05-19)
**Branch:** `claude/review-bitcoin-blockchain-plan-6NGZ5` (originally `claude/review-ots-blockchain-deps-bKns7`)
**Module:** `quartz/` (with thin Android wiring in `amethyst/`)
**Status:** Plan — not yet implemented

## Revision log

**2026-05-19 — decisions locked, scope tightened.** After review against the
intervening 2026-05-14 onchain-zaps work:

- **Scope stays OTS-only.** Trustless verification of NIP-BC onchain zaps
  (kind 8333) needs more than headers — at minimum BIP-37 merkleblock or
  full-block fetch on top of this stack. That becomes a follow-up plan
  (see §20).
- **Module moves to `quartz/.../bitcoin/`** — sibling of `nip03Timestamp/`
  and `nipBCOnchainZaps/`, not nested under NIP-03. The future merkle-block
  phase will reuse `BitcoinPeer` / `HeaderStore` / `HeaderValidator`.
- **Storage is SQLite** via `androidx.sqlite` + `BundledSQLiteDriver`,
  matching the existing `SQLiteEventStore` (`nip01Core/store/sqlite/`).
  No flat append-only file; no hand-rolled height index. See §9.
- **No bundled headers file.** Ship a single hardcoded `Checkpoint(height,
  blockHash, chainWork, time)` constant, bumped at release. First-run sync
  starts at the checkpoint and pulls forward to the tip over P2P. See §7.
- **Pre-checkpoint OTS proofs fall through to HTTP** (`OkHttpBitcoinExplorer`).
  No backward-from-checkpoint sync. Strict-mode users get a clear error
  instead of opening an HTTP connection.
- **`BitcoinExplorerEndpoint`** (`amethyst/.../model/nip03Timestamp/`) is
  now the shared Esplora-URL resolver for OTS and onchain zaps. The
  composite resolver builder must read from it for the HTTP fallback path.
- **Interop test vectors** (§14a) added — every consensus-relevant layer
  (header parser, compact-target, retargeter, P2P codec, OTS proofs) is
  pinned to upstream vectors committed under
  `quartz/src/commonTest/resources/bitcoin/`. A nightly differential test
  asserts `LocalHeadersBitcoinExplorer.blockHash(h)` ==
  `OkHttpBitcoinExplorer.blockHash(h)` for every height in
  `[checkpoint, tip]`, catching any consensus-rule drift end-to-end.

## 1. Motivation

NIP-03 (OpenTimestamps attestations, kind 1040) is currently verified by hitting
a third-party block explorer (`blockstream.info` or `mempool.space`) over HTTPS.
That explorer is a trusted oracle: it can lie about a block's `merkleRoot` and
silently invalidate or fabricate a timestamp.

OTS verification only needs **one piece of chain data** per attestation:
the 80-byte block header at a given height. From that header we use:

- `merkleRoot` — to compare against the digest produced by the proof's ops,
- `time` — to report as the proven timestamp.

Nothing else. No transactions, no UTXOs, no scripts, no witness data,
no compact filters. The OTS proof file itself carries every other byte of
witness (calendar tree siblings, the prefix/suffix of the on-chain
transaction, and the merkle path from that tx up to the block root) inline as
operands of `APPEND`/`PREPEND` ops.

The Bitcoin P2P protocol exposes a first-class `getheaders`/`headers` message
pair specifically for downloading just headers; Bitcoin Core has used it for
"headers-first" sync since v0.10 (2014). This plan implements a headers-only
client that lets Amethyst verify NIP-03 attestations against the actual
proof-of-work chain instead of a trusted explorer.

## 2. Goals & non-goals

### Goals

- Verify any NIP-03 OTS attestation **without trusting any single party** other
  than Bitcoin proof-of-work (and the app build itself, via a checkpoint).
- Run on Android phones; budget ≤100 MB disk, ≤1 % battery/day, ≤10 min
  first-run sync on WiFi.
- Plug into existing `BitcoinExplorer` interface — zero changes to OTS
  verification logic.
- Work over Tor when the user has Tor enabled.
- Keep existing HTTP explorers as a fallback for proofs older than the
  pinned checkpoint, or when local sync hasn't completed yet.
- Live in `quartz/src/commonMain/.../bitcoin/` so `cli/`, `desktopApp/` and
  any future merkle-block / onchain-zap work inherit the same primitives.

### Non-goals

- We are **not** building a full Bitcoin node. No mempool, no UTXO set, no
  block bodies, no script verification.
- We are **not** implementing wallet features (BIP-32 keys, BIP-37 bloom,
  BIP-157/158 compact filters). NIP-03 doesn't need them.
- We are **not** replacing OkHttp explorers entirely — they remain a
  fallback path and the way unsynced proofs get verified.
- No support for testnet/signet (mainnet only — NIP-03 attestations are
  on mainnet).

## 3. Architecture overview

```
┌────────────────────────────────────────────────────────────────────┐
│                         Existing OTS code                          │
│  OtsEvent.verify(resolver) ──► OpenTimestamps.verify ──► explorer  │
│                                              │                     │
│                                              ▼                     │
│                                    BitcoinExplorer  (interface)    │
└────────────────────────────────────────────────────────────────────┘
                                      ▲
                       ┌──────────────┼──────────────┐
                       │              │              │
              OkHttpBitcoinExplorer   │   LocalHeadersBitcoinExplorer  ◄── NEW
              (existing, fallback,    │              │
               URL from              │              ▼
               BitcoinExplorer-      │   ┌─────────────────────────┐
               Endpoint)             │   │   HeaderStore           │
                                      │   │   (SQLite, androidx-    │
                                      │   │    sqlite + Bundled-    │
                                      │   │    SQLiteDriver)        │
                                      │   └─────────────────────────┘
                                      │              ▲
                                      │              │
                                      │   ┌─────────────────────────┐
                                      │   │   HeadersSyncEngine     │
                                      │   │ (validates PoW + chain) │
                                      │   └─────────────────────────┘
                                      │              ▲
                                      │              │
                                      │   ┌─────────────────────────┐
                                      │   │   PeerPool              │
                                      │   │ (multiple BitcoinPeer)  │
                                      │   └─────────────────────────┘
                                      │              ▲
                                      │              │
                                      │   ┌─────────────────────────┐
                                      │   │   BitcoinPeer           │
                                      │   │ (TCP socket + framing + │
                                      │   │  version/getheaders)    │
                                      │   └─────────────────────────┘
                                      │              ▲
                                      │              │
                                      │   ┌─────────────────────────┐
                                      │   │   PeerDiscovery         │
                                      │   │ (DNS seeds, fixed seeds)│
                                      │   └─────────────────────────┘
                                      │
                            CompositeBitcoinExplorer
                            (try local first; if height
                             below pinned checkpoint OR not yet
                             reached by sync, fall back to HTTP
                             via BitcoinExplorerEndpoint)
```

## 4. Module layout

All new code in `quartz/src/commonMain/.../bitcoin/`, sibling of
`nip03Timestamp/` and `nipBCOnchainZaps/`. Protocol code is
platform-independent; socket I/O is the only `expect`/`actual` boundary.
Storage uses `androidx.sqlite` which is already KMP — no platform split.

```
quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/bitcoin/
├── proto/
│   ├── BitcoinNetMagic.kt          # 0xf9beb4d9 mainnet
│   ├── MessageHeader.kt            # 24-byte envelope codec
│   ├── VarInt.kt                   # CompactSize encode/decode
│   ├── NetAddr.kt                  # net_addr (26B / 30B with timestamp)
│   ├── ServiceFlags.kt             # NODE_NETWORK, NODE_NETWORK_LIMITED, NODE_WITNESS
│   ├── messages/
│   │   ├── VersionMessage.kt
│   │   ├── VerackMessage.kt
│   │   ├── PingMessage.kt
│   │   ├── PongMessage.kt
│   │   ├── GetHeadersMessage.kt    # locator + stop hash
│   │   ├── HeadersMessage.kt       # up to 2,000 × (80B header + varint=0)
│   │   └── SendHeadersMessage.kt   # BIP-130
│   └── BitcoinMessage.kt           # sealed interface
├── header/
│   ├── BlockHeader80.kt            # 80-byte parsed header (ver, prev, merkle, time, bits, nonce)
│   ├── BlockHash.kt                # @JvmInline value class wrapping ByteArray(32)
│   ├── HeaderHasher.kt             # dsha256 over 80B → blockHash
│   ├── DifficultyTarget.kt         # bits ↔ target (256-bit) compact form
│   ├── ChainWork.kt                # cumulative work accumulator (uint256 BE)
│   └── PowValidator.kt             # checks hash ≤ target
├── consensus/
│   ├── RetargetCalculator.kt       # every 2016 blocks, BIP-? rules
│   ├── MedianTimePast.kt           # last-11 median, for time sanity
│   └── HeaderValidator.kt          # composes the above + linkage check
├── store/
│   ├── HeaderStore.kt              # public API: append/getByHeight/getByHash/tip
│   ├── SqliteHeaderStore.kt        # androidx.sqlite impl (commonMain — driver-agnostic)
│   ├── HeaderStoreSchema.kt        # IModule for schema + migrations (see §9)
│   ├── Checkpoint.kt               # (height, blockHash, chainWork, time)
│   └── PinnedCheckpoint.kt         # hardcoded release-time checkpoint constant
├── sync/
│   ├── LocatorBuilder.kt           # exponential locator
│   ├── HeadersSyncEngine.kt        # drives getheaders loop, applies validator, persists
│   └── ReorgHandler.kt             # rare; switches to higher-chainwork tip
├── peer/
│   ├── PeerSocket.kt               # expect (open / send / recv / close)
│   ├── PeerPool.kt                 # holds N BitcoinPeer connections
│   ├── BitcoinPeer.kt              # high-level: handshake, send msg, receive msg
│   ├── PeerScoring.kt              # ban misbehaving peers
│   └── PeerDiscovery.kt            # expect — DNS seeds + fixed seeds
└── LocalHeadersBitcoinExplorer.kt  # implements nip03Timestamp.ots.BitcoinExplorer
                                    # (with small LRU on top, like OtsBlockHeightCache)

quartz/src/jvmAndroid/kotlin/com/vitorpamplona/quartz/bitcoin/
└── peer/
    ├── TcpPeerSocket.jvmAndroid.kt     # actual TCP socket (java.net.Socket / SOCKS)
    └── DnsSeedDiscovery.jvmAndroid.kt  # InetAddress.getAllByName on seed hostnames
```

Two expect/actual seams only: `PeerSocket` (TCP plus optional SOCKS5 for Tor)
and `PeerDiscovery` (DNS-seed resolution). Header validation, message codecs,
store, sync engine, peer pool — all compile in `commonMain`, all testable
cross-platform without a single platform shim. Storage compiles in
`commonMain` too because `androidx.sqlite` + `BundledSQLiteDriver` are KMP.

### Wiring on Android

```
amethyst/src/main/java/com/vitorpamplona/amethyst/model/nip03Timestamp/
├── HeaderSyncWorker.kt              # WorkManager job (CONNECTED + optional UNMETERED)
├── HeaderSyncForegroundService.kt   # FGS for first-run / large catch-up
├── LocalHeadersOtsResolverBuilder.kt # plugs LocalHeadersBitcoinExplorer in
└── (modify) TorAwareOkHttpOtsResolverBuilder.kt → CompositeOtsResolverBuilder
```

`AppModules.kt` switches from `TorAwareOkHttpOtsResolverBuilder` to a
composite that prefers the local explorer and falls back to HTTP only when
local is missing the height. The HTTP path resolves its base URL via the
existing `BitcoinExplorerEndpoint` (shared with the onchain-zap
`EsploraBackend`), so a user-configured custom Esplora is honoured on both
the OTS and the zap side.

`AppModules.kt:433` already passes `rootFilesDir = { appContext.filesDir }`
into Quartz; the headers SQLite file goes under
`$filesDir/bitcoin/headers.db` via a new entry in that wiring.

On desktop and CLI, the equivalent data-dir paths are already plumbed for
`SQLiteEventStore` — same hook reused here.

## 5. Bitcoin P2P protocol — minimum we implement

We implement a strict subset. Anything not in this list is unimplemented and
silently dropped on receive.

### Outbound messages we send

| Command       | When |
|---------------|------|
| `version`     | Right after TCP connect |
| `verack`      | After receiving peer's `version` |
| `ping`        | Every 60 s if idle, to keep connection alive |
| `pong`        | In response to peer's `ping` |
| `getheaders`  | The actual workhorse: locator + zero stop hash |
| `sendheaders` | (optional) tell peer we want unsolicited new tips via `headers` |

### Inbound messages we handle

| Command   | Action |
|-----------|--------|
| `version` | Validate services flag, store user-agent + start_height; reply `verack` |
| `verack`  | Mark handshake complete |
| `ping`    | Reply `pong` with same nonce |
| `pong`    | Update RTT estimate |
| `headers` | Hand off to `HeadersSyncEngine` |
| `addr`/`addrv2` | (later) feed into peer pool for fallback discovery |
| anything else | ignore, do not disconnect |

### `version` payload we send

- `version`: 70016 (supports `wtxidrelay`, but we don't use it)
- `services`: 0 (we serve nothing — pure leech)
- `timestamp`: current unix time
- `addr_recv`/`addr_from`: zeroed
- `nonce`: random 64-bit
- `user_agent`: `/Amethyst-Quartz:<quartz-version>/`
- `start_height`: 0
- `relay`: false (we don't want unsolicited tx invs)

### `getheaders` request rules

- Block locator built with exponential back-off:
  `[tip, tip-1, tip-2, tip-4, tip-8, ..., tip-2^k, ..., genesisHash]`
  ~33 entries even at height 1 M.
- `hash_stop = 0x00..00` → "give me as many as you have, up to 2000".
- Pipeline tip-extension: after a `headers` of length 2000 arrives, immediately
  fire the next `getheaders` with the new tip. (This is the standard
  optimization Core itself uses.)

### Magic bytes & ports

- Mainnet magic: `0xf9 0xbe 0xb4 0xd9`
- Default port: 8333 (allow override for users running their own node on a
  non-standard port)

## 6. Validation rules — what makes a header chain "valid"

Per-header (`HeaderValidator`):

1. **Hash ≤ target.** Compute `dsha256(header80)` (interpreted as little-endian
   256-bit int) and compare to `compactToTarget(bits)`.
2. **Linkage.** `header.prevHash == storedTip.hash`.
3. **MTP sanity.** `header.time > medianOf(last 11 headers' time)`. (`time` is
   allowed to be up to 2 h ahead of *our* clock, but we don't enforce that on
   sync — we'd reject reorgs where we're skewed; just log.)
4. **Difficulty retarget at multiples of 2016.** Reproduce Core's
   `CalculateNextWorkRequired`:
   - `actualTimespan = clamp(prev2016.time - first2016.time, target/4, target*4)`
   - `newTarget = oldTarget * actualTimespan / (14 * 24 * 3600)`
   - Cap at `pow_limit` (max target).
5. **Version**: not validated — soft-fork bits don't matter for headers-only.
6. **Cumulative chainwork** updated as `prevWork + (2^256 / (target+1))`.

### Chain selection

- We track the **highest-cumulative-chainwork** chain we've seen, not the
  longest. Standard Bitcoin rule.
- On receiving a fork: keep both branches in memory while their tips are
  within `MAX_REORG_DEPTH = 100` of each other; once one accumulates clearly
  more work, drop the loser.
- A reorg deeper than 100 blocks is treated as adversarial and refused
  (operator must bump the checkpoint).

### Hardcoded sanity checkpoints

In `Checkpoints.kt` we ship a small list of (height, expectedBlockHash) pairs
mirroring Bitcoin Core's own historical checkpoints, plus one **release-pinned
checkpoint** updated at app build time near the current tip. Sync must agree
with all checkpoints — disagreement aborts and bans the peer.

## 7. Bundled data — none. Just a pinned checkpoint constant.

The point of this work is "verify against the Bitcoin network, not a trusted
party," so we ship no headers in the APK. Instead:

- **One hardcoded `PinnedCheckpoint` constant** in `bitcoin/store/PinnedCheckpoint.kt`:
  `(height, blockHash, chainWork, time)`. ~80 bytes of source.
- The checkpoint is pinned ~1 month before each release and bumped via a
  one-line PR per release. This is the **only** thing trusted at build time
  — and it's a single block hash any user can independently verify against
  any Bitcoin node before installing.
- **First-run sync** starts at the checkpoint and pulls forward to the
  current tip over P2P. At a ~1-month checkpoint that's ~4,300 headers
  (~340 KB on the wire, ~15 minutes' worth of `getheaders` round-trips at
  worst), seconds over WiFi.
- **Pre-checkpoint heights** are not synced locally at all. OTS proofs
  whose height precedes the checkpoint fall through to
  `OkHttpBitcoinExplorer` via the composite. Users in strict-mode
  (`localHeadersTrustOnly = true`) get a clear
  "this OTS proof predates the bundled checkpoint" error and no network call.
- **Catch-up after long absence.** A device that hasn't run the app for
  months will, on next sync, fast-forward from `tip_local` (its stored
  best_height) to `tip_network`. Each `headers` message brings 2,000
  headers; even 6 months of absence is ~26,000 headers (~2 MB) in 13 round
  trips.
- **Header growth on disk** is ~4 MB raw per year (+ SQLite overhead, see §9).

APK growth: **0 bytes**. Trust delta vs the old HTTP-explorer model: one
block hash, code-reviewable in git.

## 8. Multi-peer strategy & eclipse mitigation

A single peer can lie. We protect against that with:

1. **Peer count.** Maintain ≥4 simultaneous peer connections during sync;
   accept a header batch only after at least 2 distinct peers (different
   /16 IPv4 prefixes, different ASNs where we can tell) have served the same
   batch.
2. **Chainwork comparison.** After convergence we should land on the
   chain with highest cumulative work. If peers disagree, prefer the
   higher-work chain.
3. **Checkpoint cross-check.** Bundled checkpoints (above) detect any peer
   that disagrees with the consensus history. Disagreement → ban that IP for
   the session.
4. **DNS seed diversity.** Rotate across all 9 of Bitcoin Core's DNS seeds,
   not a single one.
5. **Hardcoded fallback IPs.** Ship the `chainparams.cpp` fallback IP list
   (about 1,000 v4 + v6 entries) so first-run works even if every DNS seed is
   blocked or poisoned.
6. **Optional user override.** A user can configure their own trusted node
   (`bitcoin.example.com:8333`) — same path as the existing custom explorer
   URL setting in `OtsSettings.kt`.

## 9. Storage — SQLite via `androidx.sqlite` + `BundledSQLiteDriver`

We reuse the same storage primitive Quartz already uses for `SQLiteEventStore`
(`quartz/.../nip01Core/store/sqlite/`): `androidx.sqlite` with the bundled
driver. This is KMP, ships in `androidx-sqlite-bundled` / `…-bundled-jvm`
(already a Quartz dependency), and is the project's standard storage choice.

The complete `SQLiteEventStore` infrastructure — `BundledSQLiteDriver`,
`SQLiteConnectionPool`, `transaction { }`, `IModule` for migrations,
`QueryBuilder` helpers — is reused by `SqliteHeaderStore` and
`HeaderStoreSchema`.

### Schema

```sql
PRAGMA journal_mode = WAL;
PRAGMA synchronous  = NORMAL;
PRAGMA foreign_keys = OFF;

CREATE TABLE block_header (
    height       INTEGER PRIMARY KEY,        -- 0..tip, dense
    hash         BLOB NOT NULL,              -- 32 B, dsha256(header), big-endian display order
    header       BLOB NOT NULL,              -- 80 B raw header
    chainwork    BLOB NOT NULL               -- 32 B cumulative work, big-endian
);

CREATE UNIQUE INDEX idx_block_header_hash ON block_header(hash);

CREATE TABLE chain_tip (
    id           INTEGER PRIMARY KEY CHECK (id = 1),  -- singleton row
    best_height  INTEGER NOT NULL,
    best_hash    BLOB    NOT NULL,
    best_work    BLOB    NOT NULL
);

CREATE TABLE checkpoint_seen (
    height       INTEGER PRIMARY KEY,         -- which checkpoints we've passed and confirmed
    hash         BLOB    NOT NULL,
    source       TEXT    NOT NULL             -- 'pinned' | 'historical'
);
```

Migrations live in `HeaderStoreSchema.kt` as an `IModule` (same pattern as
the event store's modules). v1 is the schema above; future additions
(prev-hash cache column, merkle-block annotations) ALTER from there.

### Sizing

| Item | Per record | At height 1,000,000 |
|---|---|---|
| `block_header` row (80+32+32 B payload + SQLite overhead) | ~180–220 B | ~180–220 MB |
| `idx_block_header_hash` B-tree (32 B key + rowid) | ~40 B | ~40 MB |
| `chain_tip` | 1 row | negligible |
| **Total** | | **~220–260 MB at height 1 M** |

In practice users will start from a recent checkpoint and grow ~4 MB raw
(~10 MB stored) per year, so most installs sit at 10–40 MB. The "1 M
height" line is the long-tail worst case if someone with a multi-year-old
device's local store retroactively backfills (which we explicitly don't do
in v1 — see §15 Q1).

### Atomicity & crash safety

- `HeadersSyncEngine` writes each `headers` batch (up to 2,000) inside a
  single `transaction { }`. WAL gives us atomic-or-nothing per batch.
- `chain_tip` is updated in the same transaction as the headers it points to,
  so the tip can never reference rows that didn't commit.
- A crash mid-batch leaves the WAL inconsistent; SQLite rolls it back on
  next open. No custom recovery code.
- On open, `SqliteHeaderStore.verifyTipIntegrity()` re-reads the tip row and
  confirms the referenced header exists. If not (unexpected DB corruption,
  ransomware-like sandbox damage), we delete the db file and fall back to
  re-sync from the pinned checkpoint.

### Concurrency

Same model as `SQLiteEventStore`: one connection pool, all writes go through
the sync engine on a dedicated dispatcher; reads use the pool's read slots.
OTS verifier reads are short and indexed — no contention with sync writes.

## 10. Resolving a height query (the actual `BitcoinExplorer` impl)

```kotlin
class LocalHeadersBitcoinExplorer(
    private val store: HeaderStore,
    private val sync: HeadersSyncEngine,
    private val fallback: BitcoinExplorer? = null,  // OkHttpBitcoinExplorer
) : BitcoinExplorer {
    override suspend fun blockHash(height: Int): String {
        store.getByHeight(height)?.let { return it.blockHash.toHex() }
        // height not yet synced; ask sync engine to extend, with timeout
        sync.ensureHeight(height, timeoutMs = 10_000)
        store.getByHeight(height)?.let { return it.blockHash.toHex() }
        return fallback?.blockHash(height) ?: throw NotSyncedException(height)
    }

    override suspend fun block(hash: String): BlockHeader {
        store.getByHash(hash)?.let { rec ->
            return BlockHeader(
                merkleRoot = rec.header.merkleRoot.toHex(),
                blockHash  = hash,
                time       = rec.header.time.toString(),
            )
        }
        return fallback?.block(hash) ?: throw NotSyncedException(hash)
    }
}
```

The fallback path is gated by a setting; a privacy-conscious user can
disable it ("local-only verification").

## 11. Mobile (Android) integration

### Lifecycle

- **First launch after install/upgrade.** Show a one-time "Verify Bitcoin
  timestamps locally?" prompt. If accepted, kick off a foreground-service sync
  (FGS so OS doesn't kill us mid-download). Default WiFi-only; user can
  override.
- **Subsequent launches.** Background `WorkManager` job, periodic, ~6 h
  cadence. Constraints: `NetworkType.UNMETERED` by default, `BatteryNotLow`.
- **On-demand.** When a NIP-03 attestation needs a height we don't yet have,
  request a targeted sync up to that height (capped 10 s, otherwise
  fall back to HTTP).

### Permissions

- No new runtime permissions needed. We use the foreground-service permission
  declared for nests audio (already present) plus `INTERNET` (already
  granted). Add `FOREGROUND_SERVICE_DATA_SYNC` to manifest if not present.

### Settings UI

Extend `OtsSettings.kt`:

```kotlin
data class OtsSettings(
    val customExplorerUrl: String? = null,        // existing — shared via BitcoinExplorerEndpoint
    // NEW:
    val localHeadersEnabled: Boolean = true,
    val localHeadersTrustOnly: Boolean = false,   // disables HTTP fallback; pre-checkpoint proofs error
    val localHeadersWifiOnly: Boolean = true,
    val customP2pNode: String? = null,            // e.g. "myriad.example.com:8333"
)
```

A short "Bitcoin verification" settings screen lets the user pick:
- **Local headers + HTTP fallback** (default) — best UX, trustless for
  post-checkpoint heights, HTTP for older proofs.
- **Local headers only (strict)** — refuses to verify proofs older than the
  pinned checkpoint. Most paranoid setting.
- **HTTP explorer only** — pre-local-headers behaviour. Lightest disk/CPU.

The `customExplorerUrl` field continues to feed `BitcoinExplorerEndpoint`,
so a custom HTTP Esplora is honoured both by the fallback in this feature
and by the onchain-zaps `EsploraBackend`. No new Esplora setting.

## 12. Tor integration

The codebase already has a Tor-aware HTTP path (`TorAwareOkHttpOtsResolverBuilder`).
For P2P:

- Reuse the project's existing SOCKS proxy plumbing.
- When Tor is on, route TCP socket connections through SOCKS5 to
  Tor's `127.0.0.1:9050`.
- Prefer `.onion` peer addresses (advertised via `addrv2`) when available —
  they don't expose the user's clearnet IP to the peer.
- Increase per-peer timeout (Tor RTT is ~1–5 s).

DNS seed lookup over Tor must use SOCKS5 hostname resolution
(no plaintext DNS).

## 13. Phased delivery

Each phase produces a shippable artifact with measurable behavior.

### Phase 0 — Foundation (1–2 days)

- Create `quartz/plans/2026-05-08-local-headers-explorer.md` (this file). ✓
- Add `quartz/src/commonMain/.../bitcoin/` skeleton.
- Wire empty `LocalHeadersBitcoinExplorer` that always defers to fallback.
- No behavior change yet.

### Phase 1 — Header parsing & validation, no I/O (3–5 days)

- `BlockHeader80` codec + tests against known mainnet headers (genesis, a few
  retargets, post-segwit).
- `DifficultyTarget` compact↔target with RFC test vectors.
- `PowValidator`, `RetargetCalculator`, `MedianTimePast`, `HeaderValidator`.
- Tests run on a local fixture file containing the first 50 000 headers.

### Phase 2 — Storage layer (2–3 days)

- `HeaderStore` interface + `SqliteHeaderStore` implementation in `commonMain`
  (`androidx.sqlite` is KMP, no platform shim needed).
- `HeaderStoreSchema` as an `IModule` matching the `SQLiteEventStore`
  migration pattern.
- `append(batch)`, `getByHeight(h)`, `getByHash(hash)`, `tip()`.
- Reuse `BundledSQLiteDriver`, `SQLiteConnectionPool`, `transaction { }`
  from `nip01Core/store/sqlite/` — no duplication.
- Crash-safety test: kill mid-transaction, reopen, confirm WAL rollback;
  confirm `verifyTipIntegrity()` recovers cleanly.

### Phase 3 — P2P codec, no socket (3–5 days)

- Message envelope (magic, command, length, checksum) encode/decode.
- All seven message payload codecs.
- Tests using captured wire bytes from a real Bitcoin Core peer (record once,
  replay forever).

### Phase 4 — Single-peer sync (5–7 days)

- `BitcoinPeer` over a real socket.
- `HeadersSyncEngine` drives `getheaders` loop against one peer.
- Validate every header through Phase 1 validator before persisting.
- Integration test: sync first 100 K headers from a public Bitcoin Core peer
  in CI. Skip in normal `./gradlew test`; only run nightly.

### Phase 5 — Multi-peer & eclipse hardening (3–5 days)

- `PeerPool`, `PeerScoring`.
- DNS seed discovery + fallback IP list.
- Header-batch cross-validation across peers.
- Hardcoded checkpoints in `Checkpoints.kt`.

### Phase 6 — Pinned checkpoint constant (½ day)

- `bitcoin/store/PinnedCheckpoint.kt` — single hardcoded
  `Checkpoint(height, blockHash, chainWork, time)`. ~80 bytes of source.
- A small Gradle task (`./gradlew bumpBitcoinCheckpoint`) that takes
  `(height, hash)` arguments and writes the constant. Run by the release
  process; otherwise no automation.
- No backward-sync, no bundled headers blob, no asset resources.
  Pre-checkpoint heights are routed to the HTTP fallback by the composite
  resolver (or refused in strict mode).

### Phase 7 — Android wiring (3–4 days)

- `HeaderSyncWorker` + `HeaderSyncForegroundService`.
- New "Bitcoin verification" settings screen.
- Switch `AppModules.kt` to composite resolver builder.
- Manual QA: install fresh, open NIP-03 note, watch sync run, confirm
  verification matches HTTP explorer.

### Phase 8 — Tor (2–3 days)

- SOCKS5-tunnelled TCP socket variant.
- `addrv2` decoding for `.onion` addresses.
- QA on Orbot.

### Phase 9 — Desktop & CLI integration (1–2 days)

- `desktopApp/` inherits the explorer for free (everything is in
  `quartz/commonMain/`). Confirm headers.db path is plumbed via the same
  data-dir hook already used by `SQLiteEventStore` on JVM. Confirm sync
  runs end-to-end on the desktop app.
- `cli/` — add an `amy verify-ots <event-json>` command if not present,
  wired through the same composite resolver. The CLI is a thin assembly
  layer over Quartz (per `amy-expert`), so the actual sync logic is reused
  as-is.

### Phase 10 — Rollout & telemetry (ongoing)

- Ship as opt-in for one release (default off, advertised in release notes).
- Collect (locally, no analytics): time-to-first-sync, time-to-verify,
  fallback rate. Surface in a debug screen.
- Default-on in the next release if metrics look healthy.

Total: **~22–32 engineer-days** end to end. Phases 1–4 are the bulk
(protocol + validation); phases 5–10 are integration. Phase 6 dropped from
2–3 d to ½ d because we ship no bundled data — just one constant.

## 14. Testing strategy

### Unit tests (commonMain)

- Header codec round-trip (10 known headers across history).
- `dsha256` against test vectors.
- Compact-bits encoding edge cases (0x1d00ffff, 0x1b0404cb, etc.).
- Retarget at heights 2016, 4032, 32256 (well-known).
- MTP sanity for a fixture chain.
- Locator-builder shape (geometric back-off, includes genesis).
- Reorg handler: synthetic 5-block fork with higher work wins.

### Integration tests (jvmAndroid)

- Replay-based P2P codec test using a captured pcap of a real session
  (committed as `quartz/src/jvmAndroid/test/resources/btc-handshake.bin`).
- Local-network test against a Bitcoin Core regtest node spun up in CI
  (Docker). Validates full handshake + `getheaders` loop end to end.
- `SqliteHeaderStore` round-trip on a real on-disk file (jvmTest, in a
  `tmpdir`): append 5,000 headers in batches, getByHeight, getByHash,
  reopen, confirm tip survives.
- Crash-and-resume test: kill the writer in the middle of a `transaction { }`,
  reopen, confirm SQLite WAL rolls the partial batch back and sync resumes
  from the last committed tip.

### Property tests

- `HeaderValidator` — fuzz with random 80-byte inputs; confirm invalid PoW
  always rejected and valid PoW always accepted.

### Manual QA checklist

- Verify a known NIP-03 attestation against the local explorer matches
  Blockstream's answer.
- Disable network mid-sync; resume cleanly when network returns.
- Tor on — sync completes (slowly) over Orbot.
- Disk-full simulation — fail gracefully, don't corrupt store.

## 14a. Interop test vectors

Consensus code without external test vectors is "trust me." Every layer
that could disagree with the rest of the Bitcoin network gets pinned to
authoritative upstream vectors, mirroring how `nip44.vectors.json`,
`bip39.vectors.json` and the `mls/*.json` set are already organised in
`quartz/src/commonTest/resources/`. The onchain-zaps work uses the same
discipline (BIP-341 wallet test vectors at every layer of
`nipBCOnchainZaps/taproot/` + `psbt/`).

### Sources, by component

| Component | Source(s) | Format | Storage | Effort | Priority |
|---|---|---|---|---|---|
| **`BlockHeader80` parse/serialize** | Genesis (0), block 1, block 209999 (last pre-halving), 481824 (first post-segwit), 709631 (last pre-taproot), one recent | JSON: `[{height, hash, headerHex, version, prevHash, merkleRoot, time, bits, nonce}, …]` | commit `commonTest/resources/bitcoin/known_headers.json` | 1 d | **High** |
| **`DifficultyTarget` compact↔target** | bitcoinj `UtilsTest.testCompactBitsToBigInteger`, btcd `chaincfg/chainhash/compact_test.go`. Vectors: `0x1d00ffff`, `0x1b0404cb`, `0x170398bb`, overflow + negative-bit edges | JSON pairs `(compact, targetHex)` | commit `bitcoin/compact_target.json` | ½ d | **High** |
| **Retarget (`CalculateNextWorkRequired`)** | Mainnet retarget heights 2016, 4032, 32256, 60480, ~600000 — extract `(prev_retarget_time, current_time, prev_bits, expected_new_bits)` from any full node | JSON list of retarget cases | commit `bitcoin/retargets.json` (~50 entries, ~5 KB) | 1 d | **High** |
| **`MedianTimePast`** | Any 11 consecutive mainnet headers synthesised from `known_headers.json` | inline test | inline | ½ d | Medium |
| **HeaderValidator end-to-end on real chain** | First 2016 mainnet headers (~160 KB; covers genesis + first retarget). Larger 50 K-header set lazy-fetched in nightly CI from a pinned-hash mirror, not committed | binary blob | commit `bitcoin/first_2016_headers.bin` | 1–2 d | **High** |
| **P2P wire codec — `version`/`verack`/`ping`/`pong`** | btcd `wire/msg*_test.go` has canonical byte strings; Bitcoin Wiki "Protocol documentation" has annotated hex | hex strings in JSON | commit `bitcoin/p2p_messages.json` | 1 d | **High** |
| **P2P `getheaders` / `headers` round-trip** | Capture against a real bitcoind once, ~50 KB | raw bytes | commit `bitcoin/p2p_capture.bin` | 1 d | **High** |
| **DNS seed list** | Bitcoin Core `chainparams.cpp` (current 9 seeds) | inline constant | n/a | <¼ d | Low |
| **Hardcoded fallback IPs** | Bitcoin Core `chainparamsseeds.h` (auto-generated by `contrib/seeds/`) | binary blob | commit `bitcoin/chainparamsseeds.bin` | ½ d | Medium |
| **Reorg / chain selection** | Synthetic 2-branch fork — verify higher-cumulative-chainwork wins | inline builder | inline | 1 d | **High** |
| **End-to-end OTS proofs** | `python-opentimestamps/tests/test_data/*.ots` + the 3 wild events already inline in `OtsTest.kt`. Import 5–10 more `.ots` for variety (calendar operators, eras) | `.ots` binaries + JSON manifest of expected timestamps | commit `commonTest/resources/ots/*.ots` | 1 d | **High** |
| **NIP-03 1040 events from real relays** | Scrape any relay for `kind:1040`; pick a diverse set across calendar operators and years | inline JSON | extend `OtsTest.kt` | ½ d | Medium |
| **Differential test: local vs HTTP** | For every `h ∈ [checkpoint, tip]`: assert `LocalHeadersBitcoinExplorer.blockHash(h)` == `OkHttpBitcoinExplorer.blockHash(h)`. Catches consensus drift end-to-end | runtime check, nightly CI | no fixture | 1 d | **High** |
| **Regtest end-to-end** | Bitcoin Core in Docker — mine N blocks, sync, assert tip matches | infrastructure | CI script | 2 d | Medium |
| **Adversarial peer** | Synthetic peer that lies about retarget, replays old headers, drops mid-batch, sends 2,001-headers messages. Validates ban + reconnect | inline test harness | inline | 2 d | **High** |

### Committed layout

```
quartz/src/commonTest/resources/bitcoin/
├── README.md                   # provenance + bump procedure for each file
├── known_headers.json          # ~10 hand-picked mainnet headers across history
├── compact_target.json         # ~20 compact-bits ↔ target pairs
├── retargets.json              # ~50 retarget cases from historical mainnet
├── first_2016_headers.bin      # ~160 KB; genesis + first retarget cycle
├── p2p_messages.json           # hex of canonical version/verack/ping/pong
├── p2p_capture.bin             # captured handshake + getheaders/headers (~50 KB)
└── chainparamsseeds.bin        # current Bitcoin Core fallback IP list

quartz/src/commonTest/resources/ots/
├── manifest.json               # {filename → expected unix timestamp | "pending"}
└── *.ots                       # 5–10 fixtures from python-opentimestamps
```

### Provenance discipline

The `README.md` next to each fixture records, in this order:

1. **Where it came from** — upstream URL + commit SHA.
2. **How to regenerate** — a single command, no manual steps.
3. **What it tests** — one sentence.
4. **When to bump** — e.g. *"retargets.json is stable; never bumps."* /
   *"chainparamsseeds.bin bumped each Bitcoin Core major release."*

Same pattern as the header comment of `nip44.vectors.json`. New committers
should never have to ask "where did this 32-byte blob come from."

### Phase mapping

These test imports map cleanly onto the existing phases — total extra
effort **~5–7 engineer-days** spread across the work, no new top-level
phase needed:

| Phase | Vectors consumed |
|---|---|
| **Phase 1** (header parsing & validation, no I/O) | `known_headers.json`, `compact_target.json`, `retargets.json`, `first_2016_headers.bin` |
| **Phase 3** (P2P codec, no socket) | `p2p_messages.json`, `p2p_capture.bin` |
| **Phase 4** (single-peer sync) | adversarial-peer harness + regtest-in-Docker |
| **Phase 5** (multi-peer & eclipse) | `chainparamsseeds.bin` |
| **Phase 7** (Android wiring) | differential test against `OkHttpBitcoinExplorer` |
| **Phase 9** (CLI / Desktop) | OTS `.ots` fixtures via `amy verify-ots --explorer=local` |

### Why the differential test matters most

The single highest-value test in the list is the differential one: for
every height between the pinned checkpoint and the current tip, the local
explorer's answer must equal the HTTP explorer's answer. Run it nightly in
CI. Any consensus-rule drift — a wrong retarget formula, a missed
overflow case in compact-target decoding, a partial-merkle bug — will
surface as a height where the two disagree, and the height is the bug
report. This is the test that catches the bug nobody thought to write
a unit test for.

## 15. Risks & open questions

| # | Risk | Mitigation |
|---|------|------------|
| R1 | First-run UX cost (sync from pinned checkpoint) | Pinned checkpoint ~1 month before release means ~4 K headers, ~340 KB, seconds over WiFi. Show progress in settings; sync runs in background WorkManager job anyway. |
| R2 | Mobile OS kills socket mid-sync | Foreground service for first run; resumable design (SQLite WAL handles partial batches automatically). |
| R3 | Peer feeds adversarial headers (eclipse) | Multi-peer + checkpoints + chainwork comparison. |
| R4 | Disk corruption | SQLite WAL + `verifyTipIntegrity()` on open; on detected corruption, delete db file and re-sync from pinned checkpoint. |
| R5 | Tor + P2P is slow | Increased timeouts; fallback to HTTP-over-Tor explorer (via `BitcoinExplorerEndpoint`) when sync stalls. |
| R6 | APK size growth | Zero — no bundled data ships in the APK. |
| R7 | Privacy: peers see user's IP | Tor mode supported; `version` user-agent doesn't leak Amethyst install ID. |
| R8 | Battery cost on background sync | Default to UNMETERED + BATTERY_OK constraints; back off when idle. |
| R9 | Maintenance burden — new soft-forks | Soft-forks don't affect headers-only validation (no script eval). Difficulty algorithm hasn't changed; a future change would require a code update. |
| R10 | Test flakiness if CI talks to public Bitcoin nodes | Use Bitcoin Core regtest in Docker for integration tests; public nodes only nightly. |
| R11 | SQLite store size on long-running installs | ~10 MB/year typical; ~220 MB at height 1 M which won't happen in any realistic mobile lifetime. Add a "wipe local headers" button in settings for users who want it. |

### Open questions

#### Resolved 2026-05-19

- **Q1.** *Resolved — HTTP fallback only.* Pre-checkpoint OTS proofs are
  routed to `OkHttpBitcoinExplorer` via the composite resolver. Strict-mode
  users (`localHeadersTrustOnly = true`) get a clear error. No backward
  sync code path.
- **Q2.** *Resolved — cache chainwork.* SQLite makes the choice cheap (one
  extra BLOB column). Reading current tip work without a full scan matters
  for chain-selection logic; the ~30 MB-at-1M cost is negligible on disk.
- **Q4.** *Resolved — no HTTPS headers blob.* The point of this feature is
  trustless verification, and the pinned-checkpoint approach already gives
  fast first-run. Adding a second ingestion path (signed blob over HTTPS)
  doubles the test surface for marginal benefit.

#### Still open

- **Q3.** Should `LocalHeadersBitcoinExplorer` (and friends) be a Quartz
  public API for 3rd-party Quartz consumers, or internal to Amethyst?
  Recommendation: public — it's a clean reusable primitive. Decide before
  Phase 0 lands so the package layout doesn't churn. *(See `quartz-integration`
  skill for the public-API contract Quartz exposes today.)*

## 16. Migration & rollout

1. **Ship behind a feature flag**, default off, documented in release notes.
2. **Internal QA** for one full release cycle on the flag.
3. **Default on** for new installs the release after, leaving existing
   installs on their previous setting.
4. **Default on** for everyone in the release after that.
5. **Keep HTTP explorer code path** for at least 4 releases as a safety net.

## 17. Success criteria

- A NIP-03 attestation that verifies via Blockstream also verifies via the
  local explorer (bit-for-bit identical timestamp).
- 95th-percentile cold-launch verification of a recent attestation: ≤2 s.
- 95th-percentile first-run sync (from pinned checkpoint to tip): ≤30 s on
  WiFi for a checkpoint ≤2 months old.
- No new crash-rate regression in the verification path.
- App size growth: **0 MB** (no bundled data).
- A user with HTTP explorer disabled can still verify any post-checkpoint
  attestation.

## 18. Out of scope (explicit)

- BIP-157/158 compact filters
- BIP-37 merkleblock or full-block fetch
- **Trustless verification of NIP-BC onchain zaps** — needs merkleblock or
  full-block fetch on top of this stack. See §20 for the follow-up plan.
- **Trustless wallet operations** for NIP-BC onchain zap *sending* —
  needs UTXO-by-address and broadcast paths that headers can't provide.
  Stays on user-configured Esplora.
- Lightning, BIP-32 wallet, mempool monitoring
- Verifying NIP-03 attestations against altcoin/Liquid timestamps (not in
  the spec)
- Replacing the calendar-server upgrade path (`OtsState.upgrade()`) —
  unrelated, those are simple HTTPS calls and stay as-is

## 19. Follow-up: trustless NIP-BC onchain zap verification

*Not in this plan. Tracked here so the design choices above stay aligned.
Full implementation plan lives in `amethyst/plans/2026-05-14-onchain-zaps.md`
under "Inline SPV proofs (`block` + `proof` tags) — pending".*

### What the 2026-05-19 spec change buys us

`nostr-protocol/nips#2332` adds optional `["block", "<hash>", "<height>"]`
and `["proof", "<raw-tx-hex>", "<merkle-proof-hex>"]` tags on `kind:8333`.
The proof rides inline in the Nostr event, so the trustless-verify path
needs **no new P2P messages on top of S1** — no `getdata MSG_BLOCK`, no
BIP-37 `filterload`, no full-block parser. Just header lookup + merkle
walk against bytes already in the event.

This collapses the original "follow-up plan" estimate from 10–15 days to
**~3–4 days** of work after S1 ships and the spec's merkle-proof
encoding is locked.

### Foundation reused from S1

- `LocalHeadersBitcoinExplorer.byHash(blockHash)` → validated header (for
  the `merkleRoot`).
- `HeaderStore` for the by-hash index.
- `LocalHeadersBitcoinExplorer.tipHeight()` for confirmation count.

### New work (in `nipBCOnchainZaps/`, not in `bitcoin/`)

- `MerkleProofVerifier.verify(txid, proofBytes, expectedMerkleRoot)` — small
  pure-function module. Test-vector-driven.
- `OnchainZapVerifier` rewire to prefer the inline proof path when both
  tags are present; fall back to `backend.getTx` on absence or proof
  failure (never hard-reject).
- `OnchainBackend.getMerkleProof(txid)` on the send side, plus a
  post-confirmation worker in `OnchainZapSender` that publishes a second
  `kind:8333` once the tx confirms, this time carrying `block` + `proof`.
  Receivers dedupe by `(txid, target)` preferring the variant with a
  valid SPV proof.

Detailed phase breakdown (G.1–G.7), gap matrix, and design rationale
live in the onchain-zaps plan referenced above. The previous text of
this section (BIP-37 / full-block / partial-merkle-tree options) is
obsolete now that the proof is carried inline — kept in git history if
anyone needs to revisit the assumption.

## 20. References

- NIP-03: https://github.com/nostr-protocol/nips/blob/master/03.md
- OpenTimestamps spec: https://github.com/opentimestamps/python-opentimestamps
- Bitcoin Core P2P protocol: `src/protocol.h`, `src/net_processing.cpp`,
  `src/headerssync.cpp`
- BIP-130 (`sendheaders`): https://github.com/bitcoin/bips/blob/master/bip-0130.mediawiki
- Headers-first sync rationale: Bitcoin Core 0.10 release notes (2015)
- Existing OTS surface in this repo:
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip03Timestamp/ots/BitcoinExplorer.kt`
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip03Timestamp/ots/BlockHeader.kt`
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip03Timestamp/ots/attestation/BitcoinBlockHeaderAttestation.kt`
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip03Timestamp/ots/OtsBlockHeightCache.kt`
  - `quartz/src/jvmAndroid/kotlin/com/vitorpamplona/quartz/nip03Timestamp/okhttp/OkHttpBitcoinExplorer.kt`
  - `amethyst/src/main/java/com/vitorpamplona/amethyst/model/nip03Timestamp/TorAwareOkHttpOtsResolverBuilder.kt`
  - `amethyst/src/main/java/com/vitorpamplona/amethyst/model/nip03Timestamp/OtsSettings.kt`
  - `amethyst/src/main/java/com/vitorpamplona/amethyst/model/nip03Timestamp/BitcoinExplorerEndpoint.kt`
- Storage precedent (reused as-is):
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/SQLiteEventStore.kt`
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/IModule.kt`
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/SQLiteConnectionExt.kt`
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/SQLiteConnectionPool.kt`
- Follow-up area:
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nipBCOnchainZaps/verify/OnchainZapVerifier.kt`
  - `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nipBCOnchainZaps/chain/OnchainBackend.kt`
