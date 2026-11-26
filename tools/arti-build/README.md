# Arti Android Build Tools

Custom-built [Arti](https://gitlab.torproject.org/tpo/core/arti) (Tor in Rust) native libraries
for Amethyst Android. This replaces the Guardian Project's `arti-mobile-ex` AAR with a minimal
JNI wrapper built directly from Arti source.

## Why custom build?

| | Guardian Project AAR | Custom build |
|---|---|---|
| **Size** | ~140MB | ~22MB for all four ABIs — 4-6MB in the APK a device installs |
| **16KB pages** | No | Yes on the 64-bit ABIs (rustc aligns them to 16 KiB) |
| **Stop/restart** | Broken (state file lock) | Works (TorClient persists, only SOCKS proxy stops) |
| **Version** | Behind | Pinned to latest (see [`ARTI_VERSION`](ARTI_VERSION)) |

## Quick start

Pre-built `.so` files should be committed to `amethyst/src/main/jniLibs/`. You only need to
rebuild if you want to verify binaries, update the Arti version, or modify the JNI wrapper.

## Reproducible builds

The shipped `.so` is **built to be reproducible** so anyone — F-Droid, Zapstore,
or an independent auditor — can rebuild it from this tag and confirm the
committed binary wasn't tampered with. **Five** things have to be fixed:

| Source of non-determinism | Pinned by |
|---|---|
| `rustc` / cargo version | [`rust-toolchain.toml`](rust-toolchain.toml) (rustup auto-installs it) |
| **Android NDK revision** | [`ANDROID_NDK_VERSION`](ANDROID_NDK_VERSION); `build-arti.sh` refuses to build with any other revision |
| transitive dependency versions | committed [`Cargo.lock`](Cargo.lock); builds run `cargo --locked` |
| absolute paths *embedded* in the binary | `--remap-path-prefix` in [`repro-env.sh`](repro-env.sh) |
| codegen/link **ordering** keyed on the real build path | **canonical build path** (`build-arti.sh` builds in `/tmp/amethyst-arti-build`) |

> **Why the NDK is pinned.** It is not just an SDK detail: the NDK supplies the
> clang that compiles Arti's C dependencies (`ring`, `zstd-sys`,
> `libsqlite3-sys`) and the `lld` that links the final `cdylib`, both of which
> stamp themselves into the binary's `.comment` section next to `rustc`'s own
> version. Swapping the NDK changes the bytes exactly like swapping `rustc`
> would. Before this was pinned the build picked the first directory matching
> `~/Android/Sdk/ndk/*/`, so the committed libraries were produced by r25b while
> this file told everyone to install r27 — two verifiers could both follow the
> README and get different, equally "correct" results. `build-arti.sh` now
> reads each candidate's `source.properties` and keeps looking until it finds
> the pinned revision, then re-checks the `.note.android.ident` stamp of every
> `.so` it produced.
>
> [`CARGO_NDK_VERSION`](CARGO_NDK_VERSION) records the `cargo-ndk` release the
> pinned output was verified with. `cargo-ndk` only wraps the NDK, so a mismatch
> is a warning rather than an error — but it is the next thing to check if your
> rebuild does not match.
>
> **The app build reads this pin too.** `amethyst/build.gradle.kts` sets
> `ndkVersion` from `ANDROID_NDK_VERSION`, because AGP runs the NDK's
> `llvm-strip` over every native library it packages — the toolchain that strips
> a library is as much an APK input as the one that compiled it. Unset,
> `ndkVersion` follows AGP's own default (r28 on AGP 9.4.0) and moves with every
> AGP bump. So bumping this file changes what packagers need installed, not only
> what rebuilders need: bump it, rebuild the `.so`, and commit both.
>
> **`libarti_android.so` skips that strip step.** The release profile here
> already strips it — no `.symtab`, no `.debug_*` — so `llvm-strip
> --strip-unneeded` has nothing to remove and only rebuilds `.comment`, the
> section carrying the rustc/clang/lld stamps (273 bytes change on arm64-v8a).
> `packaging.jniLibs.keepDebugSymbols` in `amethyst/build.gradle.kts` therefore
> excludes it, which costs no APK size and means the library inside a built APK
> is byte-identical to the one committed in `src/main/jniLibs/`: `unzip -p
> app.apk lib/arm64-v8a/libarti_android.so | sha256sum` can be checked straight
> against the file this script reproduces.

