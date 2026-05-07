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
        # Wait for the simulator's readiness port BEFORE first send.
        # The sim sets up ns3 + tcpdump capture asynchronously; until
        # sim:57832 opens, packets we send are blackholed and never
        # show up in the pcap. The longrtt test in particular checks
        # for ≥2 ClientHellos in the trace — if our first Initial leaves
        # before sim is capturing, only the PTO retransmit lands in the
        # pcap and the test fails with "Expected at least 2 ClientHellos".
        # aioquic, picoquic, quic-go all gate their client launch on
        # this same probe. Skip if the wait helper isn't on PATH so
        # off-runner invocations (no sim) keep working.
        if [ -x /wait-for-it.sh ]; then
            /wait-for-it.sh sim:57832 -s -t 30 || \
                echo "(wait-for-it sim:57832 timed out; continuing anyway)" >&2
        fi
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
