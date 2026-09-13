#!/usr/bin/env bash
#
# Build Arti native libraries for Android from source.
#
# Prerequisites:
#   - Rust toolchain: rustup, cargo
#   - Android targets: rustup target add aarch64-linux-android x86_64-linux-android
#   - cargo-ndk: cargo install cargo-ndk
#   - Android NDK: the exact revision pinned in ANDROID_NDK_VERSION
#     (sdkmanager "ndk;<revision>") — see README.md -> "Reproducible builds"
#
# Usage:
#   ./build-arti.sh              # Build for all targets (arm64 + x86_64)
#   ./build-arti.sh --release    # Build arm64 only (for release)
#   ./build-arti.sh --clean      # Clean and rebuild
#
set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ARTI_VERSION=$(cat "$SCRIPT_DIR/ARTI_VERSION" | tr -d '[:space:]')

# Reproducibility: the NDK ships the clang that compiles Arti's C dependencies
# (ring, zstd-sys, libsqlite3-sys) and the lld that links the whole cdylib, so
# its revision is baked into the output bytes exactly like rustc's is — both
# land in the .comment section of the shipped .so. Pin it here and refuse to
# build with anything else; the old glob over ~/Android/Sdk/ndk/*/ silently
# picked up whatever happened to be installed first.
NDK_VERSION=$(cat "$SCRIPT_DIR/ANDROID_NDK_VERSION" | tr -d '[:space:]')
# The NDK build number (last component of the revision) is what the linker
# stamps into .note.android.ident, so it is how we verify the output afterwards.
NDK_BUILD_NUMBER="${NDK_VERSION##*.}"
# cargo-ndk only wraps the NDK (it sets CC/AR/linker and the --platform flags),
# but those flags reach the linker, so record the version we verified with and
# warn when it differs. Not a hard error: unlike the NDK itself, it has no
# proven effect on the bytes.
CARGO_NDK_VERSION=$(cat "$SCRIPT_DIR/CARGO_NDK_VERSION" | tr -d '[:space:]')

# Reproducibility: rustc bakes the *real* (un-remapped) absolute paths of the
# build artifacts into its codegen/link ORDERING, so --remap-path-prefix alone
# is not enough — the .so only reproduces byte-for-byte when the compile happens
# at a fixed path. Everyone who needs to reproduce the shipped binary (us,
# F-Droid, an independent verifier) must therefore build at this same canonical
# location. Overriding ARTI_REPRO_DIR changes the output bytes; only do it if
# you don't care about matching the published .so.
ARTI_BUILD_ROOT="${ARTI_REPRO_DIR:-/tmp/amethyst-arti-build}"
ARTI_SOURCE_DIR="$ARTI_BUILD_ROOT/.arti-source"
OUTPUT_DIR="$PROJECT_ROOT/amethyst/src/main/jniLibs"
LIB_NAME="libarti_android.so"
MIN_SDK_VERSION=26

# Default targets
TARGETS=("aarch64-linux-android" "x86_64-linux-android")
RELEASE_ONLY=false
CLEAN=false
REGEN_LOCK=false

# Parse arguments
for arg in "$@"; do
    case $arg in
        --release) RELEASE_ONLY=true; TARGETS=("aarch64-linux-android") ;;
        --clean) CLEAN=true ;;
        # Refresh the committed Cargo.lock from the pinned Arti tag, then exit
        # (no compile — needs only git + cargo, not the NDK). Use after bumping
        # ARTI_VERSION / Cargo.toml; the normal build is --locked and will fail
        # until the lock is regenerated and committed.
        --regen-lock) REGEN_LOCK=true; CLEAN=true ;;
        --help) echo "Usage: $0 [--release] [--clean] [--regen-lock] [--help]"; exit 0 ;;
    esac
done

print_header()  { echo -e "\n${BLUE}=== $1 ===${NC}"; }
print_success() { echo -e "${GREEN}✓ $1${NC}"; }
print_error()   { echo -e "${RED}✗ $1${NC}"; }
print_info()    { echo -e "${YELLOW}→ $1${NC}"; }

