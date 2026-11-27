#!/usr/bin/env bash
#
# Build the Arti wrapper as an iOS static library with its C ABI (src/ffi.rs, `--features ffi`).
#
# Same crate, lib.rs, Cargo.lock, Rust toolchain and canonical build path as the Android build
# (build-arti.sh), so both platforms run one engine. The Android .so does not change: the C ABI
# is behind the `ffi` feature, which only this script turns on (README.md -> "iOS").
#
# Output, COMMITTED, like Android's jniLibs (README.md -> "iOS"):
#   iosApp/libs/arti/aarch64-apple-ios/libarti.a        device
#   iosApp/libs/arti/aarch64-apple-ios-sim/libarti.a    Apple-silicon simulator
#   iosApp/libs/arti/BUILD_INFO                         toolchain, Xcode, sha256 of each archive,
#                                                       digest of the inputs it was built from
# Debug info is stripped (strip -S), which takes each archive from ~18.9 MB to ~12.7 MB.
#
# The app and test builds only read these files; nothing runs Rust there. Run this script when
# you change src/, Cargo.*, ARTI_VERSION, a pin or this script, then verify-reproducible-ios.sh,
# and commit the archives with the change, as you would the Android .so.
# :iosApp:verifyArtiIosArchives fails while BUILD_INFO's input digest does not match the sources.
#
# Everything is built in a staging directory and moved into place only after every check passed,
# so a failed run leaves the committed files as they were.
#
# Reproducible: the same bytes from any checkout, CARGO_HOME, Xcode install path or calling
# environment, given the pins (Rust in rust-toolchain.toml, Xcode in IOS_XCODE_VERSION,
# Cargo.lock, ARTI_VERSION); verify-reproducible-ios.sh checks.
#
# Prerequisites: macOS with the pinned Xcode, rustup (rust-toolchain.toml pins the Rust version;
# the two iOS targets are added here when missing), git.
#
# Usage:
#   ./build-arti-ios.sh                        # device + simulator
#   ./build-arti-ios.sh --target=<triple>      # one of them, repeatable
#   ./build-arti-ios.sh --clean                # drop the iOS wrapper's cargo target dir first
#   ./build-arti-ios.sh --any-xcode            # build with another Xcode than IOS_XCODE_VERSION
#                                              # (the bytes will not match the committed ones)
#   ./build-arti-ios.sh --print-inputs-digest  # print the input digest BUILD_INFO records, exit
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

say() { echo "→ $*"; }
die() { echo "✗ $*" >&2; exit 1; }

