# Local Bitcoin headers explorer for NIP-03 OTS

**Date:** 2026-05-08
**Branch:** `claude/review-ots-blockchain-deps-bKns7`
**Module:** `quartz/` (with thin Android wiring in `amethyst/`)
**Status:** Plan — not yet implemented

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
  bundled checkpoint, or when local sync hasn't completed yet.
- Live in `quartz/` so `cli/` and `desktopApp/` benefit too.

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
              (existing, fallback)    │              │
                                      │              ▼
                                      │   ┌─────────────────────────┐
                                      │   │   HeaderStore           │
                                      │   │   (append-only file +   │
                                      │   │    height index)        │
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
                             not synced yet, fall back to HTTP)
```

## 4. Module layout

All new code in `quartz/` so non-Android targets (CLI, Desktop) inherit it.
Most of the protocol code is platform-independent; only socket I/O is in
`jvmAndroid`.

```
quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip03Timestamp/bitcoin/
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
│   │   └── HeadersMessage.kt       # up to 2,000 × (80B header + varint=0)
│   └── BitcoinMessage.kt           # sealed interface
├── header/
│   ├── BlockHeader80.kt            # 80-byte parsed header (ver, prev, merkle, time, bits, nonce)
│   ├── BlockHash.kt                # value class wrapping ByteArray(32)
│   ├── HeaderHasher.kt             # dsha256 over 80B → blockHash
│   ├── DifficultyTarget.kt         # bits ↔ target (256-bit) compact form
│   ├── ChainWork.kt                # cumulative work accumulator
│   └── PowValidator.kt             # checks hash ≤ target
├── consensus/
│   ├── RetargetCalculator.kt       # every 2016 blocks, BIP-? rules
│   ├── MedianTimePast.kt           # last-11 median, for time sanity
│   └── HeaderValidator.kt          # composes the above + linkage check
├── store/
│   ├── HeaderStore.kt              # append-only file + height→offset index
│   ├── Checkpoint.kt               # (height, blockHash, chainWork, time)
│   └── HeaderRecord.kt             # 80B header + cached blockHash + cumulativeChainWork
├── sync/
│   ├── LocatorBuilder.kt           # exponential locator
│   ├── HeadersSyncEngine.kt        # drives getheaders loop, applies validator, persists
│   └── ReorgHandler.kt             # rare; switches to higher-chainwork tip
├── peer/
│   ├── PeerPool.kt                 # holds N BitcoinPeer connections
│   ├── BitcoinPeer.kt              # high-level: handshake, send msg, receive msg
│   └── PeerScoring.kt              # ban misbehaving peers
└── LocalHeadersBitcoinExplorer.kt  # implements quartz.../ots/BitcoinExplorer

quartz/src/jvmAndroid/kotlin/com/vitorpamplona/quartz/nip03Timestamp/bitcoin/
├── peer/
│   ├── TcpPeerSocket.jvmAndroid.kt # actual TCP socket (java.net.Socket)
│   └── DnsSeedDiscovery.jvmAndroid.kt  # InetAddress.getAllByName on seed hostnames
└── store/
    └── FileHeaderStore.jvmAndroid.kt   # RandomAccessFile-backed HeaderStore

quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip03Timestamp/bitcoin/
└── PeerSocket.kt                       # expect (open / send / recv / close)
└── HeaderStorage.kt                    # expect (append, getByHeight, tip)
```

The `expect`/`actual` split keeps protocol logic testable cross-platform; the
socket layer and disk layer are the only platform-dependent pieces.

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
local is missing the height.

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

## 7. Bundled data

To avoid making every fresh install download 75 MB:

- App ships an **assets file** `bitcoin_headers_<height>.bin`: raw 80-byte
  headers from genesis up to a release-pinned height (≤ 1 month before build).
  ~72 MB — too large for Play Store delta-update friendliness.
- Ship instead a **compact bundled checkpoint**: just one `Checkpoint(height,
  blockHash, chainWork, time)` per app release, plus the most recent ~50 K
  headers (~4 MB) so users can verify any attestation from the past ~year
  immediately.
- For older proofs, lazy-sync from the checkpoint *backwards* down to genesis
  on demand (rare path).
- Forward sync from checkpoint to current tip is what runs at first launch
  (~10 KB to a few MB depending on release age).

This keeps APK growth at ~4 MB and first-run sync time at seconds.

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

## 9. Storage format

Append-only flat file plus a sparse index.

### `headers.bin`

Records, fixed 112 bytes each, indexed by height (record N at offset `N*112`):

```
+0   80B  raw block header
+80  32B  cumulative chainwork (uint256, big-endian)
```

The block hash and target are recomputed from bytes 0..80 lazily; we don't
store them. Total at height 1M: 112 MB. We can drop this to 80 B/record
(no chainwork) if we accept recomputing chainwork on startup — TBD; default
is 112 B for fast tip selection.

### `tips.json`

A small JSON file holding:

```json
{
  "best_height": 945123,
  "best_hash":   "00000000000000000001abc...",
  "best_work":   "0x000...",
  "checkpoints_passed": [600000, 700000, 800000, 940000]
}
```

### Atomicity

Sync writes to `headers.bin.tmp`, fsyncs, then atomically renames or
appends-and-fsyncs in batches of 2000. Crash recovery: on startup, scan from
last fsynced offset, validate linkage, and discard partial trailing records.

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
    val customExplorerUrl: String? = null,
    // NEW:
    val localHeadersEnabled: Boolean = true,
    val localHeadersTrustOnly: Boolean = false,  // disables HTTP fallback
    val localHeadersWifiOnly: Boolean = true,
    val customP2pNode: String? = null,           // e.g. "myriad.example.com:8333"
)
```

