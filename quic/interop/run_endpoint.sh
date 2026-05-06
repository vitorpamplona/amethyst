#!/usr/bin/env bash
# quic-interop-runner endpoint entry point.
#
# The base image (martenseemann/quic-network-simulator-endpoint) provides
# /setup.sh, which configures routing for the runner's ns-3 sim. We source
# it inside the runner — but for `make smoke` runs against a plain Docker
# bridge network, sourcing /setup.sh succeeds AND breaks DNS resolution
# (it points the resolver at the sim's nameserver which doesn't exist
# in smoke mode). Set SMOKE_MODE=1 to skip /setup.sh entirely.
set -uo pipefail

if [ "${SMOKE_MODE:-0}" = "1" ]; then
    echo "(smoke mode — skipping /setup.sh, using container default networking)" >&2
else
    # shellcheck disable=SC1091
    source /setup.sh 2>/dev/null || echo "(setup.sh skipped — running outside the runner sim)" >&2
fi

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
