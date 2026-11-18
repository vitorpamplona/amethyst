---
name: find-missing-translations
description: Translation audits of `strings.xml` locales in `commonsUI/.../composeResources` and `amethyst/src/main/res`: missing or untranslated keys, CLDR plural categories, base vs regional locale folders, lint gates. Use before translating or seeding locale files.
---

# Find missing translations

How to author a new string (where it goes, `Res.string`, positional args, Compose
escaping) is in AGENTS.md "Strings" — not repeated here. This skill is the audit:
diff locales against their default, fill the gaps, and pass the gates.

## Two catalogs — scan both, report separately

Each catalog's counts are its own; report them separately.

| Catalog | Default file | Holds |
|--|--|--|
| **commonsUI** (Compose resources, Android + Desktop) | `commonsUI/src/commonMain/composeResources/values/strings.xml` | Every feature string: the large catalog, where nearly all gaps are |
| **amethyst** (Android `res/`) | `amethyst/src/main/res/values/strings.xml` | The synchronous-platform tier only: small, and shrinking as keys move out |

- New keys always go in commonsUI. A feature key found in the amethyst tree belongs in commonsUI: move it with `tools/strings-migrate/migrate.py <keys…>` (moves every locale byte-for-byte), don't translate it in place.
- The two trees' locale dirs need not match: enumerate `values-*` under each base, don't assume.
- A key name present in both trees does **not** mean the same English. Copy an existing translation across trees only when the two English values are byte-identical; otherwise translate fresh (or copy from the key whose *value* matches).
- The trees escape differently. commonsUI authors edge whitespace raw, never quote-wrapped, and `\'` renders literally there; copying Android-style `"replying to "` or `\'` into commonsUI is a bug. `tools/agent-hooks/compose_escaping_check.py` catches it (CI and pre-push).
- Same English repeated under many keys (`Cancel`, `Save`, …) across the trees is expected. Don't dedupe during a translation pass.

## Default target locales

`values-cs`, `values-de`, `values-sv`, `values-pt` (unless the user names others).

House conventions of the existing translations. Follow them, and grep the locale file for a term before
coining one:

| Locale | Addresses the user as | relay / signer | Also |
|--|--|--|--|
| cs | formal *vy* (`vaše`, `Zkuste`) | relé (or relay) / podepisovač | masculine past tense (`se připojil`), never `připojil(a)` doublets |
| de | *du* (`deine`) | Relay / Signierer | dev loanwords stay (Commit, Branch, Repository) |
| sv | *du* (`dina`) | relä / signerare | |
| pt (Brazilian) | *você* (`seus`) | relay / assinador | `one` also covers 0, so a `one` item keeps the number |

In all four, decimals take a comma (`~1,2 km`). cs, de and sv typography puts a space before `%`
(`5 %`), though the existing files are mixed; pt-BR writes `5%`. Brand and tool names stay as they are
(Claude Code, GitHub, ngit, Amethyst).

## Where a translation goes

Translations live only in this repo; Crowdin is retired, so nothing overwrites a value later.

- **Base folder = the full translation.** Each language has one: `values-es` (Mexican Spanish),
  `values-pt` (Brazilian), `values-zh` (Simplified), `values-de`, … Translate there.
- **A regional folder holds only that region's differences** (`values-es-rES`, `values-es-rUS`,
  `values-nl-rBE`, `values-ru-rUA`, `values-zh-rSG`). Android and Compose resources resolve a key
  region → language → default one key at a time, so a key the regional file lacks shows the base
  translation, not English. Don't diff a regional folder against the default: nearly everything is
  "missing" by design. `batch.py` refuses one.
- **Exceptions:** `values-zh-rTW` and `values-zh-rHK` are full Traditional-Chinese files (`values-zh` is
  Simplified, so they must not fall back to it); diff and fill them like a base folder. `values-en-rGB`
  overrides the default English.
- **Indonesian is `values-in`**, never `values-id`: commonsUI's build serves the folder under both codes,
  since Locale reports `in` on Android 14 and older but `id` on desktop and Android 15+.
- `tools/agent-hooks/locale_layout_check.py` (pre-push and CI) rejects a regional value identical to the
  base one; `--fix` deletes them.
