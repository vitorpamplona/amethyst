---
name: find-non-lambda-logs
description: Use when auditing or migrating Log calls to lambda overloads, after adding new logging, or checking for string interpolation in Log.d/i/w/e calls that waste allocations when the log level is filtered out
---

# Find Non-Lambda Log Calls

## Overview

Locates `Log.d/i/w/e` calls that use string interpolation without the lambda overload, wasting string allocation when the log level is filtered out in release builds.

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

### Step 3: Verify no android.util.Log leakage

```
pattern: android\.util\.Log\.(d|i|w|e|v)\(
type: kotlin
```

These bypass the `Log.minLevel` filter entirely. Exclude `PlatformLog.android.kt` which is the wrapper implementation.

## Fix Pattern

```kotlin
// Before
Log.d("Tag", "Processing event ${event.id} from ${relay.url}")

// After
Log.d("Tag") { "Processing event ${event.id} from ${relay.url}" }
```

## Do NOT Convert

- Calls passing a `Throwable` parameter - the lambda overload `(tag) { message }` has no throwable parameter
- Static string calls with no `$` interpolation - no allocation benefit
- Commented-out log calls
