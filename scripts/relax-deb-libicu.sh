#!/usr/bin/env bash
# Broaden the libicu Depends clause in a jpackage-built .deb so it installs
# across Debian/Ubuntu releases.
#
# jpackage shells out to `dpkg-shlibdeps` against the bundled JDK runtime's
# native libraries (libfontmanager.so, etc., which link libicu). That pins the
# Depends to whatever libicu the build host ships — libicu74 on ubuntu-24.04 —
# even though the bundled JRE works fine against any reasonably recent ICU.
# Without this rewrite, users on Ubuntu 22.04 (libicu70), Debian 12 (libicu72),
# Debian 11 (libicu67), or Debian 13 (libicu76) cannot install the package.
#
# Neither jpackage nor the Compose Multiplatform DSL exposes a way to override
# the auto-generated Depends, so we rewrite the .deb after the fact.
#
# Usage: relax-deb-libicu.sh <path-to-deb> [<path-to-deb> ...]
set -euo pipefail

# Spans Debian 11 → 13 and Ubuntu 20.04 → 26.04. Append new SONAMEs here when
# a new Debian/Ubuntu release ships a bumped libicu.
ALT='libicu66 | libicu67 | libicu70 | libicu72 | libicu74 | libicu76 | libicu77'

for deb in "$@"; do
    if [[ ! -f "$deb" ]]; then
        echo "skip: not a file: $deb" >&2
        continue
    fi

    work="$(mktemp -d)"
    dpkg-deb -R "$deb" "$work/pkg"
    control="$work/pkg/DEBIAN/control"

    if grep -qE 'libicu[0-9]+' "$control"; then
        sed -i -E "s/libicu[0-9]+([[:space:]]*\\|[[:space:]]*libicu[0-9]+)*/${ALT}/g" "$control"
        dpkg-deb -b "$work/pkg" "$deb" >/dev/null
        echo "Relaxed libicu dep: $deb"
    else
        echo "No libicu dep, leaving as-is: $deb"
    fi

    rm -rf "$work"
done
