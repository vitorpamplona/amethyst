# NIP-77 reconcile profiling: where geode's sync time actually goes

**Status: investigation + verified fix core (not yet wired).** Follow-up to
the 1M-event relayBench run (`relay.damus.io`, geode 1.12.6 vs strfry
v1-b80cda3), where geode lost the negentropy phase: initial reconcile
6,066 ms / 27 rounds vs strfry 1,270 ms / 14; identical-set reconcile
1,947 ms vs 557 ms (~3.5×).

## What the numbers said, layer by layer

Three benchmarks isolate each layer (all reproduce the relayBench slice
shape: 1M effective corpus, 80%/80% index slices, 60% overlap → a
**contiguous** oldest-20% + newest-20% diff, ~200k each way):

| layer | tool | 1M / 200k-diff result |
|---|---|---|
| library reconcile only (no wire) | `quartz …prodbench.NegentropyReconcileBenchmark` | server **207 ms**, client 302 ms, 14 rounds, seal 331 ms |
| real geode server, one JVM, loopback ws | `geode …perf.NegentropyServerReconcileBenchmark` (`-DnegServerBench=1`) | negotiate **3,214 ms**, 14 rounds |
| two processes + real net | relayBench production | **6,066 ms**, 27 rounds |

Key facts this establishes:

1. **The reconciliation algorithm is not the bottleneck.** The pure
   kmp-negentropy server loop is ~200 ms for the whole 14-round exchange.
   My first cut of the library benchmark showed 22 s / 139 rounds — that
   was a **benchmark bug**: slicing by array index while ids sort randomly
   scatters the diff through sorted order (negentropy's worst case). Real
   relayBench slices are contiguous *time* ranges; fixing the benchmark to
   monotonic `created_at` dropped it to 200 ms / 14 rounds, matching
   strfry's round count exactly.

2. **The gap is geode's live server path, not framing.** Same 14 rounds,
   same 500 KB frame cap as strfry, but 3,214 ms in-process (15× the
   library loop) and 6,066 ms across processes. Rounds only inflate to 27
   in production from *seeding drift* (rejected events during seeding make
   the stored sets differ from the clean slices → some scatter); strfry saw
   less drift that run.

3. **JFR of the real-geode run (server call-trees) splits as:**
   - **~40% hex + UTF-8 + JSON serialization** of the hundred-KB hex NEG
     payload each round (`Hex.encode`/`decode`, `UTF_8.encode`, Jackson).
   - **~26% actual reconcile** (`StorageVector.forEach` +
     `FingerprintCalculator.run` — the per-range fingerprint).
   - rest: allocation (`MessageBuilder.branch`, `Arrays.copyOf`), one-time
     seal/sort.
   The client path is even heavier and dominated by `HashMap.putVal`/
   `resize` accumulating 400k have/need ids as hex strings.

So geode-vs-strfry on sync is mostly the **JVM constant-factor tax on
serializing hex-in-JSON payloads**, which strfry pays in zero-copy C++ —
not a single fixable hotspot. `quartz.utils.Hex` is already an optimized
table-based codec, so there's no cheap win there.

## The one algorithmic lever: prefix-sum fingerprints

The ~26% reconcile slice *is* addressable, and it dominates the
steady-state identical-set reconcile (where geode loses 3.5×): every round
the library recomputes each range fingerprint with an **O(range) walk**.
Negentropy's fingerprint is `sha256( Σ id (mod 2²⁵⁶, 8 LE u32 limbs) ‖
varint(count) )[0:16]` — the inner sum is **additive**, so a prefix-sum
table answers any range's raw sum in **O(1)** (limb-wise subtract with
borrow) + one sha256.

`quartz …prodbench.NegentropyPrefixFingerprintTest` proves this:
- reproduces the library's fingerprint **bit-for-bit** over 2,000 random
  ranges + boundaries at 50k;
- **460× faster per call** on a reconcile-shaped range mix (465 ms → 1.0 ms).

For the top-of-tree fingerprints (each round re-walks ~all 800k ids) and
the identical-set case (16 full-corpus-ish fingerprints per reconcile),
this is the difference between an O(n) walk and a table lookup.

## Shipped — wired via kmp-negentropy v1.1.1+ (option 1)

The cleanest option landed upstream: kmp-negentropy **v1.1.1** added the
`IStorage.fingerprint(begin, end)` seam and a drop-in `PrefixSumStorageVector`
that builds the additive prefix-sum table at `seal()` and answers any range
fingerprint in O(1). Quartz now seals a `PrefixSumStorageVector` in both
`NegentropyServerSession.sealVector` (server / relay-relay responder, also
backing the `LiveNegentropyIndex` snapshot cache) and `NegentropySession`
(initiator). Byte-identical to the plain vector; `NegentropyPrefixFingerprintTest`
asserts the library's accelerated path matches the plain walk over 2000 random
ranges + boundaries.

**v1.2.0** then sped up the library's own reconcile/fingerprint internals. At
the 1M relayBench slice shape (`NegentropyReconcileBenchmark -DnegBenchN=1000000`,
converges exactly, need/have = 200k):

| | pre-fix (v1.0.2) | v1.1.1 | v1.2.0 |
|---|---:|---:|---:|
| seal (NEG-OPEN) | 331 ms | 424 ms | **320 ms** |
| server reconcile | 207 ms | 128 ms | **128 ms** |
| client reconcile | 302 ms | 264 ms | **178 ms** |
| library range-fingerprint walk | 465 ms | 447 ms | **252 ms** |

The prefix-sum still answers each range fingerprint in **0.7 ms** — 356× the
(now faster) v1.2.0 walk. A quartz-side fast server (option 2 below) is no
longer needed.

### Option 2 (not taken): quartz-side fast server

Reimplementing the server reconcile against the prefix-sum index — larger and
interop-critical (must match the wire byte for byte with strfry). Superseded by
the upstream seam; the benchmarks + bit-exact test remain the safety net.

The serialization tax (~40%) is separate and only closes by writing the
hex payload straight into the output buffer as ASCII instead of
`bytes → hexString → JSON string → UTF-8 bytes`.

## Artifacts (all landed here, gated/CI-safe)

- `quartz …prodbench.NegentropyReconcileBenchmark` — library reconcile,
  default 20k (fast CI correctness guard), `-DnegBenchN=1000000` for scale.
- `quartz …prodbench.NegentropyPrefixFingerprintTest` — the verified,
  bit-exact prefix-sum core + per-call speedup (runs at 50k in CI).
- `geode …perf.NegentropyServerReconcileBenchmark` — real in-process geode
  server, opt-in `-DnegServerBench=1`, JFR via `-PnegProfile=/path.jfr`.
