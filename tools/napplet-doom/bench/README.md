# doom.wasm on JVM and Kotlin/Native WebAssembly runtimes

Can a native Compose card play a game from the feed, running the napplet's `doom.wasm` with a
WebAssembly runtime instead of a WebView? This spike measures how long one game tic takes on
[Chicory](https://github.com/dylibso/chicory) (pure Java, Apache-2.0) and
[Chasm](https://github.com/CharlieTap/chasm) (Kotlin Multiplatform, MIT/Apache-2.0), against
V8 as the baseline a WebView napplet gets.

## Run it

```bash
tools/napplet-doom/build.sh                         # builds build/doom.wasm (and fetches Freedoom)
```

```bash
./gradlew -p tools/napplet-doom/bench benchJvm -Ptics=2100 -Pwad=/path/to/doom1.wad
```

```bash
./gradlew -p tools/napplet-doom/bench linkReleaseExecutableLinuxX64
```

```bash
tools/napplet-doom/bench/build/bin/linuxX64/releaseExecutable/doom-wasm-bench.kexe \
  tools/napplet-doom/build/doom.wasm /path/to/doom1.wad 2100
```

```bash
node tools/napplet-doom/bench/bench-node.mjs 2100 /path/to/doom1.wad
```

`-Pruntimes=chicory-aot,chicory,chasm` picks JVM runtimes; without `-Pwad` it uses Freedoom. The
build is standalone (its own `settings.gradle.kts`), not part of Amethyst's. Set `DUMP=dir` on
the Node run to save each checkpoint's frame as a PPM.

## Method

`src/commonMain/kotlin/bench/DoomHost.kt` is the Kotlin twin of the napplet page: the 14 WASI calls
against an in-memory file system holding the WAD, and the 10 `doom` imports. Sound and music are
no-ops; `draw_frame` copies the 320×200 frame out of guest memory, as a Compose card would. Each
runtime gets a thin adapter (`ChasmEngine.kt`, `ChicoryEngine.kt`) over the same host.

The clock is virtual: before each `dg_tick()` it is pinned to the next game tic, so every call runs
exactly one 35 Hz tic plus its frame and every runtime does the same work. That is checked: each
runtime's frame at tics 175 to 2100 must hash the same as V8's, and all of them do. 2100 tics is 60
seconds of game time. The title screen fills roughly the first 175 tics, so most of "first 350" is
title and warm-up; after that it is the demo loop: id's own recordings, played in 3D with monsters.

## Results

Doom shareware `doom1.wad` (v1.9). Intel Xeon @ 2.1 GHz, 4 vCPU cloud VM; OpenJDK 21 (HotSpot);
Kotlin 2.4.21; Node 22 (V8 12.4); Chasm 2.3.0; Chicory 1.7.5. Milliseconds per tic; the budget is
28.57 ms.

| Runtime | load + start | mean | p50 | p95 | p99 | max | over budget | vs V8 |
|---|---|---|---|---|---|---|---|---|
| V8 (Node), the WebView path | 0.05 s | 0.45 | 0.40 | 0.66 | 0.82 | 4.1 | 0% | 1× |
| Chicory, compiled to JVM bytecode | 1.8 s | 3.03 | 2.75 | 5.61 | 6.61 | 26.0 | 0% | 6.7× |
| Chasm, JVM | 1.1 s | 14.53 | 12.59 | 22.38 | 24.06 | 28.9 | 0.1% | 32× |
| Chasm, Kotlin/Native (linuxX64) | 0.14 s | 20.16 | 18.18 | 31.95 | 34.33 | 46.6 | 12.2% | 45× |
| Chicory, interpreter | 1.5 s | 116.41 | 106.0 | 170.2 | 181.6 | 210.9 | 100% | 259× |

Rows are the tics after the first 350 (JIT warm-up excluded). Freedoom Phase 1 gives the same
picture, a little heavier: V8 0.60, Chicory compiled 3.59, Chasm JVM 17.59 (4.4% over budget),
Chicory interpreter 138.6.

## What it says

- **Desktop can play it natively today.** Chicory's compiler runs a tic in 3 ms, a tenth of the
  budget, with the module compiled when it loads (1.5 s on this VM). It works on any `.wasm` that
  arrives at runtime, so a game from the feed stays content.
- **Interpreters are marginal.** Chasm averages 15 ms on the JVM and 20 ms as native code, with a
  long enough tail that 12% of native tics miss the budget on this machine. A device with a faster
  core than this cloud VM could hold 35 fps, a slower one would not; Chasm on Kotlin/Native is the
  only runtime here that reaches iOS. Chicory's interpreter is about 4× too slow.
- **Android is unmeasured.** There is no device or KVM emulator in this environment. Android runs
  ART rather than HotSpot, Chicory's runtime compiler emits JVM bytecode that ART cannot load (its
  Android path converts to DEX and is experimental), and compiling the wasm into the APK at build
  time would ship the GPL engine inside Amethyst. The WebView napplet, at V8 speed, remains the
  known-good path there.

Next step: run `commonMain` (Chasm) and Chicory's interpreter on a mid-range Android phone, for
example from an instrumented test, before choosing a runtime for a native card.
