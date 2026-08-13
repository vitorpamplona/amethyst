# Can the phone's GPU mine NIP-13 proof of work?

**Date:** 2026-08-13
**Status:** analysis + measurements; recommendation is **NOT to build a GPU miner**
**Verdict:** a flagship phone GPU lands at roughly the same SHA-256 throughput as the
CPU cores the miner already uses, because ARMv8 CPUs have SHA-256 **in silicon** and
mobile GPUs do not. The unclaimed speedup is on the CPU side — and on Android it is
batching attempts under one JNI call, not the midstate (see the two regimes below).

Also specifies advancing `created_at` while mining, which shares the same pass
structure a midstate would need.

## The question

Can we offload `PoWMiner`'s hash loop to the phone GPU to mine NIP-13 proof of work
faster?

## What the miner does today

`quartz/…/nip13Pow/miner/PoWMiner.kt` serializes the event once into the NIP-01 id
payload, finds the nonce inside that byte array, then mutates the nonce bytes in
place and re-hashes:

```kotlin
fun reachedDesiredPoW(byteArray: ByteArray) =
    PoWRankEvaluator.atLeastPowRank(sha256Into(hashOut, byteArray, byteArray.size), …)
```

`PoWMiner.mine()` races `workers` copies of that search over disjoint nonce prefixes.
`PoWPolicy.minerWorkers()` sets `workers = cores / 2` so the UI and relay client keep
their cores. `sha256Into` is `MessageDigest("SHA-256")` from a `ThreadLocal`
(`quartz/…/utils/sha256/Sha256.jvmAndroid.kt`), i.e. Conscrypt → BoringSSL.

Two facts about that loop drive everything below.

### 1. It re-hashes the whole payload every attempt

`fastMakeJsonForId` serializes `[0,pubkey,created_at,kind,tags,content]`. The PoW tag
is appended as the **last** tag, so only the tail — the rest of the nonce tag plus
the whole content string — changes between attempts. Everything before the 64-byte
SHA-256 block containing the nonce is constant and could be absorbed **once** into a
midstate.

Measured layout (4 tags: 2 `e` + 2 `p`, nonce at byte 407):

| Payload | Size | Padded blocks | Blocks that actually change |
| ------- | ---- | ------------- | --------------------------- |
| 49-char note | 473 B | 8 | **2** |
| 223-char note | 663 B | 11 | **5** |

Six of the eight (resp. eleven) block compressions per attempt are recomputing bytes
that never change. Real posts carry more `p` tags than this sample, which pushes the
nonce further right and makes the constant prefix bigger, not smaller.

### 2. Cost model of the current hash call

Single-thread `sha256Into` on the build container's x86-64 JVM, payload swept from 32 B
to 4 KB:

| Payload | Blocks | hashes/s | µs/hash |
| ------- | ------ | -------- | ------- |
| 32 B | 1 | 4,025,423 | 0.248 |
| 64 B | 2 | 2,197,941 | 0.455 |
| 256 B | 5 | 1,010,665 | 0.989 |
| 1024 B | 17 | 329,215 | 3.038 |
| 4096 B | 65 | 88,845 | 11.256 |

That is a clean straight line: **cost ≈ 76 ns fixed + 172 ns per 64-byte block**
(predicts 3.00 µs at 17 blocks vs 3.04 µs measured). The fixed part is the
`MessageDigest` call overhead (JNI + digest setup); the slope is the compression
function.

What transfers to a phone is the *shape*: per-attempt cost is dominated by block
count, with a fixed per-call floor that becomes significant once block count drops.

### 3. Two regimes, and the JVM is in the slow one

The size of every optimization below depends on whether SHA-256 runs on dedicated
silicon or in software, because that moves the 172 ns slope but not the 76 ns floor.
Both regimes, measured on the same container CPU (Xeon @ 2.1 GHz):

| Path | ns per block |
| ---- | ------------ |
| `sha256Into` → `MessageDigest` (JDK 21) | **172** |
| `openssl speed -evp sha256`, SHA-NI, 8 KB asymptote (1,357 MB/s) | **47** |

The JVM is **3.7x off hardware SHA-256 on the same CPU**, despite `sha_ni` in
`/proc/cpuinfo` and `UseSHA256Intrinsics = true`. I could not explain the gap and did
not chase it, but it deserves its own investigation: it would speed up event
*verification*, which is far hotter than mining.

ARMv8 crypto extensions land near the hardware number (~2 GB/s per core ≈ 32 ns per
block), so **Android is in the fast regime and JVM targets are in the slow one** —
which is exactly what decides the value of a midstate.

## What GPU APIs are actually reachable

| API | Reachable from Kotlin? | Notes |
| --- | --- | ----- |
| **OpenGL ES 3.1 compute** | **Yes** — `android.opengl.GLES31`, no NDK | Only realistic pure-Kotlin path. ES 3.1 guarantees just 4 SSBO bindings; needs an offscreen EGL pbuffer context on its own thread |
| Vulkan compute | No — NDK only | Better (subgroups, less driver overhead), but `amethyst` has no C/C++ toolchain today; would add CMake + NDK to the Android build |
| OpenCL | No | Not part of Android's guaranteed surface; `dlopen("libOpenCL.so")` works on some Qualcomm/ARM devices and not others |
| RenderScript | No | Deprecated in API 31; Google's own migration guide points at ES 3.1 compute |

