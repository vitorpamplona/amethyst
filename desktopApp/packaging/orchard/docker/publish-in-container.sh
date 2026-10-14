#!/usr/bin/env bash
# Runs inside the image built from this directory's Dockerfile; publish-orchard.sh
# starts it. Per arch: unpack the release's .flatpak bundle into an app tree with
# ostree, then run orchard-publish on it.
#
#   dry    build and sign with a throwaway key; push nothing, send nothing
#   live   push to ghcr and send the events to the manifest's relays
#
# Mounts: /bundles (the release .flatpak files, read-only), /manifest (a copy of
# desktopApp/packaging/orchard), /proof/orchard.txt (the repo's proof file).
# Env: ORCHARD_PUBLISH_VERSION; live also needs ORCHARD_NSEC,
# ORCHARD_REGISTRY_USER and ORCHARD_REGISTRY_PASSWORD.
set -euo pipefail

MODE="${1:?usage: dry|live}"
VER="${ORCHARD_PUBLISH_VERSION:?set ORCHARD_PUBLISH_VERSION}"
APP=com.vitorpamplona.amethyst.Amethyst
REGISTRY=ghcr.io/vitorpamplona/amethyst-flatpak
EXPECTED_NPUB="$(grep -v '^#' /proof/orchard.txt | grep -o 'npub1[0-9a-z]*' | head -1)"

case "$MODE" in
  dry)
    # Without a key orchard-publish signs with a throwaway one.
    unset ORCHARD_NSEC
    EXTRA=(--skip-push --skip-relays) ;;
  live)
    [ -n "${ORCHARD_NSEC:-}" ] || { echo "live needs ORCHARD_NSEC" >&2; exit 2; }
    [ -n "${ORCHARD_REGISTRY_PASSWORD:-}" ] || { echo "live needs registry credentials" >&2; exit 2; }
    EXTRA=() ;;
  *) echo "usage: dry|live" >&2; exit 2 ;;
esac

# Live: prove the key before anything leaves. orchard-publish prints
# "publisher <hex> (<npub>)" as soon as it loads the key; this run pushes and
# sends nothing, so whatever it signs stays in the container.
if [ "$MODE" = live ]; then
  rm -rf /tmp/keycheck /manifest/tree && mkdir -p /manifest/tree/files /manifest/tree/export
  printf '[Application]\nname=%s\n' "$APP" > /manifest/tree/metadata
  got="$(orchard-publish "/manifest/$APP.publish.toml" --skip-push --skip-relays --out /tmp/keycheck 2>&1 \
          | grep -o 'npub1[0-9a-z]*' | head -1 || true)"
  rm -rf /tmp/keycheck /manifest/tree
  if [ "$got" != "$EXPECTED_NPUB" ]; then
    echo "REFUSING: this key is ${got:-unreadable}, orchard.txt names $EXPECTED_NPUB" >&2
    exit 3
  fi
  echo "key matches orchard.txt ($got)"
fi

for pair in x64:x86_64 arm64:aarch64; do
  GH=${pair%%:*}; FP=${pair##*:}
  echo "=== linux-$FP"
  rm -rf /tmp/repo /manifest/tree
  ostree init --repo=/tmp/repo --mode=archive-z2
  flatpak build-import-bundle /tmp/repo "/bundles/amethyst-desktop-$VER-linux-$GH.flatpak" 2>&1 \
    | grep --line-buffered -v 'GLib-CRITICAL' || true
  ostree checkout --repo=/tmp/repo -U "app/$APP/$FP/master" /manifest/tree
  args=("/manifest/$APP.publish.toml" --arch "linux-$FP" --out "/out/$FP" "${EXTRA[@]}")
  [ "$MODE" = live ] && args+=(--registry "$REGISTRY" --tag "latest-linux-$FP")
  # A failure stops the script (errexit + pipefail). The filter drops the
  # throwaway nsec a dry run prints.
  orchard-publish "${args[@]}" 2>&1 \
    | grep --line-buffered -v -e '^no key: generated' -e '^  nsec '
done
rm -rf /manifest/tree /tmp/repo
