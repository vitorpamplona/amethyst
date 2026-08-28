---
name: find-non-lambda-logs
description: Use when auditing or migrating Log calls — flags interpolated Log.d/i/w/e that should use the lambda overload (allocation hygiene), catch-block Log.w/e that interpolate ${e.message} but drop the throwable (lost stack traces), and files still importing android.util.Log (no lambda overload, bypasses Log.minLevel)
---

# Find Non-Lambda Log Calls

## Overview

Two related logging hygiene issues:

1. **Lambda overload missing.** `Log.d/i/w/e` calls that use string interpolation without the lambda overload waste string allocation when the log level is filtered out in release builds.
2. **Throwable dropped in catch blocks.** `Log.w/e` calls inside `catch (e: ...)` blocks that interpolate `${e.message}` but don't pass `e` lose the stack trace, and log nothing useful when `e.message` is null (NPE, IOException with no message, etc.).
3. **Still on `android.util.Log`.** Files importing the platform logger bypass `Log.minLevel` and the `LogSink`, and have no lambda overload — so neither fix above can be applied to them. Step 4 finds these; the last section migrates them.

## When to Use

- After merging branches that add new logging
- Periodic audit of logging hygiene
- After migrating `android.util.Log` usages to the shared `Log` wrapper

## What to Flag

Calls with **string interpolation** (`$` in message) that do **not** pass a throwable:

```kotlin
// FLAG - interpolation without lambda, no throwable
Log.d("Tag", "Processing ${event.id}")
Log.w("Tag", "Failed for $url")

// IGNORE - passes throwable (lambda overload doesn't accept throwable)
Log.w("Tag", "Error: ${e.message}", e)
Log.e("Tag", "Failed for $url", throwable)

// IGNORE - no interpolation (no allocation benefit from lambda)
Log.d("Tag", "Initialization complete")
```

## Search Commands

**Important:** Tags can be string literals (`"Tag"`) or variables (`tag`, `LOG_TAG`). Run both patterns for each step.

**Run Step 4 before Steps 1–3.** It identifies the files that have no lambda overload available; converting a call in one of those does not compile. Subtract its file list from the Step 1–3 candidates.

