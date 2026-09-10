# marmotBench — quartz Marmot vs MDK, head to head

Measures the same Marmot/MLS operations on both engines so "are we as fast as
the reference" has an answer instead of an opinion.

The quartz half lives here. The MDK half is its own criterion suite:

```sh
# quartz
./gradlew :marmotBench:run                     # table
./gradlew :marmotBench:run --args=--json       # machine-readable

# MDK (the reference)
cd <mdk> && cargo bench -p cgka-engine --bench group_lifecycle
```

## What is compared

| this module           | MDK bench                   |
|-----------------------|-----------------------------|
| `create_group/N`      | `bench_create_group`        |
| `join_welcome`        | `bench_join_welcome`        |
| `send_app_message`    | `bench_app_message_send`    |
| `ingest_app_message`  | `bench_app_message_ingest`  |

Both sides exclude transport crypto and run over in-memory storage, so what is
measured is the engine's own CPU cost. Setup is outside the measured window on
both sides — criterion's `iter_batched(.., PerIteration)` there, an explicit
`setup` lambda here.

**One shape difference, deliberately not hidden:** MDK folds invitees into the
founding group (`FoundingGroupCreated`), while we create at epoch 0 and add in
a second commit to epoch 1. `create_group/N` therefore includes one more commit
on our side. That is a real cost, and averaging it away would be the wrong kind
of favourable.

## Why allocation is reported next to latency

The brief is "as fast if not faster, while avoiding GC as much as possible",
and those are two different measurements. A JVM can win a microbenchmark while
allocating tens of times more per operation; the bill arrives later as GC
pauses on a phone, in a frame the benchmark never renders. So every row carries
**bytes allocated per operation** from `com.sun.management.ThreadMXBean`
(in the JDK — no dependency), alongside p50/p90/p99.

That counter is per-thread, which is why every benchmark body runs inline on
the harness thread via `runBlocking`. Work dispatched elsewhere would allocate
off-book and read as free.

The JVM runs with `-XX:+UseSerialGC` on a fixed 4g heap: the point is to keep
allocation attributable to the benchmark thread and to keep a collection from
landing inside a measured sample and corrupting the percentile it falls in.

## Reading the numbers honestly

- Rust has no GC, so `alloc/op` has no MDK counterpart. It is not a
  head-to-head column — it is our own regression signal, and the number to
  drive down.
- Latency across a JVM and a Rust binary on the same host is a fair comparison
  of *this* workload on *this* machine. It is not a language benchmark.
- `create_group/32` builds 32 KeyPackages in setup. That cost is excluded, but
  it makes each iteration expensive to prepare — hence the low iteration count.

## Result: eliminating the field-arithmetic allocation

The first run of this module put **93% of all sampled allocation** (JFR
`jdk.ObjectAllocationSample`) in `Curve25519Field.mul/add/sub`. The pure-Kotlin
Curve25519 returned a fresh `LongArray(16)` from every field operation, and a
Montgomery ladder performs ~18 of them per bit for 255 bits — so a single
X25519 scalar multiplication allocated over a megabyte of garbage.

