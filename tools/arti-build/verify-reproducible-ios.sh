#!/usr/bin/env bash
#
# Verify that the iOS Arti archives (build-arti-ios.sh) build reproducibly, and that the ones
# committed in iosApp/libs/arti are those bytes. The iOS counterpart of verify-reproducible.sh.
#
# Builds twice from clean. The second build runs from a copy of tools/arti-build at another path,
# with a fresh, empty CARGO_HOME somewhere else, so it sees what a second machine sees: another
# checkout, another crate registry path, a cold download. A path that leaks into the archive
# shows up here as a difference, which two builds in one place cannot reveal. Both compile at the
# canonical path (/tmp/amethyst-arti-build), as build-arti.sh does for Android.
#
# Build 1 writes the committed location, so after a run `git diff iosApp/libs/arti` shows whether
# the committed archives are the reproducible ones.
#
# Usage:
#   ./verify-reproducible-ios.sh                   # both slices
#   ./verify-reproducible-ios.sh --target=aarch64-apple-ios
#   ./verify-reproducible-ios.sh --any-xcode       # another Xcode than IOS_XCODE_VERSION: checks
#                                                  # reproducibility only (the bytes will differ
#                                                  # from the committed ones)
#
# Exit 0 = reproducible, 1 = not (or the wrong Xcode).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LIBS="$PROJECT_ROOT/iosApp/libs/arti"
XCODE_PIN=$(tr -d '[:space:]' < "$SCRIPT_DIR/IOS_XCODE_VERSION")

TARGET_ARGS=()
ANY_XCODE=false
for arg in "$@"; do
    case $arg in
        --target=*) TARGET_ARGS+=("$arg") ;;
        --any-xcode) ANY_XCODE=true ;;
        --help) sed -n '2,22p' "$0"; exit 0 ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done

XCODE_FOUND=$(xcodebuild -version 2>/dev/null | awk '/^Xcode/{v=$2} /^Build version/{b=$3} END{print v"("b")"}')
if [ "$XCODE_FOUND" != "$XCODE_PIN" ] && [ "$ANY_XCODE" = false ]; then
    echo "✗ Xcode $XCODE_FOUND, but IOS_XCODE_VERSION pins $XCODE_PIN."
    echo "  Xcode's clang, SDK and strip are inputs of the archive, so another Xcode gives other"
    echo "  bytes. Select the pinned one (DEVELOPER_DIR=/Applications/Xcode-<version>.app/Contents/Developer"
    echo "  or xcode-select -s), or pass --any-xcode to check reproducibility alone."
    exit 1
fi

# "<sha256>  <triple>/libarti.a" for every archive under a libs dir, sorted.
hashes() {
    (cd "$1" && for a in */libarti.a; do [ -f "$a" ] && shasum -a 256 "$a"; done)
}

WORK="$(mktemp -d "${TMPDIR:-/tmp}/arti-ios-verify.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

echo "### Reproducibility check for the iOS Arti archives"
echo "### Xcode $XCODE_FOUND (pinned: $XCODE_PIN); canonical build path ${ARTI_REPRO_DIR:-/tmp/amethyst-arti-build}"
echo

echo "### Build 1 of 2 (clean, this checkout, into iosApp/libs/arti)…"
"$SCRIPT_DIR/build-arti-ios.sh" --clean ${TARGET_ARGS[@]+"${TARGET_ARGS[@]}"}
H1="$(hashes "$LIBS")"
echo "--- build 1 ---"; echo "$H1"; echo

echo "### Build 2 of 2 (clean, a copy of tools/arti-build elsewhere, empty CARGO_HOME)…"
COPY="$WORK/elsewhere/arti-build"
OUT2="$WORK/libs"
mkdir -p "$COPY"
(cd "$SCRIPT_DIR" && tar --exclude ./out -cf - .) | (cd "$COPY" && tar -xf -)
CARGO_HOME="$WORK/cargo-home" ARTI_IOS_OUT_DIR="$OUT2" \
    "$COPY/build-arti-ios.sh" --clean ${TARGET_ARGS[@]+"${TARGET_ARGS[@]}"}
H2="$(hashes "$OUT2")"
# Build 1 may hold a slice this run did not rebuild; compare only what build 2 made.
H1_SAME="$(while read -r _ path; do grep " $path\$" <<< "$H1" || true; done <<< "$H2")"
echo "--- build 2 ---"; echo "$H2"; echo

if [ "$H1_SAME" != "$H2" ]; then
    echo "❌ NOT REPRODUCIBLE — the two builds differ:"
    diff <(echo "$H1_SAME") <(echo "$H2") || true
    rm -rf "$SCRIPT_DIR/out/ios-build2"
    mkdir -p "$SCRIPT_DIR/out"
    cp -R "$OUT2" "$SCRIPT_DIR/out/ios-build2"
    echo "  Build 2's archives are kept in tools/arti-build/out/ios-build2. Compare the members:"
    echo "  ar -x each archive into its own dir, cmp them, and run strings -a on the ones that"
    echo "  differ (a leaked absolute path is the usual cause)."
    exit 1
fi
echo "✅ REPRODUCIBLE — both clean builds produced identical archives."

echo
echo "### vs. the committed archives:"
if ! git -C "$PROJECT_ROOT" ls-files --error-unmatch iosApp/libs/arti/aarch64-apple-ios/libarti.a \
    iosApp/libs/arti/aarch64-apple-ios-sim/libarti.a >/dev/null 2>&1; then
    echo "⚠ No committed archives yet: git -C \"$PROJECT_ROOT\" add iosApp/libs/arti && git commit"
elif git -C "$PROJECT_ROOT" diff --quiet -- iosApp/libs/arti; then
    echo "✓ The reproducible build matches what's committed."
else
    echo "⚠ The reproducible build differs from the committed archives."
    if [ "$ANY_XCODE" = true ]; then
        echo "  Expected with another Xcode than the pinned one; restore the committed ones with"
        echo "      git -C \"$PROJECT_ROOT\" checkout -- iosApp/libs/arti"
    else
        echo "  If src/, Cargo.* or a pin changed, commit the rebuilt archives with that change:"
        echo "      git -C \"$PROJECT_ROOT\" add iosApp/libs/arti && git commit"
    fi
fi
