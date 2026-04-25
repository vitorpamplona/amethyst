#!/usr/bin/env bash
# Spin up Christian Huitema's picoquic reference server on UDP 4433.
# Picoquic is the IETF QUIC WG's reference impl and the most permissive
# server for a brand-new client to interop against — it logs detailed
# qlog traces and accepts a wide range of transport parameters.
#
# Usage:
#   quic/scripts/run-picoquic.sh                  # foreground, ^C to stop
#   quic/scripts/run-picoquic.sh -d               # detached
#
# Then in another terminal, drive our client:
#   ./gradlew :quic:jvmTest --tests '*InteropRunner*' \
#     -DinteropHost=127.0.0.1 -DinteropPort=4433
#
# Or compile + run the runner main directly:
#   ./gradlew :quic:jvmTestClasses
#   java -cp 'quic/build/classes/kotlin/jvm/{main,test}:…' \
#     com.vitorpamplona.quic.interop.InteropRunnerKt 127.0.0.1 4433

set -euo pipefail

DETACH=${1:-}

IMAGE="privateoctopus/picoquic:latest"
CONTAINER="amethyst-picoquic-interop"

echo "Pulling $IMAGE..."
docker pull "$IMAGE" >/dev/null

# Stop any prior instance so re-runs are idempotent.
docker rm -f "$CONTAINER" 2>/dev/null || true

if [ "$DETACH" = "-d" ]; then
  docker run -d --name "$CONTAINER" -p 4433:4433/udp "$IMAGE" \
    picoquicdemo -p 4433
  echo "picoquic started (container=$CONTAINER) on UDP 4433"
  echo "Stop with: docker rm -f $CONTAINER"
else
  exec docker run --rm --name "$CONTAINER" -p 4433:4433/udp "$IMAGE" \
    picoquicdemo -p 4433
fi
