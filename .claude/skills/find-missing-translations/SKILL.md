---
name: find-missing-translations
description: Use when comparing Android strings.xml locale files to find untranslated string resources, missing translation keys, or preparing translation work for a specific language
---

# Find Missing Translations

## Overview

Extract string resource keys from the default `values/strings.xml` that are absent in a target locale's `strings.xml`, excluding non-translatable entries. Outputs a table ready for translation.

## When to Use

- Need to find untranslated strings for a specific locale
- Preparing a batch of strings for a translator
- Checking translation coverage after adding new features

## Technique

### 1. Identify files

```
Default:  amethyst/src/main/res/values/strings.xml
Target:   amethyst/src/main/res/values-<locale>/strings.xml
```

Default locale: `cs-rCZ` if none specified. User may override (e.g., `pt-rBR`, `ja`).

### 2. Extract and diff keys

Use a single bash pipeline to extract translatable keys from both files and diff them:

```bash
# Extract translatable keys from default (exclude translatable="false")
comm -23 \
  <(grep '<string name=' amethyst/src/main/res/values/strings.xml \
    | grep -v 'translatable="false"' \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort) \
  <(grep '<string name=' amethyst/src/main/res/values-<LOCALE>/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort)
```

This gives the list of missing key names.

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
  <(grep '<string name=' amethyst/src/main/res/values-<LOCALE>/strings.xml \
    | sed 's/.*name="\([^"]*\)".*/\1/' | sort))
```

### 4. Present results

Output the missing entries as raw XML resource lines (copy-paste ready for the locale file):

```xml
    <string name="attestation_valid">Valid</string>
    <string name="attestation_valid_from">Valid from %1$s</string>
    <string name="feed_group_lists">Lists</string>
```

Also check `<string-array>` and `<plurals>` tags using the same approach if the project uses them.

## Common Mistakes

- **Forgetting `translatable="false"`** — these should never appear in locale files
- **Not checking string-arrays/plurals** — only checking `<string>` misses other resource types
- **Modifying files** — this is a read-only research task unless the user asks to add entries