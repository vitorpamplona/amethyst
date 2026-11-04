---
name: find-missing-translations
description: Translation audits of `strings.xml` locales in `commonsUI/.../composeResources` and `amethyst/src/main/res`: missing or untranslated keys, CLDR plural categories, Crowdin sync rules, lint gates. Use before translating or seeding locale files.
---

# Find missing translations

How to author a new string (where it goes, `Res.string`, positional args, Compose
escaping) is in AGENTS.md "Strings" — not repeated here. This skill is the audit:
diff locales against their default, fill the gaps, and pass the gates.

## Two catalogs — scan both, report separately

Each is its own Crowdin source in `crowdin.yml`, so each count reconciles against its own Crowdin UI number.

| Catalog | Default file | Size |
|--|--|--|
| **commonsUI** (Compose resources, Android + Desktop) | `commonsUI/src/commonMain/composeResources/values/strings.xml` | ~6,155 `<string>` + ~198 `<plurals>` |
| **amethyst** (Android `res/`) | `amethyst/src/main/res/values/strings.xml` | ~191 `<string>` + ~13 `<plurals>` — the synchronous-platform tier only |

- New keys always go in commonsUI. A feature key found in the amethyst tree belongs in commonsUI: move it with `tools/strings-migrate/migrate.py <keys…>` (moves every locale byte-for-byte), don't translate it in place.
- Locale dirs differ between the trees (56 vs 57) — enumerate `values-*` under each base, don't assume.
- A key name present in both trees does **not** mean the same English. Copy an existing translation across trees only when the two English values are byte-identical; otherwise translate fresh (or copy from the key whose *value* matches).
- The trees escape differently. commonsUI authors edge whitespace raw, never quote-wrapped, and `\'` renders literally there; copying Android-style `"replying to "` or `\'` into commonsUI is a bug. `tools/agent-hooks/compose_escaping_check.py` catches it (CI and pre-push).
- Same English repeated under many keys (`Cancel`, `Save`, …) across the trees is expected. Don't dedupe during a translation pass.

## Default target locales

`values-cs`, `values-de-rDE`, `values-sv-rSE`, `values-pt-rBR` (unless the user names others).
Czech is on the base qualifier because `crowdin.yml` maps `cs: cs` (in both sources); there is no `values-cs-rCZ`.

## Crowdin rules that decide what sticks

- `.github/workflows/crowdin.yml` passes `import_eq_suggestions: true`, so the upload seeds Crowdin with whatever is in the repo locale files, values identical to the source included, as **pending** (unapproved) suggestions. So:
  - **Do copy the English verbatim** for keys a locale keeps in English (brand names like `Nowhere …`, loanwords like `Apps`/`Feed`, format-only values like `v%1$s`, `+%1$d`, example placeholders). Leaving them out keeps them untranslated in Crowdin forever.
  - **Never for `<plurals>`** — English `one`/`other` in cs/pl fails `MissingQuantity`.
  - **Not for words the locale really translates** (German "Arbeitsbereich", "Training"): list those for a human translator instead of seeding a wrong suggestion.
- **A repo-side edit to a translated value only sticks where Crowdin's database doesn't contradict it.** Download replaces file content with Crowdin's export. Adding a key Crowdin holds nothing for sticks; overwriting a value Crowdin holds differently — including an *empty* value — is reverted on the next sync. Durable value fixes go in the Crowdin web UI. Edits to the **source** `values/strings.xml` do stick (it is Crowdin's input).
- **`tools:ignore` goes on the source entry in `amethyst/src/main/res/values/strings.xml`, never on a locale file** — Crowdin propagates source attributes into every export and strips ones added only to a locale. Never add `tools:` attributes to the commonsUI catalog at all: its root doesn't declare the namespace, so the XML becomes malformed (the escaping check flags it). Before suppressing, `grep -rn "<key>" --include='*.kt'`: an unused key should be deleted instead.
- Reading a Crowdin sync diff: compare key **sets** per file, not `-`/`+` lines; Crowdin reorders whole files.

## 1. Diff each locale, both resource types

Diff `<string>` **and** `<plurals>` separately (a missing `<plurals>` never shows in a `<string>` diff), and diff **every** target locale — files are asymmetric, so `cs` is not an upper bound.

