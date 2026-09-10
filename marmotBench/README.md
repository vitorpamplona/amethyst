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

| this module              | MDK bench                   |
|--------------------------|-----------------------------|
| `create_group/N`         | `bench_create_group`        |
| `join_welcome`           | `bench_join_welcome`        |
| `send_app_message/N`     | `bench_app_message_send`    |
| `ingest_app_message/N`   | `bench_app_message_ingest`  |
| `ingest_commit/N`        | (no counterpart)            |

`ingest_commit` has no MDK counterpart because neither suite had one. It is the
operation every member pays on every membership or settings change, and the
only one whose cost is supposed to grow with the group, so leaving it
unmeasured left the most load-bearing path in the protocol untested.

Both sides exclude transport crypto and run over in-memory storage, so what is
measured is the engine's own CPU cost. Setup is outside the measured window on
both sides — criterion's `iter_batched(.., PerIteration)` there, an explicit
`setup` lambda here.

**On the shape of `create_group/N`.** An earlier version of this file claimed
we spend "one more commit" than MDK's founding creation. That was wrong, and
`--epoch-probe` exists to keep it honest. It reported, before the founding-add
fix below: `create` publishes nothing and leaves epoch 0, `addMember` produces
exactly one commit, and the invitee joins at epoch 1. MLS permits nothing else
— RFC 9420 section 11 requires a group to be created with a single member — so
MDK commits its founding Adds internally too. Both sides do one commit's worth
of ratchet work. It now reports zero commits published for that same step, for
the reason in the next paragraph; re-run it rather than trusting this text.

What did differ is that we PUBLISHED that founding commit and MDK does not.
`protocol-core/publish-lifecycle.md` gives the founding Add an empty
group-message publication obligation, because the creator is the only
pre-existing member and no peer can be forked by failing to publish it. We
implemented that exception for the epoch-0 creation and missed that it extends
to the Add immediately after. It is fixed now: the founding Add merges locally
and only the Welcomes go out, which is both what the spec says and what the
reference implementation does.

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

Against MDK, over the whole suite (two runs shown where they differ; `alloc/op`
reproduces to four significant figures):

| operation            | MDK (Rust) | quartz before | quartz now      | vs MDK       |
|----------------------|------------|---------------|-----------------|--------------|
| `create_group/1`     |  3.61 ms   | 16.93 ms      | 5.25 - 5.85 ms  | 1.5-1.6x slower |
| `create_group/8`     |  9.93 ms   | 51.47 ms      | 16.23 - 16.87 ms| 1.7x slower  |
| `create_group/32`    | 31.64 ms   | 190.08 ms     | 77.5 - 195.7 ms | 2.5x - 6x (see below) |
| `join_welcome`       |  4.77 ms   |  6.22 ms      | 1.89 - 1.93 ms  | **2.5x faster** |
| `send_app_message/0` |  4.28 ms   |  1.72 ms      | 0.55 - 0.60 ms  | **7.1x faster** |
| `ingest_app_message/1`| (n/a)     |  3.11 ms      | 0.88 - 0.95 ms  | —            |

`send_app_message/0` is the row that lines up with MDK, not a flattering pick:
their `prepare_app_send` builds the group with `create_request(vec![])`, so
their sender is alone in it too. The `/1`, `/8` and `/32` rows have no MDK
counterpart. `ingest_app_message` has none either — MDK's bench binary panics
in `bench_deferred_outbound_preflight_matrix` before reaching
`bench_app_message_ingest`, so there is no reference number to compare against
rather than one we chose not to use.

`create_group` remains the weakest row, and `create_group/32` is not just the
noisiest in the suite — it is the one number here that should not be quoted as
a single figure at all. Across five post-rewrite runs on this host its p50 came
out 77.5, 81.5, 82.5, 86.3 and 195.7 ms: four clustered within 11% of each
other and one more than twice the rest. The row has the fewest iterations in
the suite (each one has to build a 32-member group in setup) and this is a
shared cloud vCPU, so a single noisy neighbour moves it in a way it cannot move
the 300-iteration rows. Treat "roughly 2.5x MDK, occasionally much worse" as
the honest reading, and re-run before believing any movement in it.

### What not publishing the founding commit was worth

The founding-add fix removed a commit event that `create_group/N` used to
build, sign, outer-encrypt and publish for an audience of nobody. It is a
correctness fix first (see the commit), but it is also the one change in this
file whose benchmark effect is worth stating precisely, because it is smaller
than it sounds:

