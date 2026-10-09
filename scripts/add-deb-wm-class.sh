#!/usr/bin/env bash
# Tie the desktop app's windows to its menu entry in a jpackage-built .deb.
#
# Why this is needed: Linux docks and app switchers (GNOME, KDE, ...) find
# which app a window belongs to by matching its WM_CLASS against the
# StartupWMClass of the app's desktop entry. The app names its windows
# "Amethyst" (AmethystDesktop.kt), and the AppImage and Flatpak entries
# declare it, but jpackage's own desktop entry has no StartupWMClass. With
# the .deb, the switcher showed a generic icon and no app name, and the
# running app did not group with its launcher.
#
# Neither jpackage nor the Compose Multiplatform DSL can add a line to that
# entry (Compose empties jpackage's --resource-dir right before it runs), so
# we rewrite the .deb after the fact (same approach as
# scripts/add-deb-gstreamer-deps.sh). The .rpm gets the same line from
# desktopApp/build.gradle.kts.
#
# Usage: add-deb-wm-class.sh <path-to-deb> [<path-to-deb> ...]
set -euo pipefail

WM_CLASS='Amethyst'

# An unmatched glob reaches us as the literal pattern: fail rather than ship an unchanged .deb.
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

    # jpackage installs its entry under /opt/<package>/lib and registers it from postinst. The
    # bundled runtime has directories named like java.desktop under legal/: skip it.
    mapfile -t entries < <(find "$work/pkg" \( -path "$work/pkg/DEBIAN" -o -path "*/lib/runtime" \) -prune -o -type f -name "*.desktop" -print)
    if [[ ${#entries[@]} -eq 0 ]]; then
        echo "error: no desktop entry in $deb" >&2
        exit 1
    fi

    changed=0
    for entry in "${entries[@]}"; do
        if grep -q '^StartupWMClass=' "$entry"; then
            continue
        fi
        echo "StartupWMClass=${WM_CLASS}" >> "$entry"
        changed=1

        # Keep dpkg --verify quiet when the package lists its files' checksums.
        md5sums="$work/pkg/DEBIAN/md5sums"
        if [[ -f "$md5sums" ]]; then
            rel="${entry#"$work/pkg/"}"
            sum="$(md5sum "$entry" | cut -d' ' -f1)"
            sed -i -E "s|^[0-9a-f]{32}  ${rel}\$|${sum}  ${rel}|" "$md5sums"
        fi
    done

    if [[ $changed -eq 0 ]]; then
        echo "StartupWMClass already declared, leaving as-is: $deb"
        rm -rf "$work"
        trap - EXIT
        continue
    fi

    # Built beside it and moved over it, so an interrupted run leaves the original intact.
    dpkg-deb --root-owner-group -Zxz -b "$work/pkg" "$work/out.deb" >/dev/null
    mv -f "$work/out.deb" "$deb"
    echo "Added StartupWMClass=${WM_CLASS}: $deb"

    rm -rf "$work"
    trap - EXIT
done
