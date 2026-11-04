#!/usr/bin/env bash
# Install the desktop app's menu entry the Debian way in a jpackage-built .deb,
# and give the package its final compression.
#
# 1. StartupWMClass. Linux docks and app switchers (GNOME, KDE, ...) find which
#    app a window belongs to by matching its WM_CLASS against the
#    StartupWMClass of the app's desktop entry. The app names its windows
#    "Amethyst" (AmethystDesktop.kt), and the AppImage and Flatpak entries
#    declare it, but jpackage's own desktop entry has no StartupWMClass. With
#    the .deb, the switcher showed a generic icon and no app name, and the
#    running app did not group with its launcher.
#
# 2. The entry as a package file. jpackage leaves the entry under
#    /opt/<package>/lib and registers it from postinst with
#    `xdg-desktop-menu install`, under `set -e`. xdg-desktop-menu exits 3 ("No
#    writable system menu directory found") when /usr/share/desktop-directories
#    is missing, as on minimal and non-GNOME installs without gnome-menus, so the
#    package was left half-configured and apt, gdebi and the App Center all
#    reported the install as failed. The entry is now a file the package owns in
#    /usr/share/applications, under the name xdg-desktop-menu gave its copy (so
#    pinned launchers keep working and an upgrade replaces that copy), and
#    postinst/prerm no longer call xdg-desktop-menu. desktop-file-utils' dpkg
#    trigger refreshes the menu cache. An upgrade from an older .deb still runs
#    that version's prerm, which removes the copy it registered; if that fails,
#    jpackage's prerm answers dpkg's `failed-upgrade` call with success and the
#    upgrade goes on.
#
# 3. Compression. This is the one step that rewrites the payload, so it decides
#    how the shipped .deb is compressed: always xz, whatever jpackage's
#    dpkg-deb defaulted to (zstd on Ubuntu) and whatever ran before. The steps
#    that only edit the control file (scripts/deb-control.sh) copy the payload
#    through untouched, so the order the steps run in doesn't matter.
#
# Neither jpackage nor the Compose Multiplatform DSL can change any of this
# (Compose empties jpackage's --resource-dir right before it runs), so we
# rewrite the .deb after the fact. The .rpm gets StartupWMClass from
# desktopApp/build.gradle.kts; rpm treats a failed %post as a warning, so the
# .rpm keeps jpackage's registration.
#
# Usage: fix-deb-desktop-entry.sh <path-to-deb> [<path-to-deb> ...]
set -euo pipefail

WM_CLASS='Amethyst'
# xz: smaller downloads than zstd, and readable by every dpkg in a supported Debian or Ubuntu.
COMPRESSION='xz'

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
    changes=()

    # jpackage installs its entry under /opt/<package>/lib. The bundled runtime has
    # directories named like java.desktop under legal/: skip it.
    mapfile -t entries < <(find "$work/pkg/opt" -path "*/lib/runtime" -prune -o -type f -name "*.desktop" -print)
    if [[ ${#entries[@]} -eq 0 ]]; then
        echo "error: no desktop entry under /opt in $deb" >&2
        exit 1
    fi

    for entry in "${entries[@]}"; do
        if ! grep -q '^StartupWMClass=' "$entry"; then
            echo "StartupWMClass=${WM_CLASS}" >> "$entry"
            changes+=("StartupWMClass=${WM_CLASS}")
        fi

        # URI schemes: the desktop offers Amethyst for nostr:/amethyst: links and passes the
        # URI as the launcher's argument (DesktopDeepLinks.extractFromArgs).
        if ! grep -q '^MimeType=' "$entry"; then
            echo 'MimeType=x-scheme-handler/nostr;x-scheme-handler/amethyst;' >> "$entry"
            changes+=("MimeType=x-scheme-handler/nostr;x-scheme-handler/amethyst;")
        fi
        if grep -q '^Exec=' "$entry" && ! grep -qE '^Exec=.*%u' "$entry"; then
            sed -i -E 's|^Exec=(.+)$|Exec=\1 %u|' "$entry"
            changes+=("Exec gets %u")
        fi

        shipped="$work/pkg/usr/share/applications/$(basename "$entry")"
        if ! cmp -s "$entry" "$shipped"; then
            install -D -m 644 "$entry" "$shipped"
            changes+=("/usr/share/applications/$(basename "$entry")")
        fi
    done

    for script in postinst prerm; do
        file="$work/pkg/DEBIAN/$script"
        if [[ -f "$file" ]] && grep -qE '^[[:space:]]*xdg-desktop-menu[[:space:]]' "$file"; then
            sed -i -E '/^[[:space:]]*xdg-desktop-menu[[:space:]]/d' "$file"
            changes+=("no xdg-desktop-menu in $script")
        fi
    done
    # jpackage also registers icons and MIME types through xdg-utils when an app
    # declares file associations. This app declares none; if that changes, those
    # calls can fail the same way and need the same treatment.
    if grep -lE 'xdg-(desktop-menu|icon-resource|mime)' "$work/pkg/DEBIAN/"*; then
        echo "error: maintainer scripts above still call xdg-utils; extend $0 to cover them" >&2
        exit 1
    fi

    if [[ ${#changes[@]} -gt 0 && -f "$work/pkg/DEBIAN/md5sums" ]]; then
        # Keep dpkg --verify quiet when the package lists its files' checksums.
        (cd "$work/pkg" && find . -path ./DEBIAN -prune -o -type f -printf '%P\0' | sort -z | xargs -0 md5sum) > "$work/md5sums"
        mv -f "$work/md5sums" "$work/pkg/DEBIAN/md5sums"
    fi

    data="$(ar t "$deb" | grep '^data\.tar' || true)"
    if [[ ${#changes[@]} -eq 0 && $data == "data.tar.${COMPRESSION}" ]]; then
        echo "Desktop entry already installed, leaving as-is: $deb"
        rm -rf "$work"
        trap - EXIT
        continue
    fi
    if [[ $data != "data.tar.${COMPRESSION}" ]]; then
        changes+=("${data:-payload} -> data.tar.${COMPRESSION}")
    fi

    # Built beside it and moved over it, so an interrupted run leaves the original intact.
    dpkg-deb --root-owner-group "-Z${COMPRESSION}" -b "$work/pkg" "$work/out.deb" >/dev/null
    mv -f "$work/out.deb" "$deb"
    echo "Fixed desktop entry: $deb"
    printf '  %s\n' "${changes[@]}"

    rm -rf "$work"
    trap - EXIT
done
