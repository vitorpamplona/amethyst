#!/usr/bin/env bash
#
# Turn an obfuscated Amethyst crash report back into real class, method, file
# and line names.
#
#   scripts/retrace.sh report.txt          # figures out the release by itself
#   pbpaste | scripts/retrace.sh           # ... or straight off the clipboard
#
# A report produced by ReportAssembler names its own build on line 1:
#
#     java.lang.IllegalStateException: 1.16.0-PLAY
#
# so that is all this needs to fetch the right mapping from the matching GitHub
# Release (amethyst-googleplay-mapping-v1.16.0.txt.gz) and cache it.
#
# If you only have a bare stack trace with no such header, name the build:
#
#   scripts/retrace.sh --release v1.16.0 --flavor play trace.txt
#
# ... or point at a mapping yourself, e.g. for a build you made locally:
#
#   scripts/retrace.sh amethyst/build/outputs/mapping/playRelease/mapping.txt trace.txt
#
# Mappings are never guessed at. Every frame of an obfuscated trace carries
# `r8-map-id-<hash>`, which is the `pg_map_id` of the one mapping that produced
# that build, so the mapping is checked against the report before any output is
# printed — a wrong mapping produces plausible, wrong answers, which is worse
# than no answer. --force overrides.
#
# The R8 that does the work is fetched at the version recorded in the mapping's
# own header, so there is no tooling to pin and this keeps working across AGP
# bumps. Everything is cached under ~/.cache/amethyst-retrace/.
set -euo pipefail

REPO="${AMETHYST_REPO:-vitorpamplona/amethyst}"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/amethyst-retrace"

MAPPING=""
RELEASE=""
FLAVOR=""
REPORT=""
FORCE=0

die() { echo "error: $*" >&2; exit 2; }

usage() {
    sed -n '3,30p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-2}"
}

# A mapping file, or the report? Decide by content, not by extension, so both
# documented argument orders keep working.
looks_like_mapping() {
    local f="$1"
    [ -f "$f" ] || return 1
    case "$f" in
        *.prt) return 0 ;;
        *.gz)  gunzip -c "$f" 2>/dev/null | head -c 200 | grep -q '^# compiler' && return 0 || return 1 ;;
        *)     head -c 200 "$f" 2>/dev/null | grep -q '^# compiler' && return 0 || return 1 ;;
    esac
}

while [ $# -gt 0 ]; do
    case "$1" in
        --release) RELEASE="${2:-}"; shift 2 ;;
        --flavor)  FLAVOR="${2:-}";  shift 2 ;;
        --mapping) MAPPING="${2:-}"; shift 2 ;;
        --force)   FORCE=1; shift ;;
        -h|--help) usage 0 ;;
        -*) die "unknown option $1 (try --help)" ;;
        *)
            if [ -z "$MAPPING" ] && [ -z "$RELEASE" ] && looks_like_mapping "$1"; then
                MAPPING="$1"
            elif [ -z "$REPORT" ]; then
                REPORT="$1"
            else
                die "unexpected argument $1"
            fi
            shift ;;
    esac
done

mkdir -p "$CACHE"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ---- materialise the report ------------------------------------------------
# Always to a file: we have to read it twice (header, map id) before retracing,
# and that is not possible on a pipe.
REPORT_FILE="$TMP/report.txt"
if [ -n "$REPORT" ]; then
    [ -f "$REPORT" ] || die "no such file: $REPORT"
    cp "$REPORT" "$REPORT_FILE"
else
    [ -t 0 ] && echo "reading the report from stdin (ctrl-D when done) ..." >&2
    cat > "$REPORT_FILE"
fi
[ -s "$REPORT_FILE" ] || die "the report is empty"

# Retrace only rewrites frames it recognises, and it recognises them by the
# leading `at`. Reports from Amethyst builds before the ReportAssembler fix
# wrote bare "    com.foo.Bar.baz(File.kt:12)" lines, which would otherwise be
# passed through still obfuscated and look like a mapping problem. Put the `at`
# back on any frame-shaped line that is missing it. Anything that is not
# <indent><dotted.name>(<something>:<digits>) is left exactly as it is.
sed -E 's/^([[:space:]]+)([A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z0-9_$<>]+)+\([^()]*:[0-9]+\))[[:space:]]*$/\1at \2/' \
    "$REPORT_FILE" > "$TMP/normalised.txt"
mv "$TMP/normalised.txt" "$REPORT_FILE"