# The digest of everything that decides the archives' bytes, recorded in BUILD_INFO
# (`<triple>.inputs`). :iosApp:verifyArtiIosArchives (iosApp/build.gradle.kts) computes the same
# digest and fails when the committed archives were built from other sources. Both sides must
# stay identical, so the definition is spelled out (README.md -> "iOS", same text):
#   files:  ARTI_VERSION Cargo.lock Cargo.toml IOS_XCODE_VERSION build-arti-ios.sh
#           include/arti_ffi.h repro-env.sh rust-toolchain.toml, plus every src/*.rs
#           (directly in src/), paths relative to tools/arti-build
#   order:  sorted by path, byte order
#   digest: sha256 of the concatenation of one line per file,
#           "<lowercase hex sha256 of the file>  <path>\n" (shasum -a 256's output)
inputs_digest() {
    (
        cd "$SCRIPT_DIR"
        {
            printf '%s\n' ARTI_VERSION Cargo.lock Cargo.toml IOS_XCODE_VERSION build-arti-ios.sh \
                include/arti_ffi.h repro-env.sh rust-toolchain.toml
            for f in src/*.rs; do [ -f "$f" ] && printf '%s\n' "$f"; done
        } | LC_ALL=C sort | while IFS= read -r f; do
            [ -f "$f" ] || { echo "missing input $f" >&2; exit 1; }
            /usr/bin/shasum -a 256 "$f"
        done | /usr/bin/shasum -a 256 | cut -d' ' -f1
    )
}

ARTI_VERSION=$(tr -d '[:space:]' < "$SCRIPT_DIR/ARTI_VERSION")
# The pinned Xcode. Its clang compiles ring, zstd-sys and libsqlite3-sys, its SDK is linked
# against and its strip post-processes the archives, so it is an input the way the NDK is for
# Android. Another Xcode is refused unless --any-xcode says so.
XCODE_PIN=$(tr -d '[:space:]' < "$SCRIPT_DIR/IOS_XCODE_VERSION")
RUST_CHANNEL=$(sed -n 's/^channel *= *"\([^"]*\)".*/\1/p' "$SCRIPT_DIR/rust-toolchain.toml")
[ -n "$RUST_CHANNEL" ] || die "no channel in rust-toolchain.toml"
# Keep in step with deploymentTarget in iosApp/xcode/project.yml.
DEPLOYMENT_TARGET=16.0
# The system libraries the archive needs at the final link (rustc's native-static-libs). The test
# executables in iosApp/build.gradle.kts and iosApp/xcode/project.yml pass the same list.
NATIVE_LIBS="-liconv -lSystem -lc -lm"

# Same canonical path as build-arti.sh (see there, and README.md -> "Reproducible builds").
ARTI_BUILD_ROOT="${ARTI_REPRO_DIR:-/tmp/amethyst-arti-build}"
ARTI_SOURCE_DIR="$ARTI_BUILD_ROOT/.arti-source"
WRAPPER_DIR="$ARTI_SOURCE_DIR/arti-ios-wrapper"
# The CARGO_HOME cargo sees: a directory of our own whose registry/ and git/ are the real
# CARGO_HOME's, so the downloads are shared but no config.toml of the user's reaches the build.
SHADOW_CARGO_HOME="$ARTI_BUILD_ROOT/cargo-home"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# verify-reproducible-ios.sh points its builds elsewhere; everyone else writes the committed
# location.
OUT_DIR="${ARTI_IOS_OUT_DIR:-$PROJECT_ROOT/iosApp/libs/arti}"
LOG_DIR="$SCRIPT_DIR/out/ios-logs"
HEADER="$SCRIPT_DIR/include/arti_ffi.h"

ALL_TARGETS="aarch64-apple-ios aarch64-apple-ios-sim"
TARGETS=($ALL_TARGETS)
SELECTED_TARGETS=""
CLEAN=false
ANY_XCODE=false
for arg in "$@"; do
    case $arg in
        --target=*) SELECTED_TARGETS="$SELECTED_TARGETS ${arg#--target=}" ;;
        --clean) CLEAN=true ;;
        --any-xcode) ANY_XCODE=true ;;
        --print-inputs-digest) inputs_digest; exit 0 ;;
        --help) sed -n '2,37p' "$0"; exit 0 ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done
if [ -n "$SELECTED_TARGETS" ]; then
    # shellcheck disable=SC2206 # target triples never contain spaces
    TARGETS=($SELECTED_TARGETS)
fi
for target in "${TARGETS[@]}"; do
    case " $ALL_TARGETS " in
        *" $target "*) ;;
        *) die "Unknown iOS target $target" ;;
    esac
done

# ----------------------------------------------------------------------------
# A clean environment for every tool that builds
# ----------------------------------------------------------------------------

[ "$(uname -s)" = "Darwin" ] || die "iOS archives need macOS with Xcode"

# Gradle and Xcode run this with a minimal PATH. rustup's proxies live in ~/.cargo/bin for a
# rustup-init install and in Homebrew's keg for `brew install rustup` (not linked into bin).
CARGO_BIN=""
CARGO_ON_PATH="$(command -v cargo 2>/dev/null || true)"
for dir in "${CARGO_HOME:-$HOME/.cargo}/bin" "$HOME/.cargo/bin" /opt/homebrew/opt/rustup/bin /usr/local/opt/rustup/bin \
    ${CARGO_ON_PATH:+"$(dirname "$CARGO_ON_PATH")"}; do
    if [ -x "$dir/cargo" ] && [ -x "$dir/rustup" ] && "$dir/cargo" --version >/dev/null 2>&1; then
        CARGO_BIN="$dir"
        break
    fi
done
[ -n "$CARGO_BIN" ] || die "cargo/rustup not found (install rustup)"

