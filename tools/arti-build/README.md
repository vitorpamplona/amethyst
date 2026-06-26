# Arti Android Build Tools

Custom-built [Arti](https://gitlab.torproject.org/tpo/core/arti) (Tor in Rust) native libraries
for Amethyst Android. This replaces the Guardian Project's `arti-mobile-ex` AAR with a minimal
JNI wrapper built directly from Arti source.

## Why custom build?

| | Guardian Project AAR | Custom build |
|---|---|---|
| **Size** | ~140MB | ~11MB |
| **16KB pages** | No | Yes (NDK 25+) |
| **Stop/restart** | Broken (state file lock) | Works (TorClient persists, only SOCKS proxy stops) |
| **Version** | Behind | Pinned to latest (currently 1.9.0) |

## Quick start

Pre-built `.so` files should be committed to `amethyst/src/main/jniLibs/`. You only need to
rebuild if you want to verify binaries, update the Arti version, or modify the JNI wrapper.

## Reproducible builds

The shipped `.so` is **built to be byte-for-byte reproducible** so anyone —
F-Droid, Zapstore, or an independent auditor — can rebuild it from this tag and
confirm the committed binary wasn't tampered with. Three pins make that hold:

| Source of non-determinism | Pinned by |
|---|---|
| `rustc` / cargo version | [`rust-toolchain.toml`](rust-toolchain.toml) (rustup auto-installs it) |
| transitive dependency versions | committed [`Cargo.lock`](Cargo.lock); builds run `cargo --locked` |
| absolute build paths baked into the binary | `--remap-path-prefix` in [`repro-env.sh`](repro-env.sh) |

`repro-env.sh` (sourced by both build scripts) also sets `CARGO_INCREMENTAL=0`
and a fixed `SOURCE_DATE_EPOCH` derived from the Arti tag. The size-optimized
release profile in `Cargo.toml` (`lto`, `codegen-units = 1`, `strip`,
`panic = "abort"`) is itself deterministic for a fixed toolchain.

### Verify the committed binary reproduces

```bash
# Build twice into different checkout paths and confirm identical bytes.
# (Path remapping is what lets two different directories produce the same .so.)
cp -r tools/arti-build /tmp/arti-a && (cd /tmp/arti-a && ./build-arti.sh --release)
cp -r tools/arti-build /tmp/arti-b && (cd /tmp/arti-b && ./build-arti.sh --release)
sha256sum /tmp/arti-{a,b}/../../amethyst/src/main/jniLibs/arm64-v8a/libarti_android.so
```

A clean run prints the same SHA-256 for both, and matches the committed
`amethyst/src/main/jniLibs/arm64-v8a/libarti_android.so`.

## Prerequisites

1. **Rust toolchain** — the exact version is pinned in `rust-toolchain.toml`;
   rustup installs it automatically. You only need rustup itself:
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   ```

2. **Android targets**
   ```bash
   rustup target add aarch64-linux-android x86_64-linux-android
   ```

3. **cargo-ndk**
   ```bash
   cargo install cargo-ndk
   ```

4. **Android NDK 25+** (required for 16KB page size support)
   ```bash
   # Via Android Studio: SDK Manager → SDK Tools → NDK (Side by side)
   # Or via command line:
   sdkmanager "ndk;27.0.12077973"

   # Set environment variable
   export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/27.0.12077973"
   ```

## Building

```bash
cd tools/arti-build

# Build for all targets (arm64 + x86_64)
./build-arti.sh

# Build arm64 only (for release APKs)
./build-arti.sh --release

# Clean rebuild from scratch
./build-arti.sh --clean
```

The script will:
1. Clone official Arti source from `gitlab.torproject.org`
2. Check out the version pinned in `ARTI_VERSION`
3. Copy the JNI wrapper into the source tree
4. Compile with `cargo-ndk` for each target architecture
5. Output `.so` files to `amethyst/src/main/jniLibs/{arm64-v8a,x86_64}/`
6. Verify JNI symbols are exported correctly

## Output

```
amethyst/src/main/jniLibs/
├── arm64-v8a/
│   └── libarti_android.so    (~5-6 MB)
└── x86_64/
    └── libarti_android.so    (~6-7 MB, emulator support)
```

## Verifying 16KB page alignment

Google Play requires 16KB page-aligned native libraries. Verify with:

```bash
readelf -l amethyst/src/main/jniLibs/arm64-v8a/libarti_android.so | grep LOAD
```

The first LOAD segment alignment should be `0x4000` (16384 bytes).

## Directory structure

```
tools/arti-build/
├── README.md            # This file
├── ARTI_VERSION         # Pinned Arti git tag (e.g., arti-v1.9.0)
├── rust-toolchain.toml  # Pinned rustc version + Android targets (reproducibility)
├── Cargo.toml           # Rust dependencies and build profile
├── Cargo.lock           # Pinned transitive dependency versions (reproducibility)
├── repro-env.sh         # Deterministic build env (path remapping, epoch) — sourced by both scripts
├── build-arti.sh        # Build script (Android targets, shipped in APK)
├── build-arti-host.sh   # Build script (host target, for JVM integration tests)
├── src/
│   └── lib.rs           # JNI bridge (Rust → Kotlin)
└── .arti-source/        # [gitignored] Cloned Arti repository
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
   ./build-arti.sh --clean          # clones the new tag + sets up the wrapper
   cp .arti-source/arti-android-wrapper/Cargo.lock ./Cargo.lock
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
cargo install cargo-ndk
```

### NDK not found
```bash
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/<version>"
```

### Rust targets not installed
```bash
rustup target add aarch64-linux-android x86_64-linux-android
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
