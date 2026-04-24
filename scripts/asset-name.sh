#!/usr/bin/env bash
# Single source of truth for Amethyst release asset naming.
#
# Two product lines share this contract:
#   amethyst-desktop-<version>-<family>-<arch>.<ext>   (Compose Desktop app)
#   amy-<version>-<family>-<arch>.<ext>                (Amy CLI — cli/ module)
#
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
#   collect_assets     <family> <arch> <version> <dest_dir>   # desktop
#   collect_cli_assets <family> <arch> <version> <dest_dir>   # amy CLI
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
#   amy-1.08.0-macos-arm64.tar.gz
#   amy-1.08.0-macos-x64.tar.gz
#   amy-1.08.0-linux-x64.tar.gz
#   amy-1.08.0-linux-x64.deb
#   amy-1.08.0-linux-x64.rpm

set -euo pipefail

# Print the canonical asset filename for a given family/arch/version/extension.
# Usage: asset_name <family> <arch> <version> <ext>
asset_name() {
    local family="$1" arch="$2" version="$3" ext="$4"
    printf 'amethyst-desktop-%s-%s-%s.%s' "$version" "$family" "$arch" "$ext"
}

# CLI variant of asset_name — same shape, different product prefix.
# Usage: cli_asset_name <family> <arch> <version> <ext>
cli_asset_name() {
    local family="$1" arch="$2" version="$3" ext="$4"
    printf 'amy-%s-%s-%s.%s' "$version" "$family" "$arch" "$ext"
}

# Copy + rename build outputs into <dest_dir> using the canonical naming scheme.
# Usage: collect_assets <family> <arch> <version> <dest_dir>
# Expects build outputs under desktopApp/build/... (Compose binaries + custom tasks + portable archives).
collect_assets() {
    local family="$1" arch="$2" version="$3" dest="$4"
    # Normalize internal matrix family aliases to canonical asset-name families.
    [[ "$family" == "linux-portable" ]] && family="linux"
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

# Copy + rename amy CLI build outputs into <dest_dir> using the canonical
# amy-<version>-<family>-<arch>.<ext> scheme.
#
# Expected inputs:
#   cli/build/amy-image/amy/    flat app-image built by :cli:amyImage
#                               (bin/amy + lib/*.jar + runtime/)
#   cli/build/jpackage/*.deb    from :cli:jpackageDeb (Linux only)
#   cli/build/jpackage/*.rpm    from :cli:jpackageRpm (Linux only)
#
# Usage: collect_cli_assets <family> <arch> <version> <dest_dir>
collect_cli_assets() {
    local family="$1" arch="$2" version="$3" dest="$4"
    mkdir -p "$dest"
    local abs_dest
    abs_dest="$(cd "$dest" && pwd)"
    shopt -s nullglob

    # 1. Tar the flat app-image into amy-<version>-<family>-<arch>.tar.gz.
    #    This is the portable-across-OS asset — macOS runners produce only
    #    this one.
    local app_image="cli/build/amy-image/amy"
    if [ -d "$app_image" ]; then
        local tarball="$abs_dest/$(cli_asset_name "$family" "$arch" "$version" tar.gz)"
        ( cd "$(dirname "$app_image")" && tar czf "$tarball" "$(basename "$app_image")" )
        echo "Collected: $tarball"
    fi

    # 2. Linux native installers (.deb, .rpm). jpackage writes them directly
    #    to cli/build/jpackage/ with its own naming, so we re-copy under the
    #    canonical scheme.
    local src base ext dst
    for src in \
        cli/build/jpackage/*.deb \
        cli/build/jpackage/*.rpm \
    ; do
        [ -f "$src" ] || continue
        base="$(basename "$src")"
        ext="${base##*.}"
        dst="$abs_dest/$(cli_asset_name "$family" "$arch" "$version" "$ext")"
        cp "$src" "$dst"
        echo "Collected: $dst"
    done
    shopt -u nullglob
}
