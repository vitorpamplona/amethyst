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

### 4. Present results and ask to translate

Output the missing entries as raw XML resource lines (copy-paste ready):

```xml
    <string name="attestation_valid">Valid</string>
    <string name="attestation_valid_from">Valid from %1$s</string>
    <string name="feed_group_lists">Lists</string>
```

Also check `<string-array>` and `<plurals>` tags using the same approach if the project uses them.

**Then ask the user:** "Would you like me to translate these missing strings into [list of target locales]?"

### 5. Adding translations (if approved)

When adding translated strings to locale files:

- **Append new strings at the bottom** of the file, just before the closing `</resources>` tag.
- Do NOT try to insert them in alphabetical or matching order — a separate process handles ordering.

## Common Mistakes

- **Forgetting `translatable="false"`** — these should never appear in locale files
- **Not checking string-arrays/plurals** — only checking `<string>` misses other resource types
- **Diffing each locale separately** — only diff against `cs-rCZ`; assume the same keys are missing everywhere
- **Inserting strings in a specific position** — always append at the bottom; ordering is handled separately