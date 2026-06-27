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
#   ./verify-reproducible.sh              # both ABIs (arm64-v8a + x86_64)
#   ./verify-reproducible.sh --release    # arm64-v8a only (faster)
#
# Prerequisites are the same as build-arti.sh (rustup, cargo-ndk, Android NDK).
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

# sha256 of every built .so, keyed by ABI dir (relative paths → stable keys).
# `find | sort` (plain text sort) is portable across GNU and BSD userlands.
hashes() {
    ( cd "$JNILIBS" && find . -name libarti_android.so | sort | while IFS= read -r f; do
        sha256 "$f"
    done )
}

echo "### Reproducibility check for libarti_android.so"
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
if git -C "$PROJECT_ROOT" diff --quiet -- amethyst/src/main/jniLibs/; then
    echo "✓ The reproducible build matches what's committed — the shipped .so is verifiable as-is."
else
    echo "⚠ The reproducible build differs from the committed .so (e.g. the committed one"
    echo "  predates this toolchain). Commit the rebuilt binaries so the shipped artifact"
    echo "  is itself a reproducible build:"
    echo "      git -C \"$PROJECT_ROOT\" add amethyst/src/main/jniLibs && git commit"
fi
