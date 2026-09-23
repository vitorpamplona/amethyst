# shellcheck shell=bash
#
# stack.sh — boot the cordn test stack: a geode relay plus the REFERENCE
# coordinator in Docker. Sourced by tier-b.sh and interop-client.sh.
#
# ─────────────────────────────────────────────────────────────────────────────
# The reference coordinator (`ghcr.io/cordn-msg/cordn`, and the
# `packages/coordinator` / `packages/server` sources it is built from) ships
# with NO LICENSE — default copyright, all rights reserved. See §7 of
# quartz/plans/2026-09-17-cordn-interop.md.
#
# Nothing here is wired into a build: no Gradle task, no CI job, and nothing
# pulls the image for you. You pull it by hand having decided that is
# something you want to do. Do not add these scripts to a build file.
# ─────────────────────────────────────────────────────────────────────────────
#
# The caller sets WORK (a scratch directory) and may set PORT. After
# `stack_up`, these are exported:
#
#   RELAY   ws://127.0.0.1:$PORT
#   COORD   the coordinator's pubkey, read from its own startup log
#
# `stack_down` is registered by the caller's EXIT trap; KEEP=1 skips it.

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
AMY="$ROOT/cli/build/install/amy/bin/amy"
GEODE="$ROOT/geode/build/install/geode/bin/geode"
IMAGE="ghcr.io/cordn-msg/cordn:latest"

PORT="${PORT:-7447}"
RELAY="ws://127.0.0.1:$PORT"
CONTAINER="${CONTAINER:-cordn-test}"
GEODE_PID=""

# Prereqs, each with its own message. A stopped daemon and an unpulled image
# both fail `docker image inspect`, and telling someone to pull an image they
# cannot pull sends them the wrong way.
stack_require() {
    for f in "$AMY" "$GEODE"; do
        [ -x "$f" ] || {
            echo "missing $f — run ./gradlew :cli:installDist :geode:installDist"
            exit 2
        }
    done
    docker info >/dev/null 2>&1 || {
        echo "the docker daemon is not reachable — start it (e.g. 'sudo dockerd &' or 'systemctl start docker') and retry"
        exit 2
    }
    docker image inspect "$IMAGE" >/dev/null 2>&1 || {
        echo "missing $IMAGE — pull it by hand, and read the licence note at the top of this file first"
        exit 2
    }
}

stack_up() {
    "$GEODE" --port "$PORT" >"$WORK/geode.log" 2>&1 &
    GEODE_PID=$!
    for _ in $(seq 30); do
        curl -sS --noproxy '*' -H 'Accept: application/nostr+json' "http://127.0.0.1:$PORT/" >/dev/null 2>&1 && break
        sleep 1
    done

    # The container has to reach geode, which is on the host's loopback.
    #
    # On Linux `--network host` puts it there. On Docker Desktop (macOS,
    # Windows) the daemon is inside a VM, so `--network host` is the *VM's*
    # loopback and 127.0.0.1:$PORT is nothing at all: the coordinator retries
    # "Relay connection error" until stack_up gives up on a pubkey that was
    # never going to arrive. There the host is reachable by name instead, over
    # the default bridge.
    #
    # A stable key so the coordinator pubkey survives a re-run against the
    # same WORK directory.
    if [ "$(uname -s)" = "Linux" ]; then
        COORD_NET="--network host"
        COORD_RELAY="$RELAY"
    else
        COORD_NET="--add-host=host.docker.internal:host-gateway"
        COORD_RELAY="ws://host.docker.internal:$PORT"
    fi

    [ -f "$WORK/coordinator.key" ] || openssl rand -hex 32 >"$WORK/coordinator.key"
    docker rm -f "$CONTAINER" >/dev/null 2>&1
    # shellcheck disable=SC2086  # COORD_NET is two words on purpose
    docker run -d --name "$CONTAINER" $COORD_NET \
        -e CORDN_STORAGE_BACKEND=memory \
        -e CORDN_ANNOUNCED=false \
        -e CORDN_RELAY_URLS="$COORD_RELAY" \
        -e CORDN_SERVER_PRIVATE_KEY="$(cat "$WORK/coordinator.key")" \
        -e CORDN_SERVER_NAME="cordn-test" \
        "$IMAGE" >/dev/null || { echo "could not start $CONTAINER"; exit 1; }

    # Read the pubkey out of its own startup log rather than deriving it: the
    # coordinator is the authority on its identity, and a key we derived
    # wrongly would fail later as an unreachable coordinator.
    COORD=""
    for _ in $(seq 60); do
        COORD=$(docker logs "$CONTAINER" 2>&1 | grep -oE 'serverPubkey":"[0-9a-f]{64}' | head -1 | cut -d'"' -f3)
        [ -n "$COORD" ] && break
        sleep 1
    done
    [ -n "$COORD" ] || {
        echo "coordinator never announced its pubkey"
        docker logs "$CONTAINER" | tail -20
        exit 1
    }
}

stack_down() {
    if [ "${KEEP:-0}" != "1" ]; then
        docker rm -f "$CONTAINER" >/dev/null 2>&1
        [ -n "$GEODE_PID" ] && kill "$GEODE_PID" 2>/dev/null
    else
        echo
        echo "KEEP=1: relay on $RELAY, coordinator $CONTAINER ($COORD), state in $WORK"
    fi
}
