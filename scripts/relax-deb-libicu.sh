#!/usr/bin/env bash
# Broaden the build-host-pinned Depends clauses in a jpackage-built .deb so it
# installs across Debian/Ubuntu releases.
#
# jpackage shells out to `dpkg-shlibdeps` against the bundled JDK runtime's
# native libraries, which pins each Depends to the package name the build host
# (ubuntu-24.04) ships, even though the bundled JRE runs fine against older
# releases. Two kinds of pin are relaxed here:
#
# 1. libicu: libfontmanager.so etc. link libicu, pinned to libicu74. Users on
#    Ubuntu 22.04 (libicu70), Debian 12 (libicu72), Debian 11 (libicu67) or
#    Debian 13 (libicu76) could not install the package.
#
# 2. The 64-bit time_t transition: Ubuntu 24.04 and Debian 13 renamed many
#    libraries with a t64 suffix (libasound2 -> libasound2t64, libpng16-16 ->
#    libpng16-16t64). Ubuntu 22.04, Debian 12 and their derivatives (Mint 21,
#    Pop!_OS 22.04, Zorin 17) have only the old names, so apt, gdebi and the App
#    Center refused the package. Each `<name>t64` becomes `<name>t64 | <name>`:
#    newer releases pick the t64 package, older ones the original.
#
# Neither jpackage nor the Compose Multiplatform DSL exposes a way to override
# the auto-generated Depends, so we rewrite the .deb after the fact.
#
# Usage: relax-deb-libicu.sh <path-to-deb> [<path-to-deb> ...]
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/deb-control.sh"

# Spans Debian 11 → 13 and Ubuntu 20.04 → 26.04 (libicu78). Append new SONAMEs here when
# a new Debian/Ubuntu release ships a bumped libicu.
ALT='libicu66 | libicu67 | libicu70 | libicu72 | libicu74 | libicu76 | libicu77 | libicu78'

relax_control() {
    local control="$1"

    sed -i -E "s/libicu[0-9]+([[:space:]]*\\|[[:space:]]*libicu[0-9]+)*/${ALT}/g" "$control"

    # Only clauses that name a single package are rewritten, so a clause that
    # already lists alternatives (including one this script wrote) is left alone.
    perl -i -pe '
        if (/^(Pre-Depends|Depends|Recommends):(.*)$/) {
            my ($field, $list) = ($1, $2);
            my @clauses = map {
                /^\s*([a-z0-9][a-z0-9.+-]*?)t64(\s*\([^)]*\))?\s*$/
                    ? " $1t64" . ($2 // "") . " | $1"
                    : $_
            } split(/,/, $list, -1);
            $_ = "$field:" . join(",", @clauses) . "\n";
        }
    ' "$control"
}

# An unmatched glob reaches us as the literal pattern: fail rather than ship an unrelaxed .deb.
if [[ $# -eq 0 ]]; then
    echo "error: no .deb given" >&2
    exit 1
fi

for deb in "$@"; do
    if [[ ! -f "$deb" ]]; then
        echo "error: not a file: $deb" >&2
        exit 1
    fi

    deb_edit_control "$deb" relax_control
    if [[ $DEB_CONTROL_CHANGED -eq 1 ]]; then
        echo "Relaxed pinned deps: $deb"
        dpkg-deb -f "$deb" Depends
    else
        echo "No pinned deps to relax, leaving as-is: $deb"
    fi
done