# ============================================================================
# Prerequisites
# ============================================================================

check_prerequisites() {
    print_header "Checking prerequisites"

    command -v git >/dev/null 2>&1 || { print_error "git not found"; exit 1; }
    command -v rustup >/dev/null 2>&1 || { print_error "rustup not found"; exit 1; }
    command -v cargo >/dev/null 2>&1 || { print_error "cargo not found"; exit 1; }
    command -v cargo-ndk >/dev/null 2>&1 || { print_error "cargo-ndk not found. Install: cargo install cargo-ndk --version $CARGO_NDK_VERSION --locked"; exit 1; }

    local found_cargo_ndk
    found_cargo_ndk="$(cargo ndk --version 2>/dev/null | awk '{print $2}' || true)"
    if [ "$found_cargo_ndk" != "$CARGO_NDK_VERSION" ]; then
        print_info "cargo-ndk ${found_cargo_ndk:-unknown} != pinned $CARGO_NDK_VERSION — if the"
        print_info "  output does not match the committed .so, try: cargo install cargo-ndk --version $CARGO_NDK_VERSION --locked"
    else
        print_success "cargo-ndk: $CARGO_NDK_VERSION"
    fi

    # An explicit ANDROID_NDK_HOME wins (it is verified below like any other);
    # otherwise look for the pinned revision by name in the usual SDK layouts.
    # Deliberately no wildcard: picking "some NDK" is what let the committed
    # binaries be built with r25b while the docs asked for r27.
    if [ -z "${ANDROID_NDK_HOME:-}" ]; then
        for candidate in \
            "${ANDROID_NDK_ROOT:-}" \
            "${ANDROID_HOME:-}/ndk/$NDK_VERSION" \
            "${ANDROID_SDK_ROOT:-}/ndk/$NDK_VERSION" \
            "$HOME/Android/Sdk/ndk/$NDK_VERSION" \
            "$HOME/Library/Android/sdk/ndk/$NDK_VERSION" \
            "/usr/local/lib/android/sdk/ndk/$NDK_VERSION"; do
            [ -n "$candidate" ] || continue
            if [ -d "$candidate" ]; then
                export ANDROID_NDK_HOME="${candidate%/}"
                break
            fi
        done
    fi

    if [ -z "${ANDROID_NDK_HOME:-}" ]; then
        print_error "Android NDK $NDK_VERSION not found (and ANDROID_NDK_HOME is unset)"
        echo "  Install it:  sdkmanager \"ndk;$NDK_VERSION\""
        echo "  Or point ANDROID_NDK_HOME at an existing $NDK_VERSION install."
        exit 1
    fi

    local found_ndk
    found_ndk="$(sed -n 's/^Pkg\.Revision *= *//p' "$ANDROID_NDK_HOME/source.properties" 2>/dev/null | tr -d '[:space:]' || true)"
    if [ "$found_ndk" != "$NDK_VERSION" ]; then
        print_error "NDK revision mismatch — this build would not reproduce the shipped .so"
        echo "  Pinned:   $NDK_VERSION (tools/arti-build/ANDROID_NDK_VERSION)"
        echo "  Found:    ${found_ndk:-unknown} at $ANDROID_NDK_HOME"
        echo "  Install:  sdkmanager \"ndk;$NDK_VERSION\""
        exit 1
    fi

    print_success "NDK: $ANDROID_NDK_HOME ($NDK_VERSION)"

    for target in "${TARGETS[@]}"; do
        if ! rustup target list --installed | grep -q "$target"; then
            print_info "Adding Rust target: $target"
            rustup target add "$target"
        fi
        print_success "Target: $target"
    done
}

# ============================================================================
# Source Management
# ============================================================================

