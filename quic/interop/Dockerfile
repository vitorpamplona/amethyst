# quic-interop-runner endpoint image for :quic.
#
# Build (from repo root):
#   ./gradlew :quic-interop:installDist
#   docker build -t amethyst-quic-interop -f quic/interop/Dockerfile .
#
# Or run `make build` inside quic/interop/.
FROM martenseemann/quic-network-simulator-endpoint:latest

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY quic/interop/build/install/quic-interop /opt/quic-interop
COPY quic/interop/run_endpoint.sh /run_endpoint.sh
RUN chmod +x /run_endpoint.sh /opt/quic-interop/bin/quic-interop

# Build-time toggle for verbose tracing. Pass --build-arg DEBUG=1 (or
# `make build DEBUG=1` via the Makefile) to bake QUIC_INTEROP_DEBUG into
# the image so the InteropClient + writer emit per-drain stats to stderr.
# Off by default — production matrix runs stay quiet.
ARG DEBUG=0
ENV QUIC_INTEROP_DEBUG=${DEBUG}