`repro-env.sh` (sourced by both build scripts) also sets `CARGO_INCREMENTAL=0`
and a fixed `SOURCE_DATE_EPOCH` derived from the Arti tag. The size-optimized
release profile in `Cargo.toml` (`lto`, `codegen-units = 1`, `strip`,
`panic = "abort"`) is itself deterministic for a fixed toolchain.

> **Why the canonical path matters.** Verified empirically: with the toolchain,
> lockfile, and path-remapping all in place, two builds at the **same** path are
> byte-for-byte identical, but two builds at **different** paths still differ —
> not in any embedded string (no path leaks into the binary) but in the *order*
> rustc lays out functions/data, which it derives from the real on-disk artifact
> paths. `--remap-path-prefix` only rewrites embedded strings, not that internal
> ordering. So `build-arti.sh` always compiles in a fixed location
> (`/tmp/amethyst-arti-build`, override with `ARTI_REPRO_DIR`); F-Droid and any
> verifier must use the **same** path to get matching bytes. This is the standard
> way Rust libraries are reproduced (F-Droid builds Rust at a fixed path too).

### Verify the committed binary reproduces

From `tools/arti-build/`, the helper builds twice from clean and diffs the output:

```bash
./verify-reproducible.sh            # all four shipped ABIs
./verify-reproducible.sh --release  # arm64-v8a only (faster)
./verify-reproducible.sh --target=armv7-linux-androideabi   # one ABI
```

It prints `✅ REPRODUCIBLE` when two clean builds produce identical bytes, then
reports whether that matches the committed `.so`. Both builds compile in the
canonical `/tmp/amethyst-arti-build`, so the result is independent of where the
repo is checked out.

## Prerequisites

1. **Rust toolchain** — the exact version is pinned in `rust-toolchain.toml`;
   rustup installs it automatically. You only need rustup itself:
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   ```

2. **Android targets**
   ```bash
   rustup target add aarch64-linux-android x86_64-linux-android \
     armv7-linux-androideabi i686-linux-android
   ```
   One per ABI the APK is split for. They are also listed in
   `rust-toolchain.toml`, so a first invocation of the build scripts installs
   whatever is missing.

3. **cargo-ndk** — the release the pinned output was verified with:
   ```bash
   cargo install cargo-ndk --version "$(cat CARGO_NDK_VERSION)" --locked
   ```

4. **Android NDK** — the exact revision in [`ANDROID_NDK_VERSION`](ANDROID_NDK_VERSION)
   (currently **30.0.16248370**, r30). Any other revision is refused: it would
   produce a `.so` that does not match the committed one.
   ```bash
   # Via Android Studio: SDK Manager → SDK Tools → NDK (Side by side)
   # Or via command line:
   sdkmanager "ndk;$(cat ANDROID_NDK_VERSION)"
   ```
   `build-arti.sh` finds it automatically under `$ANDROID_HOME/ndk/`,
   `$ANDROID_SDK_ROOT/ndk/`, `~/Android/Sdk/ndk/`, `~/Library/Android/sdk/ndk/`
   or `/usr/local/lib/android/sdk/ndk/`. `ANDROID_NDK_HOME` and
   `ANDROID_NDK_ROOT` are tried first when set, but they are only hints: every
   candidate is checked against its own `source.properties`, and one at the
   wrong revision is reported and skipped rather than failing the build. CI
   images (GitHub runners among them) export both at a bundled NDK that is not
   ours.

## Building

```bash
cd tools/arti-build

# Build every shipped ABI (arm64-v8a, x86_64, armeabi-v7a, x86)
./build-arti.sh

# Build arm64-v8a only — a fast local loop, NOT enough to cut a release:
# the APK splits ship four ABIs and each one needs its own libarti_android.so
./build-arti.sh --release

# Build a single ABI without touching the other committed .so files
./build-arti.sh --target=armv7-linux-androideabi

# Print the jniLibs ABI dirs a given invocation would write, then exit
./build-arti.sh --print-abis --release      # -> arm64-v8a

