#!/usr/bin/env bash
# Declare the GStreamer packages the desktop app plays video and audio with in a
# jpackage-built .deb.
#
# Why this is needed: the media player (kdroidFilter ComposeMediaPlayer) ships a
# small native library that drives the system's GStreamer at runtime. It is
# unpacked from a jar when the first video plays, so neither jpackage's
# dpkg-shlibdeps pass nor anything else sees it, and the .deb declared no
# GStreamer at all. On a system without the plugins every video showed
# "Can't play this video — Failed to create native player".
#
# What the player uses, and where it comes from:
#   playbin, appsink, volume, videoconvert   gstreamer1.0-plugins-base
#   souphttpsrc, qtdemux, HLS, autoaudiosink gstreamer1.0-plugins-good
#   H.264 / AAC decoders                     gstreamer1.0-libav
# base and good are in Debian and Ubuntu main, so they are hard Depends.
# libav is in Ubuntu universe, so it is a Recommends: apt installs it by
# default, and a system without universe can still install the app.
#
# Neither jpackage nor the Compose Multiplatform DSL exposes a way to add to
# the auto-generated Depends, so we rewrite the .deb after the fact (same
# approach as scripts/relax-deb-libicu.sh and scripts/add-deb-libegl-dep.sh).
#
# Usage: add-deb-gstreamer-deps.sh <path-to-deb> [<path-to-deb> ...]
set -euo pipefail

DEPENDS='gstreamer1.0-plugins-base, gstreamer1.0-plugins-good'
RECOMMENDS='gstreamer1.0-libav'

# An unmatched glob reaches us as the literal pattern: fail rather than ship a .deb without the deps.
if [[ $# -eq 0 ]]; then
    echo "error: no .deb given" >&2
    exit 1
fi

for deb in "$@"; do
    if [[ ! -f "$deb" ]]; then
        echo "error: not a file: $deb" >&2
        exit 1
    fi

    work="$(mktemp -d)"
    trap 'rm -rf "$work"' EXIT
    dpkg-deb -R "$deb" "$work/pkg"
    control="$work/pkg/DEBIAN/control"

    if grep -qE '(^|[ ,])gstreamer1\.0-plugins-base([ ,]|$)' "$control"; then
        echo "GStreamer already declared, leaving as-is: $deb"
        rm -rf "$work"
        trap - EXIT
        continue
    fi

    # A binary control file is one stanza; jpackage's template can end in a blank
    # line (its Homepage slot), after which an appended field would start a second.
    sed -i '/^[[:space:]]*$/d' "$control"

    # jpackage writes Depends as one physical line, possibly empty
    # ("Depends: " when it found nothing to depend on).
    if grep -qE '^Depends:[[:space:]]*[^[:space:]]' "$control"; then
        sed -i -E "s/^(Depends:.*[^,[:space:]])[[:space:]]*\$/\\1, ${DEPENDS}/" "$control"
    elif grep -qE '^Depends:' "$control"; then
        sed -i -E "s/^Depends:.*\$/Depends: ${DEPENDS}/" "$control"
    else
        echo "Depends: ${DEPENDS}" >> "$control"
    fi

    if grep -qE '^Recommends:' "$control"; then
        sed -i -E "s/^(Recommends:.*[^,[:space:]])[[:space:]]*\$/\\1, ${RECOMMENDS}/" "$control"
    else
        echo "Recommends: ${RECOMMENDS}" >> "$control"
    fi

    # Built beside it and moved over it, so an interrupted run leaves the original intact.
    dpkg-deb --root-owner-group -Zxz -b "$work/pkg" "$work/out.deb" >/dev/null
    mv -f "$work/out.deb" "$deb"
    echo "Added GStreamer deps: $deb"

    rm -rf "$work"
    trap - EXIT
done
