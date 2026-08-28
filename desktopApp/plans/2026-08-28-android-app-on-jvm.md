# Shipping the Android app as a JVM desktop app

**Date:** 2026-08-28 · **Owning module:** `desktopApp` · **Status:** proposal (no code moved yet)

Goal: stop re-implementing Amethyst's screens for desktop and instead **compile the
existing Android app for the JVM**. The current `:desktopApp` is a desktop-first
rewrite; this plan turns the Android UI into the shared baseline and re-casts
`:desktopApp`'s work as the *platform layer* underneath it plus a *desktop-affordance
layer* on top of it.

Thesis, in one line: **flip the default from "share a file once someone proves it is
shareable" to "everything is shared unless the compiler says otherwise."**

---

## 0. Why the current path is losing

| | files |
|---|---|
| `amethyst/src/main` UI (`ui/**`) | 1748 |
| `desktopApp` UI (`desktop/ui/**`) | 163 |

~9% of the Android UI has been rebuilt, and every Android feature widens the gap.
The rewrite is a race against a codebase that moves faster than the rewrite does.

`commons/plans/2026-05-30-amethyst-to-commons-migration.md` proposes the other
direction — lift shared code *down* into `commons`. Same destination, but it requires
a human design judgment per file ("does this belong in commons?") across 2341 files,
and its keystone Phase A has been queued since May. This plan does not replace it; it
removes the need to finish it *before* desktop parity, by letting the Kotlin compiler
do the classification instead of a reviewer.

---

## 1. The measurement that makes this viable

Surveyed `amethyst/src/main` (**2341** Kotlin files):

| Signal | Files / refs | Note |
|---|---|---|
| Touch **no** Android-only API at all | **1696 files (72%)** | Compose Multiplatform serves the identical `androidx.compose.*` API on desktop |
| Import `android.*` | 442 files | **158 distinct classes**, heavily concentrated |
| `android.content.Context` | 235 refs | the dominant one |
| `android.content.Intent` / `os.Build` / `net.Uri` / `widget.Toast` | 76 / 57 / 49 / 38 | next four cover most of the rest |
| `androidx.compose.ui.platform.LocalContext` | 206 files | import swap |
| `androidx.compose.ui.tooling` (`@Preview`) | 137 files | pure import swap |
| `androidx.compose.ui.res.*` | 93 files | served by §3 |
| `LocalConfiguration` / `LocalView` / `AndroidView` / `windowsizeclass` | 15 / 11 / 4 / 3 | hand-handled |

**The single most important finding:** localized strings are already funnelled through
**one project-owned helper**, `stringRes(id: Int)` in
`amethyst/src/main/java/com/vitorpamplona/amethyst/ui/StringResourceCache.kt`:

| call form | occurrences |
|---|---|
| `stringRes(R.string.…)` | **3439** |
| `stringResource(R.string.…)` | 313 |
| `getString(R.string.…)` (non-composable) | 72 |
| `R.plurals.…` / `R.drawable.…` | 124 / 86 |

90% of string reads go through a choke point we own. That converts the scariest-looking
number in the codebase (6106 `R.string` references across 762 files) into a
single-file problem — see §3.

**Verified, not assumed:** the JVM has no objection to classes declared in package
`android.*` (unlike `java.*`, which the classloader seals). Compiled and ran a
`package android.content; class Context` on the session JDK; it loads and runs.

---

## 2. Build shape

`:amethyst` is `com.android.application` today and therefore cannot carry a `jvm`
target. Split it in three:

- **`:amethystApp`** — the Android application only: `AndroidManifest.xml`, the
  `Amethyst` Application class, `MainActivity`, foreground/notification services,
  WorkManager workers, widgets, the `:napplet` process wiring, Google Services,
  baseline-profile + R8 config. Tens of files, not thousands.
- **`:amethystShared`** — KMP library via the same `androidKotlinMultiplatformLibrary`
  plugin `:commons` already uses, with targets `android` + `jvm` and source sets
  `commonMain` / `jvmAndroid` / `androidMain` / `jvmMain`. Almost everything moves
  here **keeping its package names**, so no import in any moved file changes.
- **`:desktopAppMobile`** *(name TBD)* — a `compose.desktop` application that opens a
  `Window` and calls the shared root composable. ~10 files, reusing the packaging,
  ProGuard, signing and notarization config `:desktopApp` already has.

