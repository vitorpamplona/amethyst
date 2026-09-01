# String resources

Two traps live here: retiring a key without cleaning up its translations, and
getting plural categories wrong.

## Renaming or removing a string key

Deleting or renaming a key in the default `values/strings.xml` **orphans every
`values-*/strings.xml` entry that still declares it**. Android lint reports each
orphan as an `[ExtraTranslation]` **error**, and lint errors abort
`:amethyst:lint<Variant>` — which fails the whole `test-and-build-android` CI job.

**Rule: retire the key in every locale in the same commit that changes the
default locale.** One `git grep -l 'name="old_key"' amethyst/src/main/res` and a
delete pass; there is no follow-up commit that makes it right.

**Do not wait for Crowdin.** Crowdin does eventually drop retired keys, but that
is irrelevant: lint runs against the tree you push, not against Crowdin's next
export. It is also only *mostly* reliable, which is what makes it a trap — the
sync may clean most locales and silently leave the rest, so the tree looks
correct in the files you happen to open.

> 2026-08-31: `d6d5a72e49` renamed `route_video` → `route_media` and
> `new_short` → `new_media`, with the commit message reasoning "Crowdin drops the
> retired keys on its next sync". The sync that merged right after cleaned 32 of
> the 47 locales and left both keys in 15 — 30 `[ExtraTranslation]` errors, red
> main. Fixed in `1ce583ec92`.

Renaming rather than editing in place is still correct when the **meaning**
changes (an in-place edit silently keeps 47 translations of the old meaning).
The key is new; the obligation is to delete the old one everywhere at once.

### Checking before you push

`:amethyst:lintFdroidBenchmark` catches this, but takes ~19 minutes on a warm
daemon, so it is not a per-commit gate. Use the sub-second scan instead:

```bash
.claude/hooks/orphan_strings_check.py
```

It diffs every locale's resource names against its tree's default `values/` and
exits non-zero listing any orphan. It covers **both** Crowdin-managed resource
systems — the Android res trees (`amethyst/src/main/res`,
`commons/src/androidMain/res`) and the Compose-Multiplatform catalog
(`commons/src/commonMain/composeResources`) — and says which consequence applies:
lint only polices the Android trees, but an orphan in the Compose catalog is the
same mistake and leaves a dead translation behind.

The same script runs at two other layers, so the rule is enforced rather than
merely described:

| Layer | Where | Covers |
|---|---|---|
| CI | the fast `lint` job in `.github/workflows/build.yml` | every PR and every push to `main`, **including the Crowdin sync bot's** |
| Agent session | `.claude/hooks/pre-push-orphan-strings.sh`, wired as a `PreToolUse` hook in `.claude/settings.json` | a `git push` or PR creation from a Claude Code session |

The CI layer is the one that matters most: the 2026-08-31 desync arrived through
a bot-authored PR (`.github/workflows/crowdin.yml` → `create-pull-request`), with
no local session anywhere in the path. It sits in the 15-minute `lint` job rather
than the 60-minute `test-and-build-android` job that originally caught it.

## Plural rules

Always consider Slavic / Baltic / Semitic / Celtic languages when a string contains a count. The CLDR plural categories `one` / `other` that English uses are **not enough** — these language families decline the noun on `few`, `many`, `two`, `zero`, etc.

1. **Any string whose noun changes form with the count must be a `<plurals>` resource, not a `<string>`.** If the English reads naturally as "1 X" vs "N X" with a different noun form, it's a plural.
2. **The same applies to thresholds** ("more than %1$d hashtags"). The count IS the threshold, and the noun form depends on it in some languages.
3. **Never hardcode `"1"` in the English text** of a `quantity="one"` item — always use the `%1$d` placeholder. Hardcoding breaks every language whose `one` category covers numbers other than 1 (e.g. some Slavic languages).
4. **Each locale must include every CLDR category it uses**, not just the categories present in English. Quick reference:
   - English / German / Swedish / Brazilian Portuguese / Hungarian: `one`, `other`
   - Czech / Polish / Russian / Ukrainian / Croatian: `one`, `few`, `many`, `other`
   - Arabic: `zero`, `one`, `two`, `few`, `many`, `other`
   - Latvian: `zero`, `one`, `other`
   - Chinese / Japanese: `other` only

   Latvian's `zero` does **not** mean "no items" — it covers 0, 10, 11–19, 20, 30, … (`n % 10 = 0` or `n % 100 = 11..19`), so it fires on most counts and must read as a normal plural form. Never strip `<item quantity="zero">` from `values-lv-rLV`; among the locales we ship, only Arabic and Latvian have an integer-bearing `zero`.

## Plural anti-patterns to flag

When adding or reviewing strings, flag these:

- `<string name="…">%1$d items</string>` where the noun is countable → should be `<plurals>`.
- `<item quantity="one">1 reply</item>` → hardcoded `1`, should be `%1$d reply`.
- A locale `strings.xml` providing only `one`/`other` for Polish / Czech / Russian → missing `few`/`many`, will silently fall through to `other` for counts 2–4, 22–24, etc.

## Plural call-site patterns

In a `@Composable`:

```kotlin
import androidx.compose.ui.res.pluralStringResource
// ...
val n = items.size
Text(pluralStringResource(R.plurals.foo, n, n))
```

Pass the count **twice**: once as the CLDR selector, once as the `%1$d` format arg. Hoist the count to a local `val` if computing it is non-trivial (lists, mapNotNull, etc.) — the API forces two reads and you don't want two allocations.

Outside Compose (Workers, callbacks, services), use the project helper:

```kotlin
import com.vitorpamplona.amethyst.ui.pluralStringRes
// ...
pluralStringRes(ctx, R.plurals.foo, count, count)
```

Defined in `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/StringResourceCache.kt` — wraps `ctx.resources.getQuantityString(id, count, *formatArgs)`.

## Workflow for new count strings

1. Add the new `<plurals>` to default `values/strings.xml` with `one` + `other`.
2. If you're also adding a locale-specific translation (e.g. zh-rCN, pl-rPL), add it as `<plurals>` with at least `other`. Crowdin will fan out to all CLDR categories for that locale.
3. If you're **converting** an existing `<string>` to `<plurals>`, you **must** convert it in every locale that already had the `<string>` — otherwise aapt2 will fail with a resource-type mismatch, and an orphaned locale `<string>` trips `ExtraTranslation`. Give each locale its **full CLDR category set** at conversion time. Carrying the old text over as an `other`-only block does **not** work: lint's `MissingQuantity` is an error, so CI fails long before Crowdin gets a chance to backfill. Mind the declension too — the retained text is usually the plural form, so reusing it verbatim for `one` yields "1 odpowiedzi".
4. Reference: [Android `<plurals>` docs](https://developer.android.com/guide/topics/resources/string-resource#Plurals) and [CLDR plural rules](https://unicode-org.github.io/cldr-staging/charts/latest/supplemental/language_plural_rules.html).
