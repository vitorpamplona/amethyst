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

## The one rule that matters: never fail silently

Every feature is meant to get a real desktop implementation eventually. That
makes an inert stub the most expensive kind of placeholder: it compiles, runs,
does nothing, and nobody finds out until a user reports a dead button. A stub
that cannot do the thing must do one of these instead, never nothing:

| situation | what the stub must do | example |
|---|---|---|
| the JVM can genuinely do it | **do it** | `Bitmap` is a `BufferedImage`; `Handler` posts to the AWT queue; `ACTION_VIEW` opens the browser |
| a desktop app can do it, but this layer cannot | **expose an SPI** and let the app install it | `Toast.Presenter`, `NotificationManager.Presenter`, `MediaMetadataRetriever.Extractor`, `VideoEngine` |
| nothing can do it yet | **report it** to `PlatformGaps` | `Context.sendBroadcast`, an Intent with no desktop meaning |
| doing nothing would be *unsafe* | **throw** | `ExifInterface` — a no-op would make the caller report "metadata stripped" for a file that still carries GPS |

`PlatformGaps.report(feature, detail)` writes each distinct gap to stderr once
by default, so gaps show up during development before anyone wires a reporter.
An app can replace the reporter to surface them in the UI, and `PlatformGaps.seen()`
lists everything hit so far — useful for a diagnostics screen or a test that
asserts a screen touches no gaps.

Genuinely-nothing-to-do methods are fine and need no ceremony: base-class
lifecycle hooks that are no-ops on Android too (`Activity.onCreate`), and
configuration a desktop backend has no use for (`NotificationChannel.enableVibration`).

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
