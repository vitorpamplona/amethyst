#!/usr/bin/env python3
"""Translate a batch of missing strings: extract the English, validate the translations, insert them.

Usage (from the repo root; LOCALE is the values-* suffix, e.g. cs de sv pt):

  batch.py extract  DIR [--base BASE] LOCALE...
      Re-diffs each locale against the default and writes DIR/<LOCALE>.en.xml: every <string> and
      <plurals> that locale lacks, as raw XML. Prints counts and the top key prefixes.

  batch.py validate DIR [--base BASE] [--verbatim k1,k2] LOCALE...
      Checks DIR/<LOCALE>.xml (the translations, same elements, no <resources> wrapper) against
      DIR/<LOCALE>.en.xml: same keys, no duplicates, well-formed, same placeholders, same number of
      \\n, no Android-only \\' escapes or quote-wrapping, every CLDR plural category the locale uses,
      and the --verbatim keys copied unchanged. Needs Babel (CLDR), see README.md.

  batch.py insert   DIR [--base BASE] LOCALE...
      Validates, re-diffs the live locale file, and appends only the keys it still lacks before
      </resources>. Never touches existing entries.

BASE defaults to the commonsUI catalog (commonsUI/src/commonMain/composeResources); pass
amethyst/src/main/res for the Android one.

Translate into a language's base folder (values-es, not values-es-rES): a regional folder only holds
that region's differences, so "every key it lacks" is nearly the whole catalog. The tool refuses a
regional LOCALE whose base folder exists, except zh-rTW / zh-rHK (full Traditional-Chinese files).
"""
import argparse
import collections
import io
import os
import re
import sys
import xml.dom.minidom

COMMONS = "commonsUI/src/commonMain/composeResources"
ELEMENT = re.compile(r'[ \t]*<(string|plurals) name="([^"]+)"([^>]*)>.*?</\1>', re.S)
ITEM = re.compile(r'<item quantity="([^"]+)"[^>]*>(.*?)</item>', re.S)
PLACEHOLDER = re.compile(r"(?<!\\)%(?:\d+\$)?[sdf]")  # (?<!\\): \%2$d is a literal


def placeholders(text):
    return set(PLACEHOLDER.findall(text))


def elements(text):
    """name -> (type, raw slice, inner text) for every <string>/<plurals>, translatable ones only."""
    out = collections.OrderedDict()
    for m in ELEMENT.finditer(text):
        if 'translatable="false"' in m[3]:
            continue
        out.setdefault(m[2], []).append((m[1], m[0].strip("\n")))
    return out


def read(path):
    return io.open(path, encoding="utf-8").read()


def missing(base, locale):
    default = elements(read(f"{base}/values/strings.xml"))
    have = elements(read(f"{base}/values-{locale}/strings.xml"))
    return collections.OrderedDict((k, v) for k, v in default.items() if k not in have)


def plural_rule(locale):
    """The CLDR plural rule for a values-* suffix: es-rES -> es_ES, `in` is the old code for id."""
    try:
        from babel import Locale
    except ImportError:
        sys.exit("validate/insert need Babel: see tools/strings-translate/README.md")
    lang, _, region = locale.partition("-r")
    lang = {"in": "id"}.get(lang, lang)
    for tag in ([f"{lang}_{region}"] if region else []) + [lang]:
        try:
            return Locale.parse(tag).plural_form
        except Exception:
            pass
    sys.exit(f"Babel does not know the locale {locale}")


def cmd_extract(a):
    for loc in a.locales:
        miss = missing(a.base, loc)
        with io.open(f"{a.dir}/{loc}.en.xml", "w", encoding="utf-8") as f:
            for entries in miss.values():
                for _, raw in entries:
                    f.write(raw.strip() + "\n")
        kinds = collections.Counter(t for v in miss.values() for t, _ in v)
        prefixes = collections.Counter(k.split("_")[0] for k in miss)
        top = ", ".join(f"{p} {n}" for p, n in prefixes.most_common(8))
        print(f"{loc}: {kinds['string']} strings, {kinds['plurals']} plurals -> {a.dir}/{loc}.en.xml  ({top})")


