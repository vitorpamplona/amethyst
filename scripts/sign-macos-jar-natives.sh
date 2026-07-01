#!/usr/bin/env bash
#
# sign-macos-jar-natives.sh — codesign the macOS Mach-O native libraries that
# ship *inside* bundled jars (jna, secp256k1, sqlite-bundled, skiko, jkeychain,
# kdroidFilter mediaplayer, …).
#
# Why this exists: `codesign` on an .app bundle and a `find … -type f` signing
# loop both only see *loose* Mach-O files — neither descends into `.jar`
# archives. Apple's notary service, however, recursively inspects every jar and
# rejects any unsigned Mach-O inside it ("Notarization status: Invalid"). So we
# unzip each native, sign it with hardened runtime + a secure timestamp, and
# write it back into the jar. Run this BEFORE the enclosing .app/tarball is
# sealed and notarized.
#
# Usage:
#   SIGN_IDENTITY="Developer ID Application: NAME (TEAMID)" \
#     scripts/sign-macos-jar-natives.sh <dir-containing-jars> [<dir2> ...]
#
# Idempotent: re-signing an already-signed native just replaces the signature.
# No-op (exit 0) when SIGN_IDENTITY is empty, so unsigned local/PR builds are
# unaffected — exactly like the rest of the release signing path.
set -euo pipefail

IDENTITY="${SIGN_IDENTITY:-}"
if [ -z "$IDENTITY" ]; then
  echo "sign-macos-jar-natives: SIGN_IDENTITY unset — skipping (unsigned build)."
  exit 0
fi

if [ "$#" -lt 1 ]; then
  echo "usage: SIGN_IDENTITY=… $0 <dir-with-jars> [<dir2> ...]" >&2
  exit 2
fi

# `jar` (from the JDK) updates a single entry in place without rewriting the
# rest of the archive, so we don't disturb the manifest or other entries.
JAR_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
command -v "$JAR_BIN" >/dev/null 2>&1 || JAR_BIN="jar"

signed_total=0
jars_touched=0

sign_one_jar() {
  local jar="$1"
  # Candidate native entries by extension. The `file` check below is the real
  # gate — it filters out the many Linux ELF `.so` siblings (jna, sqlite, …)
  # and catches macOS Mach-O hiding behind a `.so` name (jkeychain's
  # osxkeychain.so). zipinfo -1 lists entry paths only.
  local entries
  entries="$(zipinfo -1 "$jar" 2>/dev/null | grep -iE '\.(dylib|jnilib|so)$' || true)"
  [ -n "$entries" ] || return 0

  local work jar_abs dirty=0
  work="$(mktemp -d)"
  jar_abs="$(cd "$(dirname "$jar")" && pwd)/$(basename "$jar")"

  while IFS= read -r entry; do
    [ -n "$entry" ] || continue
    ( cd "$work" && unzip -o -q "$jar_abs" "$entry" )
    local f="$work/$entry"
    [ -f "$f" ] || continue
    case "$(file -b "$f")" in
      *Mach-O*)
        codesign --force --options runtime --timestamp --sign "$IDENTITY" "$f" >/dev/null 2>&1
        codesign --verify --strict "$f"
        ( cd "$work" && "$JAR_BIN" uf "$jar_abs" "$entry" )
        echo "    signed: $(basename "$jar") :: $entry"
        signed_total=$((signed_total + 1))
        dirty=1
        ;;
    esac
  done <<< "$entries"

  [ "$dirty" -eq 1 ] && jars_touched=$((jars_touched + 1))
  rm -rf "$work"
}

for dir in "$@"; do
  [ -d "$dir" ] || { echo "  (skip, not a dir: $dir)"; continue; }
  echo "Scanning for jar-embedded macOS natives under: $dir"
  while IFS= read -r jar; do
    sign_one_jar "$jar"
  done < <(find "$dir" -type f -name '*.jar')
done

echo "sign-macos-jar-natives: signed $signed_total native(s) across $jars_touched jar(s)."
