# Wave 1: Android string resources → commons Compose resources

**Date:** 2026-09-22
**Scope:** every `R.string` / `R.plurals` reference in the `amethyst` module,
and the string catalog behind them.

This is the first wave of the "build the Android app for JVM/desktop" plan. It
is a precondition for every later wave: a screen cannot move to `commonsUI`
while its labels come from `amethyst/src/main/res`.

## Why this wave is not a search-and-replace

The obvious framing — "swap `R.string.x` for `Res.string.x`" — is wrong, and
the reason is worth writing down because it is what sets the wave's size.

Compose resources has exactly two accessors:

| | Android `R` | Compose resources |
|---|---|---|
| in composition | `stringResource(id)` | `stringResource(res)` |
| outside composition | `ctx.getString(id)` | `suspend getString(res)` — **no blocking form** |

The app had **458 call sites** shaped `stringRes(context, R.string.x)`, spread
across ViewModels, notification builders, foreground services, intent helpers
and Android callbacks. None of them can keep working after the key moves,
because there is no non-suspend Context-free way to read a Compose resource.

Deferring those keys does not help: only **167 of 1,912** keys are used
*exclusively* at composable call sites. `cancel`, `save`, `error` and friends
appear on both sides, and a key can only live in one resource system. So the
Context call sites had to be dealt with before any key could move.

**Decision (maintainer, 2026-09-22): no `runBlocking` bridge.** A
`stringResBlocking(id) = runBlocking { getString(id) }` shim would have made
all 458 sites a mechanical rename, but it puts a blocking resource read on
paths that can run on the main thread. Every site was converted properly
instead.

## What "converted properly" meant, by scope

| scope | count | conversion |
|---|---|---|
| `@Composable` | 264 | drop the `context` argument — `stringRes(id)` |
| already inside `launch { }` / `withContext { }` | 46 | `loadStringRes(id)` (the existing suspend bridge in `commonsUI/.../ui/StringRes.kt`) |
| plain function, caller already in a coroutine | ~80 | function becomes `suspend` |
| plain function, no coroutine in reach | ~68 | label hoisted into composition and passed as a `String` |

The interesting cases:

- **`TimeAgoFormatter`** — every caller handed it `LocalContext.current`, so the
  formatters became `@Composable` and lost the parameter entirely. `context`
  survives only in `timeAbsolute`, where `DateFormat.getTimeFormat` needs it.
  `RelayCompose` had one inside `derivedStateOf { }`, which a composable cannot
  enter; it now formats directly in composition (it is a cheap formatter).
- **The notification stack** is one connected component — `NotificationCategory`,
  `NotificationUtils`, `CallNotifier`, `SignerConsentNotifier`,
  `CalendarReminderNotifier`, `ZapNotification`, `InlineReplyFeedback`,
  `ConversationShortcuts`, `RelayPurposeSummary` — so it went `suspend` as a
  unit. Its two public entry points (`postStandard`, `postConversation`) already
  were. The ripple reached `NotificationChannels.Entry`, whose `channelId`/
  `ensure` lambdas are now `suspend`, and the notification settings screen,
  which drives them from `repeatOnLifecycle` + `produceState` instead of
  `LifecycleResumeEffect`.
- **`ScheduledPostNotifier`** (a `commons` interface) went `suspend` on both
  implementations; its only driver is `CoroutineWorker.doWork`.
- **`FlowProgressForegroundService.render`** is now `suspend`, which is what the
  POW-mining and Blossom-sync services needed.
- **`PowDuration`** is read from three composables *and* from the mining
  service, so it has a `@Composable` pair and a `suspend` pair
  (`loadApproxDuration` / `loadTimeLeft`) rather than forcing either caller into
  the wrong shape.
- **Biometric prompts** (`authenticate` in `UpdateZapAmountDialog`) run from
  `onClick` and from Android's own callbacks. Their four labels are resolved in
  composition into an `@Immutable AuthPromptLabels` and passed down.
- **Share sheets** (`shareIcs`, `startShareUrlIntent`, `shareRelayGroup`) take
  their chooser title as a `String` parameter now.