# This script runs from a terminal, from Gradle, and from Xcode's pre-build phase, which exports
# hundreds of build settings. Any of them could reach rustc, cargo or the C compiler cc-rs drives
# (ring, zstd-sys, libsqlite3-sys) and change the bytes: RUSTFLAGS, RUSTC_BOOTSTRAP,
# RUSTUP_TOOLCHAIN, CARGO_UNSTABLE_*, CARGO_PROFILE_*, CFLAGS, CC_<target>, a hyphenated
# CFLAGS_aarch64-apple-ios, SDKROOT, ... So nothing is inherited: every tool below runs under
# `env -i` with this allowlist, plus what the build sets itself. PATH is fixed too, since cc-rs
# finds `clang`/`cc` on it: rustup's proxies, then the system's, whose /usr/bin/clang is Xcode's
# (DEVELOPER_DIR picks which Xcode).
CLEAN_ENV=(env -i "HOME=$HOME" "PATH=$CARGO_BIN:/usr/bin:/bin:/usr/sbin:/sbin" "TMPDIR=${TMPDIR:-/tmp}" "LC_ALL=C")
for var in USER LOGNAME DEVELOPER_DIR RUSTUP_HOME \
    HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY http_proxy https_proxy all_proxy no_proxy \
    SSL_CERT_FILE SSL_CERT_DIR; do
    if [ -n "${!var:-}" ]; then CLEAN_ENV+=("$var=${!var}"); fi
done
clean() { "${CLEAN_ENV[@]}" "$@"; }

XCODE_FOUND=$(clean xcodebuild -version 2>/dev/null | awk '/^Xcode/{v=$2} /^Build version/{b=$3} END{print v"("b")"}')
if [ "$XCODE_FOUND" != "$XCODE_PIN" ]; then
    if [ "$ANY_XCODE" = true ]; then
        say "Xcode $XCODE_FOUND, IOS_XCODE_VERSION pins $XCODE_PIN: --any-xcode, so building anyway; the archives will not match the committed ones"
    else
        echo "✗ Xcode $XCODE_FOUND, but IOS_XCODE_VERSION pins $XCODE_PIN." >&2
        echo "  Xcode's clang, SDK and strip are inputs of the archive, so another Xcode gives other" >&2
        echo "  bytes. Select the pinned one (DEVELOPER_DIR=/Applications/Xcode-<version>.app/Contents/Developer" >&2
        echo "  or xcode-select -s), or pass --any-xcode to build with this one anyway." >&2
        exit 1
    fi
fi

command -v git >/dev/null 2>&1 || die "git not found"

# The real CARGO_HOME, where the registry and the git checkouts are downloaded to.
REAL_CARGO_HOME="${CARGO_HOME:-$HOME/.cargo}"
mkdir -p "$REAL_CARGO_HOME/registry" "$REAL_CARGO_HOME/git"

# rustup resolves the toolchain from rust-toolchain.toml in the current directory (and installs
# it when missing); the build itself names it with RUSTUP_TOOLCHAIN, since it runs elsewhere.
for target in "${TARGETS[@]}"; do
    if ! (cd "$SCRIPT_DIR" && clean "CARGO_HOME=$REAL_CARGO_HOME" rustup target list --installed) | grep -qx "$target"; then
        say "Adding Rust target $target"
        (cd "$SCRIPT_DIR" && clean "CARGO_HOME=$REAL_CARGO_HOME" rustup target add "$target")
    fi
done
RUSTC_VERSION=$(cd "$SCRIPT_DIR" && clean "CARGO_HOME=$REAL_CARGO_HOME" "RUSTUP_TOOLCHAIN=$RUST_CHANNEL" rustc --version)
case "$RUSTC_VERSION" in
    "rustc $RUST_CHANNEL "*) ;;
    *) die "RUSTUP_TOOLCHAIN=$RUST_CHANNEL runs '$RUSTC_VERSION'" ;;
esac

# ----------------------------------------------------------------------------
# Source: the same Arti clone build-arti.sh uses
# ----------------------------------------------------------------------------

mkdir -p "$ARTI_BUILD_ROOT"
if [ ! -d "$ARTI_SOURCE_DIR/.git" ]; then
    say "Cloning Arti $ARTI_VERSION into $ARTI_SOURCE_DIR"
    clean git clone --depth 1 --branch "$ARTI_VERSION" https://gitlab.torproject.org/tpo/core/arti.git "$ARTI_SOURCE_DIR"
elif [ "$(clean git -C "$ARTI_SOURCE_DIR" describe --tags --exact-match 2>/dev/null || true)" != "$ARTI_VERSION" ]; then
    say "Moving the Arti clone to $ARTI_VERSION"
    clean git -C "$ARTI_SOURCE_DIR" fetch --depth 1 origin tag "$ARTI_VERSION"
    clean git -C "$ARTI_SOURCE_DIR" checkout "$ARTI_VERSION"
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

