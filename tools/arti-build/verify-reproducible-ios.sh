#!/usr/bin/env bash
#
# Verify that the iOS Arti archives (build-arti-ios.sh) build reproducibly, and that the ones
# committed in iosApp/libs/arti are those bytes. The iOS counterpart of verify-reproducible.sh.
#
# Builds twice from clean. The second build runs from a copy of tools/arti-build at another path,
# with a fresh, empty CARGO_HOME (its path has a space), another HOME and another TMPDIR, so it
# sees what a second machine sees: another checkout, another crate registry path, a cold
# download. A path that leaks into the archive shows up here as a difference, which two builds
# in one place cannot reveal. Neither build inherits this shell's environment beyond PATH's
# rustup, RUSTUP_HOME, DEVELOPER_DIR and proxy settings. Both compile at the canonical path
# (/tmp/amethyst-arti-build), as build-arti.sh does for Android.
#
# Build 1 writes the committed location (build-arti-ios.sh stages its output and moves it into
# place only when the build succeeded), so after a run `git diff iosApp/libs/arti` shows whether
# the committed archives are the reproducible ones. With --any-xcode it writes a scratch
# directory instead, since its bytes cannot be the committed ones.
#
# Usage:
#   ./verify-reproducible-ios.sh                   # both slices
#   ./verify-reproducible-ios.sh --target=aarch64-apple-ios
#   ./verify-reproducible-ios.sh --any-xcode       # another Xcode than IOS_XCODE_VERSION: checks
#                                                  # reproducibility only, leaves iosApp/libs/arti
#
# Exit 0 = reproducible, 1 = not (or the wrong Xcode).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LIBS="$PROJECT_ROOT/iosApp/libs/arti"
XCODE_PIN=$(tr -d '[:space:]' < "$SCRIPT_DIR/IOS_XCODE_VERSION")

BUILD_ARGS=(--clean)
ANY_XCODE=false
for arg in "$@"; do
    case $arg in
        --target=*) BUILD_ARGS+=("$arg") ;;
        --any-xcode) ANY_XCODE=true; BUILD_ARGS+=("$arg") ;;
        --help) sed -n '2,26p' "$0"; exit 0 ;;
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

# rustup's proxies, as build-arti-ios.sh finds them.
CARGO_BIN=""
for dir in "${CARGO_HOME:-$HOME/.cargo}/bin" "$HOME/.cargo/bin" /opt/homebrew/opt/rustup/bin /usr/local/opt/rustup/bin; do
    if [ -x "$dir/cargo" ] && [ -x "$dir/rustup" ]; then CARGO_BIN="$dir"; break; fi
done
[ -n "$CARGO_BIN" ] || { echo "✗ cargo/rustup not found (install rustup)" >&2; exit 1; }

# What both builds get from here; each adds its own HOME, TMPDIR and CARGO_HOME.
BASE_ENV=(env -i "PATH=$CARGO_BIN:/usr/bin:/bin:/usr/sbin:/sbin" "RUSTUP_HOME=${RUSTUP_HOME:-$HOME/.rustup}" "LC_ALL=C")
for var in USER LOGNAME DEVELOPER_DIR ARTI_REPRO_DIR \
    HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY http_proxy https_proxy all_proxy no_proxy \
    SSL_CERT_FILE SSL_CERT_DIR; do
    if [ -n "${!var:-}" ]; then BASE_ENV+=("$var=${!var}"); fi
done

# "<sha256>  <triple>/libarti.a" for every archive under a libs dir, sorted.
hashes() {
    (cd "$1" && for a in */libarti.a; do [ -f "$a" ] && shasum -a 256 "$a"; done)
}

WORK="$(mktemp -d "${TMPDIR:-/tmp}/arti-ios-verify.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

echo "### Reproducibility check for the iOS Arti archives"
echo "### Xcode $XCODE_FOUND (pinned: $XCODE_PIN); canonical build path ${ARTI_REPRO_DIR:-/tmp/amethyst-arti-build}"
echo

