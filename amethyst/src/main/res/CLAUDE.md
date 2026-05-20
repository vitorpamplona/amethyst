# String resources — plural handling

Always consider Slavic / Baltic / Semitic / Celtic languages when a string contains a count. The CLDR plural categories `one` / `other` that English uses are **not enough** — these language families decline the noun on `few`, `many`, `two`, `zero`, etc.

## Rules

1. **Any string whose noun changes form with the count must be a `<plurals>` resource, not a `<string>`.** If the English reads naturally as "1 X" vs "N X" with a different noun form, it's a plural.
2. **The same applies to thresholds** ("more than %1$d hashtags"). The count IS the threshold, and the noun form depends on it in some languages.
3. **Never hardcode `"1"` in the English text** of a `quantity="one"` item — always use the `%1$d` placeholder. Hardcoding breaks every language whose `one` category covers numbers other than 1 (e.g. some Slavic languages).
4. **Each locale must include every CLDR category it uses**, not just the categories present in English. Quick reference:
   - English / German / Swedish / Brazilian Portuguese / Hungarian: `one`, `other`
   - Czech / Polish / Russian / Ukrainian / Croatian: `one`, `few`, `many`, `other`
   - Arabic: `zero`, `one`, `two`, `few`, `many`, `other`
   - Chinese / Japanese: `other` only

## Anti-patterns to flag

When adding or reviewing strings, flag these:

- `<string name="…">%1$d items</string>` where the noun is countable → should be `<plurals>`.
- `<item quantity="one">1 reply</item>` → hardcoded `1`, should be `%1$d reply`.
- A locale `strings.xml` providing only `one`/`other` for Polish / Czech / Russian → missing `few`/`many`, will silently fall through to `other` for counts 2–4, 22–24, etc.

## Call-site patterns

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
3. If you're **converting** an existing `<string>` to `<plurals>`, you **must** convert it in every locale that already had the `<string>` — otherwise aapt2 will fail with a resource-type mismatch. Use `<plurals>` with `other` only to preserve existing translation (Crowdin fills the rest).
4. Reference: [Android `<plurals>` docs](https://developer.android.com/guide/topics/resources/string-resource#Plurals) and [CLDR plural rules](https://unicode-org.github.io/cldr-staging/charts/latest/supplemental/language_plural_rules.html).
