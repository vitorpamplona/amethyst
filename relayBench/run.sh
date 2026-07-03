#!/usr/bin/env bash
#
# relaybench — one-command Nostr relay benchmark (geode vs strfry vs anything).
#
#   ./relayBench/run.sh                  # deterministic 10k synthetic corpus
#   ./relayBench/run.sh --quick          # 2k-event smoke run
#   ./relayBench/run.sh --real           # replay the checked-in real-event dump
#   ./relayBench/run.sh --corpus f.gz --limit 50000
#   ./relayBench/run.sh --relay 'myrelay=/path/bin --port {port} --db {dir}'
#
# strfry resolution order: $STRFRY_BIN → `strfry` on PATH → build from
# source into relayBench/.cache (needs the deps listed in the error text).
# Skip strfry entirely with SKIP_STRFRY=1; skip geode with SKIP_GEODE=1.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$HERE")"
CACHE="$HERE/.cache"

ARGS=()
for arg in "$@"; do
    case "$arg" in
        --real)
            ARGS+=("--corpus" "$ROOT/quartz/src/commonTest/resources/nostr_vitor_startup_data.json.gz")
            ;;
        *)
            ARGS+=("$arg")
            ;;
    esac
done

# ---------- strfry ----------
resolve_strfry() {
    if [[ -n "${STRFRY_BIN:-}" ]]; then
        echo "$STRFRY_BIN"
        return
    fi
    if command -v strfry > /dev/null 2>&1; then
        command -v strfry
        return
    fi
    if [[ -x "$CACHE/strfry/strfry" ]]; then
        echo "$CACHE/strfry/strfry"
        return
    fi
    echo "building strfry from source (first run only — a few minutes)…" >&2
    mkdir -p "$CACHE"
    if [[ ! -d "$CACHE/strfry" ]]; then
        git clone --depth 1 https://github.com/hoytech/strfry.git "$CACHE/strfry" >&2
    fi
    (
        cd "$CACHE/strfry"
        git submodule update --init --depth 1 >&2
        make setup-golpe >&2
        make -j"$(nproc)" >&2
    ) || {
        cat >&2 << 'EOF'

strfry build failed. On Debian/Ubuntu install its deps with:

  sudo apt install build-essential libyaml-perl libtemplate-perl \
       libregexp-grammars-perl libssl-dev zlib1g-dev liblmdb-dev \
       libflatbuffers-dev libsecp256k1-dev libzstd-dev

or point STRFRY_BIN at an existing binary, or SKIP_STRFRY=1 to bench without it.
EOF
        return 1
    }
    echo "$CACHE/strfry/strfry"
}

STRFRY=""
if [[ "${SKIP_STRFRY:-0}" != "1" ]]; then
    STRFRY="$(resolve_strfry)" || exit 1
    echo "strfry: $STRFRY"
fi

# ---------- build geode + harness ----------
echo "building geode + relaybench…"
GRADLE_TASKS=(":relayBench:installDist")
if [[ "${SKIP_GEODE:-0}" != "1" ]]; then
    GRADLE_TASKS+=(":geode:installDist")
fi
(cd "$ROOT" && ./gradlew -q "${GRADLE_TASKS[@]}")

RELAY_FLAGS=()
if [[ "${SKIP_GEODE:-0}" != "1" ]]; then
    RELAY_FLAGS+=("--geode-bin" "$ROOT/geode/build/install/geode/bin/geode")
fi
if [[ -n "$STRFRY" ]]; then
    RELAY_FLAGS+=("--strfry-bin" "$STRFRY")
fi

# ---------- run ----------
cd "$ROOT"
exec "$ROOT/relayBench/build/install/relaybench/bin/relaybench" \
    "${RELAY_FLAGS[@]}" \
    "${ARGS[@]}"