if [ "$ANY_XCODE" = true ]; then
    OUT1="$WORK/libs1"
    echo "### Build 1 of 2 (clean, this checkout, into a scratch dir: --any-xcode)…"
else
    OUT1="$LIBS"
    echo "### Build 1 of 2 (clean, this checkout, into iosApp/libs/arti)…"
fi
CARGO_HOME_ENV=()
if [ -n "${CARGO_HOME:-}" ]; then CARGO_HOME_ENV=("CARGO_HOME=$CARGO_HOME"); fi
"${BASE_ENV[@]}" "HOME=$HOME" "TMPDIR=${TMPDIR:-/tmp}" ${CARGO_HOME_ENV[@]+"${CARGO_HOME_ENV[@]}"} \
    "ARTI_IOS_OUT_DIR=$OUT1" "$SCRIPT_DIR/build-arti-ios.sh" "${BUILD_ARGS[@]}"
H1="$(hashes "$OUT1")"
echo "--- build 1 ---"; echo "$H1"; echo

echo "### Build 2 of 2 (clean, a copy of tools/arti-build elsewhere, empty CARGO_HOME, other HOME and TMPDIR)…"
COPY="$WORK/elsewhere/arti-build"
OUT2="$WORK/libs2"
mkdir -p "$COPY" "$WORK/home" "$WORK/tmp"
# The sources only: not the git-ignored build products (out/, a stray target/ or .arti-source/).
(cd "$SCRIPT_DIR" && tar --exclude ./out --exclude ./target --exclude ./.arti-source -cf - .) | (cd "$COPY" && tar -xf -)
# TMPDIR ends in "/", which once leaked as "…T//…" (README.md -> "iOS reproducibility").
"${BASE_ENV[@]}" "HOME=$WORK/home" "TMPDIR=$WORK/tmp/" "CARGO_HOME=$WORK/cargo home" \
    "ARTI_IOS_OUT_DIR=$OUT2" "$COPY/build-arti-ios.sh" "${BUILD_ARGS[@]}"
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
if [ -f "$OUT1/BUILD_INFO" ] && [ -f "$OUT2/BUILD_INFO" ] && ! cmp -s "$OUT1/BUILD_INFO" "$OUT2/BUILD_INFO" \
    && [ "$(hashes "$OUT1" | wc -l)" = "$(hashes "$OUT2" | wc -l)" ]; then
    echo "❌ The two BUILD_INFO files differ (the input digest must not depend on the checkout):"
    diff "$OUT1/BUILD_INFO" "$OUT2/BUILD_INFO" || true
    exit 1
fi

echo
if [ "$ANY_XCODE" = true ]; then
    echo "### --any-xcode: iosApp/libs/arti left as it is (another Xcode gives other bytes)."
    exit 0
fi
echo "### vs. the committed archives:"
if ! git -C "$PROJECT_ROOT" ls-files --error-unmatch iosApp/libs/arti/aarch64-apple-ios/libarti.a \
    iosApp/libs/arti/aarch64-apple-ios-sim/libarti.a >/dev/null 2>&1; then
    echo "⚠ No committed archives yet: git -C \"$PROJECT_ROOT\" add iosApp/libs/arti && git commit"
elif git -C "$PROJECT_ROOT" diff --quiet -- iosApp/libs/arti; then
    echo "✓ The reproducible build matches what's committed (archives and BUILD_INFO)."
else
    git -C "$PROJECT_ROOT" diff --stat -- iosApp/libs/arti
    echo "⚠ The reproducible build differs from what's committed."
    echo "  If src/, Cargo.*, a pin or build-arti-ios.sh changed, commit the rebuilt archives and"
    echo "  BUILD_INFO with that change:"
    echo "      git -C \"$PROJECT_ROOT\" add iosApp/libs/arti && git commit"
fi
