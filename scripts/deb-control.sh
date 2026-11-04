#!/usr/bin/env bash
# Shared by the scripts that rewrite a jpackage-built .deb's control file
# (relax-deb-libicu.sh, add-deb-libegl-dep.sh, add-deb-gstreamer-deps.sh).
# Source it; don't run it.
#
# A .deb is an ar archive of debian-binary, control.tar.* and data.tar.*. Those
# scripts only change the control file, so this swaps the control member and
# copies the data member through untouched. Unpacking and rebuilding the whole
# package with dpkg-deb instead recompresses the ~340 MB payload every time,
# about a minute of xz per script per .deb.
#
# deb_edit_control <deb> <edit-fn>
#   Runs `<edit-fn> <path-to-control>` on a copy of the control file. If the
#   file changed, writes it back into <deb>; otherwise leaves <deb> untouched.
#   Sets DEB_CONTROL_CHANGED to 1 or 0 to say which. Call it as a plain command:
#   bash ignores `set -e` inside a function called from `if` or `||`, so a
#   failure in here would go unnoticed there.

deb_edit_control() {
    local deb="$1" edit="$2"
    local work deb_abs members=()

    mapfile -t members < <(ar t "$deb")
    if [[ ${#members[@]} -ne 3 || ${members[0]} != debian-binary ||
          ${members[1]} != control.tar* || ${members[2]} != data.tar* ]]; then
        echo "error: unexpected .deb members in $deb: ${members[*]}" >&2
        exit 1
    fi

    deb_abs="$(cd "$(dirname "$deb")" && pwd)/$(basename "$deb")"
    work="$(mktemp -d)"
    (cd "$work" && ar x "$deb_abs")
    mkdir "$work/control"
    tar -xf "$work/${members[1]}" -C "$work/control"
    cp "$work/control/control" "$work/control.orig"

    "$edit" "$work/control/control"

    if cmp -s "$work/control/control" "$work/control.orig"; then
        DEB_CONTROL_CHANGED=0
        rm -rf "$work"
        return 0
    fi

    # Same member format dpkg-deb writes: root-owned, xz (readable by every dpkg
    # that ships in a supported Debian or Ubuntu), members in the order dpkg requires.
    rm -f "$work/${members[1]}"
    tar --format=gnu --owner=0 --group=0 --numeric-owner --sort=name -C "$work/control" -cf - . |
        xz -c > "$work/control.tar.xz"
    # Built beside it and moved over it, so an interrupted run leaves the original intact.
    (cd "$work" && ar rcD out.deb debian-binary control.tar.xz "${members[2]}")
    mv -f "$work/out.deb" "$deb"
    rm -rf "$work"
    DEB_CONTROL_CHANGED=1
}
