#!/usr/bin/env bash
# Ensure a jpackage-built desktop .deb declares libegl1 as a runtime dep on
# arm64.
#
# Why this is needed: the compose-desktop skiko native shipped in
# ${app}/lib/app/libskiko-linux-arm64.so has libEGL.so.1 in DT_NEEDED (the
# aarch64 build uses EGL alongside GLX, unlike the x86_64 skiko which only
# links libGL.so.1). jpackage's --type deb only auto-generates Depends from
# dpkg-shlibdeps against the bundled JRE under ${app}/lib/runtime/, NOT the
# ${app}/lib/app/ tree — so libegl1 never makes it into the arm64 .deb.
#
# On most desktop Linux systems libegl1 is already installed as a transitive
# of the desktop environment. But minimal aarch64 installs (Armbian Server +
# a lightweight WM, Raspberry Pi OS Lite + LXDE, etc.) can miss it. Without
# libegl1 the app dies at startup with:
#
#   Exception in thread "main" org.jetbrains.skiko.LibraryLoadException:
#     Failed to loade library …/libskiko-linux-arm64.so
#   Caused by: java.lang.UnsatisfiedLinkError:
#     libEGL.so.1: cannot open shared object file: No such file or directory
#
# Neither jpackage nor the Compose Multiplatform 1.11 DSL exposes a way to
# override the auto-generated Depends, so we rewrite the .deb after the fact
# (same approach as scripts/relax-deb-libicu.sh).
#
# Usage: add-deb-libegl-dep.sh <path-to-deb> [<path-to-deb> ...]
set -euo pipefail

for deb in "$@"; do
    if [[ ! -f "$deb" ]]; then
        echo "skip: not a file: $deb" >&2
        continue
    fi

    work="$(mktemp -d)"
    trap 'rm -rf "$work"' EXIT
    dpkg-deb -R "$deb" "$work/pkg"
    control="$work/pkg/DEBIAN/control"

    # Only touch .debs whose payload actually contains the arm64 skiko native.
    # Applying this to x64 .debs is harmless but the whole point is to be
    # surgical.
    if ! find "$work/pkg" -type f -name 'libskiko-linux-arm64.so' | grep -q .; then
        echo "No arm64 skiko in payload, leaving as-is: $deb"
        rm -rf "$work"
        trap - EXIT
        continue
    fi

    if grep -qE '(^| )libegl1( |,|$)' "$control"; then
        echo "libegl1 already in Depends, leaving as-is: $deb"
        rm -rf "$work"
        trap - EXIT
        continue
    fi

    # Append libegl1 to the Depends line. jpackage-generated lines are single
    # physical lines, e.g.
    #   Depends: libasound2t64, ..., zlib1g
    # We insert `, libegl1` before the trailing newline.
    sed -i -E 's/^(Depends: .*[^,[:space:]])[[:space:]]*$/\1, libegl1/' "$control"

    dpkg-deb --root-owner-group -Zxz -b "$work/pkg" "$deb" >/dev/null
    echo "Added libegl1 dep: $deb"

    rm -rf "$work"
    trap - EXIT
done
