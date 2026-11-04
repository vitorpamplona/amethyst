#!/usr/bin/env bash
# Ensure a jpackage-built desktop .deb declares libxkbcommon0, which the
# bundled JetBrains Runtime loads to read the keyboard on Wayland.
#
# Why this is needed: the Linux packages bundle the JetBrains Runtime so the
# app can draw natively on Wayland (WLToolkit, lib/runtime/lib/libawt_wlawt.so;
# see desktopApp/.../platform/WaylandDisplay.kt). jpackage's dpkg-shlibdeps
# pass picks up what that library links (libwayland-client0,
# libwayland-cursor0), but libxkbcommon is opened with dlopen at runtime, so it
# never makes it into Depends. Every Wayland desktop already has it (GTK and
# the compositors depend on it); a minimal install might not, and then keys
# don't reach the app.
#
# Same approach as scripts/add-deb-libegl-dep.sh: rewrite the .deb after the fact.
#
# Usage: add-deb-wayland-deps.sh <path-to-deb> [<path-to-deb> ...]
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/deb-control.sh"

add_libxkbcommon() {
    local control="$1"

    if grep -qE '(^| )libxkbcommon0( |,|$)' "$control"; then
        return
    fi

    sed -i -E 's/^(Depends: .*[^,[:space:]])[[:space:]]*$/\1, libxkbcommon0/' "$control"
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

    # Only a runtime with the Wayland toolkit needs it (a .deb built on a stock JDK has none).
    # The listing is captured first: `grep -q` on a pipe can exit before dpkg-deb finishes, and
    # pipefail would read that SIGPIPE as no match.
    listing="$(dpkg-deb -c "$deb")"
    if ! grep -q '/libawt_wlawt\.so$' <<< "$listing"; then
        echo "No Wayland toolkit in the bundled runtime, leaving as-is: $deb"
        continue
    fi

    deb_edit_control "$deb" add_libxkbcommon
    if [[ $DEB_CONTROL_CHANGED -eq 1 ]]; then
        echo "Added libxkbcommon0 dep: $deb"
    else
        echo "libxkbcommon0 already in Depends, leaving as-is: $deb"
    fi
done