`jvmAndroid` is the load-bearing source set: it compiles into **both** targets, so a
file there can reference platform types that exist on both sides without an
`expect/actual`. The repo already runs this pattern in `quartz`, `commons`, `quic` and
`nestsClient` — this is an established convention here, not a new invention. Note that
`commons` also enforces a `verifyKmpPurity` gate for its iOS targets; `:amethystShared`
starts with **no** iOS target, so it is not subject to that gate and may use OkHttp and
Jackson freely in `jvmAndroid`.

### 2.1 How `android.*` types resolve in shared code — settle this first

Two candidate mechanisms. **Phase 0 must decide empirically; do not build on a guess.**

- **(a) `compat` package + `expect`/`actual typealias` — recommended default.**
  `jvmAndroid` code imports `com.vitorpamplona.amethyst.compat.Context`;
  `androidMain` declares `actual typealias Context = android.content.Context`,
  `jvmMain` declares a real JVM `actual class Context`. Textbook KMP, no classpath
  games, zero risk of the Android build silently resolving against a stub. Cost: a
  `sed` over the ~442 files rewriting `import android.content.Context` →
  `import com.vitorpamplona.amethyst.compat.Context`. Mechanical, reviewable as a
  codemod, not hand-work.
- **(b) Raw `android.*` shim jar on the JVM target only.** Zero import edits — the
  same `import android.content.Context` line resolves from `android.jar` on Android
  and from the shim on JVM. Cheaper on paper, but it is **unverified** whether AGP
  tolerates that jar on the Android compile classpath without duplicate-class or
  resolution-order problems, and a shim that drifts from the real API could change
  what the Android build compiles against. Only adopt if Phase 0 proves it clean.

Several `android.*` uses need no shim at all and should just be swapped:

| Android API | replacement |
|---|---|
| `android.util.LruCache` (19) | `androidx.collection.LruCache` — already a dependency, already KMP |
| `android.os.SystemClock.elapsedRealtime` (19) | `kotlin.time.TimeSource.Monotonic` |
| `android.os.Build.VERSION.SDK_INT` (57) | a `Platform` expect/actual capability object |
| `android.os.Handler` / `Looper` (23) | coroutines on the main dispatcher |
| `android.util.Log` | already being migrated repo-wide; finish it here |

---

## 3. Resources — generate an Android-compatible `R` for the JVM

`amethyst/src/main/res` holds **4404** strings across **57** locales, 108 plurals,
and 84 drawables. Inspected the default `strings.xml`: plain entries, positional
`%1$s` args (609 uses — `java.lang.String.format` handles these identically), 15 bare
`%s`/`%d`, 2 `CDATA`, **no** inline HTML markup. There is nothing exotic to port.

**Do not** rewrite 762 call sites. Instead:

1. A Gradle codegen task parses `res/values*/strings.xml` and `drawable*/` and emits,
   for the JVM target, an `R` object with `R.string.*` / `R.plurals.* ` / `R.drawable.*`
   as stable `const val Int`, plus a compact per-locale lookup table packaged as a
   JVM resource. (Ids need only be self-consistent within the process — they never
   cross the Android/JVM boundary.)
2. `jvmMain` supplies the `actual` bodies for the `StringResourceCache.kt` family
   (`stringRes(id)`, `stringRes(id, vararg)`, plural and painter variants) against
   that table: `String.format` for args, a small CLDR plural-rules table for the 108
   plurals, and image decoding for the drawables.
3. `androidMain` keeps today's implementation verbatim.

Net effect: **~4000 call sites compile unchanged.** The 72 non-composable
`getString(R.string.…)` sites are the only ones needing individual attention.

**Considered and deferred:** moving to Compose Multiplatform Resources
(`composeResources/values-XX/strings.xml`), which reads the *same* XML format and is
already what `commons` and Crowdin use. That is the better long-term home, but it
forces the `R.string.x` → `Res.string.x` rewrite across 762 files up front. The two
systems co-exist fine, so ship the generated `R` first to unblock desktop, then
migrate to CMP resources incrementally afterwards.

---

## 4. Compose deltas (codemod-sized)

| Change | Files |
|---|---|
| `androidx.compose.ui.tooling.preview.Preview` → `org.jetbrains.compose.ui.tooling.preview.Preview` | 137 — pure import swap |
| `androidx.compose.ui.platform.LocalContext` → `…amethyst.compat.LocalContext` (delegates to the real one on Android) | 206 — import swap |
| `androidx.compose.ui.res.*` (`stringResource`, `painterResource`) | 93 — absorbed by §3 |
| `LocalConfiguration` (15), `LocalView` (11), `AndroidView` (4), `material3.windowsizeclass` (3) | ~33 — hand-handled |