A short "Bitcoin verification" settings screen lets the user pick:
- Local headers (default)
- HTTP explorer only (current behavior, lighter)
- Local + HTTP fallback
- Local only (strict, may delay verification of brand-new attestations)

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

- `HeaderStore` interface + `FileHeaderStore` actual on jvmAndroid.
- Append, get-by-height, get-by-hash (in-memory hash→height map built at
  startup, ~60 MB at height 1 M — acceptable, or use a sparse on-disk index).
- Crash-recovery test.

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

### Phase 6 — Bundled checkpoint & lazy-genesis-sync (2–3 days)

- Build-time generation of the release-pinned checkpoint and trailing-50K
  headers blob, packaged as `quartz/src/jvmAndroid/resources/bitcoin/...`.
- Forward sync from checkpoint to tip on first launch.
- Backward (genesis-direction) sync triggered only when an OTS proof requests
  a height before the checkpoint.

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

- `desktopApp/` already inherits the explorer because it's in `quartz/`.
  Confirm it works end-to-end on JVM.
- `cli/` (`amy verify-ots`) — confirm CLI sync also works headlessly.

### Phase 10 — Rollout & telemetry (ongoing)

- Ship as opt-in for one release (default off, advertised in release notes).
- Collect (locally, no analytics): time-to-first-sync, time-to-verify,
  fallback rate. Surface in a debug screen.
- Default-on in the next release if metrics look healthy.

Total: **~25–35 engineer-days** end to end. Phases 1–4 are the bulk
(protocol + validation); phases 5–10 are integration.

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
- Crash-and-resume test: kill mid-sync, restart, confirm we pick up at the
  correct height.

### Property tests

- `HeaderValidator` — fuzz with random 80-byte inputs; confirm invalid PoW
  always rejected and valid PoW always accepted.

### Manual QA checklist

- Verify a known NIP-03 attestation against the local explorer matches
  Blockstream's answer.
- Disable network mid-sync; resume cleanly when network returns.
- Tor on — sync completes (slowly) over Orbot.
- Disk-full simulation — fail gracefully, don't corrupt store.

## 15. Risks & open questions

| # | Risk | Mitigation |
|---|------|------------|
| R1 | First-run UX cost (1–10 min sync) puts users off | Bundled checkpoint + 50 K trailing headers means most installs need only seconds. Show progress prominently. |
| R2 | Mobile OS kills socket mid-sync | Foreground service for first run; resumable design. |
| R3 | Peer feeds adversarial headers (eclipse) | Multi-peer + checkpoints + chainwork comparison. |
| R4 | Disk corruption | Atomic appends, checksum on close, fall back to re-sync from last checkpoint. |
| R5 | Tor + P2P is slow | Increased timeouts; fallback to HTTP-over-Tor explorer when sync stalls. |
| R6 | APK size growth from bundled headers | Cap bundle at most-recent ~50 K headers (~4 MB). Older headers fetched on demand. |
| R7 | Privacy: peers see user's IP | Tor mode supported; `version` user-agent doesn't leak Amethyst install ID. |
| R8 | Battery cost on background sync | Default to UNMETERED + BATTERY_OK constraints; back off when idle. |
| R9 | Maintenance burden — new soft-forks | Soft-forks don't affect headers-only validation (no script eval). Difficulty algorithm hasn't changed; a future change would require a code update. |
| R10 | Test flakiness if CI talks to public Bitcoin nodes | Use Bitcoin Core regtest in Docker for integration tests; public nodes only nightly. |

### Open questions

- **Q1.** Do we actually want backward-from-checkpoint sync, or just refuse
  to verify proofs older than the bundled checkpoint and tell users to use
  the HTTP fallback? Backward sync is operational complexity for a rare path.
- **Q2.** Storage: 80 B/record (recompute chainwork on startup) vs
  112 B/record (cache it)? At height 1M, 32 MB difference. Probably worth
  caching.
- **Q3.** Should we expose this as a Quartz public API (so 3rd-party apps
  using Quartz also benefit) or keep it internal to NIP-03? Recommendation:
  public — it's generally useful.
- **Q4.** Do we ship a "headers blob" download URL (HTTPS) as a faster
  alternative to P2P for first-run? Trade-off: faster but adds a trust point.
  Could be made cross-checked (download blob, then verify every header's PoW
  locally — same trust as P2P then). Worth considering as Phase 6b.

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
- 95th-percentile first-run sync (post-checkpoint): ≤30 s on WiFi.
- No new crash-rate regression in the verification path.
- App size growth: ≤5 MB.
- A user with HTTP explorer disabled can still verify any post-checkpoint
  attestation.

## 18. Out of scope (explicit)

- BIP-157/158 compact filters
- Lightning, BIP-32 wallet, mempool monitoring
- Verifying NIP-03 attestations against altcoin/Liquid timestamps (not in
  the spec)
- Replacing the calendar-server upgrade path (`OtsState.upgrade()`) —
  unrelated, those are simple HTTPS calls and stay as-is

## 19. References

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
  - `quartz/src/jvmAndroid/kotlin/com/vitorpamplona/quartz/nip03Timestamp/okhttp/OkHttpBitcoinExplorer.kt`
  - `amethyst/src/main/java/com/vitorpamplona/amethyst/model/nip03Timestamp/TorAwareOkHttpOtsResolverBuilder.kt`
  - `amethyst/src/main/java/com/vitorpamplona/amethyst/model/nip03Timestamp/OtsSettings.kt`
