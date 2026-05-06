#!/usr/bin/env bash
# quic-interop-runner endpoint entry point.
#
# The base image (martenseemann/quic-network-simulator-endpoint) provides
# /setup.sh, which configures routing for the runner's ns-3 sim. Source it
# inside the runner; tolerate failure (e.g. missing NET_ADMIN cap) so the
# JVM client still launches if a caller invokes the image outside the
# runner — they get default container networking instead.
set -uo pipefail

# shellcheck disable=SC1091
source /setup.sh 2>/dev/null || echo "(setup.sh skipped — not under the runner sim)" >&2

set -e

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
