#!/usr/bin/env bash
#
# Build the Arti wrapper as an iOS static library with its C ABI (src/ffi.rs, `--features ffi`).
#
# Same crate, lib.rs, Cargo.lock, Rust toolchain and canonical build path as the Android build
# (build-arti.sh), so both platforms run one engine. The Android .so does not change: the C ABI
# is behind the `ffi` feature, which only this script turns on (README.md -> "iOS").
#
# Output, not committed (tools/arti-build/.gitignore):
#   out/ios/aarch64-apple-ios/libarti.a        device
#   out/ios/aarch64-apple-ios-sim/libarti.a    Apple-silicon simulator
#   out/ios/Arti.xcframework                   both slices + the header, for an Xcode target
#   out/ios/include/arti_ffi.h                 copy of include/arti_ffi.h
#   out/ios/BUILD_INFO                         toolchain, Xcode and sha256 of each archive
#
# :iosApp's buildArtiIos task runs this script, and its test executables link the archives
# (iosApp/build.gradle.kts). Run it by hand only to rebuild outside Gradle.
#
# Prerequisites: macOS with Xcode, rustup (rust-toolchain.toml pins 1.98.1; the two iOS targets
# are added here when missing), git.
#
# Usage:
#   ./build-arti-ios.sh                    # device + simulator
#   ./build-arti-ios.sh --target=<triple>  # one of them, repeatable
#   ./build-arti-ios.sh --clean            # drop the iOS wrapper's cargo target dir first
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARTI_VERSION=$(tr -d '[:space:]' < "$SCRIPT_DIR/ARTI_VERSION")
# The Xcode the committed notes were measured with. Xcode's clang compiles ring, zstd-sys and
# libsqlite3-sys, so it is an input of the archive the way the NDK is for Android. A mismatch only
# warns: iOS output is not yet claimed to be reproducible.
XCODE_PIN=$(tr -d '[:space:]' < "$SCRIPT_DIR/IOS_XCODE_VERSION")
# Keep in step with deploymentTarget in iosApp/xcode/project.yml.
export IPHONEOS_DEPLOYMENT_TARGET=16.0

# Same canonical path as build-arti.sh (see there, and README.md -> "Reproducible builds").
ARTI_BUILD_ROOT="${ARTI_REPRO_DIR:-/tmp/amethyst-arti-build}"
ARTI_SOURCE_DIR="$ARTI_BUILD_ROOT/.arti-source"
WRAPPER_DIR="$ARTI_SOURCE_DIR/arti-ios-wrapper"
OUT_DIR="$SCRIPT_DIR/out/ios"
HEADER="$SCRIPT_DIR/include/arti_ffi.h"

TARGETS=("aarch64-apple-ios" "aarch64-apple-ios-sim")
SELECTED_TARGETS=""
CLEAN=false
for arg in "$@"; do
    case $arg in
        --target=*) SELECTED_TARGETS="$SELECTED_TARGETS ${arg#--target=}" ;;
        --clean) CLEAN=true ;;
        --help) echo "Usage: $0 [--target=<triple>]... [--clean]"; exit 0 ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done
if [ -n "$SELECTED_TARGETS" ]; then
    # shellcheck disable=SC2206 # target triples never contain spaces
    TARGETS=($SELECTED_TARGETS)
fi

say() { echo "→ $*"; }
die() { echo "✗ $*" >&2; exit 1; }

# ----------------------------------------------------------------------------
# Prerequisites
# ----------------------------------------------------------------------------

[ "$(uname -s)" = "Darwin" ] || die "iOS archives need macOS with Xcode"
command -v xcrun >/dev/null 2>&1 || die "xcrun not found: install Xcode"
command -v git >/dev/null 2>&1 || die "git not found"

