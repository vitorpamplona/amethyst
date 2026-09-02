#!/usr/bin/env python3
"""Convert Android string-resource escaping to what Compose resources understand.

Android's aapt and Compose Multiplatform do NOT share escaping rules. Moving a
strings.xml from `res/values/` to `composeResources/values/` verbatim therefore
renders some strings wrong -- the login screen showed `Don\\'t have a Nostr account?`
with a literal backslash.

Compose 1.11.1 `handleSpecialCharacters` (compose-gradle-plugin) resolves ONLY:

    \\uXXXX, \\n, \\t     and collapses \\\\ -> \\

It does not handle these Android conventions, so this script applies them itself:

    \\'  -> '        \\"  -> "        \\?  -> ?        \\@  -> @
    "…"  -> …        (Android wraps a value in quotes to preserve leading/trailing
                      spaces; Compose would render the quotes literally)

\\n, \\t, \\uXXXX and \\\\ are deliberately left alone -- Compose already resolves
them, and converting them here would double-process.

Usage:  fix_escapes.py <file-or-dir> [...]        (idempotent; safe to re-run)
"""
import re
import sys
import os

STRING_EL = re.compile(r"(<(?:string|item)\b[^>]*>)(.*?)(</(?:string|item)>)", re.S)
# `tools:` attributes are an Android-lint construct. They arrive with strings moved out of
# res/values/, whose <resources> root declares xmlns:tools -- composeResources roots do not,
# so the prefix is unbound and the XML is malformed. Compose parses namespace-unaware and
# drops them silently today, but nothing should rely on that, and Android lint never runs on
# composeResources so they carry no meaning there either.
TOOLS_ATTR = re.compile(r'\s+tools:[\w.-]+="[^"]*"')
TOOLS_NS = re.compile(r'\s+xmlns:tools="[^"]*"')
# a backslash escape NOT itself preceded by a backslash
ANDROID_ONLY = re.compile(r"(?<!\\)\\(['\"?@])")


def fix_text(text: str, unwrap_quotes: bool = True) -> str:
    """Convert one element's text.

    NOTE: quote-unwrapping is NOT idempotent and must run exactly once per file, at
    migration time. Android wraps a value in quotes to protect whitespace, but after
    `\"` has been converted to `"` a legitimately quoted value is indistinguishable
    from a wrapped one -- a second pass strips the real quotes. Repair runs over
    already-migrated files must therefore pass unwrap_quotes=False.
    """
    if unwrap_quotes and len(text) >= 2 and text.startswith('"') and text.endswith('"'):
        text = text[1:-1]
    return ANDROID_ONLY.sub(r"\1", text)


def strip_android_only_attrs(src: str) -> tuple:
    """Drop `tools:` attributes (and any xmlns:tools) that mean nothing here."""
    out, n = TOOLS_ATTR.subn("", src)
    out, m = TOOLS_NS.subn("", out)
    return out, n + m


def fix_file(path: str, unwrap_quotes: bool = True) -> int:
    src = open(path, encoding="utf-8").read()
    changed = 0

    def repl(m):
        nonlocal changed
        fixed = fix_text(m.group(2), unwrap_quotes)
        if fixed != m.group(2):
            changed += 1
        return m.group(1) + fixed + m.group(3)

    out = STRING_EL.sub(repl, src)
    out, stripped = strip_android_only_attrs(out)
    changed += stripped
    if changed:
        open(path, "w", encoding="utf-8").write(out)
    return changed


def main(paths, unwrap_quotes=True):
    total_files = total_entries = 0
    for p in paths:
        files = []
        if os.path.isdir(p):
            for root, _, names in os.walk(p):
                files += [os.path.join(root, n) for n in names if n.endswith(".xml")]
        else:
            files = [p]
        for f in sorted(files):
            n = fix_file(f, unwrap_quotes)
            if n:
                total_files += 1
                total_entries += n
                print(f"  {f}: {n} entries")
    print(f"\nfixed {total_entries} entries across {total_files} files")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--no-unwrap-quotes"]
    if len(args) < 1:
        sys.exit(__doc__)
    main(args, unwrap_quotes="--no-unwrap-quotes" not in sys.argv)
