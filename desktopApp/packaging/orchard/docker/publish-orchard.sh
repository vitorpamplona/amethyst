#!/usr/bin/env bash
# Publish the release's Flatpak to Orchard from any machine with Docker, macOS
# included: ostree and flatpak, which unpack the release bundles, only exist on
# Linux, so the unpacking and orchard-publish run in a container.
#
#   desktopApp/packaging/orchard/docker/publish-orchard.sh dry    # nothing leaves
#   desktopApp/packaging/orchard/docker/publish-orchard.sh live
#
# The version is `app` in gradle/libs.versions.toml; its v<version> GitHub
# Release must already have the two linux .flatpak assets. Both arches publish
# in one run, and live refuses to push or send anything unless the key's npub is
# the one orchard.txt names.
#
# live reads Amethyst's nsec from ORCHARD_NSEC if set, else from the file
# ORCHARD_NSEC_FILE (default ~/.orchard-nsec). Create the file in your own shell
# so the key stays out of history, and delete it afterwards:
#   zsh:  read -rs "k?Amethyst nsec: "; echo; (umask 077; printf %s "$k" > ~/.orchard-nsec); unset k
#   bash: read -rsp "Amethyst nsec: " k; echo; (umask 077; printf %s "$k" > ~/.orchard-nsec); unset k
# The ghcr login comes from `gh` (needs `gh auth refresh -s write:packages`).
# Secrets reach the container as environment variables, never as arguments.
#
# Requires: docker, gh; bash 3.2 (macOS) is enough. Downloads go to build/orchard/.
set -euo pipefail

MODE="${1:-}"
case "$MODE" in dry|live) ;; *) sed -n '2,22p' "$0"; exit 2 ;; esac

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$HERE/../../../.." && pwd)"
ORCHARD_DIR="$(dirname "$HERE")"
WORK="$REPO_ROOT/build/orchard"
IMAGE=amethyst-orchard-publish

command -v docker >/dev/null || { echo "docker not found" >&2; exit 1; }
command -v gh >/dev/null || { echo "gh not found" >&2; exit 1; }

VER="$(grep -E '^app[[:space:]]*=' "$REPO_ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
[ -n "$VER" ] || { echo "no app version in gradle/libs.versions.toml" >&2; exit 1; }
echo "Amethyst $VER, $MODE run"

if [ "$MODE" = live ]; then
  if [ -z "${ORCHARD_NSEC:-}" ]; then
    KEY_FILE="${ORCHARD_NSEC_FILE:-$HOME/.orchard-nsec}"
    [ -s "$KEY_FILE" ] || { echo "no key: set ORCHARD_NSEC or create $KEY_FILE (see the header)" >&2; exit 2; }
    ORCHARD_NSEC="$(< "$KEY_FILE")"
  fi
  ORCHARD_REGISTRY_USER="$(gh api user -q .login)"
  ORCHARD_REGISTRY_PASSWORD="$(gh auth token)"
  export ORCHARD_NSEC ORCHARD_REGISTRY_USER ORCHARD_REGISTRY_PASSWORD
  SECRETS=(-e ORCHARD_NSEC -e ORCHARD_REGISTRY_USER -e ORCHARD_REGISTRY_PASSWORD)
else
  SECRETS=()
fi

echo "--- building $IMAGE (cached after the first run)"
docker build --quiet -t "$IMAGE" "$HERE" >/dev/null

echo "--- downloading the v$VER bundles"
mkdir -p "$WORK/bundles"
gh release download "v$VER" --repo vitorpamplona/amethyst --dir "$WORK/bundles" --skip-existing \
  -p "amethyst-desktop-$VER-linux-x64.flatpak" -p "amethyst-desktop-$VER-linux-arm64.flatpak"

# A copy of the listing config, so the extracted tree never lands in the repo.
rm -rf "$WORK/manifest" "$WORK/out"
mkdir -p "$WORK/manifest" "$WORK/out"
cp "$ORCHARD_DIR"/*.publish.toml "$ORCHARD_DIR"/description.md "$WORK/manifest/"

echo "--- publishing"
docker run --rm ${SECRETS[@]+"${SECRETS[@]}"} -e ORCHARD_PUBLISH_VERSION="$VER" \
  -v "$WORK/bundles:/bundles:ro" -v "$WORK/manifest:/manifest" -v "$WORK/out:/out" \
  -v "$REPO_ROOT/orchard.txt:/proof/orchard.txt:ro" \
  "$IMAGE" "$MODE" 2>&1 | grep --line-buffered -v 'nsec1' | tee "$WORK/$MODE.log"

if [ "$MODE" = live ]; then
  echo "--- done. Log: $WORK/live.log"
  echo "If this was the package's first push, make it public (Orchard can't pull a private one):"
  echo "  https://github.com/users/vitorpamplona/packages/container/amethyst-flatpak/settings"
  if [ -n "${KEY_FILE:-}" ]; then echo "Delete the key file: rm $KEY_FILE"; fi
fi
