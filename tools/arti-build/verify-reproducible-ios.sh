#!/usr/bin/env bash
#
# Verify that the iOS Arti archives (build-arti-ios.sh) build reproducibly.
#
# Builds twice from clean and compares the bytes of every libarti.a. The second build runs from
# a copy of tools/arti-build at another path and with a fresh, empty CARGO_HOME somewhere else,
# so it sees what a second machine sees: a different checkout, a different crate registry path
# and a cold download. A path that leaks into the archive shows up here as a difference, which
# two builds on one machine cannot reveal. Both compile at the canonical path
# (/tmp/amethyst-arti-build), as build-arti.sh does for Android.
#
# Then it compares the result with IOS_ARCHIVES.sha256, the hashes committed for the pinned
# Xcode (IOS_XCODE_VERSION), so anyone can check that the archives a tag produces are the ones
# recorded for it.
#
# Usage:
#   ./verify-reproducible-ios.sh                 # both slices
#   ./verify-reproducible-ios.sh --target=aarch64-apple-ios
#   ./verify-reproducible-ios.sh --write-hashes  # after a change to src/, Cargo.* or a pin:
#                                                # verify, then record the new hashes
#   ./verify-reproducible-ios.sh --any-xcode     # run with another Xcode (hashes will differ)
#
# Exit 0 = reproducible (and matching the committed hashes, unless --write-hashes or
# --any-xcode), 1 = not.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HASHES="$SCRIPT_DIR/IOS_ARCHIVES.sha256"
XCODE_PIN=$(tr -d '[:space:]' < "$SCRIPT_DIR/IOS_XCODE_VERSION")

TARGET_ARGS=()
WRITE=false
ANY_XCODE=false
for arg in "$@"; do
    case $arg in
        --target=*) TARGET_ARGS+=("$arg") ;;
        --write-hashes) WRITE=true ;;
        --any-xcode) ANY_XCODE=true ;;
        --help) sed -n '2,24p' "$0"; exit 0 ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done

XCODE_FOUND=$(xcodebuild -version 2>/dev/null | awk '/^Xcode/{v=$2} /^Build version/{b=$3} END{print v"("b")"}')
if [ "$XCODE_FOUND" != "$XCODE_PIN" ] && [ "$ANY_XCODE" = false ]; then
    echo "✗ Xcode $XCODE_FOUND, but IOS_XCODE_VERSION pins $XCODE_PIN."
    echo "  Xcode's clang and SDK are inputs of the archive, so another Xcode gives other bytes."
    echo "  Select the pinned one (DEVELOPER_DIR=/Applications/Xcode-<version>.app/Contents/Developer"
    echo "  or xcode-select -s), or pass --any-xcode to check reproducibility alone."
    exit 1
fi

# "<sha256>  <triple>/libarti.a" for every archive under an out/ios dir, sorted.
hashes() {
    (cd "$1" && for a in */libarti.a; do [ -f "$a" ] && shasum -a 256 "$a"; done)
}

WORK="$(mktemp -d "${TMPDIR:-/tmp}/arti-ios-verify.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

echo "### Reproducibility check for the iOS Arti archives"
echo "### Xcode $XCODE_FOUND (pinned: $XCODE_PIN); canonical build path ${ARTI_REPRO_DIR:-/tmp/amethyst-arti-build}"
echo

echo "### Build 1 of 2 (clean, this checkout)…"
rm -rf "$SCRIPT_DIR/out/ios"
"$SCRIPT_DIR/build-arti-ios.sh" --clean ${TARGET_ARGS[@]+"${TARGET_ARGS[@]}"}
H1="$(hashes "$SCRIPT_DIR/out/ios")"
echo "--- build 1 ---"; echo "$H1"; echo

echo "### Build 2 of 2 (clean, a copy of tools/arti-build elsewhere, empty CARGO_HOME)…"
COPY="$WORK/elsewhere/arti-build"
mkdir -p "$COPY"
(cd "$SCRIPT_DIR" && tar --exclude ./out -cf - .) | (cd "$COPY" && tar -xf -)
CARGO_HOME="$WORK/cargo-home" "$COPY/build-arti-ios.sh" --clean ${TARGET_ARGS[@]+"${TARGET_ARGS[@]}"}
H2="$(hashes "$COPY/out/ios")"
echo "--- build 2 ---"; echo "$H2"; echo

if [ "$H1" != "$H2" ]; then
    echo "❌ NOT REPRODUCIBLE — the two builds differ:"
    diff <(echo "$H1") <(echo "$H2") || true
    rm -rf "$SCRIPT_DIR/out/ios-build2"
    cp -R "$COPY/out/ios" "$SCRIPT_DIR/out/ios-build2"
    echo "  Build 2's archives are kept in out/ios-build2. Compare the members: ar -x each"
    echo "  archive into its own dir, cmp them, and run strings -a on the ones that differ"
    echo "  (a leaked absolute path is the usual cause)."
    exit 1
fi
echo "✅ REPRODUCIBLE — both clean builds produced identical archives."

echo
if [ "$WRITE" = true ]; then
    {
        echo "# sha256 of tools/arti-build/out/ios/<triple>/libarti.a, as build-arti-ios.sh builds them"
        echo "# with the Xcode in IOS_XCODE_VERSION ($XCODE_FOUND). Refresh with"
        echo "# ./verify-reproducible-ios.sh --write-hashes after any change to what they are built from."
        # Keep the slices this run did not build.
        if [ -f "$HASHES" ]; then
            { grep -v '^#' "$HASHES" || true; } | while read -r _ path; do
                grep -q " $path\$" <<< "$H1" || grep " $path\$" "$HASHES" || true
            done
        fi
        echo "$H1"
    } > "$HASHES.new"
    { grep '^#' "$HASHES.new"; grep -v '^#' "$HASHES.new" | sort -k2; } > "$HASHES"
    rm -f "$HASHES.new"
    echo "✓ Wrote $HASHES — commit it with the change it records."
    exit 0
fi

echo "### vs. IOS_ARCHIVES.sha256:"
status=0
while read -r hash path; do
    expected=$({ grep -v '^#' "$HASHES" 2>/dev/null || true; } | awk -v p="$path" '$2 == p {print $1}')
    if [ -z "$expected" ]; then
        echo "⚠ $path: no committed hash"
        status=1
    elif [ "$expected" = "$hash" ]; then
        echo "✓ $path matches the committed hash"
    else
        echo "✗ $path is $hash, committed $expected"
        status=1
    fi
done <<< "$H1"
if [ "$status" -ne 0 ]; then
    if [ "$ANY_XCODE" = true ]; then
        echo "  (expected with another Xcode than the pinned one)"
        exit 0
    fi
    echo "  The sources or pins changed without a hash refresh, or something else in the build"
    echo "  differs. If the change is intended: ./verify-reproducible-ios.sh --write-hashes"
fi
exit "$status"