- **Copy the English verbatim** for keys a locale keeps in English (brand names like `Nowhere …`,
  loanwords like `Apps`/`Feed`, format-only values like `v%1$s`, `+%1$d`), so the key no longer counts as
  missing. **Never for `<plurals>`** (English `one`/`other` in cs/pl fails `MissingQuantity`), and **not
  for words the locale really translates** (German "Arbeitsbereich", "Training"): list those for a human
  translator instead.
- **`tools:ignore` goes on the source entry in `amethyst/src/main/res/values/strings.xml`, never on a locale file.** Never add `tools:` attributes to the commonsUI catalog at all: its root doesn't declare the namespace, so the XML becomes malformed (the escaping check flags it). Before suppressing, `grep -rn "<key>" --include='*.kt'`: an unused key should be deleted instead.

## 1. Diff each locale, both resource types

Diff `<string>` **and** `<plurals>` separately (a missing `<plurals>` never shows in a `<string>` diff), and diff **every** target locale — files are asymmetric, so `cs` is not an upper bound.

```bash
keys() { grep "<$2 name=" "$1" | grep -v 'translatable="false"' | sed 's/.*name="\([^"]*\)".*/\1/' | sort -u; }
for base in commonsUI/src/commonMain/composeResources amethyst/src/main/res; do
  echo "## $base"
  for l in cs de sv pt; do
    for t in string plurals; do
      m=$(comm -23 <(keys $base/values/strings.xml $t) <(keys $base/values-$l/strings.xml $t))
      echo "$l $t: $(printf '%s' "$m" | grep -c .)"
    done
  done
done
```

The `strings + plurals` total per locale is its untranslated count. It prints counts only:
a big gap lists hundreds of keys. To see them, run `tools/strings-translate/batch.py extract` (step 4).
It writes each locale's missing English to a file and prints the top key prefixes, the summary to show
the user. For a handful of keys, `grep "name=\"$key\""` pulls a `<string>`; a `<plurals>` is a multi-line
block (opening tag through `</plurals>`).

## 2. Audit before translating

Stop and ask the user about any missing English string that looks plural-shaped. It should become a
`<plurals>` first: a key's type is fixed by the source, so a translator cannot make a `<string>` plural in
one locale, and translating the wrong shape means fixing it in every locale.

**Judge by meaning, not by the English wording.** English barely inflects, so a count can look harmless
and still need a plural. Any `%d` that counts things is a candidate:

- A noun that changes with the count: `"%1$d follower"`, `"1 reply"` (hardcoded `1`), `"%1$d item(s)"`
  (an `(s)` hedge is always wrong), thresholds like "more than %1$d hashtags".
- **Invariant English that inflects in Czech or Polish**, where the noun *and* the adjective or verb change
  with the count (Czech 1 / 2–4 / 5+): `"%1$d selected"`, `"+%1$d more"`, `"%1$d failed"`, `"%1$d new"`.
  English plurals with identical `one`/`other` items (`claude_code_tool_group_failed`) exist for exactly this.
- **A translator's dodge** shows the source is the wrong shape: cs `"Vybráno: %1$d"` (a colon sidesteps
  agreement), or `"+%1$d dalších"` beside `"+%2$d další"` for the same English (each right for only part of
  the range).

Not candidates: identifiers (`"kind %1$d"`, `"line %1$d"`), percentages, unit abbreviations
(`"5 min"`, `"10 MB"`), and a count that is always the same constant. **Check the call site first**: a
`"%1$d days"` fed `UNUSED_MIRROR_DAYS = 60` has one correct form in every locale and stays a `<string>`.
Amounts pre-formatted into `%s` (sats) can't be plurals at all; they read as units.

To list the candidates across a whole catalog (a heuristic: review every hit by hand):

```bash
python3 - commonsUI/src/commonMain/composeResources amethyst/src/main/res <<'PY'
import re, io, sys
S = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
COUNT = re.compile(r'(?<!kind )(?<!\^)(?<!\\)%\d+\$d(?!%)\s+([A-Za-z][\w-]*)')   # %1$d <word>
UNITS = {'s','ms','sec','min','h','hr','d','m','km','kb','mb','gb','tb','b','px','dp','sp','x','sat','sats',
         'bytes','byte','hz','khz','fps','of','to','and','or','in','at','on','by'}
for base in sys.argv[1:]:
    for m in S.finditer(io.open(f'{base}/values/strings.xml', encoding='utf-8').read()):
        if any(w.lower() not in UNITS for w in COUNT.findall(m[2])): print(f'{m[1]}\t{m[2]}')
PY
```

