# `:androidStubs`

JVM-side stand-ins for the `android.*` framework classes that shared Amethyst
code references, so that **one source file can compile for both Android and the
JVM with no `expect`/`actual` and no import rewrite**.

A file in a `jvmAndroid` source set writes the ordinary Android import:

```kotlin
import android.content.Context
fun packageName(context: Context) = context.packageName
```

…and it resolves from `android.jar` on the Android target and from this module
on the JVM target.

## Why this is safe (measured, not assumed)

Wired as `compileOnly` on the shared `jvmAndroid` source set and
`implementation` on `jvmMain` only. Verified on `:amethystShared`:

| classpath | `:androidStubs` present |
|---|---|
| `androidCompileClasspath` | yes (compileOnly) |
| `androidRuntimeClasspath` | **no** — never dexed, never in the APK |
| `jvmCompileClasspath` / `jvmRuntimeClasspath` | yes |

The decisive question is which `Context` the **Android** target compiles
against when both `android.jar` and this jar are on its compile classpath.
Measured by calling `Context.getCacheDir()`, which exists on the real framework
class and is deliberately absent here:

- Android target → **compiles**. `android.jar` wins; the stub cannot weaken or
  alter what the Android app is built against.
- JVM target → **fails** with `Unresolved reference 'cacheDir'`.

So the mechanism is one-directional: it can only ever fail *closed*, on the JVM
side, and the JVM compiler doubles as the worklist — every unresolved reference
names an Android API that still needs a JVM implementation.

Because Kotlin never runs a metadata compilation for a source set shared only
among JVM-family targets (`compileJvmAndroidKotlinMetadata` is `SKIPPED`), there
is no third classpath to reconcile.

## Rules

1. **Java, not Kotlin.** Kotlin sees Java members as platform types (`String!`),
   exactly as it sees real `android.jar` members. Kotlin stubs would instead
   declare hard non-null/nullable types and would silently change what compiles
   in the shared source set.
2. **Declare only what shared code actually calls.** An absent method fails at
   compile time on the JVM target, which is the signal we want. Do not add
   speculative surface.
3. **Never a runtime dependency of the Android app.** Keep it `compileOnly`
   wherever an Android target can see it.
4. Behaviour-bearing stubs delegate to a JVM implementation; pure data holders
   (e.g. `Uri`) may implement directly.
