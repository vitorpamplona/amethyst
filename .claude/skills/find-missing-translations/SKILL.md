---
name: find-missing-translations
description: Use when comparing Android strings.xml locale files to find untranslated string resources, missing translation keys, or preparing translation work for a specific language
---

# Find Missing Translations

## Overview

Extract string resource keys from a default `values/strings.xml` that are absent in a target locale's `strings.xml`, excluding non-translatable entries. Outputs missing keys and offers to translate them.

The repo now has **two independent Crowdin-managed resource trees** — you must scan **both** (see "Resource trees" below).

## When to Use

- Need to find untranslated strings for a specific locale
- Preparing a batch of strings for a translator
- Checking translation coverage after adding new features

## Resource trees (scan BOTH)

There are two separate `strings.xml` trees, each with its own default `values/` and per-locale `values-<locale>/` files, each wired into `crowdin.yml` independently:

| Tree | Default file | Per-locale file |
|------|--------------|-----------------|
| **amethyst** (Android app) | `amethystShared/src/androidMain/res/values/strings.xml` | `amethystShared/src/androidMain/res/values-<locale>/strings.xml` |
| **commons** (KMP Compose resources, shared by Android + Desktop) | `commons/src/commonMain/composeResources/values/strings.xml` | `commons/src/commonMain/composeResources/values-<locale>/strings.xml` |

The `commons` tree appeared when shared event-renderer composables were extracted out of `amethyst/` into `commons/` (Compose Multiplatform `stringResource`). It is **not** a copy of the amethyst tree — the vast majority of its keys are commons-only; only a small handful overlap. Every diff/count/translate command below works on either tree by swapping the base path — **run the whole technique once per tree** and report them separately (each maps to its own Crowdin file, so the counts should reconcile against two different Crowdin UI numbers).

**Locale-qualifier caveat:** `commons` uses the same region-qualified locale dirs as amethyst for our four targets (`values-cs`, `values-de-rDE`, `values-sv-rSE`, `values-pt-rBR`), but the *full* set of locale dirs differs between trees. Enumerate `values-*` under each tree's own base rather than assuming they match.

**Overlap (copy — but only after checking the English matches):** a few `commons` keys share a *name* with a key in the amethyst tree. For such a key already translated in the amethyst locale file you may **copy the existing approved translation verbatim** — but **only if the two English source values are byte-identical.** A shared key name does **not** guarantee a shared meaning.

> ⚠️ **Mistake we actually made (2026-07-18):** `napplet_card_permissions` exists in *both* trees with the *same key name* but *different English* — commons = `"What it can access"`, amethyst = `"Permissions:"`. Copying the amethyst translation by key name produced the wrong string in commons (it said "Permissions:" where the UI reads "What it can access"). **Always diff the English values, not just the key names.** When the English differs, translate the commons value fresh — or, better, find the amethyst key whose *value* matches (here `favorite_app_access_show` = "What it can access") and copy *that* approved translation.

Detect name-overlap **and flag value mismatches** in one pass:

```bash
cdef=commons/src/commonMain/composeResources/values/strings.xml
adef=amethystShared/src/androidMain/res/values/strings.xml
comm -12 \
  <(grep '<string name=' "$cdef" | sed 's/.*name="\([^"]*\)".*/\1/' | sort -u) \
  <(grep '<string name=' "$adef" | grep -v 'translatable="false"' | sed 's/.*name="\([^"]*\)".*/\1/' | sort -u) \
| while read -r k; do
    cv=$(grep -m1 "name=\"$k\"" "$cdef" | sed 's/.*>\(.*\)<\/string>/\1/')
    av=$(grep -m1 "name=\"$k\"" "$adef" | sed 's/.*>\(.*\)<\/string>/\1/')
    [ "$cv" = "$av" ] && echo "SAFE-COPY   $k" || echo "VALUE-DIFFERS $k  commons=\"$cv\"  amethyst=\"$av\""
  done
```

Only `SAFE-COPY` keys may be copied verbatim. For `VALUE-DIFFERS`, translate the commons English fresh (or copy from the amethyst key that has the *matching value*).

