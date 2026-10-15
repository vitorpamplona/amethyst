# orchard-publish next to the ostree and flatpak tools that unpack a release
# .flatpak bundle into the app tree it publishes. Neither tool exists on macOS,
# so publish-orchard.sh runs the whole publish in this image.
#
# Orchard is cloned at a tag and checked against its commit, so a moved tag
# fails the build instead of building something else. Orchard is MIT-licensed;
# it is a release tool and nothing from it ships in Amethyst. Keep the pin in
# step with ORCHARD_TAG/ORCHARD_COMMIT in .github/workflows/create-release.yml,
# which builds the same tool to push the release images.
FROM debian:trixie

ARG ORCHARD_TAG=v0.1.2
ARG ORCHARD_COMMIT=087940634e83b436f96591fec4a5cc193c704383

RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl git build-essential pkg-config ostree flatpak \
    && rm -rf /var/lib/apt/lists/*
# The toolchain itself comes from Orchard's rust-toolchain.toml.
RUN curl -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain none
ENV PATH=/root/.cargo/bin:$PATH

RUN git clone --quiet --depth 1 --branch "$ORCHARD_TAG" https://github.com/Letdown2491/orchard /src/orchard \
    && test "$(git -C /src/orchard rev-parse HEAD)" = "$ORCHARD_COMMIT"
WORKDIR /src/orchard
RUN cargo build --release --locked -p orchard-publish \
    && cp target/release/orchard-publish /usr/local/bin/ \
    && rm -rf target /root/.cargo/registry

COPY publish-in-container.sh /usr/local/bin/publish-in-container.sh
WORKDIR /work
ENTRYPOINT ["/usr/local/bin/publish-in-container.sh"]