- **`ToastManager`** already carried resource *ids* and resolved them where the
  toast is drawn — so the two `ReusableZapButton` sites needed no lookup at all,
  just the id overload. The toast message classes now carry `StringResource`.
- **`Name.name(context)`** became `suspend nameOrDefault()`, and
  `FeedFilterSpinner` reads it through a `rememberDisplayName` helper that shows
  the plain `name()` for the frame the resource takes to arrive.

## The XML move

`tools/strings-migrate/migrate.py` moves elements verbatim so Crowdin sees a
pure move (both trees are Crowdin-managed with the same android layout). Two
changes were needed for a move this size:

- **`%%` was read as a bare conversion.** The old `BARE_FORMAT` scanned for a
  `%` not followed by `%`, but the *second* `%` of `%1$d%% of all` then started
  a fresh match, and the space flag in `[-#+ 0,(]*` let `% o` look like a
  conversion. Two keys (`active_subs_share`, `uptime`) were rejected for a
  format bug they did not have. The scanner now consumes `%%` as a unit and
  callers test `is_bare()` rather than `.search()`.
- **A bulk `@keyfile` mode.** The per-key path re-read and rewrote each locale
  file once per key: 1,900 keys × 58 files is ~110k rewrites of a 300 KB file.
  `extract_many()` does one pass per file. Verified byte-identical to the old
  path on a 40-key sample across all 57 locale dirs.

Nine keys used non-positional format specifiers (`%d`, `%s`), which
compose-resources cannot format. They were rewritten to `%1$d` / `%1$s` across
all 58 locale files (480 elements) — positional form is valid for Android too,
so behaviour is unchanged on both sides.

Result: **1,906 keys moved**, 67,275 elements across 57 locale dirs.
`amethyst/src/main/res/values/strings.xml` went 2,051 → 143 keys;
`commonsUI/.../composeResources/values/strings.xml` went 3,016 → 4,926.

Six keys did not move:

- `app_name`, `block_hide_user`, `rename`, `save` are referenced from
  `AndroidManifest.xml` / `res/xml`, so the Android copy stays and a copy was
  added to the Compose catalog for the Kotlin side.
- `podcast_value_for_value`, `show_less` already existed in commons; the
  duplicate app-side copies were dropped.

## Guard against silent key loss

A first attempt at the call-site rewrite ate the key in
`stringRes(Res.string.x, arg)` — the Context-detector matched *any* two-argument
call, so the first argument was consumed as if it were a `context`. 344
references disappeared with no error. Two things came out of that:

- The detector uses an **allowlist** of expressions that really are a Context in
  this tree (`context`, `ctx`, `appContext`, `applicationContext`, `actContext`,
  `localContext`, `targetContext`, `this`, and the two
  `Amethyst.instance.appContext` spellings), not "anything before the comma".
- `check_refs.py` (in the migration scratch, worth keeping alongside the tool)
  diffs the per-key, per-file multiset of `R.*` + `Res.*` references against
  HEAD. A rewrite may move a key between namespaces; it may not change the
  multiset. Every rewrite pass after that was run through it.

## Status

- `R.string` / `R.plurals` references left in Kotlin: **0**
- `.claude/hooks/orphan_strings_check.py`: passes
- All 58 default + locale XML files parse
- `check_refs.py`: no key lost (the only deltas are the intentional
  `PowDuration` suspend twins and the hoisted labels)

**Not yet verified: this has never compiled.** The container's shared egress IP
is rate-limited by Maven Central (~50% of requests answer 429) and Gradle
disables a repository on the first failure, so `:amethyst:compileFdroidDebugKotlin`
could not be driven to completion in the session that wrote this. The changes
that need a compiler to confirm are:

1. Every scope classification. A `stringRes(id)` inside a non-composable lambda
   (`onClick = { }`) and a `loadStringRes(id)` outside a coroutine are both
   compile errors, which is exactly the check that has not run.
2. The `Int` → `StringResource` retyping (98 declarations across 39 files).
   Carriers reached only through a positional argument were not found
   statically and will surface as type errors.
3. Unused imports and vals left behind by the hoisting (ktlint errors).

Next step is a compile-fix loop, then `./gradlew spotlessApply`, then
`:amethyst:lintFdroidBenchmark` for `ExtraTranslation`.
