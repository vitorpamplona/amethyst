# Can the phone's GPU mine NIP-13 proof of work?

**Date:** 2026-08-13
**Status:** analysis + measurements; recommendation is **NOT to build a GPU miner**
**Verdict:** a flagship phone GPU lands at roughly the same SHA-256 throughput as the
CPU cores the miner already uses, because ARMv8 CPUs have SHA-256 **in silicon** and
mobile GPUs do not. The unclaimed speedup is on the CPU side: a midstate is ~3x on
JVM targets, and on Android it hinges on Conscrypt's per-digest JNI cost, which is
still unmeasured.

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

**Those numbers were wrong — about 3.3x pessimistic — and are kept here only because
the first two revisions of this plan reasoned from them.** See the correction below;
everything downstream uses the corrected model.

### 3. Correction: the JVM already runs SHA-256 at hardware speed

The table above suggested `cost ≈ 76 ns + 172 ns/block`, which made the JVM look 3.7x
slower than `openssl speed -evp sha256` (1,357 MB/s ⇒ 47 ns/block) on the same CPU.
That gap does not exist. Re-measured with a best-of-5 loop:

| bytes | blocks | ns/hash | marginal ns/block |
| ----- | ------ | ------- | ----------------- |
| 64 | 2 | 134 | — |
| 256 | 5 | 270 | 45 |
| 512 | 9 | 455 | 46 |
| 1,024 | 17 | 846 | 48 |
| 4,096 | 65 | 3,143 | 47 |
| 65,536 | 1,025 | 47,395 | 46 |

**Corrected model: `cost ≈ 42 ns fixed + 46 ns per 64-byte block`** — the marginal
cost is flat at every size and equals OpenSSL's 47 ns/block. The HotSpot intrinsic is
doing the work: `-XX:-UseSHA256Intrinsics` gives 390 ns/block (8x worse) and
`-XX:TieredStopAtLevel=1` gives 583 ns/block.

The original figure was a single unguarded 500 ms sample with no best-of-N, taken in
the same Gradle invocation as the module's first full compile (4-core container, 6 GB
Gradle daemon + 8 GB Kotlin daemon). Every controlled repetition since lands at
46–48 ns/block: standalone `java` and inside a Gradle test worker; the original
timing method and a best-of-5; with and without four CPU hogs; and immediately after
a forced recompile. Compilation shape was ruled out too — an OSR-compiled loop in
`main` and a hot C2-compiled method differ by 1.02x.

**There is therefore no "slow regime".** OpenJDK reaches hardware SHA-256 with an
intrinsic over pure-Java code — no JNI at all — and ARMv8 crypto extensions land in
the same place (~2 GB/s per core ≈ 32 ns/block). Android is the one platform whose
per-call cost is unmeasured, because Conscrypt makes a real JNI call per digest where
OpenJDK makes none.

**Lesson for the next measurement:** take best-of-N, never a single wall-clock window,
and never in the same Gradle invocation that compiled the code.

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

**1. Midstate — ~3x on JVM targets, unknown on Android.**
SHA-256 is `state ← compress(state, block)` over 64-byte blocks, strictly sequential.
Every block before the one containing the nonce is identical on every attempt, so it
can be absorbed **once** into 8 uint32 words and restored per attempt. The boundary is
`(searchFrom / 64) * 64` — `searchFrom`, not `nonceStarts`, because a parallel
worker's fixed prefix is constant too. This is legal only because the payload length
is constant within one `search()` pass (the nonce only grows on exhaustion, which
restarts the pass), so the padding and length suffix don't move.

On the corrected model (`42 + 46n`), for a JVM target:

| | now | with midstate | speedup |
| --- | --- | --- | --- |
| Short note, 8 blocks → 2 | 410 ns | 134 ns | **3.1x** |
| Long note, 11 blocks → 5 | 548 ns | 272 ns | **2.0x** |

The fixed cost is only 42 ns there, so cutting block count is nearly a pure win. That
is the case for doing it in `desktopApp`/`cli`/`geode`.

**Android is the open question, and it turns on one unmeasured number:** Conscrypt's
per-digest JNI cost. OpenJDK pays none (intrinsic over pure Java); Conscrypt pays a
real call. If that overhead is ~200 ns against ~32 ns/block on ARMv8, an 8-block
attempt is 456 ns and a 2-block one is 264 ns — and neither route to a midstate keeps
that win. `MessageDigest` doesn't expose state, so it is either `clone()`-ing a
pre-absorbed digest (keeps the ARM instruction but adds a *second* JNI call) or a
Kotlin compression function in `commonMain` (drops to zero JNI but forfeits the
hardware instruction, at maybe 300–500 ns/block on ART). Whether Conscrypt's digest is
even `Cloneable` is also unverified.

So: measure Conscrypt's fixed per-digest cost on a real device first. That single
number decides whether Android gets the JVM's 3x, roughly nothing, or a regression.

**2. Kill the per-call floor, if there is one.**
If Android does turn out to be JNI-bound, the way past it is to run many attempts
*below* the JNI boundary — one native call that restores the midstate, walks N nonces
and returns a winner. That keeps the ARMv8 instruction *and* pays the call cost once
per million attempts. It needs an NDK toolchain, which `amethyst` does not have today
(`nestsClient` builds native code, so it is not unprecedented). It is the same
batching structure the GPU path needs — and it beats the GPU by keeping the hardware
SHA-256. Conditional on the measurement above; do not build it on the estimate.

**3. `PoWMiner` only uses half the cores.** `minerWorkers = cores / 2` is right while
the user is composing, but a job that has been handed to the foreground service and
is no longer blocking any UI could use more.

**4. Delegate off-device.** `quartz/…/nip90Dvms/eventPowDelegation/` already models
NIP-90 kinds 5970/6970 — hand the template to a DVM with real hardware. That beats
every on-device option and is already specced.

## Advancing `created_at` while mining — SHIPPED

Not a speedup — a correctness fix that this analysis is a prerequisite for, because
it interacts with the midstate. Implemented as described below; `PoWMiner.run/mine`
take an optional `refreshCreatedAt: (() -> Long)?`, covered by
`quartz/…/PoWMinerCreatedAtTest.kt`.

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

The new parameter goes **last**, after `isActive`, so the trailing-lambda call sites
keep binding their lambda to `isActive`. Kotlin would otherwise silently retarget them
at `refreshCreatedAt`; the `Boolean`/`Long` mismatch makes that a compile error rather
than a bug, but those sites were rewritten to a named `isActive = { … }` anyway.

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

The layout table came from a throwaway `quartz` JVM test that serialized templates via
`EventHasherSerializer.fastMakeJsonForId` and located the nonce with
`ByteArray.indexOf`. The corrected cost sweep timed `sha256Into` per size with a
300k-iteration warmup and best-of-5 timing, cross-checked against standalone Java
(`java Sweep.java`) and `openssl speed -evp sha256`. Neither probe was kept — they
printed rather than asserted.

A permanent version belongs in `benchmark/`, where it can be taken on-device. The one
number worth having there is Conscrypt's fixed per-digest cost: subtract the fitted
slope from a 1-block hash, exactly as the `42 + 46n` fit above does. Everything the
Android side of this plan is still undecided about follows from it.
