#!/usr/bin/env bash
# quic-interop-runner endpoint entry point.
#
# The base image (martenseemann/quic-network-simulator-endpoint) sets up
# routing in /setup.sh; we source it then dispatch to our JVM client based
# on the ROLE env var pushed in by the runner.
set -euo pipefail

# shellcheck disable=SC1091
source /setup.sh

case "${ROLE:-client}" in
    client)
        exec /opt/quic-interop/bin/quic-interop
        ;;
    server)
        # Phase 0: server role unimplemented. Exit 127 so the runner skips.
        echo "server role not implemented" >&2
        exit 127
        ;;
    *)
        echo "unknown ROLE=${ROLE}" >&2
        exit 127
        ;;
esac
