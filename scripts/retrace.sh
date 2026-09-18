#!/usr/bin/env bash
#
# Retrace an obfuscated Amethyst stack trace back to real class, method, file
# and line names.
#
#   scripts/retrace.sh <mapping> [stacktrace-file]      # trace on stdin if omitted
#
# <mapping> is the mapping file for the EXACT build the crash came from:
#   * mapping-<flavor>-<tag>.txt.gz   — attached to every GitHub Release
#   * mapping.prt                     — R8's partition map (faster, same content)
#   * amethyst/build/outputs/mapping/<variant>/mapping.txt  — a local build
# .txt, .txt.gz and .prt are all accepted.
#
# Picking the right one is not guesswork: since the release build is minified,
# R8 replaces every class's SourceFile attribute with `r8-map-id-<hash>`, and
# that hash is the `pg_map_id` on line 6 of the matching mapping file. A trace
# therefore names its own mapping — grep the releases for that id.
#
# The R8 jar that does the work is fetched from Google's Maven at the version
# recorded in the mapping's own header, so this keeps working across AGP bumps
# with nothing to pin. It is cached under ~/.cache/amethyst-retrace/.
set -euo pipefail

MAP="${1:-}"
TRACE="${2:-}"

if [ -z "$MAP" ] || [ ! -f "$MAP" ]; then
    echo "usage: $0 <mapping.txt|mapping.txt.gz|mapping.prt> [stacktrace-file]" >&2
    exit 2
fi

CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/amethyst-retrace"
mkdir -p "$CACHE"

# ---- resolve the mapping to a plain .txt (Retrace cannot read .gz) ----------
PARTITION=0
case "$MAP" in
    *.prt)
        PARTITION=1
        ;;
    *.gz)
        PLAIN="$CACHE/$(basename "${MAP%.gz}")"
        if [ ! -s "$PLAIN" ] || [ "$MAP" -nt "$PLAIN" ]; then
            echo "decompressing $(basename "$MAP") ..." >&2
            gunzip -c "$MAP" > "$PLAIN"
        fi
        MAP="$PLAIN"
        ;;
esac

# ---- fetch the matching R8 ------------------------------------------------
if [ "$PARTITION" -eq 1 ]; then
    # A partition map is a zip; its header is not greppable. Fall back to the
    # newest R8 we already have, else ask for an explicit version.
    R8_VERSION="${R8_VERSION:-}"
    if [ -z "$R8_VERSION" ]; then
        R8_VERSION=$(ls "$CACHE"/r8-*.jar 2>/dev/null | sed 's/.*r8-\(.*\)\.jar/\1/' | sort -V | tail -1 || true)
    fi
    if [ -z "$R8_VERSION" ]; then
        echo "error: cannot read the R8 version out of a .prt partition map." >&2
        echo "       Set R8_VERSION=<x.y.z> (see 'compiler_version' in the matching" >&2
        echo "       mapping.txt), or retrace against the .txt.gz instead." >&2
        exit 2
    fi
else
    R8_VERSION=$(head -c 4096 "$MAP" | sed -n 's/^# compiler_version: //p' | head -1)
    if [ -z "$R8_VERSION" ]; then
        echo "error: no '# compiler_version:' header in $MAP — is it really an R8 mapping?" >&2
        exit 2
    fi
fi

R8_JAR="$CACHE/r8-${R8_VERSION}.jar"
if [ ! -s "$R8_JAR" ]; then
    echo "fetching R8 ${R8_VERSION} ..." >&2
    curl -fsSL -o "$R8_JAR.tmp" \
        "https://maven.google.com/com/android/tools/r8/${R8_VERSION}/r8-${R8_VERSION}.jar"
    mv "$R8_JAR.tmp" "$R8_JAR"
fi

# ---- retrace ---------------------------------------------------------------
if [ "$PARTITION" -eq 1 ]; then
    exec java -cp "$R8_JAR" com.android.tools.r8.retrace.Retrace \
        --partition-map "$MAP" ${TRACE:+"$TRACE"}
else
    exec java -cp "$R8_JAR" com.android.tools.r8.retrace.Retrace \
        "$MAP" ${TRACE:+"$TRACE"}
fi
