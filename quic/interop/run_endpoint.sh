#!/usr/bin/env bash
# quic-interop-runner endpoint entry point.
#
# The base image (martenseemann/quic-network-simulator-endpoint) provides
# /setup.sh, which configures routing for the runner's ns-3 sim. We try
# to source it but tolerate failure so smoke tests outside the runner
# (without --privileged / the sim's network) still launch the JVM client.
# Inside the runner, /setup.sh succeeds because the runner provides the
# necessary capabilities.
set -uo pipefail

# shellcheck disable=SC1091
source /setup.sh 2>/dev/null || echo "(setup.sh skipped — running outside the runner sim)" >&2

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
