#!/usr/bin/env bash
#
# Verify that libzxingcpp_android.so builds reproducibly.
#
# Builds the native library twice from a clean state into scratch directories
# and confirms the two outputs are byte-for-byte identical, then compares them
# against what is committed under jniLibs. Both builds compile in the canonical
# path, so a match here means any checkout -- ours, F-Droid's, an auditor's --
# produces the same bytes. See README.md -> "Reproducible builds".
#
# Usage:
#   ./verify-reproducible.sh                  # every ABI
#   ./verify-reproducible.sh --abi arm64-v8a  # one ABI (faster)
#
# Exit 0 = reproducible and matching the commit, exit 1 = they differ.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JNILIBS="$PROJECT_ROOT/amethyst/src/main/jniLibs"
LIB_NAME="libzxingcpp_android.so"

PASSTHRU=("$@")

# Only the ABIs this run actually rebuilds. Hashing the whole tree would let an
# untouched ABI hash identically in both runs and report the lot reproducible --
# the same trap tools/arti-build hit and documents.
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)
for ((i = 0; i < ${#PASSTHRU[@]}; i++)); do
    [ "${PASSTHRU[$i]}" = "--abi" ] && ABIS=("${PASSTHRU[$((i + 1))]}")
done

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi
}

hashes_in() {
    local dir="$1" abi
    ( cd "$dir" && for abi in "${ABIS[@]}"; do
        [ -f "$abi/$LIB_NAME" ] && sha256 "$abi/$LIB_NAME"
    done )
}

RUN_A="$(mktemp -d -t zxingcpp-verify-a.XXXXXX)"
RUN_B="$(mktemp -d -t zxingcpp-verify-b.XXXXXX)"
trap 'rm -rf "$RUN_A" "$RUN_B"' EXIT

printf '==> Build 1 of 2\n'
"$SCRIPT_DIR/build-zxingcpp.sh" --out "$RUN_A" ${PASSTHRU[@]+"${PASSTHRU[@]}"} >/dev/null

printf '==> Build 2 of 2\n'
"$SCRIPT_DIR/build-zxingcpp.sh" --out "$RUN_B" ${PASSTHRU[@]+"${PASSTHRU[@]}"} >/dev/null

A="$(hashes_in "$RUN_A")"
B="$(hashes_in "$RUN_B")"

if [ "$A" != "$B" ]; then
    printf '\nNOT REPRODUCIBLE -- the two builds differ:\n'
    diff <(printf '%s\n' "$A") <(printf '%s\n' "$B") || true
    exit 1
fi

printf '\nReproducible: two builds produced identical bytes.\n'
printf '%s\n' "$A" | sed 's/^/  /'

# A reproducible build that does not match the commit is still a problem: it
# means the shipped library came from something other than this tag.
COMMITTED="$(hashes_in "$JNILIBS")"
if [ "$COMMITTED" != "$A" ]; then
    printf '\nWARNING: the committed libraries do NOT match this build.\n'
    diff <(printf '%s\n' "$COMMITTED") <(printf '%s\n' "$A") || true
    printf '\nRun build-zxingcpp.sh and commit the result.\n'
    exit 1
fi

printf '\nCommitted libraries match.\n'
