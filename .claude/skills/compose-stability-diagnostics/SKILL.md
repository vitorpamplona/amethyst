---
name: compose-stability-diagnostics
description: Use when writing or reviewing Jetpack Compose parameter stability, compiler reports, skippability, unstable UI state classes, collection parameters, or Kotlin 2.0+ strong skipping behavior. Technique-layer skill — complements the codebase-specific compose-expert and kotlin-expert.
---

# Compose stability diagnostics

## Core principle

Compose performance problems from parameters are about **whether inputs compare cheaply and predictably across recompositions**. With Kotlin 2.0.20+ strong skipping is enabled by default, so unstable parameters no longer automatically make restartable composables non-skippable. That does not make stability irrelevant: unstable parameters are compared by instance identity (`===`), stable parameters by equality (`equals`), and churny instances can still defeat skipping.

First identify the compiler mode you are on, then read reports in that context.

## When to use this skill

- A composable or screen recomposes more than expected and parameter churn is suspected.
- A UI-state/model class is passed to composables and contains `List`, `Set`, `Map`, ranges, Java time/money types, or third-party types.
- `composables.txt` / `classes.txt` shows unstable parameters or non-skippable composables.
- A project uses Kotlin < 2.0.20, disables strong skipping, or has old Compose compiler report guidance.

## 1. Start with strong skipping

On Kotlin 2.0.20+, strong skipping is enabled by default. In that mode:

- Restartable composables are skippable even when parameters are unstable, unless explicitly opted out.
- Stable parameters compare with `equals`.
- Unstable parameters compare with instance equality (`===`).
- Lambdas inside composables are automatically remembered based on captures.

That means the question changes from "is this composable skippable at all?" to "will these parameters compare the way I expect, and are callers creating new unstable instances every frame?"

For older compiler setups or strong skipping disabled, the legacy rule still matters: a restartable composable with unstable parameters may be restartable but not skippable.

## 2. Generate compiler reports

With Kotlin 2.0+ the Compose Compiler is configured through the Kotlin Gradle plugin:

```kotlin
plugins {
    alias(libs.plugins.android.application) // or android.library / jvm
    alias(libs.plugins.kotlin.android)      // or kotlin.multiplatform / kotlin.jvm
    alias(libs.plugins.compose.compiler)
}

if (providers.gradleProperty("composeReports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}
```

Then build the variant whose compiler configuration you care about, for example:

```bash
./gradlew :app:assembleRelease -PcomposeReports=true
```

Use release/non-debuggable builds for runtime profiling. Compiler reports are build-time outputs, so the important thing is matching the variant and compiler flags you ship.

Key files:

| File | What it tells you |
|---|---|
| `<module>-classes.txt` | Stability of classes and properties |
| `<module>-composables.txt` | Restartable/skippable status and parameter stability |
| `<module>-composables.csv` | Same data in sortable form |
| `<module>-module.json` | Aggregate metrics |

## 3. Fix stability where semantics need it

Pick the lightest fix that makes the type's immutability or equality semantics true.

### Immutable collections

`kotlin.collections.List` is an interface; Compose cannot know the runtime implementation is immutable. Prefer `kotlinx.collections.immutable` at UI-state boundaries:

```kotlin
// Before: unstable collection interfaces
data class UiState(val items: List<Item>, val tags: Set<String>)

// After: immutable collection contracts
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class UiState(val items: ImmutableList<Item>, val tags: ImmutableSet<String>)
```

Producers convert once at the boundary with `.toImmutableList()` / `.toImmutableSet()`.

### `@Immutable` / `@Stable`

- Use `@Immutable` when every property is effectively immutable and equality describes all observable state.
- Use `@Stable` for types whose mutable state is observable by Compose, typically via `MutableState`.

Do not annotate to silence a report. A false stability promise can produce stale UI.

### Third-party immutable types

For types you cannot annotate, use `stabilityConfigurationFiles`:

```kotlin
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf"),
    )
}
```

```text
java.math.BigDecimal
java.math.BigInteger
java.time.*
kotlinx.datetime.*
```

Only list types you are willing to promise are immutable. Do not list mutable types such as `java.util.Date`.

## Quick reference

| Symptom | Diagnosis | Fix |
|---|---|---|
| Kotlin 2.0.20+ but old docs say unstable means non-skippable | Strong skipping changed the default | Check comparison semantics and instance churn instead |
| `unstable val items: List<Item>` | Interface collection | Use `ImmutableList<Item>` or another true immutable wrapper |
| `unstable val price: BigDecimal` | External immutable type | Add to stability config |
| `@Immutable` on a type with mutable internals | False promise | Fix the model or remove the annotation |
| Composable skips poorly despite strong skipping | New unstable instance each recomposition | Remember, hoist, or make the type stable/equality-based |
| Reports not generated | Compose compiler plugin missing or flag not set | Apply `org.jetbrains.kotlin.plugin.compose` and enable destinations |

## When NOT to apply

- The issue is a fast-changing `State` read in composition, such as scroll or animation. Use `compose-state-deferred-reads`.
- The recomposition count matches real data changes.
- The bug is wrong data or stale state, not excess work.
- The code is test-only and readability is more important than report cleanliness.

## Related

- [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) - frame-rate state should often be read in layout/draw rather than composition.
- [`compose-recomposition-performance`](../compose-recomposition-performance/SKILL.md) - entry point when you are not sure which recomposition axis is involved.
