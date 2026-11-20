#!/usr/bin/env python3
"""Fail if a regional locale file repeats its language's base file.

Every language keeps ONE full translation in its base folder (`values-es`,
`values-pt`, ...). A regional folder (`values-es-rES`, `values-ru-rUA`, ...)
holds only the strings that region says differently: Android resources and
Compose resources both resolve a key region -> language -> default, one key at
a time, so a value identical to the base one is dead weight that drifts the day
someone edits the base and forgets the copy.

Flags, per res tree:
- a regional value identical to the base folder's value for that key;
- a regional folder whose language has no base folder (it would serve that one
  region only; the translation belongs in `values-<lang>`).

Exceptions: `en-r*` overrides the default `values/` (a `values-en` would also
catch en-US). zh-rTW and zh-rHK are full Traditional-Chinese files: `values-zh`
is Simplified, so they cannot fall back to it.

Run by hand, or via pre-push-orphan-strings.sh. `--fix` deletes the duplicate
values in place. Exits 0 when clean, 2 with a report when not.
"""

import glob
import os
import re
import sys
from collections import defaultdict

REGIONAL = re.compile(r"^values-([a-z]{2,3})-r([A-Z]{2,3})$")
ELEMENT = re.compile(
    r'^[ \t]*<(string|plurals|string-array)\b[^>]*?\bname="([^"]+)"[^>]*?(?:/>|>.*?</\1>)[ \t]*\r?\n',
    re.M | re.S,
)
FULL_FILES = {"zh-rTW", "zh-rHK"}
ROOTS = ("*/src/*/res", "*/src/*/composeResources")


def norm(text):
    return re.sub(r">\s+<", "><", text.strip())


def elements(path):
    with open(path, encoding="utf-8", newline="") as handle:
        raw = handle.read()
    return raw, {m.group(2): m for m in ELEMENT.finditer(raw)}


def scan(fix):
    duplicates = defaultdict(list)  # path -> [key, ...]
    orphaned_regions = []
    for root in sorted({r for pattern in ROOTS for r in glob.glob(pattern)}):
        if not os.path.isdir(os.path.join(root, "values")):
            continue
        for locale_dir in sorted(glob.glob(os.path.join(root, "values-*"))):
            name = os.path.basename(locale_dir)
            match = REGIONAL.match(name)
            path = os.path.join(locale_dir, "strings.xml")
            if not match or not os.path.exists(path):
                continue
            lang, region = match.groups()
            if f"{lang}-r{region}" in FULL_FILES:
                continue
            base_dir = os.path.join(root, "values" if lang == "en" else f"values-{lang}")
            base_path = os.path.join(base_dir, "strings.xml")
            if not os.path.exists(base_path):
                orphaned_regions.append(locale_dir)
                continue
            base = {k: norm(m.group(0)) for k, m in elements(base_path)[1].items()}
            raw, regional = elements(path)
            dupes = [m for k, m in regional.items() if base.get(k) == norm(m.group(0))]
            if not dupes:
                continue
            duplicates[path] = [m.group(2) for m in dupes]
            if fix:
                for m in sorted(dupes, key=lambda m: m.start(), reverse=True):
                    raw = raw[: m.start()] + raw[m.end():]
                with open(path, "w", encoding="utf-8", newline="") as handle:
                    handle.write(raw)
    return duplicates, orphaned_regions


def main():
    fix = "--fix" in sys.argv[1:]
    duplicates, orphaned_regions = scan(fix)
    if fix:
        for path, keys in sorted(duplicates.items()):
            print(f"removed {len(keys)} duplicate value(s) from {path}")
        duplicates = {}
    if not (duplicates or orphaned_regions):
        return 0

    out = sys.stderr
    if duplicates:
        total = sum(len(v) for v in duplicates.values())
        print(f"BLOCKED: {total} regional value(s) identical to the base language file.", file=out)
        print(file=out)
        for path, keys in sorted(duplicates.items()):
            shown = " ".join(keys[:8]) + (" ..." if len(keys) > 8 else "")
            print(f"  {path}: {len(keys)} — {shown}", file=out)
        print(file=out)
        print(
            "A regional file holds only what that region says differently; the key falls\n"
            "back to values-<lang> anyway. Translate in values-<lang>, and delete the copies\n"
            "with: tools/agent-hooks/locale_layout_check.py --fix",
            file=out,
        )
    if orphaned_regions:
        print(file=out)
        print("BLOCKED: regional folder(s) with no base language folder:", file=out)
        for d in orphaned_regions:
            print(f"  {d}", file=out)
        print(
            "Put the language's translation in values-<lang> so every region gets it;\n"
            "keep values-<lang>-r<REGION> for that region's differences only.",
            file=out,
        )
    return 2


if __name__ == "__main__":
    sys.exit(main())
