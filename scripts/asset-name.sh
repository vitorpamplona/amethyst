#!/usr/bin/env bash
# Single source of truth for Amethyst Desktop release asset naming.
#
# Contract: amethyst-desktop-<version>-<family>-<arch>.<ext>
#   <version> = tag stripped of leading 'v' (e.g. "1.08.0")
#   <family>  = macos | windows | linux
#   <arch>    = x64 | arm64
#   <ext>     = dmg | msi | zip | deb | rpm | AppImage | tar.gz
#
# Consumed by: .github/workflows/create-release.yml, .github/workflows/bump-*.yml,
#              BUILDING.md, local release runbooks.
#
# Usage:
#   source scripts/asset-name.sh
#   collect_assets <family> <arch> <version> <dest_dir>
#
# Expected examples:
#   amethyst-desktop-1.08.0-macos-x64.dmg
#   amethyst-desktop-1.08.0-macos-arm64.dmg
#   amethyst-desktop-1.08.0-windows-x64.msi
#   amethyst-desktop-1.08.0-windows-x64.zip
#   amethyst-desktop-1.08.0-linux-x64.deb
#   amethyst-desktop-1.08.0-linux-x64.rpm
#   amethyst-desktop-1.08.0-linux-x64.AppImage
#   amethyst-desktop-1.08.0-linux-x64.tar.gz

set -euo pipefail

# Print the canonical asset filename for a given family/arch/version/extension.
# Usage: asset_name <family> <arch> <version> <ext>
asset_name() {
    local family="$1" arch="$2" version="$3" ext="$4"
    printf 'amethyst-desktop-%s-%s-%s.%s' "$version" "$family" "$arch" "$ext"
}

# Copy + rename build outputs into <dest_dir> using the canonical naming scheme.
# Usage: collect_assets <family> <arch> <version> <dest_dir>
# Expects build outputs under desktopApp/build/... (Compose binaries + custom tasks + portable archives).
collect_assets() {
    local family="$1" arch="$2" version="$3" dest="$4"
    mkdir -p "$dest"
    shopt -s nullglob

    # Compose Desktop jpackage outputs (main-release/<format>/*.ext)
    local src ext base dst
    for src in \
        desktopApp/build/compose/binaries/main-release/dmg/*.dmg \
        desktopApp/build/compose/binaries/main-release/msi/*.msi \
        desktopApp/build/compose/binaries/main-release/deb/*.deb \
        desktopApp/build/compose/binaries/main-release/rpm/*.rpm \
        desktopApp/build/appimage/*.AppImage \
        desktopApp/build/portable/*.tar.gz \
        desktopApp/build/portable/*.zip \
    ; do
        [ -f "$src" ] || continue
        base="$(basename "$src")"
        case "$base" in
            *.tar.gz) ext="tar.gz" ;;
            *)        ext="${base##*.}" ;;
        esac
        dst="$dest/$(asset_name "$family" "$arch" "$version" "$ext")"
        cp "$src" "$dst"
        echo "Collected: $dst"
    done
    shopt -u nullglob
}