```bash
keys() { grep "<$2 name=" "$1" | grep -v 'translatable="false"' | sed 's/.*name="\([^"]*\)".*/\1/' | sort -u; }
for base in commonsUI/src/commonMain/composeResources amethyst/src/main/res; do
  echo "## $base"
  for l in cs de-rDE sv-rSE pt-rBR; do
    for t in string plurals; do
      m=$(comm -23 <(keys $base/values/strings.xml $t) <(keys $base/values-$l/strings.xml $t))
      echo "$l $t: $(printf '%s' "$m" | grep -c .)"; printf '%s\n' "$m" | sed '/^$/d;s/^/    /'
    done
  done
done
```

The `strings + plurals` total per locale should match Crowdin's "N untranslated". Pull English values with `grep "name=\"$key\""` for strings; a `<plurals>` is a multi-line block (opening tag through `</plurals>`).

## 2. Audit before translating

Stop and ask the user about any missing English string that looks plural-shaped: a hardcoded `1` next to a noun (`"1 reply"`), or a count placeholder whose noun changes with the count (`"%1$d follower"`, thresholds like "more than %1$d hashtags"). It should become a `<plurals>` first; translating the wrong shape means fixing it in every locale. The pattern is noisy (`"Limits for kind %1$d"` isn't plural) — review by hand.

Also scan the trees you touch for:

- `quantity="one"` items with a literal digit and no `%d`/`%1$d` placeholder.
- `quantity="zero"` items outside `values-ar*`, `values-lv*` (and Welsh, `cy`, if a dir ever appears) — dead code: CLDR maps 0 to `other` there. For "no items" wording use a separate `<string>` and an `if (count == 0)` branch at the call site.
- Empty items `<item quantity="…"></item>` — render as nothing, and Polish `many` is the common case.
- Placeholder parity between locale and base:

```bash
python3 - <<'PY'
import re, io, glob
S = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
P = re.compile(r'<plurals name="([^"]+)"[^>]*>(.*?)</plurals>', re.S)
I = re.compile(r'<item quantity="([^"]+)"[^>]*>(.*?)</item>', re.S)
PH = re.compile(r'(?<!\\)%(?:(\d+)\$)?([sdf])')   # (?<!\\) required: \%2$d is a literal
sig = lambda t: sorted(set(m.group(0) for m in PH.finditer(t)))
for base in ['commonsUI/src/commonMain/composeResources', 'amethyst/src/main/res']:
    d = io.open(f'{base}/values/strings.xml', encoding='utf-8').read()
    ds = {m[1]: sig(m[2]) for m in S.finditer(d)}
    dp = {m[1]: sorted(set(x for it in I.finditer(m[2]) for x in sig(it[2]))) for m in P.finditer(d)}
    for p in sorted(glob.glob(f'{base}/values-*/strings.xml')):
        if '/values-ar' in p: continue   # Arabic drops the numeral idiomatically
        s = io.open(p, encoding='utf-8').read()
        for m in S.finditer(s):
            if m[1] in ds and sig(m[2]) != ds[m[1]]: print(p, m[1], ds[m[1]], sig(m[2]))
        for m in P.finditer(s):
            for it in I.finditer(m[2]) if m[1] in dp else []:
                if sig(it[2]) != dp[m[1]]: print(p, f'{m[1]}[{it[1]}]', dp[m[1]], sig(it[2]))
PY
grep -rn '<item quantity="[a-z]*"></item>' amethyst/src/main/res/values*/strings.xml commonsUI/src/commonMain/composeResources/values*/strings.xml
```

Compare placeholder *sets*: a repeated index (German repeating `%1$s` where English says "They") is legitimate.

## 3. CLDR plural categories

Every locale's `<plurals>` needs **every** category its language uses, not English's `one`/`other`:

| Locales | Categories |
|--|--|
| en, de, sv, pt-BR, hu | `one`, `other` |
| cs, pl, ru, uk, hr (Slavic) | `one`, `few`, `many`, `other` |
| ar | `zero`, `one`, `two`, `few`, `many`, `other` |
| lv | `zero`, `one`, `other` |
| zh, ja | `other` |

- **Latvian `zero` is not "no items"**: it covers 0, 10, 11–19, 20, 30, … (`n % 10 = 0` or `n % 100 = 11..19`), so it fires on most counts and must read as a normal plural form. Never strip `zero` from `values-lv-rLV` (or `values-ar-rSA`); among shipped locales only these two have an integer-bearing `zero`.
- Never hardcode `1` in a `quantity="one"` item; `one` covers other numbers in some languages.
- Converting a `<string>` to `<plurals>`: convert it in **every** locale in the same change (aapt2 rejects a type mismatch; a leftover locale `<string>` is an orphan) and give each locale its **full** category set. An `other`-only block fails `MissingQuantity` before Crowdin can backfill. Re-check declension — the retained text is usually the plural form ("1 odpowiedzi").
- Verify a locale you're unsure of against CLDR rather than memory:

```bash
python3 -m venv "$TMPDIR/cldr" && "$TMPDIR/cldr/bin/pip" -q install babel
"$TMPDIR/cldr/bin/python" -c "
from babel import Locale
for c in ['lv','ar','cs','pl','ru','de','pt_BR']:
    r = Locale.parse(c).plural_form; print(c, sorted({r(n) for n in range(10001)}))"
```

Call-site patterns for plurals: `amethyst/src/main/res/AGENTS.md` "Plural call-site patterns".

## 4. Present, then insert (if approved)

Show the missing entries as copy-paste-ready XML lines, flag the plural-shaped ones, then ask: "Translate these into [locales]?"

When inserting:

- **Append** before `</resources>`; don't try to order (Crowdin reorders on sync).
- **Per-locale insert rule:** insert into each locale **only the keys missing from that locale** (its own diff from step 1), never one union block pasted into every file — that duplicates keys in locales that already had some.
- Skip `translatable="false"` keys.

## 5. Gates, in order

1. **Duplicate-key + well-formedness gate** on every file you edited. In commonsUI a duplicate is build-breaking: `:commonsUI:convertXmlValueResourcesForCommonMain` fails with `Duplicated key '…'`.

   ```bash
   for f in <edited strings.xml files>; do
     d=$(grep -oE '<(string|plurals|string-array) name="[^"]*"' "$f" | sed 's/.*name="//;s/"$//' | sort | uniq -d)
     [ -n "$d" ] && echo "DUP in $f: $d"
     python3 -c "import sys,xml.dom.minidom; xml.dom.minidom.parse(sys.argv[1])" "$f" || echo "MALFORMED $f"
   done
   ```

2. **`tools/agent-hooks/orphan_strings_check.py`** — sub-second; fails on any locale key the default no longer has (both catalogs). Renaming or removing a source key means deleting it from every locale in the same commit; Crowdin's later cleanup is partial. Details: `amethyst/src/main/res/AGENTS.md` "Renaming or removing a string key".
3. **`tools/agent-hooks/compose_escaping_check.py`** — Android-only escaping or `tools:` attributes in the commonsUI catalog.
4. **`./gradlew :amethyst:lintPlayBenchmark`** — the final gate, the task CI runs (`.github/workflows/build.yml`, beside `lintFdroidBenchmark`; the pre-push hook runs its debug twin, `lintPlayDebug`, on every push). Android lint polices only the Android `res/` trees (not composeResources — so for commonsUI the plural categories above are on you). There is **no lint baseline** and only `MissingTranslation` is disabled (`amethyst/build.gradle.kts`), so any error fails CI:

   | Rule | Fires when | Severity |
   |--|--|--|
   | `MissingQuantity` | a locale's `<plurals>` omits a category that locale uses | error |
   | `ImpliedQuantity` | an item has no format argument where its category spans several numbers | error |
   | `ExtraTranslation` | a locale key the default doesn't have | error |
   | `StringFormatCount` / `StringFormatMatches` | placeholder count/type differs from the base | warning |

   Compiling is not evidence, and a wider BUILD SUCCESSFUL isn't either — read the report:

   ```bash
   python3 -c "
   import json,collections
   r=json.load(open('amethyst/build/reports/lint-results-playBenchmark.sarif',encoding='utf-8'))['runs'][0]['results']
   print(dict(collections.Counter(x.get('level','warning') for x in r)))
   [print('ERROR',x['ruleId'],x['locations'][0]['physicalLocation']['artifactLocation']['uri']) for x in r if x.get('level')=='error']"
   ```