**Filter the noise before counting**, or the totals mislead: drop `/build/`, `/androidTest/` and `/src/test/` (release filtering doesn't apply to tests), and drop lines whose first non-space character is `//` or `*` — commented-out calls and KDoc examples both match these patterns. A `grep -vE ':[0-9]+: *(//|\*)'` handles the last one.

### Step 1: Find interpolated Log.d/Log.i (highest priority — filtered in release)

```
pattern: Log\.(d|i)\("[^"]+",\s*"[^"]*\$
type: kotlin
```
```
pattern: Log\.(d|i)\(\w+,\s*"[^"]*\$
type: kotlin
```

### Step 2: Find interpolated Log.w/Log.e without throwable

```
pattern: Log\.(w|e)\("[^"]+",\s*"[^"]*\$
type: kotlin
```
```
pattern: Log\.(w|e)\(\w+,\s*"[^"]*\$
type: kotlin
```

Then **manually exclude** lines where a throwable is passed as third argument. Check the actual line — a catch block catching `e` doesn't mean `e` is passed to the Log call.

**`it` is the name you will miss.** `Result.onFailure { ... }` is the dominant shape in this repo, so most correct calls end `, it)`, not `, e)`. Excluding only `e`/`throwable` inflates the result badly — a 2026-08-28 pass reported 23 hits where the real number was 8, because 14 of them were `.onFailure { Log.w(TAG, "...", it) }` and already correct. Also note the throwable is not always last on the line (`}.onFailure { Log.w(...) }.getOrDefault(false)`), so anchoring the exclusion to `$` misses them:

```bash
grep -vE ',\s*(e|t|it|ex|err|error|throwable|cause|tr)\)'   # note: no $ anchor, and `it` included
```

### Step 3: Find catch-block Log.w/e that drop the throwable

Among the Step 2 hits, the calls that interpolate `${e.message}` (or `${t.message}`, `${throwable.message}`) but do not pass the exception itself are a separate bug — they lose the stack trace AND log a useless empty-ish line whenever the exception's message is null.

Quick filter:

```
pattern: Log\.(w|e)\([^)]*\$\{(e|t|throwable|cause)\.message\}[^)]*\)$
type: kotlin
```

Then for each hit, open the file and confirm the line is **inside a `catch (e: ...)` block** and **does not pass `e` (or the matching name) as a third argument**. False positives: extension functions / helpers that accept an `e: SomeError` parameter and forward it elsewhere.

Both Step 2 and Step 3 may flag the same line — handle Step 3 first (different fix), then apply Step 2 to whatever remains.

### Step 4: Verify no android.util.Log leakage

**Two patterns — the fully-qualified one alone is a false negative.** Almost nobody writes `android.util.Log.w(...)` at the call site; they `import android.util.Log` and then write `Log.w(...)`, which is indistinguishable from the wrapper by call shape. The import is the reliable signal:

```bash
# the form that actually occurs
grep -rln --include='*.kt' '^import android\.util\.Log$' . | grep -v '/build/' | grep -v PlatformLog

# the rare fully-qualified call
grep -rnE --include='*.kt' 'android\.util\.Log\.(d|i|w|e|v)\(' . | grep -v '/build/' | grep -v PlatformLog
```

On 2026-08-28 the fully-qualified pattern reported **0** while the import pattern found **16 production files** (9 in `nappletHost`, the rest in amethyst's `favorites/` and `napplet/`). Exclude `PlatformLog.android.kt`, which is the wrapper implementation and must call `android.util.Log`.

These bypass the `Log.minLevel` filter and the `LogSink` indirection entirely, and — the practical consequence for this skill — **they have no lambda overload**, so Steps 1–3 cannot be applied to them until they are migrated. Subtract these files from the Step 1–3 candidate lists, or migrate them first.

## Fix Patterns

### Lambda overload (Step 1 + Step 2)

```kotlin
// Before
Log.d("Tag", "Processing event ${event.id} from ${relay.url}")

// After
Log.d("Tag") { "Processing event ${event.id} from ${relay.url}" }
```

### Throwable overload (Step 3)

Switch to `(tag, msg, throwable)` — the lambda overload does **not** accept a throwable, so this case must use the eager-string form. Drop the redundant `${e.message}` from the message text since the throwable already carries it.

```kotlin
// Before — stack trace lost, prints "...failed: null" if e.message is null
try { groupManager.clearAllState() } catch (e: Exception) {
    Log.w("MarmotManager") { "clearAllState failed: ${e.message}" }
}

// After — full stack trace logged
try { groupManager.clearAllState() } catch (e: Exception) {
    Log.w("MarmotManager", "clearAllState failed", e)
}
```

Trade-off: the message string is allocated eagerly even when warn is filtered, but warn-level catch logs are rare-event paths so this cost is negligible compared to losing diagnostic detail.

## Do NOT Convert

- **To lambda:** calls passing a `Throwable` parameter — the lambda overload `(tag) { message }` has no throwable parameter.
- **To lambda: any call in a file that imports `android.util.Log`.** The platform `Log` has no lambda overload, so the conversion fails to compile with `None of the following candidates is applicable`. Either migrate the file first (below) or leave the call alone. (Hit on 2026-08-28: three edits in two files had to be reverted.)
- Static string calls with no `$` interpolation — no allocation benefit.
- Commented-out log calls.
- Informational/intentional log of `e.message` *outside* a catch block (rare; usually means the exception was already handled and only the message is meaningful).

## Migrating a file off `android.util.Log`

This is what unlocks Steps 1–3 for the files Step 4 finds. It is a behaviour change, so check it rather than assuming — but in this repo the check has come out safe, and here is the reasoning to redo:

1. **Which levels does the file use?** `grep -hoE 'Log\.[a-zA-Z]+' <files> | sort | uniq -c`. The wrapper has `d/i/w/e` only — **no `v`**, and no `getStackTraceString`. A `Log.v` call has no direct equivalent and needs a decision, not a rename.
2. **Would the gate drop them?** `LogLevel { DEBUG, INFO, WARN, ERROR }`, the gate is `minLevel <= <level>`, and `Amethyst.DEFAULT_LOG_LEVEL` is INFO in debug, **WARN in release** (deliberately — so relay-protocol refusals stay visible in the field). The wrapper's own default is `DEBUG`. So `Log.w` and `Log.e` survive in every build type and in every process, including before `Amethyst.init` runs — which matters for `:napplet`. `Log.d`/`Log.i` **would** go silent in release; those need a conscious call.
3. **Does the output move?** No. `PlatformLogSink` on Android delegates to `android.util.Log`, so lines land in logcat unchanged.
4. **Can the module see quartz?** `nappletHost` already has `implementation(project(":quartz"))`. Check before assuming.

Then: swap `import android.util.Log` → `import com.vitorpamplona.quartz.utils.Log`, run `./gradlew spotlessApply` (import order changes), and convert only the interpolated no-throwable calls to the lambda form. Calls that already pass a throwable keep the eager three-arg shape — the wrapper's `w(tag, msg, throwable)` matches exactly, so only the import moves.

**Verify the throwables survived**, since a careless rewrite can drop the third argument silently:

```bash
grep -rhcE 'Log\.[diwe]\([^)]*,\s*(e|it)\)' <files> | paste -sd+ - | bc   # compare before/after
```

The 2026-08-28 pass moved 16 files / 29 calls this way: 26 already passed a throwable (import only), 3 interpolated and became lambdas. Zero calls changed visibility.