# Clean rebuild from scratch
./build-arti.sh --clean
```

The script will:
1. Clone official Arti source from `gitlab.torproject.org`
2. Check out the version pinned in `ARTI_VERSION`
3. Copy the JNI wrapper into the source tree
4. Compile with `cargo-ndk` for each target architecture
5. Output `.so` files to `amethyst/src/main/jniLibs/{arm64-v8a,x86_64,armeabi-v7a,x86}/`
6. Verify JNI symbols are exported correctly

## Output

```
amethyst/src/main/jniLibs/
├── arm64-v8a/
│   └── libarti_android.so    (~5-6 MB)
├── x86_64/
│   └── libarti_android.so    (~6-7 MB, emulator support)
├── armeabi-v7a/
│   └── libarti_android.so    (~3-4 MB, 32-bit ARM devices)
└── x86/
    └── libarti_android.so    (~6 MB, 32-bit x86 images)
```

**One per ABI split, always.** `amethyst/build.gradle.kts` splits the APK four
ways and `create-release.yml` publishes all four, so an ABI missing from this
tree ships an APK that is complete except for Arti: the dependencies' native
libraries are all there (secp256k1's JNI, for one, ships every ABI), the app
installs and runs, and Tor alone is dead for that install. `System.loadLibrary`
throws, `TorManager`'s status flow swallows the error, and Tor reports Off
forever. With the defaults (`TorType.INTERNAL`, DM relays and unknown relays
routed over Tor) those relays then dial a SOCKS port nothing listens on and
never connect — so the ABI loses its DMs too, not just Tor.

The ABI list therefore lives in three places that must agree: `splits.abi` in
`amethyst/build.gradle.kts`, `targets` in `rust-toolchain.toml`, and `TARGETS`
in `build-arti.sh`. (`verify-reproducible.sh` has no copy of its own — it asks
`build-arti.sh --print-abis`, so it can never hash a different set than the one
it just rebuilt.) The `verifyNativeAbis` Gradle task, wired into `preBuild`, fails
the build when an ABI split is missing any committed native library
(`libarti_android.so`, `libzxingcpp_android.so`) **or** has one that is
not an ELF of that architecture — a truncated file or arm64's library copied
into `x86/` loads as nothing on device, exactly like a missing one, and unlike a
missing one it looks fine in `git status`.

## Verifying 16KB page alignment

Google Play requires 16KB page-aligned native libraries. Verify with:

```bash
readelf -l amethyst/src/main/jniLibs/arm64-v8a/libarti_android.so | grep LOAD
```

The first LOAD segment alignment should be `0x4000` (16384 bytes). This comes
from rustc's Android target spec (`max-page-size=16384`), not from the NDK, so
it holds for every NDK revision we could build with.

The requirement is a 64-bit one — 16 KB pages exist only on 64-bit Android
devices — so it applies to `arm64-v8a` and `x86_64`. The 32-bit libraries
(`armeabi-v7a`, `x86`) link at the 4 KB alignment their targets specify
(`0x1000`), which is correct for them and not a regression to fix.

## Checking which toolchain built a `.so`

The shipped binaries say so themselves — useful when a rebuild does not match, or
when auditing a `.so` you did not build:

```bash
# NDK release name + build number (the last component of the pinned revision)
readelf -p .note.android.ident amethyst/src/main/jniLibs/arm64-v8a/libarti_android.so

# clang / lld (from the NDK) and rustc versions
readelf -p .comment amethyst/src/main/jniLibs/arm64-v8a/libarti_android.so
```

For the pinned toolchain the first command prints `r30` and build `16248370`.
The second prints the `rustc` version from `rust-toolchain.toml`, the NDK's
`clang` and `LLD`, and a second, different clang string that comes from the
prebuilt runtime objects the NDK links in — two clang lines there is normal.
`build-arti.sh` runs the first check itself after every build, using the NDK's
own `llvm-readelf` so it works the same on macOS.

## Directory structure

```
tools/arti-build/
├── README.md            # This file
├── ARTI_VERSION         # Pinned Arti git tag (e.g., arti-v2.6.0)
├── ANDROID_NDK_VERSION  # Pinned NDK revision — enforced by build-arti.sh (reproducibility)
├── CARGO_NDK_VERSION    # cargo-ndk release the pinned output was verified with
├── rust-toolchain.toml  # Pinned rustc version + Android targets (reproducibility)
├── Cargo.toml           # Rust dependencies and build profile
├── Cargo.lock           # Pinned transitive dependency versions (reproducibility)
├── repro-env.sh         # Deterministic build env (path remapping, epoch) — sourced by both scripts
├── build-arti.sh        # Build script (Android targets, shipped in APK)
├── build-arti-host.sh   # Build script (host target, for JVM integration tests)
├── build-arti-ios.sh    # Build script (iOS static libraries, C ABI) — see "iOS"
├── IOS_XCODE_VERSION    # Xcode the iOS archives were measured with (warn-only)
├── verify-reproducible.sh # Builds twice + diffs to prove byte-for-byte reproducibility
├── include/
│   └── arti_ffi.h       # C header of the iOS ABI (hand-written, checked by build-arti-ios.sh)
├── out/                 # build-arti-ios.sh output, git-ignored
└── src/
    ├── lib.rs           # JNI bridge (Rust → Kotlin)
    └── ffi.rs           # C ABI for iOS, only with `--features ffi`

