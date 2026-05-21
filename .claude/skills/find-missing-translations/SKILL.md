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

## Target Locales

The default set of locales (unless the user specifies otherwise):

| Locale | Language | Directory |
|--------|----------|-----------|
| `cs-rCZ` | Czech | `values-cs-rCZ` |
| `pt-rBR` | Brazilian Portuguese | `values-pt-rBR` |
| `sv-rSE` | Swedish | `values-sv-rSE` |
| `de-rDE` | German | `values-de-rDE` |

## Technique

### 1. Identify files

```
Default:  amethyst/src/main/res/values/strings.xml
Target:   amethyst/src/main/res/values-<locale>/strings.xml
```

### 2. Find missing keys using cs-rCZ as reference

Always diff against `cs-rCZ` first — it is the most complete locale and serves as the reference. Any keys missing in `cs-rCZ` will also be missing in the other target locales.

```bash
# Extract translatable keys from default (exclude translatable="false")
comm -23 \
  <(grep '<string name=' amethyst/src/main/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethyst/src/main/res/values-cs-rCZ/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort)
```

This gives the list of missing key names. Do NOT diff each locale separately — assume the same keys are missing in all target locales.

### 3. Get English values for missing keys

For each missing key, extract its English value:

```bash
# For each missing key, extract the full line from default strings.xml
while IFS= read -r key; do
  grep "name=\"$key\"" amethyst/src/main/res/values/strings.xml
done < <(comm -23 \
  <(grep '<string name=' amethyst/src/main/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethyst/src/main/res/values-cs-rCZ/strings.xml \
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
  <(grep '<string name=' amethyst/src/main/res/values-cs-rCZ/strings.xml \
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
- **Not checking string-arrays/plurals** — only checking `<string>` misses other resource types
- **Diffing each locale separately** — only diff against `cs-rCZ`; assume the same keys are missing everywhere
- **Inserting strings in a specific position** — always append at the bottom; ordering is handled separately
- **Hardcoding `"1"` in a `<plurals>` `quantity="one"` item** — always use the count placeholder; otherwise non-English `one` categories produce wrong text
- **Copying English's `one`/`other` set into every locale** — each language must include all CLDR plural categories it uses (e.g. Czech needs `one`, `few`, `many`, `other`)
- **Using `<item quantity="zero">` to special-case count=0** — outside Arabic and Welsh, this entry is unreachable: ICU/CLDR maps 0 → `other`, so the runtime never picks the zero item and the user sees `"…0 items"`. Special-case at the call site with a separate `<string>` instead.