# ----------------------------------------------------------------------------
# Reproducibility
# ----------------------------------------------------------------------------

# Cargo reads config files ([env], [profile], [build] rustflags, ...) from $CARGO_HOME and from
# every parent of the directory it runs in. The build runs in $WRAPPER_DIR with the shadow
# CARGO_HOME, so only a stray .cargo/config* above the canonical build path could reach it
# (/tmp/.cargo/config.toml, say): refuse rather than build with it.
rm -f "$SHADOW_CARGO_HOME/registry" "$SHADOW_CARGO_HOME/git"
mkdir -p "$SHADOW_CARGO_HOME"
ln -s "$(cd "$REAL_CARGO_HOME/registry" && pwd -P)" "$SHADOW_CARGO_HOME/registry"
ln -s "$(cd "$REAL_CARGO_HOME/git" && pwd -P)" "$SHADOW_CARGO_HOME/git"
for dir in "$(cd "$WRAPPER_DIR" && pwd -L)" "$(cd "$WRAPPER_DIR" && pwd -P)"; do
    dir="$(cd "$dir" && pwd -P)"
    while :; do
        for config in "$dir/.cargo/config" "$dir/.cargo/config.toml"; do
            [ -e "$config" ] && die "cargo would read $config, which can change the archive's bytes. Move it away, or set ARTI_REPRO_DIR to a build path with no .cargo/config* above it."
        done
        [ "$dir" = / ] && break
        dir="$(dirname "$dir")"
    done
done
for config in "$SHADOW_CARGO_HOME/config" "$SHADOW_CARGO_HOME/config.toml"; do
    [ -e "$config" ] && die "remove $config: the build's CARGO_HOME holds no config"
done

# shellcheck source=repro-env.sh
source "$SCRIPT_DIR/repro-env.sh"

