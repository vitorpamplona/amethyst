#!/usr/bin/env bash
#
# Verify that libarti_android.so builds reproducibly.
#
# Builds the Arti native library twice from a clean state and confirms the two
# outputs are byte-for-byte identical. Both builds compile in the canonical path
# (/tmp/amethyst-arti-build), so a match here means any checkout — ours,
# F-Droid's, an auditor's — produces the same bytes. See README.md →
# "Reproducible builds".
#
# Usage:
#   ./verify-reproducible.sh              # all four shipped ABIs
#   ./verify-reproducible.sh --release    # arm64-v8a only (faster)
#   ./verify-reproducible.sh --target=armv7-linux-androideabi
#                                         # one ABI, by Rust target triple
#
# Prerequisites are the same as build-arti.sh (rustup, cargo-ndk, and the exact
# Android NDK revision pinned in ANDROID_NDK_VERSION — a different revision is
# refused, because it would change the output bytes).
# Exit 0 = reproducible, exit 1 = builds differ.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JNILIBS="$PROJECT_ROOT/amethyst/src/main/jniLibs"
PASSTHRU=("$@")

# Portable sha256 (coreutils sha256sum on Linux, shasum on macOS).
sha256() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi
}

# Mirrors abi_dir_for() in build-arti.sh — kept in step with it, since a triple
# that maps to the wrong directory here would hash a library this run never
# rebuilt and call it reproducible.
abi_dir_for() {
    case "$1" in
        aarch64-linux-android) echo "arm64-v8a" ;;
        x86_64-linux-android) echo "x86_64" ;;
        armv7-linux-androideabi) echo "armeabi-v7a" ;;
        i686-linux-android) echo "x86" ;;
        *) echo "Unknown Rust target '$1'" >&2; exit 1 ;;
    esac
}

# Only the ABIs this run actually rebuilds. Hashing everything under jniLibs/
# (what `find` used to do) made `--release` look like it had verified the
# x86_64 library: that build never touches it, so the untouched file hashed
# identically in both runs and the script reported the whole tree reproducible
# and matching the commit.
ABIS="arm64-v8a x86_64 armeabi-v7a x86"
SELECTED=""
for arg in ${PASSTHRU[@]+"${PASSTHRU[@]}"}; do
    case "$arg" in
        --release) ABIS="arm64-v8a" ;;
        --target=*) SELECTED="$SELECTED $(abi_dir_for "${arg#--target=}")" ;;
    esac
done
[ -n "$SELECTED" ] && ABIS="${SELECTED# }"

# sha256 of each built .so, keyed by ABI dir (relative paths → stable keys).
hashes() {
    ( cd "$JNILIBS" && for abi in $ABIS; do
        [ -f "$abi/libarti_android.so" ] && sha256 "$abi/libarti_android.so"
    done )
}

echo "### Reproducibility check for libarti_android.so"
echo "### ABIs: $ABIS"
echo "### Canonical build path: ${ARTI_REPRO_DIR:-/tmp/amethyst-arti-build}"
echo

echo "### Build 1 of 2 (clean)…"
"$SCRIPT_DIR/build-arti.sh" --clean ${PASSTHRU[@]+"${PASSTHRU[@]}"}
H1="$(hashes)"
echo "--- build 1 hashes ---"; echo "$H1"; echo

echo "### Build 2 of 2 (clean)…"
"$SCRIPT_DIR/build-arti.sh" --clean ${PASSTHRU[@]+"${PASSTHRU[@]}"}
H2="$(hashes)"
echo "--- build 2 hashes ---"; echo "$H2"; echo

if [ "$H1" = "$H2" ]; then
    echo "✅ REPRODUCIBLE — both clean builds produced identical .so bytes."
else
    echo "❌ NOT REPRODUCIBLE — the two builds differ:"
    diff <(echo "$H1") <(echo "$H2") || true
    exit 1
fi

# Informational: is the binary committed in git already the reproducible one?
echo
echo "### vs. the committed binaries:"
BUILT_PATHS=""
for abi in $ABIS; do
    BUILT_PATHS="$BUILT_PATHS amethyst/src/main/jniLibs/$abi/libarti_android.so"
done

# shellcheck disable=SC2086 # BUILT_PATHS is a deliberate multi-path list
if git -C "$PROJECT_ROOT" diff --quiet -- $BUILT_PATHS; then
    echo "✓ The reproducible build matches what's committed — the shipped .so is verifiable as-is."
else
    echo "⚠ The reproducible build differs from the committed .so (e.g. the committed one"
    echo "  predates this toolchain). Commit the rebuilt binaries so the shipped artifact"
    echo "  is itself a reproducible build:"
    echo "      git -C \"$PROJECT_ROOT\" add$BUILT_PATHS && git commit"
fi