Versions in `gradle/libs.versions.toml` are current enough for all of this: Compose
Multiplatform 1.11.1, Kotlin 2.4.10, AGP 9.3.2, `lifecycle` 2.11.0 and
`navigation-compose` 2.9.8 (both already KMP), Coil 3.5.0 (already KMP).

---

## 5. The real work: platform seams

This is where the effort actually goes — and where **`:desktopApp`'s 103 non-UI files
are already the answer**. They are the JVM `actual`s, not throwaway.

| Seam | Android | JVM | Status |
|---|---|---|---|
| Video / audio playback (~60 files, media3) | ExoPlayer | `composemediaplayer`, JCodec/ffmpeg thumbnails | **built** in `:desktopApp` |
| Key storage | `security-crypto` / KeyStore | jkeychain, encrypted file | **built** |
| Tor | Android service | `kmp-tor` | **built** |
| File pick / share / open URL | `Intent` | `java.awt.Desktop`, `FileDialog` | **built** |
| Networking / relay client | OkHttp | OkHttp | shared already |
| Notifications | `NotificationManager`, FCM / UnifiedPush | tray notifications | partial |
| Clipboard, `Toast` | Android | Compose clipboard, snackbar | small |
| Background work | WorkManager | coroutine scheduler | small |
| Camera / QR scan | `zxing-embedded` | webcam or paste-image | new, degradable |
| Biometrics | `androidx.biometric` | OS keychain prompt | new, degradable |
| Maps (osmdroid) | osmdroid | tile renderer or static map | new, degradable |
| Health Connect · TTS · Cast · PIP · napplet WebView | native | — | **feature-gate off** |

Add a capability registry (`Feature.HealthConnect.isAvailable`) so screens *hide*
unsupported features rather than calling a `TODO()` actual and crashing.

---

## 6. Phasing

- **Phase 0 — spike (~1 week). Go/no-go gate.** Stand up `:amethystShared`; settle
  §2.1 (a) vs (b) by building it; move one self-contained screen family and its
  dependencies; generate `R` for a handful of strings; render that real Android
  screen in a desktop `Window`. Every later phase is bought on this answer.
- **Phase 1 — the split.** `:amethystApp` / `:amethystShared`. Bulk-move everything
  into `commonMain`/`jvmAndroid`; `androidMain` catches whatever refuses to compile.
  **Ships no behavior change** — the existing Android test suite is the gate, and the
  Android app must be indistinguishable before and after.
- **Phase 2 — resources + Compose codemods** (§3, §4).
- **Phase 3 — seams**, one at a time, wiring the JVM actuals already in `:desktopApp`
  and feature-gating the rest.
- **Phase 4 — desktop affordances *on top of* the shared screens:** window chrome,
  keyboard shortcuts, multi-pane / deck layout, right-click menus, density. This is
  where `:desktopApp`'s desktop-first design work is re-applied **as a layer rather
  than a fork** — and it is not optional. Phone-density UI on a 27" monitor is the
  main way this plan fails its users.

---

## 7. Risks

- **Phase 1 regresses Android.** Mitigated by making Phase 1 a pure move with zero
  behavior change, gated on the existing test suite.
- **Adaptive layout is thin today** — only 3 files use `windowSizeClass`, so the
  shared screens are *not* as responsive as "share the Android UI" implies. Phase 4
  carries real design work.
- **`:amethyst` is an application module** with Google Services, baseline profiles and
  R8; the split must keep all of it on `:amethystApp`.
- **Two shipped apps from one tree** means desktop-driven refactors can break mobile.
  The Android build stays the primary CI gate.

## 8. Alternatives rejected

- **Robolectric `android-all` as the shim.** It carries real AOSP implementations, but
  needs Robolectric's instrumenting classloader and JUnit sandbox to work. It is a
  test harness, not a shippable runtime.
- **Emulation (Waydroid / ARC / an Android runtime).** Ships an Android system, not a
  desktop app; needs KVM, and is a non-starter for macOS/Windows distribution.
- **Finish the commons migration first.** Correct long-term, but it gates desktop
  parity behind a per-file design decision made 2341 times. This plan reaches the same
  place with the compiler doing the sorting, and leaves the commons migration free to
  continue underneath at its own pace.