# The Arti source is cloned into the canonical build path
# (/tmp/amethyst-arti-build/.arti-source), not under this dir — see
# "Reproducible builds" for why the build location is fixed.
```

## Updating Arti version

1. Check available versions:
   ```bash
   git ls-remote --tags https://gitlab.torproject.org/tpo/core/arti.git | grep 'arti-v' | tail -10
   ```

2. Update the version file:
   ```bash
   echo "arti-v1.10.0" > ARTI_VERSION
   ```

3. Update crate versions in `Cargo.toml` to match the new release.
   Check the crate versions at:
   ```
   https://gitlab.torproject.org/tpo/core/arti/-/raw/arti-v1.10.0/crates/arti-client/Cargo.toml
   ```

4. Regenerate the committed lockfile so the new versions are pinned (builds run
   `--locked` and will fail until this is refreshed):
   ```bash
   ./build-arti.sh --regen-lock     # re-resolves + rewrites ./Cargo.lock, no compile
   ```
   If you also bump the Rust toolchain, edit `channel` in `rust-toolchain.toml`.

5. Rebuild, then re-verify reproducibility (see "Reproducible builds" above) and
   commit the regenerated `.so` files **together with** `Cargo.lock` /
   `rust-toolchain.toml`:
   ```bash
   ./build-arti.sh --clean
   ```

## Architecture: JNI bridge

The Rust wrapper (`src/lib.rs`) exposes these JNI functions to Kotlin:

| JNI function | Kotlin | Purpose |
|---|---|---|
| `initialize(dataDir)` | `ArtiNative.initialize()` | Create TorClient, bootstrap Tor network |
| `startSocksProxy(port)` | `ArtiNative.startSocksProxy()` | Bind SOCKS5 listener on localhost |
| `stopSocksProxy()` | `ArtiNative.stopSocksProxy()` | Abort listener, release port |
| `getVersion()` | `ArtiNative.getVersion()` | Return Arti version string |
| `setLogCallback(cb)` | `ArtiNative.setLogCallback()` | Register log callback |

### Key design decisions

- **TorClient is created once** via `initialize()` and persists for the app's lifetime.
  Its state file lock is tied to the object's lifetime and released only on GC/process exit.
- **`stopSocksProxy()` only stops the TCP listener** — it does NOT destroy the TorClient.
  This allows clean stop/start cycles without state file lock conflicts.
- **SOCKS5 is implemented in Rust** using `tokio::net::TcpListener`, not delegated to Arti's
  built-in proxy. This gives us full control over the listener lifecycle.
- **Bidirectional forwarding** uses `tokio::io::copy` with `tokio::select!` for efficiency.

## iOS: C ABI and static libraries

iOS runs the same crate, `lib.rs`, `Cargo.lock`, toolchain and Arti clone as Android
(`commons/plans/2026-11-25-ios-tor.md`, B12a). Kotlin/Native cannot load a JNI library, so
`src/ffi.rs` adds a plain C face next to the JNI one:

| C function (`include/arti_ffi.h`) | Kotlin (`iosApp/src/iosArtiMain/…/ios/tor/ArtiNative.kt`) | Same as JNI |
|---|---|---|
| `arti_version()` | `ArtiNative.version` | `getVersion` |
| `arti_set_log_callback(cb)` | `setLogCallback { line -> }` | `setLogCallback` (also forwards Rust panic messages) |
| `arti_initialize(data_dir)` | `initialize(dir)` | `initialize` |
| `arti_is_bootstrapped()` | `isBootstrapped()` | `isBootstrapped` |
| `arti_bootstrap_progress_permille()` | `bootstrapProgressPermille()` | `bootstrapProgressPermille` |
| `arti_start_socks_proxy(port)` | `startSocksProxy(port)` | `startSocksProxy`, but waits for a replaced listener to close, so a restart on the same port binds |
| `arti_stop_socks_proxy()` | `stopSocksProxy()` | `stopSocksProxy`, same wait |
| `arti_destroy()` | `destroy()` | `destroy` |
| `arti_socks_accepted_count()` | `socksAcceptedCount()` | none: a test hook proving traffic used the proxy |

**Android's `.so` does not change.** The C face is behind the `ffi` Cargo feature, which only
`build-arti-ios.sh` enables. `lib.rs` gained two things, placed so no existing line moves (rustc
bakes the line and column of every `unwrap()` into the binary): `mod ffi;` at the very end, and
the log hook on the line `send_log_to_java` already had. That is also why `ffi.rs` repeats the
JNI bodies instead of sharing helpers with them; fold the two the next time the `.so` is rebuilt
anyway. Checked on macOS: all four ABIs built from the unchanged sources and from these hash the
same, and `verify-reproducible.sh` passes on the changed ones (results in the plan's B12a).

> **Host OS matters for the `.so` bytes.** The committed libraries were built on Linux: their
> `.comment` names the Linux NDK's LLD. A macOS rebuild with the same pinned NDK reproduces
> itself byte for byte but not the committed file (arm64-v8a: `9e0b1a9f…` on macOS against the
> committed `069a0cd5…`), because the macOS NDK prebuilts are different binaries (the darwin LLD,
> and an extra clang stamp). Verify the committed `.so` on Linux.

### Building

```bash
cd tools/arti-build
./build-arti-ios.sh                              # device + simulator
./build-arti-ios.sh --target=aarch64-apple-ios-sim
```

You rarely run it by hand: `:iosApp:buildArtiIos` runs it before anything that links Arti (the
app, from Xcode's pre-build step, and the simulator tests) and is up to date while its inputs
(`src/`, `include/`, `Cargo.*`, `ARTI_VERSION`, the toolchain and the script) are unchanged. From
Xcode it builds only the slice of the SDK being built (`PLATFORM_NAME`); elsewhere both. It needs
macOS, Xcode and rustup; the two iOS targets are added on first use. The first build takes about
2.5 minutes for both targets, then about 35 seconds per target after a change to `src/`.

Output goes to `out/ios/`, which git ignores:

```
out/ios/
├── aarch64-apple-ios/libarti.a       # device, ~18 MB unlinked
├── aarch64-apple-ios-sim/libarti.a   # simulator, ~18 MB unlinked
├── Arti.xcframework                  # both slices + header (only when both are built; unused by the app)
├── include/arti_ffi.h
└── BUILD_INFO                        # rustc, Xcode, sha256 per slice, native-static-libs
```

Why not committed, unlike Android's `.so`: about 36 MB per Arti bump (both slices, unlinked
objects), against about 5 MB linked. Committing them would want Git LFS or a release asset
checked by SHA-256; building them where the iOS app is built needs only rustup. That is a
maintainer call, recorded in the plan.

How the iOS build finds them:

- **Compiling** `:iosApp` needs only `include/arti_ffi.h` (committed): the cinterop
  (`iosApp/src/nativeInterop/cinterop/arti.def`) names the header, not the library. The cinterop
  and `src/iosArtiMain` are set up on a macOS host only (cinterop needs the Apple SDK).
- **Test executables** link `out/ios/<triple>/libarti.a` with `-larti -liconv`
  (`iosApp/build.gradle.kts`); the link task depends on `buildArtiIos` and takes the archive as
  an input, so a rebuilt archive relinks.
- **The app** (B12d): `AmethystKit` is a static framework, so Kotlin/Native leaves its `_arti_*`
  references undefined and Xcode resolves them when it links the app:
  `iosApp/xcode/project.yml` sets `LIBRARY_SEARCH_PATHS` to `out/ios/<triple>` per SDK and adds
  `-larti -liconv`. Its pre-build step runs `:iosApp:embedAndSignAppleFrameworkForXcode`, which
  depends on `buildArtiIos`, so building the app in Xcode (or with `xcodebuild`) builds Arti first.

Differences from the Android build:

- `panic = "unwind"` (`CARGO_PROFILE_RELEASE_PANIC`), so the `catch_unwind` around every C
  export is live and a panic in an Arti task ends that task, not the app. Android keeps "abort".
  Measured cost: linked into a stripped device binary Arti adds 5.01 MB with unwind, 4.21 MB
  with abort.
- `IPHONEOS_DEPLOYMENT_TARGET=16.0`, matching `iosApp/xcode/project.yml`.
- `fs-mistrust` is off (`dangerously_trust_everyone`), as on every non-Android target: the iOS
  app container is OS-sandboxed, and the simulator's container sits under the developer's home.
- Reproducibility is not claimed yet. Xcode's clang compiles `ring`, `zstd-sys` and
  `libsqlite3-sys`, so it is an input like the NDK; `IOS_XCODE_VERSION` records the Xcode the
  archives were measured with and the script only warns on a mismatch. One data point: two builds
  from fresh Arti clones at the canonical path, on the same Mac, gave the same archive sha256s.
- Xcode's `nm` cannot read the `compiler_builtins` members (they carry bitcode from Rust's
  newer LLVM) and exits 1; the script ignores that. The linker is unaffected.

### Tests

```bash
./gradlew :iosApp:iosSimulatorArm64Test                               # lifecycle, no network needed
./gradlew :iosApp:iosSimulatorArm64Test -Pamethyst.arti.network=true  # + the Tor routing spike
```

`ArtiNativeLifecycleTest` drives initialize, SOCKS (a raw SOCKS5 greeting), restart, stop,
destroy and re-initialize on the simulator. `TorNetworkSpikeTest` bootstraps Tor and checks what
NSURLSession sends through the SOCKS port; it only reads (check.torproject.org, one relay REQ up
to EOSE, an onion site) and is excluded unless the property is set.

## Cargo.toml features

Default features are disabled (`default-features = false`) to minimize binary size.

| Feature | Purpose | Why included |
|---|---|---|
| `tokio` | Async runtime | Required by our SOCKS proxy |
| `rustls` | TLS via pure Rust | No OpenSSL dependency, smaller binary |
| `compression` | zstd/deflate relay traffic | Reduces bandwidth on Tor circuits |
| `onion-service-client` | Access .onion addresses | Amethyst routes .onion relay connections through Tor |
| `static-sqlite` | Bundled SQLite | Android native code can't use system SQLite |

**Not included:**

| Feature | Why excluded |
|---|---|
| `native-tls` | Using `rustls` instead (smaller, no system dependency) |
| `bridge-client` | Amethyst doesn't expose bridge configuration in UI yet. Add back if needed. |
| `pt-client` | Pluggable transports — same reason as bridges |
| `onion-service-service` | We only connect to .onion, we don't host them |

### Release profile

```toml
[profile.release]
opt-level = "z"       # Optimize for size
lto = true            # Link-time optimization
codegen-units = 1     # Single codegen unit (smaller binary)
strip = true          # Strip debug symbols
panic = "abort"       # No unwinding (smaller binary)
```

## Troubleshooting

### `cargo-ndk` not found
```bash
cargo install cargo-ndk --version "$(cat CARGO_NDK_VERSION)" --locked
```

### NDK not found, or "wrong revision"
Install the pinned revision — the build refuses any other, and the error lists
every directory it looked at and what it found there:
```bash
sdkmanager "ndk;$(cat ANDROID_NDK_VERSION)"
```
Point `ANDROID_NDK_HOME` at it only if it lives outside the standard SDK
layouts; an `ANDROID_NDK_HOME` left over from another project is skipped, not
fatal.

### Rust targets not installed
```bash
rustup target add aarch64-linux-android x86_64-linux-android \
  armv7-linux-androideabi i686-linux-android
```

### Build fails with dependency errors
Try a clean build:
```bash
./build-arti.sh --clean
```

### JNI symbols missing after build
The build script verifies symbols automatically. If verification fails, check that
`src/lib.rs` function names match the Kotlin package path:
```
Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_<methodName>
```