# Gradle and Xcode run this with a minimal PATH. rustup's proxies live in ~/.cargo/bin for a
# rustup-init install and in Homebrew's keg for `brew install rustup` (not linked into bin).
for dir in "$HOME/.cargo/bin" /opt/homebrew/opt/rustup/bin /usr/local/opt/rustup/bin; do
    if [ -x "$dir/cargo" ] && "$dir/cargo" --version >/dev/null 2>&1; then
        export PATH="$dir:$PATH"
        break
    fi
done
command -v cargo >/dev/null 2>&1 && cargo --version >/dev/null 2>&1 || die "cargo not found (install rustup)"

XCODE_FOUND=$(xcodebuild -version 2>/dev/null | awk '/^Xcode/{v=$2} /^Build version/{b=$3} END{print v"("b")"}')
if [ "$XCODE_FOUND" != "$XCODE_PIN" ]; then
    say "Xcode $XCODE_FOUND, notes were measured with $XCODE_PIN (IOS_XCODE_VERSION); continuing"
fi

# rustup resolves the toolchain from rust-toolchain.toml in the current directory.
cd "$SCRIPT_DIR"
for target in "${TARGETS[@]}"; do
    if ! rustup target list --installed | grep -qx "$target"; then
        say "Adding Rust target $target"
        rustup target add "$target"
    fi
done
RUSTC_VERSION=$(rustc --version)

# ----------------------------------------------------------------------------
# Source: the same Arti clone build-arti.sh uses
# ----------------------------------------------------------------------------

mkdir -p "$ARTI_BUILD_ROOT"
if [ ! -d "$ARTI_SOURCE_DIR/.git" ]; then
    say "Cloning Arti $ARTI_VERSION into $ARTI_SOURCE_DIR"
    git clone --depth 1 --branch "$ARTI_VERSION" https://gitlab.torproject.org/tpo/core/arti.git "$ARTI_SOURCE_DIR"
elif [ "$(git -C "$ARTI_SOURCE_DIR" describe --tags --exact-match 2>/dev/null || true)" != "$ARTI_VERSION" ]; then
    say "Moving the Arti clone to $ARTI_VERSION"
    git -C "$ARTI_SOURCE_DIR" fetch --depth 1 origin tag "$ARTI_VERSION"
    git -C "$ARTI_SOURCE_DIR" checkout "$ARTI_VERSION"
fi

# Its own wrapper dir (and so its own cargo target dir) next to build-arti.sh's
# arti-android-wrapper, so an iOS build never touches the Android one's state.
mkdir -p "$WRAPPER_DIR/src"
[ "$CLEAN" = true ] && rm -rf "$WRAPPER_DIR/target"

# Copy only what changed: cargo rebuilds a path crate when a source file's mtime moves, and the
# LTO link of this crate is ~35 s per target.
stage() {
    cmp -s "$1" "$2" || cp "$1" "$2"
}
manifest="$(mktemp)"
{
    cat "$SCRIPT_DIR/Cargo.toml"
    cat << 'PATCH'

[patch.crates-io]
arti-client = { path = "../crates/arti-client" }
tor-rtcompat = { path = "../crates/tor-rtcompat" }
PATCH
} > "$manifest"
stage "$manifest" "$WRAPPER_DIR/Cargo.toml"
rm -f "$manifest"
for f in "$WRAPPER_DIR"/src/*.rs; do
    [ -e "$f" ] && [ ! -e "$SCRIPT_DIR/src/$(basename "$f")" ] && rm -f "$f"
done
for f in "$SCRIPT_DIR"/src/*.rs; do
    stage "$f" "$WRAPPER_DIR/src/$(basename "$f")"
done
stage "$SCRIPT_DIR/Cargo.lock" "$WRAPPER_DIR/Cargo.lock"

# shellcheck source=repro-env.sh
source "$SCRIPT_DIR/repro-env.sh"

# ----------------------------------------------------------------------------
# Build
# ----------------------------------------------------------------------------

# `panic = "unwind"` for iOS only (the profile's "abort" stays for Android): the catch_unwind
# around every export in ffi.rs then really catches, and a panic inside an Arti task ends that
# task instead of the app.
export CARGO_PROFILE_RELEASE_PANIC=unwind

mkdir -p "$OUT_DIR/include"
cp "$HEADER" "$OUT_DIR/include/arti_ffi.h"

# The functions the header declares; each archive must export every one.
DECLARED=$(sed -n 's/.*[ *]\(arti_[a-z_]*\)(.*/\1/p' "$HEADER" | sort -u)
[ -n "$DECLARED" ] || die "no arti_* declarations found in $HEADER"