def check(a, loc):
    errors = []
    err = errors.append
    en_text, tr_text = read(f"{a.dir}/{loc}.en.xml"), read(f"{a.dir}/{loc}.xml")
    try:
        xml.dom.minidom.parseString("<resources>" + tr_text + "</resources>")
    except Exception as e:
        err(f"malformed: {e}")
    en, tr = elements(en_text), elements(tr_text)
    for k, v in tr.items():
        if len(v) > 1:
            err(f"duplicate {k}")
    if set(en) - set(tr):
        err(f"missing {sorted(set(en) - set(tr))}")
    if set(tr) - set(en):
        err(f"extra {sorted(set(tr) - set(en))}")
    rule = plural_rule(loc)
    # Required: every category some ordinary count reaches, fractions included (Czech `many` is
    # 1.5). Optional: a category only whole millions reach (pt/fr/es/it `many`, "1 milhão de"),
    # which falls back to `other` and that Android lint does not ask for.
    needed = {rule(n) for n in list(range(10001)) + [0.5, 1.5, 2.5, 5.5, 10.5]} | {"other"}
    allowed = set(rule.tags) | {"other"}
    one_is_1 = [n for n in range(1000) if rule(n) == "one"] == [1]
    for k in set(en) & set(tr):
        (et, eraw), (tt, traw) = en[k][0], tr[k][0]
        if et != tt:
            err(f"{k}: {et} became {tt}")
            continue
        if any(s in traw for s in ("\\'", '\\"')):
            err(f"{k}: Android-only escape (write ' and \" bare)")
        if traw.count("\\n") != eraw.count("\\n"):
            err(f"{k}: \\n count differs from English")
        if et == "string":
            ev, tv = re.search(r">(.*)</string>", eraw, re.S)[1], re.search(r">(.*)</string>", traw, re.S)[1]
            if not tv.strip():
                err(f"{k}: empty")
            if len(tv) > 1 and tv[0] == tv[-1] == '"':
                err(f"{k}: quote-wrapped")
            if placeholders(tv) != placeholders(ev):
                err(f"{k}: placeholders {sorted(placeholders(ev))} -> {sorted(placeholders(tv))}")
            if k in a.verbatim and tv != ev:
                err(f"{k}: must stay verbatim")
        else:
            items = dict(ITEM.findall(traw))
            want = placeholders(dict(ITEM.findall(eraw)).get("other", ""))
            if not needed <= set(items) <= allowed:
                err(f"{k}: categories {sorted(items)}, {loc} needs {sorted(needed)}")
            for q, v in items.items():
                got = placeholders(v)
                if not v.strip():
                    err(f"{k}[{q}]: empty")
                # a `one` that means exactly 1 may spell the count out ("once", "jednou")
                elif q == "one" and one_is_1 and got <= want and all(x.endswith("d") for x in want - got):
                    continue
                elif got != want and not loc.startswith("ar"):  # Arabic drops the numeral idiomatically
                    err(f"{k}[{q}]: placeholders {sorted(want)} -> {sorted(got)}")
    kinds = collections.Counter(t for v in tr.values() for t, _ in v)
    return errors, kinds


def cmd_validate(a):
    ok = True
    for loc in a.locales:
        errors, kinds = check(a, loc)
        print(f"{loc}: {kinds['string']} strings, {kinds['plurals']} plurals, {len(errors)} problems")
        for e in errors:
            print("   ", e)
        ok &= not errors
    return 0 if ok else 1


def indent(raw):
    """4 spaces for the element, 8 for <item> lines: the catalog's own layout."""
    lines = raw.strip().split("\n")
    if not lines[0].startswith("<plurals"):
        return "    " + raw.strip() + "\n"
    out = []
    for line in lines:
        s = line.strip()
        out.append(("        " if s.startswith("<item") else "    ") + s)
    return "\n".join(out) + "\n"


def cmd_insert(a):
    if cmd_validate(a):
        sys.exit("not inserting: fix the problems above first")
    for loc in a.locales:
        path = f"{a.base}/values-{loc}/strings.xml"
        live = read(path)
        have = elements(live)
        add = [(k, v[0][1]) for k, v in elements(read(f"{a.dir}/{loc}.xml")).items() if k not in have]
        end = live.rstrip().rfind("</resources>")
        if end < 0:
            sys.exit(f"{path}: no </resources>")
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(live[:end] + "".join(indent(raw) for _, raw in add) + "</resources>\n")
        skipped = len(elements(read(f"{a.dir}/{loc}.xml"))) - len(add)
        print(f"{loc}: inserted {len(add)}" + (f", {skipped} already present (skipped)" if skipped else ""))


FULL_REGIONAL = {"zh-rTW", "zh-rHK"}


def check_targets(base, locales):
    for loc in locales:
        lang, sep, _ = loc.partition("-r")
        if sep and loc not in FULL_REGIONAL and os.path.isdir(f"{base}/values-{lang}"):
            sys.exit(
                f"{loc} is a regional override file: it holds only what that region says differently. "
                f"Translate values-{lang} instead (tools/agent-hooks/locale_layout_check.py explains the layout)."
            )


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("command", choices=["extract", "validate", "insert"])
    p.add_argument("dir", help="working directory for the .en.xml and translated .xml files")
    p.add_argument("locales", nargs="+", metavar="LOCALE")
    p.add_argument("--base", default=COMMONS)
    p.add_argument("--verbatim", default="", type=lambda s: set(filter(None, s.split(","))),
                   help="comma-separated keys that must stay identical to the English")
    a = p.parse_args()
    check_targets(a.base, a.locales)
    return {"extract": cmd_extract, "validate": cmd_validate, "insert": cmd_insert}[a.command](a) or 0


if __name__ == "__main__":
    sys.exit(main())
