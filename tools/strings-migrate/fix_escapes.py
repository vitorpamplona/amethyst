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
# a backslash escape NOT itself preceded by a backslash
ANDROID_ONLY = re.compile(r"(?<!\\)\\(['\"?@])")


def fix_text(text: str) -> str:
    # Android quote-wrapping: preserve the inner value, drop the delimiters.
    if len(text) >= 2 and text.startswith('"') and text.endswith('"'):
        text = text[1:-1]
    return ANDROID_ONLY.sub(r"\1", text)


def fix_file(path: str) -> int:
    src = open(path, encoding="utf-8").read()
    changed = 0

    def repl(m):
        nonlocal changed
        fixed = fix_text(m.group(2))
        if fixed != m.group(2):
            changed += 1
        return m.group(1) + fixed + m.group(3)

    out = STRING_EL.sub(repl, src)
    if changed:
        open(path, "w", encoding="utf-8").write(out)
    return changed


def main(paths):
    total_files = total_entries = 0
    for p in paths:
        files = []
        if os.path.isdir(p):
            for root, _, names in os.walk(p):
                files += [os.path.join(root, n) for n in names if n.endswith(".xml")]
        else:
            files = [p]
        for f in sorted(files):
            n = fix_file(f)
            if n:
                total_files += 1
                total_entries += n
                print(f"  {f}: {n} entries")
    print(f"\nfixed {total_entries} entries across {total_files} files")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