Also scan the trees you touch for:

- `quantity="one"` items with a literal digit and no `%d`/`%1$d` placeholder.
- `quantity="zero"` items outside `values-ar*`, `values-lv*` (and Welsh, `cy`, if a dir ever appears) — dead code: CLDR maps 0 to `other` there. For "no items" wording use a separate `<string>` and an `if (count == 0)` branch at the call site.
- Empty items `<item quantity="…"></item>` — render as nothing, and Polish `many` is the common case.
- Placeholder parity between locale and base. Compare each plural item with the English `other` (Czech
  `few`/`many` and Latvian `zero` have no English item). A `one` item may spell the count out ("once",
  "jednou") **only where that locale's `one` means exactly 1**: true for cs, pl, de, sv, es, false for pt-BR
  and fr (0 and 1) and for ru, uk, hr, lv (1, 21, 31…). The script asks CLDR (Babel) per locale, so it
  flags "Played once" in Russian but not in Czech:

```bash
python3 -m venv "$TMPDIR/cldr" && "$TMPDIR/cldr/bin/pip" -q install babel   # once; step 3 reuses it
"$TMPDIR/cldr/bin/python" - <<'PY'
import re, io, glob
from babel import Locale
S = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
P = re.compile(r'<plurals name="([^"]+)"[^>]*>(.*?)</plurals>', re.S)
I = re.compile(r'<item quantity="([^"]+)"[^>]*>(.*?)</item>', re.S)
PH = re.compile(r'(?<!\\)%(?:(\d+)\$)?([sdf])')   # (?<!\\) required: \%2$d is a literal
sig = lambda t: set(m.group(0) for m in PH.finditer(t))

def one_is_exactly_1(d):   # values-es-rES -> es_ES; `in` is the old code for Indonesian
    lang, _, reg = d.removeprefix('values-').partition('-r')
    lang = {'in': 'id'}.get(lang, lang)
    for tag in (f'{lang}_{reg}' if reg else lang, lang):
        try: r = Locale.parse(tag).plural_form; break
        except Exception: pass
    else: return False
    return [n for n in range(1000) if r(n) == 'one'] == [1]

for base in ['commonsUI/src/commonMain/composeResources', 'amethyst/src/main/res']:
    d = io.open(f'{base}/values/strings.xml', encoding='utf-8').read()
    ds = {m[1]: sig(m[2]) for m in S.finditer(d)}
    dp = {m[1]: {it[1]: sig(it[2]) for it in I.finditer(m[2])} for m in P.finditer(d)}
    for p in sorted(glob.glob(f'{base}/values-*/strings.xml')):
        if '/values-ar' in p: continue   # Arabic drops the numeral idiomatically
        exact1 = one_is_exactly_1(p.split('/')[-2])
        s = io.open(p, encoding='utf-8').read()
        for m in S.finditer(s):
            if m[1] in ds and sig(m[2]) != ds[m[1]]: print(p, m[1], sorted(ds[m[1]]), sorted(sig(m[2])))
        for m in P.finditer(s):
            if m[1] not in dp: continue
            for it in I.finditer(m[2]):
                want, got = dp[m[1]].get('other', set()), sig(it[2])
                if it[1] == 'one' and exact1 and got <= want and all(x.endswith('d') for x in want - got): continue
                if got != want: print(p, f'{m[1]}[{it[1]}]', sorted(want), sorted(got))
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

- **Latvian `zero` is not "no items"**: it covers 0, 10, 11–19, 20, 30, … (`n % 10 = 0` or `n % 100 = 11..19`), so it fires on most counts and must read as a normal plural form. Never strip `zero` from `values-lv` (or `values-ar`); among shipped locales only these two have an integer-bearing `zero`.
- Never hardcode `1` in a `quantity="one"` item; `one` covers other numbers in some languages.
- Converting a `<string>` to `<plurals>`: convert it in **every** locale in the same change (aapt2 rejects a type mismatch; a leftover locale `<string>` is an orphan) and give each locale its **full** category set. An `other`-only block fails `MissingQuantity`. Re-check declension — the retained text is usually the plural form ("1 odpowiedzi").
- Verify a locale you're unsure of against CLDR rather than memory:

```bash
# the Babel venv from step 2
"$TMPDIR/cldr/bin/python" -c "
from babel import Locale
for c in ['lv','ar','cs','pl','ru','de','pt_BR']:   # fractions too: Czech many is 1.5
    r = Locale.parse(c).plural_form; print(c, sorted({r(n) for n in list(range(10001)) + [0.5, 1.5, 2.5]}))"