clone_or_update_arti() {
    print_header "Setting up Arti source ($ARTI_VERSION)"

    if [ "$CLEAN" = true ] && [ -d "$ARTI_SOURCE_DIR" ]; then
        print_info "Cleaning existing source"
        rm -rf "$ARTI_SOURCE_DIR"
    fi

    mkdir -p "$ARTI_BUILD_ROOT"
    print_info "Canonical build path: $ARTI_BUILD_ROOT (set ARTI_REPRO_DIR to override)"

    if [ ! -d "$ARTI_SOURCE_DIR" ]; then
        print_info "Cloning Arti repository..."
        git clone --depth 1 --branch "$ARTI_VERSION" \
            https://gitlab.torproject.org/tpo/core/arti.git \
            "$ARTI_SOURCE_DIR"
    else
        print_info "Updating existing clone to $ARTI_VERSION"
        cd "$ARTI_SOURCE_DIR"
        git fetch --depth 1 origin tag "$ARTI_VERSION"
        git checkout "$ARTI_VERSION"
        cd "$SCRIPT_DIR"
    fi

    print_success "Arti source ready at $ARTI_SOURCE_DIR"
}

# ============================================================================
# Wrapper Setup
# ============================================================================

setup_wrapper() {
    print_header "Setting up JNI wrapper"

    local wrapper_dir="$ARTI_SOURCE_DIR/arti-android-wrapper"
    mkdir -p "$wrapper_dir/src"

    cp "$SCRIPT_DIR/Cargo.toml" "$wrapper_dir/Cargo.toml"
    cp "$SCRIPT_DIR/src/lib.rs" "$wrapper_dir/src/lib.rs"

    # Reproducibility: build against the committed lockfile so transitive
    # dependency versions are identical for everyone. `cargo --locked` (in
    # build_for_target) fails loudly if this lock is missing or stale rather than
    # silently re-resolving. (Missing is only expected during --regen-lock.)
    if [ -f "$SCRIPT_DIR/Cargo.lock" ]; then
        cp "$SCRIPT_DIR/Cargo.lock" "$wrapper_dir/Cargo.lock"
        print_success "Pinned dependencies from committed Cargo.lock"
    else
        print_info "No committed Cargo.lock yet — run with --regen-lock to create it"
    fi

    # Patch Cargo.toml to use local arti-client from the source tree
    # instead of pulling from crates.io
    cd "$wrapper_dir"

    # Add path overrides for the local arti source
    cat >> Cargo.toml << 'PATCH'

[patch.crates-io]
arti-client = { path = "../crates/arti-client" }
tor-rtcompat = { path = "../crates/tor-rtcompat" }
PATCH

    cd "$SCRIPT_DIR"
    print_success "JNI wrapper configured"
}

# ============================================================================
# Build
# ============================================================================

# Android ABI directory (as laid out under jniLibs/) for a Rust target triple.
abi_dir_for() {
    case "$1" in
        aarch64-linux-android) echo "arm64-v8a" ;;
        x86_64-linux-android) echo "x86_64" ;;
        armv7-linux-androideabi) echo "armeabi-v7a" ;;
        i686-linux-android) echo "x86" ;;
    esac
}

build_for_target() {
    local target="$1"
    print_header "Building for $target"

    local arch_dir
    arch_dir="$(abi_dir_for "$target")"

    local out_dir="$OUTPUT_DIR/$arch_dir"
    mkdir -p "$out_dir"

    cargo ndk \
        -t "$target" \
        --platform "$MIN_SDK_VERSION" \
        -o "$OUTPUT_DIR" \
        build --release --locked \
        --manifest-path "$ARTI_SOURCE_DIR/arti-android-wrapper/Cargo.toml"

    if [ -f "$out_dir/$LIB_NAME" ]; then
        local size=$(du -h "$out_dir/$LIB_NAME" | cut -f1)
        print_success "Built $arch_dir/$LIB_NAME ($size)"
    else
        print_error "Build failed — $out_dir/$LIB_NAME not found"
        exit 1
    fi
}

# ============================================================================
# Verification
# ============================================================================

