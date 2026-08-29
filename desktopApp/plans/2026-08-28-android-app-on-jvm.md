# Shipping the Android app as a JVM desktop app

**Date:** 2026-08-28 · **Owning module:** `desktopApp` · **Status:** in progress — Phase 0 done, Phase 1 under way

> **Progress.** The mechanism is proven and the resource layer is built and shipped.
> `./gradlew :amethystJvmProbe:jvmReadiness` prints the live burn-down; at the last
> run **1863 of 2340 files (80%) already compile for the JVM.** See §8.

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

### 2.1 How `android.*` types resolve in shared code — SETTLED

Two mechanisms were on the table. **Option (b), the raw stub jar, won, and the
`expect`/`actual typealias` alternative is retired** — it would have cost a codemod
over every `android.*` import for no benefit.

A file writes the ordinary Android import and it resolves from `android.jar` on
Android and from `:androidStubs` on the JVM. Wired `compileOnly` on the shared
source set and `implementation` on `jvmMain` only. Measured on `:amethystShared`:

| classpath | `:androidStubs` present |
|---|---|
| `androidCompileClasspath` | yes (compileOnly) |
| `androidRuntimeClasspath` | **no** — never dexed, never in the APK |
| `jvmCompileClasspath` / `jvmRuntimeClasspath` | yes |

The decisive question was which `Context` the **Android** target compiles against
when both jars are on its compile classpath. Measured by calling
`Context.getCacheDir()`, present on the real class and deliberately absent from the
stub: the Android target **compiles** it (so `android.jar` wins, and the stub can
never weaken or alter what the app is built against) while the JVM target fails with
`Unresolved reference 'cacheDir'`. The mechanism can only ever fail closed, on the
JVM side, and **the JVM compiler doubles as the worklist**.

Kotlin runs no metadata compilation for a source set shared only among JVM-family
targets (`compileJvmAndroidKotlinMetadata` is `SKIPPED`), so there is no third
classpath to reconcile.

**The same trick extends to Compose.** `:composeStubs` declares `LocalContext`,
`stringResource`, `pluralStringResource` and `painterResource(Int)` *inside*
`androidx.compose.ui.platform` and `androidx.compose.ui.res` — Compose's own
packages — so the existing imports resolve from the Compose Android artifacts on
Android and from the stub on the JVM. That is ~1000 references fixed with zero
source edits. (Split packages across jars are legal on the classpath; this would
need rework only if the project ever moved to JPMS modules.)

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

**Every one of these is meant to get a real desktop implementation eventually**,
so none of them may be stubbed inert and forgotten. The rule, documented in full
in `androidStubs/README.md`: a stub either does the thing (the JVM often can),
exposes an SPI for the desktop app to implement, reports itself to
`PlatformGaps`, or throws when silence would be unsafe. Never nothing.

That reframes feature-gating: a gate is a *temporary state of an SPI that has no
implementation yet*, not a decision to drop the feature. `PlatformGaps.seen()`
is the running list of what is still missing, which is exactly the backlog.

---

## 6. The structural finding that shapes everything: one big cycle

The original phasing assumed the module could be migrated in dependency order,
with `androidMain` catching whatever refused to compile. A package-graph analysis
of `amethyst/src/main/java` says otherwise:

| | |
|---|---|
| packages | 537 |
| **largest strongly connected component** | **505 packages / 2284 files (98%)** |
| every other SCC | a single package |
| packages movable today, transitively Android-free | 12 packages / **15 files (1%)** |

The file-level figure in §1 (72% of files import nothing Android-specific) is real
but misleading: those files sit *inside* the cycle, importing neighbours that
import Android. Greedy analysis confirms it — fixing the single highest-leverage
package unblocks **8 files**, and the twelve best fixes together unblock 29.

Two consequences, and they are the crux of this plan:

1. **There is no incremental package-by-package migration.** Nothing can move on
   its own, so "move a package, verify, repeat" is not available at any granularity
   coarser than the individual file.
2. **`androidMain` cannot be the fallback for Android-coupled code.** Code in
   `androidMain` is invisible to `jvmAndroid`, so pushing the 583 Android-coupled
   files down there would sever 583 edges *inside* a cycle — each one needing an
   `expect`/`actual` seam. That is the expensive path.

So the migration is **stub-driven, not move-driven**: grow `:androidStubs` and
`:composeStubs` until the whole module compiles for the JVM in place, and reserve
`expect`/`actual` for the genuinely divergent behaviour (§5). The unit of work is a
missing symbol, not a package — which is exactly what §8's gate reports.

## 7. Phasing