Each operation now has an in-place `*Into` twin, and both hot paths (the X25519
ladder and Ed25519's extended-coordinate point addition) allocate their working
set once and then run allocation-free. See `Curve25519Field`.

Allocation per operation, before and after. This column reproduces to four
significant figures across runs, so the ratios are real:

| operation           | before       | after       | reduction |
|---------------------|--------------|-------------|-----------|
| `create_group/0`    |   6 958.7 KB |    88.3 KB  | 79x       |
| `create_group/1`    |  27 074.1 KB |   570.5 KB  | 47x       |
| `create_group/8`    |  94 548.6 KB | 3 501.7 KB  | 27x       |
| `create_group/32`   | 333 101.2 KB | 33 216.6 KB | 10x       |
| `join_welcome`      |  10 331.6 KB |   252.2 KB  | 41x       |
| `send_app_message`  |   2 755.1 KB |    71.6 KB  | 38x       |
| `ingest_app_message`|   5 640.3 KB |    70.9 KB  | 80x       |

Latency improved too, though it is the noisier measurement — two post-rewrite
runs are given so the spread is visible rather than averaged away:

| operation           | p50 before | p50 after (run 1 / run 2) |
|---------------------|------------|---------------------------|
| `create_group/0`    |   6 323.7us |    4 184.2 / 3 971.5us     |
| `create_group/1`    |  16 928.2us |   13 197.2 / 13 574.2us    |
| `create_group/8`    |  51 470.7us |   43 244.0 / 43 424.7us    |
| `create_group/32`   | 190 080.0us |  202 261.1 / 172 908.9us   |
| `join_welcome`      |   6 219.3us |    4 919.5 / 4 991.3us     |
| `send_app_message`  |   1 720.9us |    1 314.3 / 1 376.0us     |
| `ingest_app_message`|   3 114.1us |    2 487.5 / 2 545.6us     |

`create_group/32` is the row to distrust: it has the fewest iterations, and its
two runs disagree by 17% at p50 and by nearly 2x at p99 (400.3ms then 210.4ms).
Read it as "no worse"; the other rows are consistent enough to read as gains.

Against MDK this closes most of the `create_group` gap — `create_group/1` goes
from 4.7x slower to about 3.7x — without changing a single protocol behaviour:
the RFC 7748 / RFC 8032 vector suites, the HPKE tests and the full 4833-test
quartz suite all pass unchanged, which is the point of keeping the allocating
functions around to differentially test against.

## Result: 10 limbs instead of 16

The allocation work above left `create_group` still ~3.9x slower than MDK, and
the primitive benchmarks said why: one X25519 scalar multiplication cost 541us,
and `create_group/1` is about two dozen of them. Curve work *was* the operation.

The cause was the representation, not the language. `Curve25519Field` used
TweetNaCl's 16 limbs of radix 2^16, so a schoolbook field multiply spent 256
limb products. SunEC's X25519 — also pure Java, same JIT, same machine — ran
the same operation in 160us using ~26-bit limbs in 10 words, which is 100
products. The ratio of products matched the ratio of times.

So the field was rewritten to 10 limbs of radix 2^25.5, the layout ref10,
curve25519-donna and SunEC all use: 100 products per multiply, 55 per square
(each off-diagonal pair once, doubled), and a dedicated scalar multiply for the
ladder's a24 constant instead of a general multiply against nine zero limbs.

| primitive        | 16 limbs | 10 limbs | speedup |
|------------------|----------|----------|---------|
| `x25519_dh`      |  541us   |  121us   | 4.5x    |
| `x25519_base`    |  535us   |  121us   | 4.4x    |
| `ed25519_sign`   | 1018us   |  259us   | 3.9x    |
| `ed25519_verify` | 2113us   |  536us   | 3.9x    |

At 121us the scalar multiplication is now faster than SunEC's 160us, which is
the useful sanity check on the result: it lands where a good managed-language
implementation should, rather than somewhere suspiciously better.

Against MDK, over the whole suite (both post-rewrite runs shown where they
differ; `alloc/op` reproduces to four significant figures):

| operation            | MDK (Rust) | quartz before | quartz now      | vs MDK       |
|----------------------|------------|---------------|-----------------|--------------|
| `create_group/1`     |  3.61 ms   | 16.93 ms      | 5.42 - 5.88 ms  | 1.5-1.6x slower |
| `create_group/8`     |  9.93 ms   | 51.47 ms      | 17.27 - 17.60 ms| 1.8x slower  |
| `create_group/32`    | 31.64 ms   | 190.08 ms     | 77.53 - 81.47 ms| 2.5x slower  |
| `join_welcome`       |  4.77 ms   |  6.22 ms      | 1.82 - 2.00 ms  | **2.5x faster** |
| `send_app_message`   |  4.28 ms   |  1.72 ms      | 0.61 - 0.71 ms  | **6.5x faster** |
| `ingest_app_message` |  (n/a)     |  3.11 ms      | 0.88 - 0.90 ms  | —            |

`create_group` remains the weakest row, and the shape difference in "What is
compared" is part of why: we create at epoch 0 and add in a second commit,
where MDK folds invitees into the founding group. `create_group/32` is also
still the noisiest row in the suite.

### Why the constants can be trusted

Changing the representation re-encodes every curve constant, which is exactly
the kind of change where a single mistyped limb produces code that still runs
and is still wrong. None of them were transcribed by hand: each was re-derived
from its existing 16-bit encoding and then checked against its mathematical
definition — `d == -121665/121666`, `d2 == 2d`, `By == 4/5`, `I^2 == -1` — and
the multiply and square formulas were generated from the representation's
weight bookkeeping and diffed against an independent reference over 20 000
random limb vectors before any Kotlin was written. The carry chain and the
canonical encoder were validated the same way, including at `p`, `p-1`, and on
non-canonical inputs such as `p` itself.

The RFC 7748 and RFC 8032 vector suites, HPKE, the MDK crypto-interop vectors
and the full quartz + commons suites all pass unchanged.
