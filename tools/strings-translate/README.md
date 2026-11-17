# strings-translate

Fills a batch of missing translations without hand-editing `strings.xml`: pull the English each
locale lacks, check the translations, and append them. `.claude/skills/find-missing-translations`
says when to use it; this file says how.

```bash
W=$TMPDIR/tr; mkdir -p $W                        # any scratch dir outside the repo
tools/strings-translate/batch.py extract $W cs de-rDE sv-rSE pt-rBR
# translate each $W/<locale>.en.xml into $W/<locale>.xml (same elements, no <resources> wrapper)
"$TMPDIR/cldr/bin/python" tools/strings-translate/batch.py validate $W --verbatim key1,key2 cs de-rDE sv-rSE pt-rBR
"$TMPDIR/cldr/bin/python" tools/strings-translate/batch.py insert   $W --verbatim key1,key2 cs de-rDE sv-rSE pt-rBR
```

- `extract` re-diffs every locale on its own, because locale files are asymmetric. It prints counts
  and the top key prefixes, which is the summary to show the user.
- `validate` checks that each translation file has the same keys as the English and no duplicates,
  that it is well-formed, and that every string keeps its placeholders and number of `\n`. It also
  rejects Android-only `\'`/`\"` escapes and quote-wrapped values, which render literally in Compose
  resources. Each plural needs every CLDR category the locale uses. A `one` item may spell the count
  out ("jednou") only where `one` means exactly 1. `--verbatim` names keys that must stay identical
  to the English (brand names).
- `insert` validates first and inserts nothing if any locale fails. It re-reads the live locale file
  and appends only the keys still missing, at the catalog's indentation, before `</resources>`.
  Re-running it is safe.
- `--base amethyst/src/main/res` targets the Android catalog instead of commonsUI.

`validate` and `insert` read the CLDR plural rules from Babel. Create the venv once:

```bash
python3 -m venv "$TMPDIR/cldr" && "$TMPDIR/cldr/bin/pip" -q install babel
```

Categories are the ones an ordinary count reaches, fractions included: Czech needs `many` (1.5).
pt/fr/es/it `many` is optional, because only whole millions reach it and it falls back to `other`.
