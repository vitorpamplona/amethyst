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

source "$(dirname "${BASH_SOURCE[0]}")/deb-control.sh"

add_libegl() {
    local control="$1"

    if grep -qE '(^| )libegl1( |,|$)' "$control"; then
        return
    fi

    # Append libegl1 to the Depends line. jpackage-generated lines are single
    # physical lines, e.g.
    #   Depends: libasound2t64, ..., zlib1g
    # We insert `, libegl1` before the trailing newline.
    sed -i -E 's/^(Depends: .*[^,[:space:]])[[:space:]]*$/\1, libegl1/' "$control"
}

# An unmatched glob reaches us as the literal pattern: fail rather than ship a .deb without the dep.
if [[ $# -eq 0 ]]; then
    echo "error: no .deb given" >&2
    exit 1
fi

for deb in "$@"; do
    if [[ ! -f "$deb" ]]; then
        echo "error: not a file: $deb" >&2
        exit 1
    fi

    # Only touch .debs whose payload actually contains the arm64 skiko native.
    # Applying this to x64 .debs is harmless but the whole point is to be
    # surgical. The listing is captured first: `grep -q` on a pipe can exit
    # before dpkg-deb finishes, and pipefail would read that SIGPIPE as no match.
    listing="$(dpkg-deb -c "$deb")"
    if ! grep -q '/libskiko-linux-arm64\.so$' <<< "$listing"; then
        echo "No arm64 skiko in payload, leaving as-is: $deb"
        continue
    fi

    deb_edit_control "$deb" add_libegl
    if [[ $DEB_CONTROL_CHANGED -eq 1 ]]; then
        echo "Added libegl1 dep: $deb"
    else
        echo "libegl1 already in Depends, leaving as-is: $deb"
    fi
done