for target in "${TARGETS[@]}"; do
    case "$target" in
        aarch64-apple-ios|aarch64-apple-ios-sim) ;;
        *) die "Unknown iOS target $target" ;;
    esac
    say "Building $target"
    # --print native-static-libs: the system libraries the archive needs at the final link. The
    # test executables in iosApp/build.gradle.kts pass the same list.
    cargo rustc --release --locked --features ffi \
        --manifest-path "$WRAPPER_DIR/Cargo.toml" \
        --target "$target" --lib --crate-type staticlib \
        -- --print native-static-libs 2> >(tee "$OUT_DIR/$target.cargo.log" >&2)

    built="$WRAPPER_DIR/target/$target/release/libarti_android.a"
    [ -f "$built" ] || die "$built not produced"
    mkdir -p "$OUT_DIR/$target"
    cp "$built" "$OUT_DIR/$target/libarti.a"

    # Xcode's nm exits 1 on the compiler_builtins members: they carry bitcode from Rust's newer
    # LLVM ("Unknown attribute kind"). The linker ignores that bitcode; read the rest anyway.
    exported=$({ nm -gU "$OUT_DIR/$target/libarti.a" 2>/dev/null || true; } | awk '$2 == "T" {print $3}' | sed -n 's/^_\(arti_.*\)/\1/p' | sort -u)
    missing=$(comm -23 <(echo "$DECLARED") <(echo "$exported"))
    [ -z "$missing" ] || die "$target: declared in arti_ffi.h but not exported: $missing"
    echo "✓ $target: $(echo "$DECLARED" | wc -l | tr -d ' ') C exports present, $(du -h "$OUT_DIR/$target/libarti.a" | cut -f1)"
done

# Xcode consumes an xcframework; it needs both slices.
if [ -f "$OUT_DIR/aarch64-apple-ios/libarti.a" ] && [ -f "$OUT_DIR/aarch64-apple-ios-sim/libarti.a" ]; then
    rm -rf "$OUT_DIR/Arti.xcframework"
    xcodebuild -create-xcframework \
        -library "$OUT_DIR/aarch64-apple-ios/libarti.a" -headers "$OUT_DIR/include" \
        -library "$OUT_DIR/aarch64-apple-ios-sim/libarti.a" -headers "$OUT_DIR/include" \
        -output "$OUT_DIR/Arti.xcframework" >/dev/null
    echo "✓ $OUT_DIR/Arti.xcframework"
fi

{
    echo "arti: $ARTI_VERSION"
    echo "rustc: $RUSTC_VERSION"
    echo "xcode: $XCODE_FOUND"
    echo "iphoneos_deployment_target: $IPHONEOS_DEPLOYMENT_TARGET"
    echo "panic: unwind"
    for target in aarch64-apple-ios aarch64-apple-ios-sim; do
        [ -f "$OUT_DIR/$target/libarti.a" ] && echo "$target: $(shasum -a 256 "$OUT_DIR/$target/libarti.a" | cut -d' ' -f1)"
    done
    sed -n 's/.*native-static-libs: /native-static-libs: /p' "$OUT_DIR"/*.cargo.log 2>/dev/null | sort -u
} > "$OUT_DIR/BUILD_INFO"
cat "$OUT_DIR/BUILD_INFO"