# ---- work out which mapping --------------------------------------------------
if [ -z "$MAPPING" ]; then
    if [ -z "$RELEASE" ]; then
        # --auto: parse "<exception>: <version>-<FLAVOR>" off line 1.
        header="$(head -1 "$REPORT_FILE" | tr -d '\r')"
        case "$header" in
            *": "*) ;;
            *) die "line 1 is not an Amethyst crash-report header, so the build is unknown.
       Pass --release <tag> [--flavor play|fdroid], or a mapping file." ;;
        esac
        vf="${header#*: }"
        vf="$(echo "$vf" | tr -d '[:space:]')"
        [ -n "$vf" ] || die "line 1 has no '<version>-<FLAVOR>' after the exception name."
        parsed_flavor="${vf##*-}"
        version="${vf%-*}"
        [ -n "$parsed_flavor" ] && [ -n "$version" ] && [ "$version" != "$vf" ] \
            || die "cannot read '<version>-<FLAVOR>' out of line 1: $header"
        [ -n "$FLAVOR" ] || FLAVOR="$parsed_flavor"
        case "$version" in
            [0-9]*.[0-9]*.[0-9]*) ;;
            *) die "line 1 reports version '$version', which is not a release version." ;;
        esac
        # generateVersionName() appends the git branch on non-main builds, so a
        # dev build reads e.g. 1.16.0-my-branch-PLAY. There is no release for it.
        case "$version" in
            *[!0-9.]*) die "version '$version' carries a branch suffix, so this is a local/CI
       build, not a release — its mapping was never published. Retrace against
       that build's own amethyst/build/outputs/mapping/<variant>/mapping.txt." ;;
        esac
        RELEASE="v$version"
    fi

    case "$RELEASE" in v*) ;; *) RELEASE="v$RELEASE" ;; esac

    case "$(echo "${FLAVOR:-play}" | tr '[:upper:]' '[:lower:]')" in
        play|googleplay) channel=googleplay ;;
        fdroid)          channel=fdroid ;;
        *) die "unknown flavor '$FLAVOR' (expected play or fdroid)" ;;
    esac

    asset="amethyst-${channel}-mapping-${RELEASE}.txt.gz"
    MAPPING="$CACHE/$asset"
    if [ ! -s "$MAPPING" ]; then
        url="https://github.com/${REPO}/releases/download/${RELEASE}/${asset}"
        echo "fetching $asset ..." >&2
        curl -fsSL -o "$MAPPING.tmp" "$url" || {
            rm -f "$MAPPING.tmp"
            die "could not download $url
       Either that release predates mapping publication (builds up to v1.16.0
       were not obfuscated — read those traces as-is), or the flavor is wrong.
       Assets: gh release view $RELEASE --json assets --jq '.assets[].name'"
        }
        mv "$MAPPING.tmp" "$MAPPING"
    fi
fi

[ -f "$MAPPING" ] || die "no such mapping: $MAPPING"

# ---- decompress if needed --------------------------------------------------
PARTITION=0
case "$MAPPING" in
    *.prt) PARTITION=1 ;;
    *.gz)
        plain="$CACHE/$(basename "${MAPPING%.gz}")"
        if [ ! -s "$plain" ] || [ "$MAPPING" -nt "$plain" ]; then
            echo "decompressing $(basename "$MAPPING") ..." >&2
            gunzip -c "$MAPPING" > "$plain"
        fi
        MAPPING="$plain"
        ;;
esac

# ---- make sure this mapping really built this report ------------------------
if [ "$PARTITION" -eq 0 ]; then
    trace_id="$(grep -om1 'r8-map-id-[0-9a-f]\{16,\}' "$REPORT_FILE" | sed 's/^r8-map-id-//' || true)"
    map_id="$(grep -m1 '^# pg_map_id:' "$MAPPING" | sed 's/.*: *//' || true)"
    if [ -z "$trace_id" ]; then
        echo "note: no r8-map-id in the report (an un-obfuscated build, or a trimmed" >&2
        echo "      trace) — cannot confirm the mapping matches." >&2
    elif [ "$trace_id" != "$map_id" ]; then
        msg="this mapping did not build this report.
       report:  $trace_id
       mapping: $map_id ($(basename "$MAPPING"))
       Retracing anyway yields wrong names that look right. Check the release
       tag and the flavor (play vs fdroid are separate R8 runs)."
        [ "$FORCE" -eq 1 ] && echo "warning: $msg" >&2 || die "$msg"
    fi
fi

# ---- fetch the R8 that wrote it ---------------------------------------------
if [ "$PARTITION" -eq 1 ]; then
    R8_VERSION="${R8_VERSION:-$(ls "$CACHE"/r8-*.jar 2>/dev/null | sed 's/.*r8-\(.*\)\.jar/\1/' | sort -V | tail -1 || true)}"
    [ -n "$R8_VERSION" ] || die "a .prt partition map does not expose its R8 version.
       Set R8_VERSION=<x.y.z> (the 'compiler_version' of the matching
       mapping.txt), or retrace against the .txt.gz instead."
else
    R8_VERSION="$(sed -n 's/^# compiler_version: //p' "$MAPPING" | head -1)"
    [ -n "$R8_VERSION" ] || die "no '# compiler_version:' header in $MAPPING — not an R8 mapping?"
fi

R8_JAR="$CACHE/r8-${R8_VERSION}.jar"
if [ ! -s "$R8_JAR" ]; then
    echo "fetching R8 ${R8_VERSION} ..." >&2
    curl -fsSL -o "$R8_JAR.tmp" \
        "https://maven.google.com/com/android/tools/r8/${R8_VERSION}/r8-${R8_VERSION}.jar" \
        || { rm -f "$R8_JAR.tmp"; die "could not download R8 ${R8_VERSION} from Google's Maven"; }
    mv "$R8_JAR.tmp" "$R8_JAR"
fi

# ---- retrace ---------------------------------------------------------------
if [ "$PARTITION" -eq 1 ]; then
    exec java -cp "$R8_JAR" com.android.tools.r8.retrace.Retrace \
        --partition-map "$MAPPING" "$REPORT_FILE"
else
    exec java -cp "$R8_JAR" com.android.tools.r8.retrace.Retrace \
        "$MAPPING" "$REPORT_FILE"
fi
