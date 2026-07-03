---
name: find-missing-translations
description: Use when comparing Android strings.xml locale files to find untranslated string resources, missing translation keys, or preparing translation work for a specific language
---

# Find Missing Translations

## Overview

Extract string resource keys from the default `values/strings.xml` that are absent in a target locale's `strings.xml`, excluding non-translatable entries. Outputs missing keys and offers to translate them.

## When to Use

- Need to find untranslated strings for a specific locale
- Preparing a batch of strings for a translator
- Checking translation coverage after adding new features

## Background: Crowdin strip-identical behavior

This repo syncs translations via Crowdin (branch `l10n_crowdin_translations`). Crowdin's default export behavior **omits any translation that exactly equals the source**, so a key that the translator deliberately kept as English (common for brand terms like `"Nowhere Drop"`, single-word loanwords like `"Apps"` / `"Feed"` / `"Issues"`, or version prefixes like `"v%1$s"`) will not appear in the locale's `strings.xml` even though the Crowdin UI shows it as 100% translated.

What this means for this skill:

1. **The raw on-disk diff is the candidate set.** A key missing from a locale file is either genuinely untranslated *or* a source-identical entry Crowdin stripped. Both are reported; the human decides which to skip. The Crowdin web UI ("N untranslated") is the ground truth for what genuinely needs work.
2. **Source-identical entries are a small, recognizable minority.** Brand terms (`Nowhere X`), single-word loanwords (`Apps` / `Feed` / `Issues`), and bare version/format strings (`v%1$s`) are the usual cases. Skip these by inspection rather than translating them to something identical.
3. **Don't add source-identical fallbacks.** Android falls back to `values/strings.xml` at runtime, so a key intentionally kept as English already renders correctly, and Crowdin's next sync would strip a local duplicate anyway.

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

```
Default:  amethyst/src/main/res/values/strings.xml
Target:   amethyst/src/main/res/values-<locale>/strings.xml
```

### 2. Find missing keys using cs as reference

Always diff against `cs` first — it is the most complete locale and serves as the reference. Any keys missing in `cs` will also be missing in the other target locales.

You MUST diff **both** `<string name=` AND `<plurals name=` — these are independent resource types and a key that is a `<plurals>` in the source will never appear in a `<string>` diff. Forgetting `<plurals>` is the most common silent failure of this skill (it misses things like `music_playlist_track_count`, `notification_count_more`, etc.).

```bash
# Strings: extract translatable keys from default (exclude translatable="false")
echo "=== missing <string> ==="
comm -23 \
  <(grep '<string name=' amethyst/src/main/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethyst/src/main/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort)

# Plurals: a separate resource type — MUST be diffed independently
echo "=== missing <plurals> ==="
comm -23 \
  <(grep '<plurals name=' amethyst/src/main/res/values/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<plurals name=' amethyst/src/main/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort)
```

This gives two lists of missing key names — keep them separate; `<plurals>` translations need the per-locale CLDR category set (see Step 5 → "Plurals: handle with care").

Crowdin can asymmetrically strip keys across locales (each translator independently chose source-identical for different keys), so **cs is not a reliable upper bound**. Diff **every** target locale and union the results — don't assume the cs set covers the others. A quick per-locale count is a useful sanity check against the Crowdin UI's "N untranslated":

```bash
for locale in cs de-rDE sv-rSE pt-rBR; do
  ns=$(comm -23 \
    <(grep '<string name=' amethyst/src/main/res/values/strings.xml \
      | grep -v 'translatable="false"' | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
    <(grep '<string name=' amethyst/src/main/res/values-$locale/strings.xml \
      | sed 's/.*name="\([^"]*\)".*/\1/' | sort) | wc -l)
  np=$(comm -23 \
    <(grep '<plurals name=' amethyst/src/main/res/values/strings.xml \
      | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
    <(grep '<plurals name=' amethyst/src/main/res/values-$locale/strings.xml \
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
  grep "name=\"$key\"" amethyst/src/main/res/values/strings.xml
done < <(comm -23 \
  <(grep '<string name=' amethyst/src/main/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethyst/src/main/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort))

# Missing <plurals>: extract the multi-line block (opening tag through </plurals>)
while IFS= read -r key; do
  awk -v key="$key" '
    $0 ~ "<plurals name=\"" key "\"" { in_p = 1 }
    in_p { print }
    in_p && /<\/plurals>/ { in_p = 0 }
  ' amethyst/src/main/res/values/strings.xml
done < <(comm -23 \
  <(grep '<plurals name=' amethyst/src/main/res/values/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<plurals name=' amethyst/src/main/res/values-cs/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort))
```

### 4. Audit missing strings for plural-shaped patterns

Before presenting results, **scan the missing English strings** for two red-flag patterns and warn the user about each match:

1. **Hardcoded `"1"` next to a noun.** A new English string like `"1 reply"`, `"1 follower"`, or `"1 minute ago"` almost always belongs in a `<plurals>` resource — not a `<string>`. Hardcoding `1` in English forces every translator to either also hardcode `1` (breaking languages where the `one` category covers other numbers, e.g. some Slavic languages) or to silently change the meaning.
2. **A `%d` / `%1$d` placeholder in a clearly singular/plural sentence** (e.g. `"%1$d reply"`, `"%d follower"`). Even though the placeholder is parameterised, English-only `one`/`other` agreement won't survive translation into languages that need `few`/`many`.

Also **audit existing `<plurals>` resources** for two anti-patterns:

1. **`quantity="one"` items that hardcode the literal `1`** (instead of using a `%d` / `%1$d` placeholder) — broken for languages where the `one` CLDR category covers more than just `n=1` (Russian, Ukrainian, Croatian, etc.).
2. **`quantity="zero"` items in any locale that doesn't natively use the `zero` CLDR category** — i.e. **everything except Arabic (`ar`) and Welsh (`cy`)**. ICU/CLDR maps `count=0` to `other` for English and all the locales we ship to (cs, de, pt-BR, sv, etc.), so `<item quantity="zero">` is **dead code** there: `getQuantityString(id, 0)` will pick `other`, never the zero entry, and the visible runtime string ends up `"…0 items"` instead of the intended `"…no items"`.

If a UX genuinely wants special "no items" wording at count=0, that has to be a call-site `if (count == 0)` branch to a separate `<string>`, **not** a `quantity="zero"` plural item.

Flag and offer to fix:

```bash
# Scan every locale's strings.xml for <item quantity="one"> entries that
# hardcode "1" (or other literal digits) instead of using a placeholder.
# Looks at default + all values-* locales.
for f in amethyst/src/main/res/values/strings.xml amethyst/src/main/res/values-*/strings.xml; do
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

Then scan for dead `quantity="zero"` entries. CLDR's `zero` category is integer-bearing only in **Arabic (`ar`)** and **Welsh (`cy`)**. In every other locale, count=0 falls through to `other`, so a `<item quantity="zero">` entry is dead and likely a translator/author bug (or it silently never fires):

```bash
for f in amethyst/src/main/res/values/strings.xml amethyst/src/main/res/values-*/strings.xml; do
  # Skip Arabic and Welsh — they natively use the zero category.
  case "$f" in
    *values-ar*|*values-cy*) continue ;;
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

Quick scan over the missing keys:

```bash
# Flag missing English values that look like they should be <plurals>
while IFS= read -r key; do
  line=$(grep "name=\"$key\"" amethyst/src/main/res/values/strings.xml)
  # Hardcoded standalone "1" (word-boundary), or a count placeholder followed by a likely-countable noun
  if echo "$line" | grep -qE '>([^<]*\b1\b[^<]*|[^<]*%[0-9]*\$?d[^<]*)<'; then
    echo "PLURAL CANDIDATE: $line"
  fi
done < <(comm -23 \
  <(grep '<string name=' amethyst/src/main/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethyst/src/main/res/values-cs/strings.xml \
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
  - German / Swedish / Brazilian Portuguese: `one`, `other`
- When a missing string contains a count placeholder and is conceptually a singular/plural pair, **flag it before translating** — it may belong as a `<plurals>` resource rather than a single `<string>`. Surface this to the user before proposing translations.
- **Do not use `quantity="zero"` outside Arabic (`ar`) and Welsh (`cy`).** CLDR's `zero` category is integer-bearing only in those two languages. Android calls `PluralRules.select(0)` for the device locale; in English/German/Czech/Polish/Russian/Swedish/Portuguese/etc. it returns `other`, so the explicit `<item quantity="zero">` is never picked at runtime and the user sees `"…0 items"` instead of the intended wording. If the design calls for "no items" at count=0, model it as a separate `<string>` and an `if (count == 0)` branch at the call site:
  ```kotlin
  val label = if (count == 0) {
      stringRes(R.string.foo_no_items, dateLabel)
  } else {
      pluralStringResource(R.plurals.foo_items, count, dateLabel, count)
  }
  ```
- Reference: [Android `<plurals>` docs](https://developer.android.com/guide/topics/resources/string-resource#Plurals) and [CLDR plural rules](https://unicode-org.github.io/cldr-staging/charts/latest/supplemental/language_plural_rules.html).

**Then ask the user:** "Would you like me to translate these missing strings into [list of target locales]?"

### 6. Adding translations (if approved)

When adding translated strings to locale files:

- **Append new strings at the bottom** of the file, just before the closing `</resources>` tag.
- Do NOT try to insert them in alphabetical or matching order — a separate process handles ordering.

## Common Mistakes

- **Forgetting `translatable="false"`** — these should never appear in locale files
- **Diffing only `<string name=`** — `<plurals>` is a separate resource type; a source `<plurals>` missing from a locale will never show up in a `<string>` diff. Always run the diff twice (once per resource type) as shown in Step 2. The same goes for `<string-array>` if the project uses it.
- **Trusting a git "sync-timestamp" heuristic to pre-filter the list** — this skill used to skip keys added before the last `New Crowdin translations` commit, on the theory that Crowdin had already "decided" them. It was dropped: a key added shortly before an export that translators hadn't reached yet is genuinely missing, so the heuristic silently dropped real work. Use the raw on-disk diff and reconcile against the Crowdin web UI's untranslated count instead.
- **Adding source-identical fallbacks locally** — they get overwritten on the next Crowdin sync. Android falls back to `values/strings.xml` at runtime anyway, so a key intentionally kept as English already renders correctly. Skip these by inspection (brand terms, loanwords, `v%1$s`-style strings); don't translate them to an identical value.
- **Skipping per-locale diffs when only diffing cs** — Crowdin can strip different keys in different locales (each translator's choice), so cs is not a reliable upper bound. Diff each target locale and union the results.
- **Inserting strings in a specific position** — always append at the bottom; ordering is handled separately
- **Hardcoding `"1"` in a `<plurals>` `quantity="one"` item** — always use the count placeholder; otherwise non-English `one` categories produce wrong text
- **Copying English's `one`/`other` set into every locale** — each language must include all CLDR plural categories it uses (e.g. Czech needs `one`, `few`, `many`, `other`)
- **Using `<item quantity="zero">` to special-case count=0** — outside Arabic and Welsh, this entry is unreachable: ICU/CLDR maps 0 → `other`, so the runtime never picks the zero item and the user sees `"…0 items"`. Special-case at the call site with a separate `<string>` instead.