# Every spelling a compiler may see a directory in: exactly as given, normalised, and with
# symlinks resolved. All three were measured leaking: cargo passes CARGO_HOME to the build
# scripts verbatim (a TMPDIR ending in "/" made it "…T//…"), clang records the compile directory
# resolved (/var -> /private/var, /tmp -> /private/tmp), and xcrun hands cc-rs a resolved SDK
# path even when DEVELOPER_DIR goes through a symlink. A spelling the compiler never sees costs
# nothing; a missing one leaks the path. One per line.
spellings() {
    local dir="$1" spelling seen=$'\n'
    for spelling in "$dir" \
        "$(printf '%s' "$dir" | sed -e 's#//*#/#g')" \
        "$(cd "$dir" && pwd -L)" "$(cd "$dir" && pwd -P)"; do
        spelling="$(printf '%s' "$spelling" | sed -e 's#\(.\)/$#\1#')"
        case "$seen" in *$'\n'"$spelling"$'\n'*) continue ;; esac
        seen="$seen$spelling"$'\n'
        printf '%s\n' "$spelling"
    done
}
# One argument for cc-rs's shell-escaped *FLAGS parsing (CC_SHELL_ESCAPED_FLAGS): single-quoted,
# so a path with a space ("/Applications/Xcode 27.1.app") stays one flag.
shell_quote() {
    printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

# The paths rustc and the C compiler embed, mapped to fixed virtual names. repro-env.sh does this
# for rustc in RUSTFLAGS, which cargo splits on whitespace; the same maps go here in every
# spelling through CARGO_ENCODED_RUSTFLAGS (flags separated by 0x1f), so a path with a space works.
#
# The C and assembly objects need it too: ring's build compiles with debug info, which records
# each source's absolute path under CARGO_HOME and the SDK's path inside the Xcode install.
# Measured: a build with another CARGO_HOME and checkout differed from this one only in ring's 23
# objects, by those strings. cc-rs reads CFLAGS_<target> for C and assembly alike; Android's
# build never sets these, so its bytes cannot move.
SEP=$'\x1f'
ENCODED_RUSTFLAGS=""
IOS_CFLAGS=""
map_dir() {
    local dir="$1" virtual="$2" spelling
    while IFS= read -r spelling; do
        ENCODED_RUSTFLAGS="${ENCODED_RUSTFLAGS:+$ENCODED_RUSTFLAGS$SEP}--remap-path-prefix=$spelling=$virtual"
        IOS_CFLAGS="${IOS_CFLAGS:+$IOS_CFLAGS }$(shell_quote "-ffile-prefix-map=$spelling=$virtual")"
    done < <(spellings "$dir")
}
map_dir "$SHADOW_CARGO_HOME" /cargo
map_dir "$REAL_CARGO_HOME" /cargo
map_dir "$ARTI_SOURCE_DIR" /arti
map_dir "$SCRIPT_DIR" /arti-build
map_dir "$(clean xcode-select -p)" /xcode

# What cargo, rustc and cc-rs see: the clean environment plus these, nothing else.
# `panic = "unwind"` for iOS only (the profile's "abort" stays for Android): the catch_unwind
# around every export in ffi.rs then really catches, and a panic inside an Arti task ends that
# task instead of the app.
BUILD_ENV=("${CLEAN_ENV[@]}"
    "CARGO_HOME=$SHADOW_CARGO_HOME"
    "RUSTUP_TOOLCHAIN=$RUST_CHANNEL"
    "CARGO_INCREMENTAL=$CARGO_INCREMENTAL"
    "CARGO_ENCODED_RUSTFLAGS=$ENCODED_RUSTFLAGS"
    "CARGO_PROFILE_RELEASE_PANIC=unwind"
    "IPHONEOS_DEPLOYMENT_TARGET=$DEPLOYMENT_TARGET"
    "CC_SHELL_ESCAPED_FLAGS=1"
    "CFLAGS_aarch64_apple_ios=$IOS_CFLAGS"
    "CFLAGS_aarch64_apple_ios_sim=$IOS_CFLAGS")
if [ -n "${SOURCE_DATE_EPOCH:-}" ]; then BUILD_ENV+=("SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"); fi

# ----------------------------------------------------------------------------
# Build, into a staging directory
# ----------------------------------------------------------------------------

mkdir -p "$OUT_DIR" "$LOG_DIR"
# Next to OUT_DIR, so moving a file into place is a rename on one file system.
STAGING="$(mktemp -d "$(dirname "$OUT_DIR")/.arti-staging.XXXXXX")"
trap 'rm -rf "$STAGING"' EXIT

# The functions the header declares; each archive must export every one.
DECLARED=$(sed -n 's/.*[ *]\(arti_[a-z_]*\)(.*/\1/p' "$HEADER" | sort -u)
[ -n "$DECLARED" ] || die "no arti_* declarations found in $HEADER"

INPUTS_DIGEST="$(inputs_digest)"

# --print native-static-libs: rustc lists the system libraries the archive needs when it links
# the staticlib. The live output goes to the terminal, stderr to the log too.
run_cargo() {
    local target="$1" log="$2"
    {
        (cd "$WRAPPER_DIR" && "${BUILD_ENV[@]}" "$CARGO_BIN/cargo" rustc --release --locked --features ffi \
            --manifest-path "$WRAPPER_DIR/Cargo.toml" \
            --target "$target" --lib --crate-type staticlib \
            -- --print native-static-libs) 2>&1 1>&3 | tee "$log" >&2
    } 3>&1
}

for target in "${TARGETS[@]}"; do
    say "Building $target"
    log="$LOG_DIR/$target.cargo.log"
    built="$WRAPPER_DIR/target/$target/release/libarti_android.a"
    # rustc only prints native-static-libs when it links, so a run that finds the crate fresh has
    # no note. Keep the last one next to the archive it describes.
    saved="$WRAPPER_DIR/target/$target/release/native-static-libs.txt"
    run_cargo "$target" "$log"
    [ -f "$built" ] || die "$built not produced"
    native=$(sed -n 's/.*native-static-libs: //p' "$log" | head -1)
    if [ -z "$native" ] && [ -f "$saved" ] && [ ! "$built" -nt "$saved" ]; then
        native=$(cat "$saved")
    elif [ -z "$native" ]; then
        say "$target was up to date and its native-static-libs unknown: relinking it"
        touch "$WRAPPER_DIR/src/lib.rs"
        run_cargo "$target" "$log"
        native=$(sed -n 's/.*native-static-libs: //p' "$log" | head -1)
    fi
    [ -n "$native" ] || die "$target: rustc printed no native-static-libs (see $log)"
    printf '%s\n' "$native" > "$saved"
    # A change here means iosApp/build.gradle.kts and iosApp/xcode/project.yml need the new list.
    [ "$native" = "$NATIVE_LIBS" ] || die "$target needs native libs '$native', not '$NATIVE_LIBS': update the list in this script, iosApp/build.gradle.kts and iosApp/xcode/project.yml"

    mkdir -p "$STAGING/$target"
    # Strip debug info: a third of the archive (Rust's prebuilt std and ring's -gfull objects
    # carry DWARF), which nothing needs, since the app's own link strips it anyway. Linked into a
    # stripped binary the result is the same size. ZERO_AR_DATE makes strip's rewritten symbol
    # table (__.SYMDEF) say uid/gid 0 and mtime 0 like the other members; without it the member
    # carries the builder's uid and the time, so two builds differed (measured).
    clean ZERO_AR_DATE=1 xcrun strip -S "$built" -o "$STAGING/$target/libarti.a"

    # Xcode's nm exits 1 on the compiler_builtins members: they carry bitcode from Rust's newer
    # LLVM ("Unknown attribute kind"). The linker ignores that bitcode; read the rest anyway.
    exported=$({ clean nm -gU "$STAGING/$target/libarti.a" 2>/dev/null || true; } | awk '$2 == "T" {print $3}' | sed -n 's/^_\(arti_.*\)/\1/p' | sort -u)
    missing=$(comm -23 <(echo "$DECLARED") <(echo "$exported"))
    [ -z "$missing" ] || die "$target: declared in arti_ffi.h but not exported: $missing"
    echo "✓ $target: $(echo "$DECLARED" | wc -l | tr -d ' ') C exports present, $(du -h "$STAGING/$target/libarti.a" | cut -f1)"
done

# BUILD_INFO. A slice this run did not build keeps its lines from the previous BUILD_INFO, as
# long as they still describe the archive that is there.
OLD_INFO="$OUT_DIR/BUILD_INFO"
{
    echo "arti: $ARTI_VERSION"
    echo "rustc: $RUSTC_VERSION"
    echo "xcode: $XCODE_FOUND"
    echo "iphoneos_sdk: $(clean xcrun --sdk iphoneos --show-sdk-version) ($(clean xcrun --sdk iphoneos --show-sdk-build-version))"
    echo "iphonesimulator_sdk: $(clean xcrun --sdk iphonesimulator --show-sdk-version) ($(clean xcrun --sdk iphonesimulator --show-sdk-build-version))"
    echo "iphoneos_deployment_target: $DEPLOYMENT_TARGET"
    echo "panic: unwind"
    echo "strip: -S"
    echo "native-static-libs: $NATIVE_LIBS"
    for target in $ALL_TARGETS; do
        if [ -f "$STAGING/$target/libarti.a" ]; then
            echo "$target: $(shasum -a 256 "$STAGING/$target/libarti.a" | cut -d' ' -f1)"
            echo "$target.inputs: $INPUTS_DIGEST"
        elif [ -f "$OUT_DIR/$target/libarti.a" ]; then
            sha="$(shasum -a 256 "$OUT_DIR/$target/libarti.a" | cut -d' ' -f1)"
            echo "$target: $sha"
            old_sha="$(sed -n "s/^$target: //p" "$OLD_INFO" 2>/dev/null || true)"
            old_inputs="$(sed -n "s/^$target\.inputs: //p" "$OLD_INFO" 2>/dev/null || true)"
            if [ "$old_sha" = "$sha" ] && [ -n "$old_inputs" ]; then
                echo "$target.inputs: $old_inputs"
            else
                echo "$target.inputs: unknown"
            fi
        fi
    done
} > "$STAGING/BUILD_INFO"

# Every check passed: move the results into place, each file a rename, BUILD_INFO last.
for target in "${TARGETS[@]}"; do
    mkdir -p "$OUT_DIR/$target"
    mv -f "$STAGING/$target/libarti.a" "$OUT_DIR/$target/libarti.a"
done
mv -f "$STAGING/BUILD_INFO" "$OUT_DIR/BUILD_INFO"
cat "$OUT_DIR/BUILD_INFO"

echo
echo "Archives written to $OUT_DIR. Commit them (and BUILD_INFO) with the change that produced them;"
echo "./verify-reproducible-ios.sh checks they reproduce, ./gradlew :iosApp:verifyArtiIosArchives"
echo "that they match the sources."