```

Recent CLDR also gives pt, fr, es and it a `many` that only whole millions reach ("1 milhão de"). It
falls back to `other` and lint doesn't ask for it, so leave it out; the snippet above doesn't list it.

Call-site patterns for plurals: `amethyst/src/main/res/AGENTS.md` "Plural call-site patterns".

## 4. Present, then insert (if approved)

Show the missing entries, flag the plural-shaped ones, then ask: "Translate these into [locales]?" A
few entries go in the reply as copy-paste-ready XML. Past a few dozen, show the counts and prefixes
from `extract` and point to its files instead.

When inserting:

- **Append** before `</resources>`; don't try to order.
- **Per-locale insert rule:** insert into each locale **only the keys missing from that locale** (its own diff from step 1), never one union block pasted into every file — that duplicates keys in locales that already had some.
- Skip `translatable="false"` keys.
- **Re-diff right before inserting.** A translation PR can land keys mid-session.

**Large batches** (dozens of keys, or several locales) go through `tools/strings-translate/batch.py`
(its README has the commands). It does all of the above and rejects a bad file before it touches the
repo:

1. `extract` writes `<locale>.en.xml` per locale into a scratch dir.
2. Write one shared brief: what the feature is (its screens, its terms), the escaping rules (AGENTS.md
   "Strings"), the `--verbatim` keys, the plural categories per locale (step 3), and the conventions
   table above.
3. Translate each `<locale>.en.xml` into `<locale>.xml`. One subagent per locale, in parallel, works
   well; they write only to the scratch dir.
4. `validate`, fix, repeat. Then spot-read a sample yourself: the checks catch structure, not meaning.
5. `insert`, then the gates.

## 5. Gates, in order

1. **Duplicate-key + well-formedness gate** on every file you edited. In commonsUI a duplicate is build-breaking: `:commonsUI:convertXmlValueResourcesForCommonMain` fails with `Duplicated key '…'`.

   ```bash
   for f in <edited strings.xml files>; do
     d=$(grep -oE '<(string|plurals|string-array) name="[^"]*"' "$f" | sed 's/.*name="//;s/"$//' | sort | uniq -d)
     [ -n "$d" ] && echo "DUP in $f: $d"
     python3 -c "import sys,xml.dom.minidom; xml.dom.minidom.parse(sys.argv[1])" "$f" || echo "MALFORMED $f"
   done
   ```

2. **`tools/agent-hooks/orphan_strings_check.py`** — sub-second; fails on any locale key the default no longer has (both catalogs). Renaming or removing a source key means deleting it from every locale in the same commit; nothing cleans up later. Details: `amethyst/src/main/res/AGENTS.md` "Renaming or removing a string key".
3. **`tools/agent-hooks/locale_layout_check.py`** — a regional value identical to its base folder's (`--fix` removes them).
4. **`tools/agent-hooks/compose_escaping_check.py`** — Android-only escaping or `tools:` attributes in the commonsUI catalog.
5. **commonsUI edits: `./gradlew :commonsUI:convertXmlValueResourcesForCommonMain`** — the build step
   that parses the catalog, the one that fails on a duplicate key. Android lint never reads
   composeResources, so this is the only build check a commonsUI-only pass gets. The plural categories
   are on you (step 3, or `batch.py validate`).
6. **Android `res/` edits: `./gradlew :amethyst:lintPlayBenchmark`** — the task CI runs (`.github/workflows/build.yml`, beside `lintFdroidBenchmark`; the pre-push hook runs its debug twin, `lintPlayDebug`, on every push). Run it when you changed `amethyst/src/main/res`; for a commonsUI-only change it proves nothing about your strings. There is **no lint baseline** and only `MissingTranslation` is disabled (`amethyst/build.gradle.kts`), so any error fails CI:

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