- **Phase 0 — spike. DONE.** `:amethystShared` + `:androidStubs` stood up, §2.1
  settled by measurement, the resource pipeline built and tested, and a composable
  written against `stringRes(R.string.x)` / `painterRes(R.drawable.x)` rendered
  offscreen on the JVM with no Android framework present.
- **Phase 1 — resources relocated. DONE.** All 57 `values*/strings.xml` and every
  `drawable*` bucket now live in `:amethystShared`; 770 `R` imports re-pointed. The
  fdroid APK assembles with 4758 strings, 110 plurals, 259 drawables and the
  translations intact, and `./gradlew test` stays green.
- **Phase 2 — burn the stub surface down (current).** Driven by §8's gate, not by
  moving files. Then split `:amethyst` into `:amethystApp` + the shared module once
  the module actually compiles for the JVM — a rename at that point, not a port.
- **Phase 3 — seams**, one at a time, wiring the JVM actuals already in `:desktopApp`
  and feature-gating the rest.
- **Phase 4 — desktop affordances *on top of* the shared screens:** window chrome,
  keyboard shortcuts, multi-pane / deck layout, right-click menus, density. This is
  where `:desktopApp`'s desktop-first design work is re-applied **as a layer rather
  than a fork** — and it is not optional. Phone-density UI on a 27" monitor is the
  main way this plan fails its users.

---

## 8. The gate: `:amethystJvmProbe`

`./gradlew :amethystJvmProbe:jvmReadiness`

The probe compiles `amethyst/src/main/java` **in place, unmoved** for the JVM
against the stub modules. It ships nothing and is excluded from `build` and
`check` — a red gate must never block the Android app's pipeline. Its product is
the compiler's error list, rendered as a burn-down: how many files already compile,
and which unresolved symbols would fix the most references.

Burn-down so far, of 2340 files:

| after | files compiling clean | errors |
|---|---|---|
| the first four stubs | 1652 (71%) | 11 708 |
| `:composeStubs` (`LocalContext`, `stringResource`, …) | 1761 (75%) | 10 776 |
| the first framework tier (Build, Uri, Intent, Bundle, Toast, Log, Context, …) | **1863 (80%)** | 8 531 |

What remains, ranked by references (the gate prints this live):

| surface | refs | shape of the fix |
|---|---|---|
| `androidx.media3` / ExoPlayer `Player` | ~205 | the real seam — back it with `composemediaplayer`, which `:desktopApp` already ships |
| `androidx.core` (`NotificationCompat`, `toUri`, …) | ~110 | stub; mostly thin wrappers over framework classes already stubbed |
| navigation `composableFromEnd*` helpers | ~225 | app-side helpers over `androidx.navigation`; KMP already, needs wiring |
| `androidx.datastore` (`stringPreferencesKey`) | ~153 | datastore is KMP; wire the JVM artifact |
| napplet `Nappletbrowser/Embed/Ipc` contracts | ~140 | WebView sandbox — desktop needs its own host or the feature gates off |
| `PendingIntent` / `NotificationManager` / `Manifest` | ~193 | stub + a desktop notification backend |
| `webrtc` / `PeerConnection` | ~81 | calls; feature-gate or a JVM WebRTC binding |
| `ExifInterface`, `MediaMetadataRetriever`, `Bitmap` | ~123 | `:desktopApp` already has commons-imaging and JCodec for these |
| `accompanist`, `ActivityResultContracts`, `work` | ~96 | permissions, file pickers, background jobs — desktop equivalents exist |

The long tail is genuinely long — 452 symbols referenced exactly once — but it is
tail, not body: each is a one-line stub or a deletion.

## 9. Risks

- **Phase 1 regresses Android.** Mitigated by making Phase 1 a pure move with zero
  behavior change, gated on the existing test suite.
- **Adaptive layout is thin today** — only 3 files use `windowSizeClass`, so the
  shared screens are *not* as responsive as "share the Android UI" implies. Phase 4
  carries real design work.
- **`:amethyst` is an application module** with Google Services, baseline profiles and
  R8; the split must keep all of it on `:amethystApp`.
- **Two shipped apps from one tree** means desktop-driven refactors can break mobile.
  The Android build stays the primary CI gate.

## 10. Alternatives rejected

- **Robolectric `android-all` as the shim.** It carries real AOSP implementations, but
  needs Robolectric's instrumenting classloader and JUnit sandbox to work. It is a
  test harness, not a shippable runtime.
- **Emulation (Waydroid / ARC / an Android runtime).** Ships an Android system, not a
  desktop app; needs KVM, and is a non-starter for macOS/Windows distribution.
- **Finish the commons migration first.** Correct long-term, but it gates desktop
  parity behind a per-file design decision made 2341 times. This plan reaches the same
  place with the compiler doing the sorting, and leaves the commons migration free to
  continue underneath at its own pace.