| row               | alloc before | alloc now | change |
|-------------------|--------------|-----------|--------|
| `create_group/0`  |    81.3 KB   |  81.3 KB  | none   |
| `create_group/1`  |   543.6 KB   | 482.5 KB  | -11%   |
| `create_group/8`  | ~3 445 KB    | 3 240 KB  | -6%    |
| `create_group/32` |  32 825 KB   | 32 355 KB | -1.4%  |

`create_group/0` is unchanged and must be: with no invitees there is no
founding Add to skip publishing. The saving grows in absolute terms with the
invitee count (~61 KB, ~205 KB, ~470 KB) because the commit not being built
carries N Add proposals, but shrinks as a fraction because everything else in
the operation grows faster. Latency moved within run-to-run noise, so no
latency claim is made for it: the reason to want this change is that a group
creation no longer depends on a relay acknowledgement the spec never asked
for.

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

## Group-size scaling: what the one-member benchmarks were hiding

The original `send_app_message` and `ingest_app_message` rows ran on groups of
one and two members. That is the flattering case, and it hid a real asymmetry.

Numbers from one full-suite run:

| benchmark                    | p50     | alloc/op   |
|------------------------------|---------|------------|
| `send_app_message/0 members` |  601 us |   68.7 KB  |
| `send_app_message/1 members` |  553 us |   73.8 KB  |
| `send_app_message/8 members` |  602 us |  117.1 KB  |
| `send_app_message/32 members`|  691 us |  253.1 KB  |
| `ingest_app_message/1`       |  877 us |   61.5 KB  |
| `ingest_app_message/8`       |  951 us |   65.1 KB  |
| `ingest_app_message/32`      |  966 us |   67.4 KB  |

**Latency is flat**, which is what MLS promises: an application message is
sealed under the sender's own ratchet and never touches the tree, so the
cryptography does not care how many members there are. The comparison against
MDK's `send_app_message` therefore survives the parameterisation.

**Allocation is not flat on the send side** — 3.5x from 0 to 32 members, while
the receive side barely moves. The cause is not subtle once looked at:

- `MlsGroupManager.encrypt` calls `persistGroup` **unconditionally**, so the
  whole group state, ratchet tree included, is serialised on every message
  sent.
- `MlsGroupManager.decrypt` calls it **only** when the message was a Commit
  that advanced the epoch. Decrypting an application message persists nothing.

So a 32-member group allocates 252.8 KB to send a message and 67.1 KB to
receive one, and the difference is a full state serialisation performed to
record what amounts to a generation-counter bump. The write itself is
necessary — a sender generation reused after a crash is a nonce-reuse-class
problem — but writing the entire group state for it is heavier than the
invariant requires. Left as a finding rather than a change: send-path
persistence is security-sensitive and deserves its own decision, not a
drive-by.

## `ingest_commit`: logarithmic in time, linear in allocation

Receiving someone else's Commit, from one full-suite run:

| benchmark                  | p50      | alloc/op    |
|----------------------------|----------|-------------|
| `ingest_commit/1 members`  | 1 950 us |   242.8 KB  |
| `ingest_commit/8 members`  | 2 367 us |   523.0 KB  |
| `ingest_commit/32 members` | 3 009 us | 1 390.0 KB  |

Latency grows, but far slower than the member count: 1.5x for a 32x bigger
group. That is the shape MLS predicts. The UpdatePath a Commit carries has one
node per LEVEL of the ratchet tree, so the receiver goes from roughly one HPKE
open at two members to roughly five at thirty-three — a log2 curve, not a
linear one.

Allocation grows 5.7x, tracking the size of the tree being parsed, rebuilt and
persisted rather than the number of curve operations. So `ingest_commit` is
mostly an allocation story, and it is the row to watch on a phone: every member
performs it on every membership or settings change, and at 1.4 MB it is by some
distance the largest single allocator in the suite.

A caution about isolated runs of this row. A `--only=ingest_commit` run
reported 3 995 / 2 905 / 3 182 us — no trend at all, and the one-member case
SLOWEST. That was JIT warm-up: `ingest_commit/1` runs first and pays for
compiling the shared group builder. The monotonic full-suite numbers above are
the trustworthy ones, which is the general rule here — prefer a full run, and
distrust whichever row happens to go first.
