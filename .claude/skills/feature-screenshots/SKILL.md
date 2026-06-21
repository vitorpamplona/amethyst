---
name: feature-screenshots
description: Capture screenshots and short animated-GIF clips of Amethyst UI to highlight a feature you are developing — no emulator, no device, no display. Use when asked to "show", "screenshot", "record", "capture", or "demo" what a feature looks like, when you want to attach a visual to a PR/answer, or when you finish a UI change and want to surface proof of how it looks. Covers both the Desktop harness (Compose UI test + ImageIO, zero new deps) and the Android harness (Robolectric + Roborazzi). Complements compose-expert (where shared composables live) and verify (which confirms behavior, not appearance).
---

# Feature Screenshots & Clips

Render any composable to a **PNG** (screenshot) or a short **animated GIF** (clip)
headlessly, then surface it with the `SendUserFile` tool. Two harnesses share the
same idea — render the real composable, drive it into the state worth showing,
capture a frame (or a sequence of frames) — and both run in CI's headless
container with no emulator and no extra system tooling.

Artifacts always land in **`<module>/build/screenshots/`** (gitignored, under `build/`).

## When to Use

Auto-invoke when the user asks to *show / screenshot / record / capture / demo* a
feature, wants a visual for a PR or answer, or when you've just finished a UI
change and a picture would prove it. For behavioral verification (does it work?)
use `verify` instead — this skill is about appearance.

## Desktop harness (no new dependencies)

Lives in `desktopApp/src/jvmTest/.../capture/`. Rides the existing
`createComposeRule` test infra; PNG/GIF encoding is pure JDK `ImageIO`.

1. Write a test next to `FeatureShowcaseDesktopTest.kt` (the template):
   ```kotlin
   compose.setContent { MaterialTheme { MyFeature(...) } }
   compose.onRoot().saveScreenshot("desktop-my-feature")          // -> PNG
   ```
   For a clip, collect frames across interactions and call `writeAnimatedGif(...)`.
2. Run headless (Skiko needs a display, hence xvfb):
   ```bash
   xvfb-run --auto-servernum ./gradlew :desktopApp:test --tests '*FeatureShowcaseDesktopTest*'
   ```
3. Helpers: `SemanticsNodeInteraction.saveScreenshot(name)`,
   `.toBufferedImage()`, and `writeAnimatedGif(frames, file, delayMs, loop)` in
   `ScreenshotCapture.kt` / `ImageWriters.kt`.

## Android harness (Robolectric + Roborazzi)

Lives in `amethyst/src/test/.../screenshots/`. Renders through the real
`AmethystTheme` — closest to what ships on a phone.

1. Write a test next to `FeatureShowcaseAndroidTest.kt` (the template):
   ```kotlin
   @RunWith(RobolectricTestRunner::class)
   @GraphicsMode(GraphicsMode.Mode.NATIVE)
   @Config(sdk = [36], application = Application::class)   // see gotchas
   class MyFeatureShots {
       @get:Rule val compose = createComposeRule()
       @Test fun shot() {
           compose.setContent { AmethystTheme(ThemeType.LIGHT) { MyFeature() } }
           compose.onRoot().captureRoboImage("build/screenshots/android-my-feature.png")
       }
   }
   ```
   GIF: `compose.onRoot().captureRoboGif(compose, "build/screenshots/x.gif") { ...interactions... }`.
2. Run the Roborazzi record task (it sets record mode + runs the tests):
   ```bash
   ./gradlew :amethyst:recordRoborazziPlayDebug --tests '*MyFeatureShots*'
   ```

### Android gotchas (learned the hard way)

- **`@Config(sdk = [36])` is mandatory.** The repo is `compileSdk 37`, but
  Robolectric ships framework jars only up to **API 36** — an unpinned test fails
  to resolve an SDK. Bump this when Robolectric adds a newer jar.
- **`application = Application::class`.** Without it Robolectric boots the real
  `Amethyst` Application, whose `onCreate` (WorkManager, Tor, Coil…) throws.
  A stock `Application` stub renders UI fine.
- **`captureRoboGif` does not create the output dir** (the PNG path does); ensure
  `build/screenshots/` exists first (a `@Before { File("build/screenshots").mkdirs() }`).
- `unitTests.isIncludeAndroidResources = true` must stay on in `amethyst/build.gradle.kts`.

## Surfacing the result

After the artifact is written, send it to the user:
```
SendUserFile(files = ["amethyst/build/screenshots/android-my-feature.png"], status = "normal")
```
Prefer `status: "proactive"` only when the user is away and the image is the deliverable.

## Don't

- Don't reach for `ffmpeg` / `scrot` / `x11grab` — they aren't installed; the
  in-JVM PNG/GIF path is the supported route.
- Don't render the whole running app to "screenshot a screen" — render the
  specific composable in isolation, as the templates do.
