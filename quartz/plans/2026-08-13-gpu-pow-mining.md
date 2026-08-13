# Can the phone's GPU mine NIP-13 proof of work?

**Date:** 2026-08-13
**Status:** analysis + measurements; recommendation is **NOT to build a GPU miner**
**Verdict:** a flagship phone GPU lands at roughly the same SHA-256 throughput as the
CPU cores the miner already uses, because ARMv8 CPUs have SHA-256 **in silicon** and
mobile GPUs do not. The unclaimed 2–4x is on the CPU side (midstate), not the GPU.

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

These absolute numbers are x86 without a SHA-NI intrinsic and say nothing about a
phone. What transfers is the *shape*: per-attempt cost is dominated by block count,
with a fixed per-call floor that becomes significant once block count drops.

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

**1. Midstate (recommended, ~100 lines in `quartz`, every platform).**
Absorb the constant prefix once, re-hash only the tail. On the measured cost model
that is **3.5x** for a short note (1,452 ns → 420 ns) and **2.1x** for a long one
(1,968 ns → 936 ns) — larger than the flagship-only 1.4x the GPU would buy, on every
device including desktop and `amy`. Needs a SHA-256 that exposes its state; `MessageDigest`
does not, so this means a small Kotlin compression function in `commonMain` (which
gives up the ARM hardware instruction) or `clone()`-ing a pre-absorbed Conscrypt
digest per attempt (which keeps it — worth measuring both on-device).

**2. Kill the per-call floor.** Once the tail is 2 blocks, the ~76 ns (x86) /
~100–200 ns (JNI on ART) fixed cost per `MessageDigest` call is a third of the
attempt. A batched or state-reusing hasher matters more than raw compression speed at
that point.

**3. `PoWMiner` only uses half the cores.** `minerWorkers = cores / 2` is right while
the user is composing, but a job that has been handed to the foreground service and
is no longer blocking any UI could use more.

**4. Delegate off-device.** `quartz/…/nip90Dvms/eventPowDelegation/` already models
NIP-90 kinds 5970/6970 — hand the template to a DVM with real hardware. That beats
every on-device option and is already specced.

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
