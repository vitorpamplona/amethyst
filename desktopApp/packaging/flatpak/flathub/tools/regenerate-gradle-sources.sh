#!/usr/bin/env bash
# Regenerate gradle-sources.json, the Maven files the Flathub build reads
# offline, for a release tag. Run it whenever a release changes dependencies.
#
#   desktopApp/packaging/flatpak/flathub/tools/regenerate-gradle-sources.sh v1.20.0
#
# For each arch it runs the manifest's own build command online, in a Debian
# container on the JetBrains Runtime the manifest pins, with a fresh Gradle
# home; the module cache then holds exactly what the build resolves. The two
# caches become one list (the arch-specific jars of both, which each arch
# ignores the other's of). x86_64 runs under emulation on an arm64 host (and
# the reverse), so expect ~10 min native plus ~25 min emulated.
#
# Requires: docker, curl, python3. Writes ../gradle-sources.json and prints the
# tag tarball's sha256 for the manifest's archive source. Work files go to
# build/flathub-regen/ at the repo root.
set -euo pipefail

TAG="${1:?usage: regenerate-gradle-sources.sh vX.Y.Z}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(dirname "$HERE")"
WORK="$(cd "$HERE/../../../../.." && pwd)/build/flathub-regen/$TAG"
JBR_BUILD=21.0.11-linux-%s-b1163.116   # keep in step with the manifest's jbrsdk sources
mkdir -p "$WORK/dl"

curl -fsSL -o "$WORK/dl/src.tar.gz" "https://github.com/vitorpamplona/amethyst/archive/refs/tags/$TAG.tar.gz"
sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }
echo "archive sha256: $(sha256 "$WORK/dl/src.tar.gz")"

capture() {  # docker-platform jbr-arch
  local platform=$1 arch=$2 jbr
  jbr="jbrsdk-$(printf "$JBR_BUILD" "$arch").tar.gz"
  [ -f "$WORK/dl/$jbr" ] || curl -fsSL -o "$WORK/dl/$jbr" "https://cache-redirector.jetbrains.com/intellij-jbr/$jbr"
  rm -rf "$WORK/cap-$arch" && mkdir -p "$WORK/cap-$arch"
  docker run --rm --platform "$platform" -v "$WORK/dl:/dl:ro" -v "$WORK/cap-$arch:/cap" debian:trixie bash -c "
    set -e
    apt-get update -qq >/dev/null && apt-get install -y -qq --no-install-recommends ca-certificates binutils >/dev/null
    mkdir -p /jbr /src && tar xzf /dl/$jbr -C /jbr --strip-components=1 && tar xzf /dl/src.tar.gz -C /src --strip-components=1
    cd /src && JAVA_HOME=/jbr GRADLE_USER_HOME=/cap/gradle-home ./gradlew --no-daemon --console=plain \
      :desktopApp:createReleaseDistributable > /cap/build.log 2>&1 || { tail -40 /cap/build.log; exit 1; }"
  echo "captured $arch"
}

capture linux/arm64 aarch64
capture linux/amd64 x64

python3 -I "$HERE/gradle-cache-to-sources.py" "$WORK/cap-aarch64/gradle-home" "$OUT/gradle-sources.json" \
  "$WORK/cap-x64/gradle-home"
echo "wrote $OUT/gradle-sources.json; delete $WORK when done"