**Whitespace-quote convention differs between trees.** Android string resources use surrounding double-quotes to preserve leading/trailing whitespace (`"replying to "`). The **commons Compose-resources tree does NOT use this convention** — it authors trailing/leading spaces raw and unquoted (`replying to `). So when copying/translating a commons string with edge whitespace, **match the commons source: raw spaces, no wrapping quotes.** (Mistake we made: we copied amethyst's quoted `"replying to "` into commons, where the quotes would render literally.) A quick check for stray quote-wrapping you introduced:

```bash
grep -nE '<string name="[^"]*">"' commons/src/commonMain/composeResources/values-*/strings.xml
# The commons English tree has zero quote-wrapped values — any hit in a locale file is almost certainly a bad copy from amethyst.
```

**Why two catalogs exist — the duplication is NOT a bug to "fix" (don't ask again).** You will see the same English text (`Cancel`, `Save`, `Delete`, `Open`, …) defined *many* times across the amethyst tree under per-feature keys **and** once more in commons under generic keys (`action_cancel`, `action_save`, …). This is **required architecture, not an error:**

- The two trees are **different resource systems**: amethyst uses Android `R.string`; commons uses Compose-Multiplatform `Res.string` (`com.vitorpamplona.amethyst.commons.resources.Res`).
- **`commons` cannot depend on `amethyst`** (amethyst depends on commons — the reverse would be circular). So a composable extracted *into* commons physically cannot reference `R.string.cancel`; it needs its own string, hence the generic `action_*` keys. That is the only way an extracted shared composable can render "Cancel."
- The scattered amethyst per-feature duplicates (`nip46_signer_cancel`, `nest_create_cancel`, …) are **pre-existing tech debt**; the commons keys did not create them.
- Both catalogs are Crowdin-managed **independently**, and Crowdin's translation memory pre-fills repeats, so translating the same word in both trees is **not** wasted effort.

**Do not** treat the value-overlap as something to deduplicate during a translation pass. Migrating amethyst's own screens onto the shared `action_*` strings is a *separate, optional* refactor and a maintainer call — out of scope for this skill. Just translate each tree correctly and independently.

## Background: Crowdin strip-identical behavior

This repo syncs translations via Crowdin (branch `l10n_crowdin_translations`). Crowdin's default export behavior **omits any translation that exactly equals the source**, so a key that the translator deliberately kept as English (common for brand terms like `"Nowhere Drop"`, single-word loanwords like `"Apps"` / `"Feed"` / `"Issues"`, or version prefixes like `"v%1$s"`) will not appear in the locale's `strings.xml` even though the Crowdin UI shows it as 100% translated.

What this means for this skill:

1. **The raw on-disk diff is the candidate set.** A key missing from a locale file is either genuinely untranslated *or* a source-identical entry Crowdin stripped. Both are reported; the human decides which to skip. The Crowdin web UI ("N untranslated") is the ground truth for what genuinely needs work.
2. **Source-identical entries are a small, recognizable minority.** Brand terms (`Nowhere X`), single-word loanwords (`Apps` / `Feed` / `Issues`), and bare version/format strings (`v%1$s`) are the usual cases. Skip these by inspection rather than translating them to something identical.
3. **Don't add source-identical fallbacks.** Android falls back to `values/strings.xml` at runtime, so a key intentionally kept as English already renders correctly, and Crowdin's next sync would strip a local duplicate anyway.

4. **A repo-side edit to a translated value only sticks where Crowdin's database
   doesn't contradict it.** Download replaces file content with Crowdin's current
   export; it does not diff or merge. So a hand fix to a locale file survives only
   if Crowdin happens to hold the same value (or holds nothing for that key). If
   Crowdin holds a *different* value — including an **empty** one — the next sync
   silently reverts you.

   Observed 2026-08-13/14 in one pass, which is what makes the rule concrete:
   `pow_estimate_minutes[few]` (pl) **survived** the sync because Crowdin's
   approved value matched the fix, while `nest_listener_count[many]` (pl) was
   **reverted to empty** two commits later because Crowdin stores an empty string
   there. Same file, same commit, opposite outcomes.

   Consequences: fixing a *value* durably means entering it in the Crowdin web UI
   — no repo commit will hold it. Changes to the **source** file are different and
   do stick, because that file is Crowdin's input, not its output: deleting a key
   from `values/strings.xml` removes it project-wide, and attributes declared
   there propagate into every export.

> **Historical note:** an earlier version of this skill tried to auto-filter the
> candidate list with a git "sync-timestamp" heuristic (skip any key added before
> the last `New Crowdin translations` commit). It was **dropped** because it
> produced false negatives: a key added shortly before an export that translators
> simply hadn't reached yet is genuinely missing, but the heuristic classified it
> as "Crowdin already decided." Trust the raw diff + the Crowdin UI instead.

## Target Locales

The default set of locales (unless the user specifies otherwise):

| Locale | Language | Directory |
|--------|----------|-----------|
| `cs` | Czech | `values-cs` |
| `pt-rBR` | Brazilian Portuguese | `values-pt-rBR` |
| `sv-rSE` | Swedish | `values-sv-rSE` |
| `de-rDE` | German | `values-de-rDE` |

> Czech was consolidated onto the base qualifier (PR #3461, 2026-07-03): a
> `cs: cs` `languages_mapping` entry in `crowdin.yml` makes Crowdin export to
> `values-cs`, and `values-cs-rCZ` no longer exists. The other locales still
> use Crowdin's default region-qualified `androidCode` until they are
> consolidated the same way — update this table as each one moves.

## Technique

### 1. Identify files

Do this for **each** resource tree (see "Resource trees" above). The examples below use the amethyst base path; repeat every step with the commons base path swapped in.

```
# amethyst tree
Default:  amethystShared/src/androidMain/res/values/strings.xml
Target:   amethystShared/src/androidMain/res/values-<locale>/strings.xml

# commons tree
Default:  commons/src/commonMain/composeResources/values/strings.xml
Target:   commons/src/commonMain/composeResources/values-<locale>/strings.xml
```

A convenient way to run the whole technique twice is to loop over the two base dirs:

```bash
for base in amethyst/src/main/res commons/src/commonMain/composeResources; do
  echo "########## tree: $base ##########"
  # ... run the diff/count/value-extraction commands with $base/values[...] ...
done
```

### 2. Find missing keys using cs as reference

Always diff against `cs` first — it is the most complete locale and serves as the reference. Any keys missing in `cs` will also be missing in the other target locales.

You MUST diff **both** `<string name=` AND `<plurals name=` — these are independent resource types and a key that is a `<plurals>` in the source will never appear in a `<string>` diff. Forgetting `<plurals>` is the most common silent failure of this skill (it misses things like `music_playlist_track_count`, `notification_count_more`, etc.).

```bash
# Strings: extract translatable keys from default (exclude translatable="false")
echo "=== missing <string> ==="
comm -23 \
  <(grep '<string name=' amethystShared/src/androidMain/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethystShared/src/androidMain/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort)

# Plurals: a separate resource type — MUST be diffed independently
echo "=== missing <plurals> ==="
comm -23 \
  <(grep '<plurals name=' amethystShared/src/androidMain/res/values/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<plurals name=' amethystShared/src/androidMain/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort)
```

This gives two lists of missing key names — keep them separate; `<plurals>` translations need the per-locale CLDR category set (see Step 5 → "Plurals: handle with care").

Crowdin can asymmetrically strip keys across locales (each translator independently chose source-identical for different keys), so **cs is not a reliable upper bound**. Diff **every** target locale and union the results — don't assume the cs set covers the others. A quick per-locale count is a useful sanity check against the Crowdin UI's "N untranslated":

```bash
for locale in cs de-rDE sv-rSE pt-rBR; do
  ns=$(comm -23 \
    <(grep '<string name=' amethystShared/src/androidMain/res/values/strings.xml \
      | grep -v 'translatable="false"' | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
    <(grep '<string name=' amethystShared/src/androidMain/res/values-$locale/strings.xml \
      | sed 's/.*name="\([^"]*\)".*/\1/' | sort) | wc -l)
  np=$(comm -23 \
    <(grep '<plurals name=' amethystShared/src/androidMain/res/values/strings.xml \
      | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
    <(grep '<plurals name=' amethystShared/src/androidMain/res/values-$locale/strings.xml \
      | sed 's/.*name="\([^"]*\)".*/\1/' | sort) | wc -l)
  echo "$locale: strings=$ns plurals=$np total=$((ns+np))"
done
```

The combined `strings + plurals` total should line up with the Crowdin web UI's untranslated count for that locale. If it does, the raw diff is your actionable set (minus any source-identical entries you skip by inspection — see Background).

### 3. Get English values for missing keys

For each missing key, extract its English value. `<string>` is a single line; `<plurals>` is a multi-line block — handle each appropriately.

```bash
# Missing <string>: full line from default strings.xml
while IFS= read -r key; do
  grep "name=\"$key\"" amethystShared/src/androidMain/res/values/strings.xml
done < <(comm -23 \
  <(grep '<string name=' amethystShared/src/androidMain/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethystShared/src/androidMain/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort))

# Missing <plurals>: extract the multi-line block (opening tag through </plurals>)
while IFS= read -r key; do
  awk -v key="$key" '
    $0 ~ "<plurals name=\"" key "\"" { in_p = 1 }
    in_p { print }
    in_p && /<\/plurals>/ { in_p = 0 }
  ' amethystShared/src/androidMain/res/values/strings.xml
done < <(comm -23 \
  <(grep '<plurals name=' amethystShared/src/androidMain/res/values/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<plurals name=' amethystShared/src/androidMain/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort))
```

### 4. Audit missing strings for plural-shaped patterns

Before presenting results, **scan the missing English strings** for two red-flag patterns and warn the user about each match:

1. **Hardcoded `"1"` next to a noun.** A new English string like `"1 reply"`, `"1 follower"`, or `"1 minute ago"` almost always belongs in a `<plurals>` resource — not a `<string>`. Hardcoding `1` in English forces every translator to either also hardcode `1` (breaking languages where the `one` category covers other numbers, e.g. some Slavic languages) or to silently change the meaning.
2. **A `%d` / `%1$d` placeholder in a clearly singular/plural sentence** (e.g. `"%1$d reply"`, `"%d follower"`). Even though the placeholder is parameterised, English-only `one`/`other` agreement won't survive translation into languages that need `few`/`many`.

Also **audit existing `<plurals>` resources** for two anti-patterns:

1. **`quantity="one"` items that hardcode the literal `1`** (instead of using a `%d` / `%1$d` placeholder) — broken for languages where the `one` CLDR category covers more than just `n=1` (Russian, Ukrainian, Croatian, etc.).
2. **`quantity="zero"` items in any locale that doesn't natively use the `zero` CLDR category** — i.e. everything except **Arabic (`ar`)**, **Latvian (`lv`)** and **Welsh (`cy`)**. ICU/CLDR maps `count=0` to `other` for English and most of the locales we ship to (cs, de, pt-BR, sv, etc.), so `<item quantity="zero">` is **dead code** there: `getQuantityString(id, 0)` will pick `other`, never the zero entry, and the visible runtime string ends up `"…0 items"` instead of the intended `"…no items"`.

> ⚠️ **Latvian is the trap here — do NOT strip its `zero` items** (we nearly did, 2026-07-22). `lv` has an integer-bearing `zero` category that covers far more than 0: `select(0)`, `select(10)` and `select(11)` all return `zero` (the rule is `n % 10 = 0` or `n % 100 = 11..19`). So a Latvian `<item quantity="zero">` is *live code on the majority of counts*, and it must read as a normal plural form ("%1$d minūšu"), **not** as "no items" wording. An earlier version of this skill claimed only `ar` and `cy` had `zero`, which flagged all ~40 correct Latvian entries as dead and would have deleted working translations.

If a UX genuinely wants special "no items" wording at count=0, that has to be a call-site `if (count == 0)` branch to a separate `<string>`, **not** a `quantity="zero"` plural item. (This is why `zero` is the wrong tool even where it exists: in `lv` it does not mean "zero".)

**Verify, don't recall.** Before asserting any locale's category set, check it against CLDR rather than memory:

```bash
python3 -m venv /tmp/cldr && /tmp/cldr/bin/pip -q install babel
/tmp/cldr/bin/python -c "
from babel import Locale
for c in ['en','lv','ar','cy','cs','de','sv','pt_BR','ru','pl']:
    r = Locale.parse(c).plural_form
    print(c, sorted({r(n) for n in range(0,10001)}), 'select(0)=', r(0), 'select(10)=', r(10))
"
```

Across the 56 locale dirs this repo ships, **only `ar-rSA` and `lv-rLV`** have an integer-bearing `zero`.

Flag and offer to fix:

```bash
# Scan every locale's strings.xml for <item quantity="one"> entries that
# hardcode "1" (or other literal digits) instead of using a placeholder.
# Looks at default + all values-* locales, in BOTH resource trees.
for f in amethystShared/src/androidMain/res/values/strings.xml amethystShared/src/androidMain/res/values-*/strings.xml \
         commons/src/commonMain/composeResources/values/strings.xml \
         commons/src/commonMain/composeResources/values-*/strings.xml; do
  awk -v file="$f" '
    /<plurals/ { in_plurals = 1; name = $0; sub(/.*name="/, "", name); sub(/".*/, "", name) }
    in_plurals && /quantity="one"/ {
      # Extract item text (between > and <)
      text = $0; sub(/^[^>]*>/, "", text); sub(/<.*$/, "", text)
      # Flag if it contains a digit AND no %d / %1$d placeholder
      if (text ~ /[0-9]/ && text !~ /%[0-9]*\$?d/) {
        print file ":  <plurals name=\"" name "\">  one=\"" text "\""
      }
    }
    /<\/plurals>/ { in_plurals = 0 }
  ' "$f"
done
```

Then scan for dead `quantity="zero"` entries. CLDR's `zero` category is integer-bearing only in **Arabic (`ar`)**, **Latvian (`lv`)** and **Welsh (`cy`)** — those three are skipped below, so a hit is a genuine bug. In every other locale, count=0 falls through to `other`, so a `<item quantity="zero">` entry is dead and likely a translator/author bug (or it silently never fires):

```bash
for f in amethystShared/src/androidMain/res/values/strings.xml amethystShared/src/androidMain/res/values-*/strings.xml \
         commons/src/commonMain/composeResources/values/strings.xml \
         commons/src/commonMain/composeResources/values-*/strings.xml; do
  # Skip Arabic, Latvian and Welsh — they natively use the zero category.
  # (Latvian's zero covers 0, 10, 11-19, 20, 30, … — stripping it breaks most counts.)
  case "$f" in
    *values-ar*|*values-cy*|*values-lv*) continue ;;
  esac
  awk -v file="$f" '
    /<plurals/ { in_plurals = 1; name = $0; sub(/.*name="/, "", name); sub(/".*/, "", name) }
    in_plurals && /quantity="zero"/ {
      text = $0; sub(/^[^>]*>/, "", text); sub(/<.*$/, "", text)
      print file ":  <plurals name=\"" name "\">  zero=\"" text "\""
    }
    /<\/plurals>/ { in_plurals = 0 }
  ' "$f"
done
```

For each hit, warn the user that the entry is unreachable in that locale. The fix is to **remove the `<item quantity="zero">`** and, if the UX wanted distinct wording for count=0, add a separate `<string>` plus an `if (count == 0)` branch at the call site (see "Plurals: handle with care" below).

Also audit **format-specifier parity and empty items** across the locales you
touched. These are a different defect class from a missing key — the key is
present and looks translated, but the placeholder was dropped, escaped, or the
item left blank, so the number never reaches the user:

```bash
python3 - <<'PY'
import re, io, glob
keyre = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
plre  = re.compile(r'<plurals name="([^"]+)"[^>]*>(.*?)</plurals>', re.S)
itre  = re.compile(r'<item quantity="([^"]+)"[^>]*>(.*?)</item>', re.S)
# (?<!\\) is REQUIRED: \%2$d is an escaped literal, not a placeholder.
phre  = re.compile(r'(?<!\\)%(?:(\d+)\$)?([sdf])')
sig = lambda t: sorted(m.group(0) for m in phre.finditer(t))
for base in ['amethyst/src/main/res', 'commons/src/commonMain/composeResources']:
    d = io.open(f'{base}/values/strings.xml', encoding='utf-8').read()
    dstr = {m.group(1): sig(m.group(2)) for m in keyre.finditer(d)}
    dpl  = {}
    for m in plre.finditer(d):
        s = set()
        for it in itre.finditer(m.group(2)): s.update(sig(it.group(2)))
        dpl[m.group(1)] = sorted(s)
    for p in sorted(glob.glob(f'{base}/values-*/strings.xml')):
        if '/values-ar' in p: continue   # see caveat below
        s = io.open(p, encoding='utf-8').read()
        for m in keyre.finditer(s):
            k, v = m.group(1), m.group(2)
            if k in dstr and sig(v) != dstr[k]:
                print(f'{p}\n    {k}  base={dstr[k]}  loc={sig(v)}')
        for m in plre.finditer(s):
            k = m.group(1)
            if k not in dpl: continue
            for it in itre.finditer(m.group(2)):
                if sig(it.group(2)) != dpl[k]:
                    print(f'{p}\n    {k}[{it.group(1)}]  base={dpl[k]}  loc={sig(it.group(2))}')
PY

# Empty plural items render as nothing at runtime — always a bug.
grep -rn '<item quantity="[a-z]*"></item>' \
  amethystShared/src/androidMain/res/values*/strings.xml \
  commons/src/commonMain/composeResources/values*/strings.xml
```

Three things this scan taught us, all of which it now encodes:

- **The `(?<!\\)` lookbehind is not optional.** Without it the scan matches
  `%2$d` *inside* `\%2$d` and scores a broken string clean. `\%` is not a
  recognised Android escape, but lint reads it as one, so the placeholder is
  reported missing. (2026-08-13: `nip46_signer_relays_some_down` in sl-rSI
  survived a "clean" sweep exactly this way.)
- **Skip Arabic.** Its `zero`/`one`/`two` forms omit the numeral idiomatically
  ("دقيقتان" = "two minutes"), so ~30 hits there are correct translations, not
  defects. Everything else is worth reading.
- **Repeated indices are legitimate.** `%1$s` appearing twice in a translation
  where the base uses it once is normal — German repeats the name where English
  says "They". Compare *sets*, and treat an arity difference as a question, not
  a verdict.

Quick scan over the missing keys:

```bash
# Flag missing English values that look like they should be <plurals>
while IFS= read -r key; do
  line=$(grep "name=\"$key\"" amethystShared/src/androidMain/res/values/strings.xml)
  # Hardcoded standalone "1" (word-boundary), or a count placeholder followed by a likely-countable noun
  if echo "$line" | grep -qE '>([^<]*\b1\b[^<]*|[^<]*%[0-9]*\$?d[^<]*)<'; then
    echo "PLURAL CANDIDATE: $line"
  fi
done < <(comm -23 \
  <(grep '<string name=' amethystShared/src/androidMain/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethystShared/src/androidMain/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort))
```

The regex is intentionally noisy — review each hit by hand. Many `%d` strings (e.g. `"Limits for kind %1$d"`, `"Max event size (bytes)"`) are *not* plural-bearing. Only flag the ones whose surrounding noun changes form with the count.

For each genuine match, **stop and warn the user before translating**, e.g.:

> ⚠️ `notification_count` is `"1 new reply"` — this hardcodes `"1"` and should likely be a `<plurals>` resource (e.g. `quantity="one"` → `"%d new reply"`, `quantity="other"` → `"%d new replies"`). Convert before translating?

Do not silently translate plural-shaped `<string>` entries; the wrong shape will then need to be fixed in every locale.

### 5. Present results and ask to translate

Output the missing entries as raw XML resource lines (copy-paste ready):

```xml
    <string name="attestation_valid">Valid</string>
    <string name="attestation_valid_from">Valid from %1$s</string>
    <string name="feed_group_lists">Lists</string>
```

Also check `<string-array>` and `<plurals>` tags using the same approach if the project uses them.

#### Plurals: handle with care

When adding or proposing **`<plurals>`** entries, follow these rules:

- **Never hardcode `"1"`** in the English text of a `quantity="one"` item. Use the format placeholder (e.g. `%1$d` / `%d`) so the runtime substitutes the actual count. Hardcoding `"1"` breaks every language whose `one` category covers numbers other than 1 (e.g. some Slavic languages).
- **Don't assume `one` + `other` is enough.** CLDR plural categories vary by language: `zero`, `one`, `two`, `few`, `many`, `other`. Always include **every category the target language uses**, not just the categories present in English. Examples:
  - English (`en`): `one`, `other`
  - Czech (`cs`): `one`, `few`, `many`, `other`
  - Polish (`pl`): `one`, `few`, `many`, `other`
  - Russian (`ru`): `one`, `few`, `many`, `other`
  - Arabic (`ar`): `zero`, `one`, `two`, `few`, `many`, `other`
  - Latvian (`lv`): `zero`, `one`, `other` — its `zero` is **not** "no items"; it covers 0, 10, 11–19, 20, 30, …
  - German / Swedish / Brazilian Portuguese: `one`, `other`
- When a missing string contains a count placeholder and is conceptually a singular/plural pair, **flag it before translating** — it may belong as a `<plurals>` resource rather than a single `<string>`. Surface this to the user before proposing translations.
- **Do not use `quantity="zero"` outside Arabic (`ar`), Latvian (`lv`) and Welsh (`cy`).** CLDR's `zero` category is integer-bearing only in those three languages. Android calls `PluralRules.select(0)` for the device locale; in English/German/Czech/Polish/Russian/Swedish/Portuguese/etc. it returns `other`, so the explicit `<item quantity="zero">` is never picked at runtime and the user sees `"…0 items"` instead of the intended wording. Conversely, **never delete an existing `zero` item from `ar`/`lv`/`cy`** — there it is live. If the design calls for "no items" at count=0, model it as a separate `<string>` and an `if (count == 0)` branch at the call site:
  ```kotlin
  val label = if (count == 0) {
      stringRes(R.string.foo_no_items, dateLabel)
  } else {
      pluralStringResource(R.plurals.foo_items, count, dateLabel, count)
  }
  ```
- **Converting an existing `<string>` to `<plurals>`: give every locale its FULL
  category set, not just `other`.** You must convert it in every locale that
  already had the `<string>` (aapt2 rejects a resource-type mismatch across
  locales, and an orphaned locale `<string>` trips `ExtraTranslation`) — but
  carrying the old text across as an `other`-only block, on the theory that
  Crowdin backfills the rest, **fails `MissingQuantity` and breaks CI before
  Crowdin ever gets a turn.** Supply `one`/`few`/`many` for pl, `one` for hu, and
  so on, at conversion time.

  Note this contradicts `amethyst/src/main/res/CLAUDE.md` step 3, which still
  advises the `other`-only shortcut. That advice is wrong; prefer this.

  Watch the declension when you do it: the retained text is usually the *plural*
  form, so reusing it verbatim for `one` produces "1 odpowiedzi". (2026-08-13:
  converting `poll_results_selections` with `other` only errored on both hu and
  pl, and the retained pl text was the few/many form.)

- **A `tools:ignore` suppression must go on the SOURCE entry in
  `values/strings.xml`, never on a locale file.** Crowdin propagates attributes
  declared on the source into every translation it exports; an attribute you add
  to `values-xx/strings.xml` alone is simply absent from the next export. That is
  why the existing `tools:ignore="Typos"` entries survive — they are declared on
  the source, and the copies in cs/de/ar/eo/bn are the *result* of propagation,
  not evidence that locale-file attributes stick. (2026-08-13: an
  `ImpliedQuantity` suppression added only to `values-pt-rBR` was stripped by the
  next sync and took `main`'s CI red.)

  Before reaching for a suppression at all, check whether the key is even used —
  a `grep -rn "<key>" --include='*.kt'` that returns nothing means deleting the
  key is the better fix than muting the rule that objects to it.

- Reference: [Android `<plurals>` docs](https://developer.android.com/guide/topics/resources/string-resource#Plurals) and [CLDR plural rules](https://unicode-org.github.io/cldr-staging/charts/latest/supplemental/language_plural_rules.html).

**Then ask the user:** "Would you like me to translate these missing strings into [list of target locales]?"

### 6. Adding translations (if approved)

When adding translated strings to locale files:

- **Append new strings at the bottom** of the file, just before the closing `</resources>` tag.
- Do NOT try to insert them in alphabetical or matching order — a separate process handles ordering.
- **Insert into each locale ONLY the keys missing from *that* locale — never a shared "union" block.** Because Crowdin strips keys asymmetrically (Step 2), a key you translate may already exist in some target locales. If you compute one union set of missing keys, translate it, and paste the *same* block into every locale, you will create **duplicate keys** in whichever locales already had them. Drive the insertion off the **per-locale** diff, not the union:

  ```bash
  # For each locale, insert only the keys comm -23 reports missing FOR THAT LOCALE.
  for l in cs de-rDE sv-rSE pt-rBR; do
    missing=$(comm -23 \
      <(grep '<string name=' $base/values/strings.xml | grep -v 'translatable="false"' \
        | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
      <(grep '<string name=' $base/values-$l/strings.xml \
        | sed 's/.*name="\([^"]*\)".*/\1/' | sort))
    # ... append ONLY the $missing keys' translations to values-$l/strings.xml ...
  done
  ```

  (This bit us on 2026-07-21: `ps1_save_block`, `podcast_value_for_value`, and `chats_history_relays` were each missing in only *some* commons locales, but the same 3-key block was pasted into all four — producing duplicates in the locales that already had them.)

- **After inserting, verify each edited file has no duplicate keys AND is well-formed XML — before you call the task done.** A duplicate key is not a warning: the `commons` tree's Compose-resources build task fails hard on it (`convertXmlValueResourcesForCommonMain: … Duplicated key '…'`), which breaks the build for everyone. Quick post-insertion gate over every file you touched:

  ```bash
  for f in <every edited strings.xml>; do
    dups=$(grep -oE '<(string|plurals) name="[^"]*"' "$f" \
      | sed 's/.*name="\([^"]*\)"/\1/' | sort | uniq -d)
    [ -n "$dups" ] && echo "DUP in $f: $dups"
    python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('$f')" \
      || echo "MALFORMED $f"
  done
  # For a commons change, also run the build task that enforces this:
  #   ./gradlew :commons:convertXmlValueResourcesForCommonMain
  ```

- **Then run Android lint. This is the gate that actually matches CI, and the
  checks above do NOT substitute for it.** Duplicate-key + well-formedness +
  `convertXmlValueResourcesForCommonMain` can all pass on a change that still
  takes CI red, because the plural rules live in lint, not in the resource
  compiler:

  ```bash
  ./gradlew :amethyst:lintPlayBenchmark     # the task CI runs (.github/workflows/build.yml)
  ```

  There is no `lint-baseline.xml` in this repo and only `MissingTranslation` is
  disabled (`amethyst/build.gradle.kts`), so `abortOnError` bites on the first
  error. Three rules matter for a translation pass:

  | Rule | Fires when | Severity |
  |------|-----------|----------|
  | `MissingQuantity` | a locale's `<plurals>` omits a CLDR category that locale uses | **error** for core categories — gates CI |
  | `ImpliedQuantity` | a `quantity` item has no format argument in a locale where that category spans more than one number | **error** — gates CI |
  | `StringFormatCount` / `StringFormatMatches` | a translation's placeholder count/type disagrees with the base entry | warning |

  (2026-08-13: a pass that cleared the duplicate/XML gate above still failed
  `lintPlayBenchmark` with 3 errors. Compiling is not evidence — `compileDebugKotlin`
  passed on the same change.)

- **Confirm the report says zero errors, don't just trust BUILD SUCCESSFUL** of a
  wider invocation:

  ```bash
  python3 -c "
  import json,io,collections
  d=json.load(io.open('amethyst/build/reports/lint-results-playBenchmark.sarif',encoding='utf-8'))
  r=d['runs'][0]['results']
  print(dict(collections.Counter(x.get('level','warning') for x in r)))
  for x in r:
      if x.get('level')=='error': print('ERROR', x['ruleId'], x['locations'][0]['physicalLocation']['artifactLocation']['uri'])
  "
  ```

## Common Mistakes

- **Scanning only the amethyst tree** — there are now **two** Crowdin-managed `strings.xml` trees (`amethyst/src/main/res` and `commons/src/commonMain/composeResources`). A key extracted into `commons/` will never show up in the amethyst diff. Run the whole technique once per tree (see "Resource trees") and report each separately.
- **Copying an overlapping `commons` translation by key name alone** — a shared key name does NOT mean shared English. `napplet_card_permissions` is "What it can access" in commons but "Permissions:" in amethyst; copying by name produced the wrong string. Diff the English *values* first; copy verbatim only when they're byte-identical, else translate fresh (see "Overlap" in Resource trees).
- **Applying amethyst's `"…"` whitespace-quote convention to a commons string** — the commons Compose-resources tree authors edge whitespace raw and unquoted; wrapping quotes copied from amethyst render literally there. Match the commons source format.
- **Trying to "dedupe" the amethyst↔commons value-overlap** — it's required architecture (commons can't depend on amethyst, so shared composables need their own `Res.string` catalog), not an error. Don't fold consolidation into a translation pass.
- **Forgetting `translatable="false"`** — these should never appear in locale files
- **Diffing only `<string name=`** — `<plurals>` is a separate resource type; a source `<plurals>` missing from a locale will never show up in a `<string>` diff. Always run the diff twice (once per resource type) as shown in Step 2. The same goes for `<string-array>` if the project uses it.
- **Trusting a git "sync-timestamp" heuristic to pre-filter the list** — this skill used to skip keys added before the last `New Crowdin translations` commit, on the theory that Crowdin had already "decided" them. It was dropped: a key added shortly before an export that translators hadn't reached yet is genuinely missing, so the heuristic silently dropped real work. Use the raw on-disk diff and reconcile against the Crowdin web UI's untranslated count instead.
- **Adding source-identical fallbacks locally** — they get overwritten on the next Crowdin sync. Android falls back to `values/strings.xml` at runtime anyway, so a key intentionally kept as English already renders correctly. Skip these by inspection (brand terms, loanwords, `v%1$s`-style strings); don't translate them to an identical value.
- **Skipping per-locale diffs when only diffing cs** — Crowdin can strip different keys in different locales (each translator's choice), so cs is not a reliable upper bound. Diff each target locale and union the results.
- **Pasting the union set of missing keys into every locale → duplicate keys** — the union is the right set to *translate*, but the wrong set to *insert*. A key missing in only some locales, inserted into all of them, duplicates in the ones that already had it. Drive each file's insertion off its own per-locale diff (see Step 6). In `commons`, a duplicate key is build-breaking: `convertXmlValueResourcesForCommonMain` fails with `Duplicated key '…'`. **Always run the post-insertion duplicate + XML-wellformedness gate in Step 6 before declaring done.** (Happened 2026-07-21 with `ps1_save_block` / `podcast_value_for_value` / `chats_history_relays`.)
- **Declaring the pass done without running `:amethyst:lintPlayBenchmark`** — the duplicate-key + XML + `convertXmlValueResourcesForCommonMain` gate is necessary but nowhere near sufficient. `MissingQuantity` and `ImpliedQuantity` are errors, there is no lint baseline, and `abortOnError` is on, so a change that compiles and passes every check in Step 6's first half can still take CI red. Compiling is not evidence. (Happened 2026-08-13: 3 lint errors after a clean duplicate/XML gate and a green `compileFdroidDebugKotlin`.)
- **Converting a `<string>` to `<plurals>` with `other` only** — "Crowdin fills the rest" is false; `MissingQuantity` errors immediately and CI fails before any sync. Supply every category the locale uses at conversion time, and re-check the declension rather than reusing the old text for `one`.
- **Putting `tools:ignore` on a locale file** — Crowdin strips it on the next export. Suppressions belong on the source entry in `values/strings.xml`, which propagates. The `tools:ignore="Typos"` copies visible in cs/de/ar/eo/bn are the *result* of that propagation, not proof that locale-file attributes survive. (Happened 2026-08-13; it broke `main`.)
- **Suppressing a lint rule on a key nothing references** — check `grep -rn "<key>" --include='*.kt'` first. `poll_results_voters` was a bare noun with no count, zero call sites, and an unlocalizable shape; deleting it retired the problem outright where a suppression would only have muted it.
- **Comparing placeholders without a `(?<!\\)` guard** — `\%2$d` is an escaped literal to lint, but a naive `%\d+\$[sd]` regex matches the placeholder inside it and reports the string clean. A parity sweep missing this guard will certify a broken translation. Also treat a *repeated* index (`%1$s` twice where the base has it once) as legitimate — German does this where English says "They".
- **Reading an empty `<item quantity="…"></item>` as merely "untranslated"** — it renders as nothing at runtime, and for a category like Polish `many` (5–21, 25–31, …) that is the common case, not an edge case. Grep for them explicitly; the missing-key diff will never surface one because the key is present.
- **Inserting strings in a specific position** — always append at the bottom; ordering is handled separately
- **Hardcoding `"1"` in a `<plurals>` `quantity="one"` item** — always use the count placeholder; otherwise non-English `one` categories produce wrong text
- **Copying English's `one`/`other` set into every locale** — each language must include all CLDR plural categories it uses (e.g. Czech needs `one`, `few`, `many`, `other`)
- **Using `<item quantity="zero">` to special-case count=0** — outside Arabic, Latvian and Welsh, this entry is unreachable: ICU/CLDR maps 0 → `other`, so the runtime never picks the zero item and the user sees `"…0 items"`. Special-case at the call site with a separate `<string>` instead.
- **Reporting Latvian `quantity="zero"` entries as dead code** — `lv` has a real, integer-bearing `zero` category covering 0, 10, 11–19, 20, 30, … so those entries fire on *most* counts. An earlier version of this skill excluded only `ar`/`cy` from the zero audit and flagged all ~40 correct `values-lv-rLV` entries; acting on that would have deleted working translations. Confirm any locale's category set against CLDR (the babel snippet in Step 4) before calling a `zero` item dead.