So: OpenGL ES 3.1 compute shaders, with a mandatory CPU fallback for devices below
ES 3.1 (`minSdk` here is 26, so that fallback is not optional).

## Why the GPU does not win

**Mobile GPUs have no SHA-256 hardware. ARM CPUs do.** ARMv8 Cryptographic Extensions
add `SHA256H`/`SHA256H2`/`SHA256SU0`/`SHA256SU1`, and BoringSSL dispatches to them at
runtime. A GPU has to emulate the same work out of general integer ALU ops.

Ops per 64-byte block in GLSL ES (no rotate builtin, so every `rotr` is shl+shr+or):

- message schedule, 48 × (2 × σ ≈ 9 ops + 3 adds) ≈ 1,000 ops
- 64 rounds × (Σ1 11 + Ch 4 + Σ0 11 + Maj 5 + 7 adds) ≈ 2,400 ops

≈ **3,400 integer ops per block compression.**

| | Throughput per block-compression |
| --- | --- |
| Flagship GPU (Adreno 750 class, ~1.5 T int ops/s at ~40% ALU efficiency) | ≈ **175 M/s** |
| Flagship CPU, ARMv8 SHA-2 ext (~2 GB/s per big core = 31 M blocks/s), 4 workers | ≈ **125 M/s** |
| Mid-range GPU | ≈ 20–50 M/s |

The GPU estimate agrees with the ~80–150 MH/s figure quoted for SHA-256 on mobile in
hashcat-style benchmarks, so two independent derivations land in the same place.

That is **~1.4x on a flagship, and a loss on mid-range hardware** — for a
GPU-vs-CPU offload that would normally be worth 10–50x. Both paths benefit equally
from the midstate optimization, so the ratio does not improve.

### And the 1.4x costs more than it's worth

- **The GPU is the compositor.** Saturating it janks the whole system, including
  other apps. The CPU miner already gives up half the cores (`minerWorkers`)
  specifically to avoid this; there is no equivalent "half a GPU".
- **Power and thermals.** Sustained GPU load is ~3–5 W and throttles within ~60 s.
  `PowMiningForegroundService` runs under the Android 14 `shortService` type with a
  hard ~3 minute budget — most of which would be spent throttled.
- **Dispatch latency.** Kernel launch + readback is ~1–5 ms, so batches must be large
  (~10⁶ nonces) to amortize. At the difficulties users actually pick, the whole job
  can finish inside one batch, wasting most of it.
- **Play policy adjacency.** Google prohibits apps that mine cryptocurrency on-device
  (remote *management* is allowed). NIP-13 PoW is not cryptocurrency, but a
  GPU-saturating component named "miner" invites review friction that the CPU path
  has never attracted.
- **Cost.** ~1,000 lines of GLSL + EGL context management + batch/nonce plumbing +
  result verification, Android-only, shared with neither `desktopApp` nor `cli`, and
  it has to be kept in lockstep with the CPU nonce enumeration forever.

## What to do instead

**1. Midstate — big on JVM targets, probably a no-op on Android.**
SHA-256 is `state ← compress(state, block)` over 64-byte blocks, strictly sequential.
Every block before the one containing the nonce is identical on every attempt, so it
can be absorbed **once** into 8 uint32 words and restored per attempt. The boundary is
`(searchFrom / 64) * 64` — `searchFrom`, not `nonceStarts`, because a parallel
worker's fixed prefix is constant too. This is legal only because the payload length
is constant within one `search()` pass (the nonce only grows on exhaustion, which
restarts the pass), so the padding and length suffix don't move.

The win depends entirely on which regime the device is in:

| | now | with midstate | speedup |
| --- | --- | --- | --- |
| Slow regime — measured `76 + 172n` (JVM targets) | 1,452 ns | 420 ns | **3.5x** |
| Fast regime — ARMv8 crypto ext, `~200 + 47n` (Android) | 576 ns | ~494 ns via `clone()` | **~1.2x** |

In the fast regime the JNI floor, not the compression, is the cost — and midstate
does nothing about it. Worse, the two ways to get a midstate both make it back:
`MessageDigest` doesn't expose state, so it is either `clone()`-ing a pre-absorbed
Conscrypt digest (keeps the ARM instruction, but adds a *second* JNI call, hence the
1.2x) or a Kotlin compression function in `commonMain` (one JNI call becomes zero, but
gives up the hardware instruction — at ~300–500 ns/block on ART that is *slower than
today*). Whether Conscrypt's digest is even `Cloneable` is unverified.

So: worth doing for `desktopApp`/`cli`/`geode`, not obviously worth doing for the
phone. Settle it with an on-device measurement before writing it.

