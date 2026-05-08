---
name: find-non-lambda-logs
description: Use when auditing or migrating Log calls — flags both interpolated Log.d/i/w/e that should use the lambda overload (allocation hygiene) and catch-block Log.w/e that interpolate ${e.message} but drop the throwable (lost stack traces)
---

# Find Non-Lambda Log Calls

## Overview

Two related logging hygiene issues:

1. **Lambda overload missing.** `Log.d/i/w/e` calls that use string interpolation without the lambda overload waste string allocation when the log level is filtered out in release builds.
2. **Throwable dropped in catch blocks.** `Log.w/e` calls inside `catch (e: ...)` blocks that interpolate `${e.message}` but don't pass `e` lose the stack trace, and log nothing useful when `e.message` is null (NPE, IOException with no message, etc.).

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

Then **manually exclude** lines where a throwable is passed as third argument (ending with `, e)`, `, throwable)`, etc.). Check the actual line — a catch block catching `e` doesn't mean `e` is passed to the Log call.

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

```
pattern: android\.util\.Log\.(d|i|w|e|v)\(
type: kotlin
```

These bypass the `Log.minLevel` filter entirely. Exclude `PlatformLog.android.kt` which is the wrapper implementation.

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
- Static string calls with no `$` interpolation — no allocation benefit.
- Commented-out log calls.
- Informational/intentional log of `e.message` *outside* a catch block (rare; usually means the exception was already handled and only the message is meaningful).