verify_jni_symbols() {
    print_header "Verifying JNI symbols"

    local expected_symbols=(
        "Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_getVersion"
        "Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_setLogCallback"
        "Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_initialize"
        "Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_startSocksProxy"
        "Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_stopSocksProxy"
        "Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_isBootstrapped"
        "Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_bootstrapProgressPermille"
        "Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_destroy"
    )

    for arch_dir in "$OUTPUT_DIR"/*/; do
        local lib="$arch_dir$LIB_NAME"
        [ -f "$lib" ] || continue

        local arch=$(basename "$arch_dir")
        local missing=0

        # Read the dynamic symbol table once, into a variable. Piping nm into
        # `grep -q` per symbol looks equivalent but is not: grep exits on the
        # first match, nm dies of SIGPIPE (141), and `set -o pipefail` then
        # reports the pipeline as failed — so every symbol that IS exported gets
        # reported as missing. (Reproducible on any build, old or new.)
        local syms
        syms="$(nm -D "$lib" 2>/dev/null || true)"

        for sym in "${expected_symbols[@]}"; do
            if [[ "$syms" != *"$sym"* ]]; then
                print_error "$arch: Missing symbol $sym"
                missing=1
            fi
        done

        if [ "$missing" -eq 0 ]; then
            print_success "$arch: All JNI symbols present"
        fi
    done
}

verify_ndk_stamp() {
    print_header "Verifying NDK stamp"

    if ! command -v readelf >/dev/null 2>&1; then
        print_info "readelf not found — skipping (install binutils to enable this check)"
        return 0
    fi

    # Every NDK-linked shared object carries .note.android.ident, which records
    # the target API level, the NDK release name (e.g. r27d) and the NDK build
    # number. Reading it back proves which toolchain actually produced the
    # binary, independently of what the environment claimed — this is how the
    # committed r25b libraries were identified in the first place.
    for target in "${TARGETS[@]}"; do
        local arch
        arch="$(abi_dir_for "$target")"
        local lib="$OUTPUT_DIR/$arch/$LIB_NAME"
        [ -f "$lib" ] || continue

        if readelf -p .note.android.ident "$lib" 2>/dev/null | grep -qw "$NDK_BUILD_NUMBER"; then
            print_success "$arch: built by NDK $NDK_VERSION"
        else
            print_error "$arch: not stamped with NDK build $NDK_BUILD_NUMBER — wrong toolchain?"
            readelf -p .note.android.ident "$lib" 2>/dev/null || true
            exit 1
        fi
    done
}

# ============================================================================
# Main
# ============================================================================

main() {
    echo -e "${BLUE}Arti Android Build — version $ARTI_VERSION${NC}"

    # --regen-lock only needs git + cargo, not the NDK/cargo-ndk toolchain.
    [ "$REGEN_LOCK" = true ] || check_prerequisites
    clone_or_update_arti
    setup_wrapper

    if [ "$REGEN_LOCK" = true ]; then
        print_header "Regenerating Cargo.lock"
        local manifest="$ARTI_SOURCE_DIR/arti-android-wrapper/Cargo.toml"
        cargo generate-lockfile --manifest-path "$manifest"
        cp "$ARTI_SOURCE_DIR/arti-android-wrapper/Cargo.lock" "$SCRIPT_DIR/Cargo.lock"
        print_success "Updated $SCRIPT_DIR/Cargo.lock — commit it, then re-run the build."
        exit 0
    fi

    # Deterministic build env (needs ARTI_SOURCE_DIR cloned above for SOURCE_DATE_EPOCH).
    # shellcheck source=repro-env.sh
    source "$SCRIPT_DIR/repro-env.sh"
    print_success "Reproducible build env loaded (RUSTFLAGS path remapping, SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-unset})"

    for target in "${TARGETS[@]}"; do
        build_for_target "$target"
    done

    verify_jni_symbols
    verify_ndk_stamp

    print_header "Build complete"
    echo ""
    echo "Libraries written to: $OUTPUT_DIR"
    echo ""
    echo "Next steps:"
    echo "  1. Verify 16KB page alignment: readelf -l <lib> | grep LOAD"
    echo "  2. Build the app: ./gradlew :amethyst:assembleDebug"
    echo "  3. Test on device"
    echo ""
}

main "$@"