**2. Kill the per-call floor — this is the real Android win.**
The fast regime is JNI-bound: ~200 ns of call overhead against ~376 ns of hashing.
The only way past it is to run many attempts *below* the JNI boundary — one native
call that restores the midstate, walks N nonces, and returns a winner. That keeps the
ARMv8 instruction *and* pays the JNI cost once per million attempts instead of once
per attempt. It needs an NDK toolchain, which `amethyst` does not have today
(`nestsClient` builds native code, so it is not unprecedented). Ironically this is the
same batching structure the GPU path needs — and it beats the GPU by keeping the
hardware SHA-256.

**3. `PoWMiner` only uses half the cores.** `minerWorkers = cores / 2` is right while
the user is composing, but a job that has been handed to the foreground service and
is no longer blocking any UI could use more.

**4. Delegate off-device.** `quartz/…/nip90Dvms/eventPowDelegation/` already models
NIP-90 kinds 5970/6970 — hand the template to a DVM with real hardware. That beats
every on-device option and is already specced.

## Advancing `created_at` while mining

Not a speedup — a correctness fix that this analysis is a prerequisite for, because
it interacts with the midstate.

NIP-13: *"It is recommended to update the `created_at` as well during this process."*
We do half of it. `PoWPublishQueue.enqueue(refreshCreatedAtOnStart = …)` and the
anonymous path in `ShortNotePostViewModel` both stamp a fresh `TimeUtils.now()` at
mining **start**, because "a job that waited in the queue (or was restored after a
process death) would otherwise publish visibly in the past". But the mining run itself
is the same problem: 28 bits at a few M h/s is ~a minute, 30 bits several — and
`PoWPolicy.MAX_DIFFICULTY` is 40. A post can still land in the feed minutes stale.

**Where it goes.** `search()` already re-serializes per pass in its
`do { … } while (nextSize < 50)` loop; today a pass only ends on nonce-space
exhaustion. Bound a pass by *either* exhaustion *or* a wall-clock budget (~1 s), and
re-stamp `createdAt` at the top of each pass. PoW search is memoryless, so restarting
a pass throws away no progress.

**API shape.** Don't make `EventTemplate.createdAt` lazy — it is `@Immutable`,
`@Serializable`, and persisted by `PoWJobPersistence`; a field that changes on read
breaks value semantics and the checkpoint format. Pass the miner a clock instead:
`PoWMiner.mine(…, refreshCreatedAt: (() -> Long)? = null)`, null meaning today's
frozen behaviour. The consumers are already correct — `PoWNostrSigner` forwards
`mined.createdAt` to `signer.sign(…)`, and the queue publishes the mined template — so
this is contained inside `PoWMiner` plus one flag at the call sites.

**"When we can" is already modelled.** Reuse `refreshCreatedAtOnStart`'s predicate
rather than inventing a second one; its comment already states the exclusion —
*"Must stay false for scheduled posts, whose future created_at is intentional."*
Replaceable/addressable kinds (created_at is their conflict-resolution key) and NIP-59
seals/gift wraps (deliberately randomized timestamps) are already excluded upstream by
`PoWPolicy.neverMine` and `kindsToMine`. Clamp with `maxOf(previous, now())` so a
wall-clock step backwards cannot move a post into the past.

**Interaction with the midstate.** `created_at` sits *before* the tags in
`[0,pubkey,created_at,kind,tags,content]`, so every bump invalidates the whole
midstate. That costs one prefix re-absorb (~1 µs) per bump against millions of
attempts between bumps — irrelevant, and it happens at the pass boundary where the
payload is being rebuilt anyway. The digit count of `created_at` is stable (10 digits
until 2286), but re-serializing per pass sidesteps that question entirely.

**Bonus.** A fresh `created_at` is a fresh search space, which makes the
`nextSize += STARTING_NONCE_SIZE` growth loop and its `RuntimeException("Could not
find PoW")` escape hatch effectively dead: one pass over a 5-char nonce is 73⁵ ≈ 2.1e9
candidates, and each bump grants another 2.1e9.

## If we want the GPU experiment anyway

Do it as a measurement, not a feature:

1. `expect fun gpuHashRate(): Double?` in `quartz`, Android actual on `GLES31`,
   returning null when compute shaders are unavailable.
2. A minimal SHA-256 compute shader over a fixed 2-block tail with midstate uploaded
   as 8 uints, one nonce per invocation, atomically reporting a winner.
3. Add it to `benchmark/…/PoWBenchmark.kt` next to `generatePow` and run both on real
   devices (a flagship and a mid-ranger).

Ship it only if the on-device number beats the CPU by enough to pay for the jank —
the analysis above says it will not, but that measurement is the only thing that
settles it, and it cannot be taken in this container (no GPU, no device).

## Reproducing the measurements

The layout table and the cost sweep came from a throwaway `quartz` JVM test that
serialized templates via `EventHasherSerializer.fastMakeJsonForId`, located the nonce
with `ByteArray.indexOf`, and timed `sha256Into` at each payload size after a
200k-iteration warmup (`./gradlew :quartz:jvmTest`). It was not kept — it printed
rather than asserted. A permanent version belongs in `benchmark/` so the numbers can
be taken on-device, where they actually matter.
