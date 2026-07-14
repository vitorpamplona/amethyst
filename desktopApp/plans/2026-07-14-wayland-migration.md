# Linux: X11 → Wayland migration assessment

**Date:** 2026-07-14
**Status:** Blocked upstream (Skiko). XWayland remains the only working path.
This doc records the audit, the blocker, the preparatory work we can do now,
and the exact trigger + recipe for migrating when upstream unblocks.

## TL;DR

There is **no way to run Amethyst Desktop natively on Wayland today**, and the
reason is *not* in our code. Compose Desktop renders through Skiko's AWT/JAWT
integration, which crashes at window creation under the JetBrains Runtime's
Wayland toolkit (`WLToolkit`) — tracked as
[SKIKO-890](https://youtrack.jetbrains.com/issue/SKIKO-890), still open as of
July 2026 and re-confirmed against Compose 1.11.1 / Skiko 0.144.6 / JBR 21.0.11
([SKIKO-1147](https://youtrack.jetbrains.com/issue/SKIKO-1147), closed as
duplicate). No released Compose Multiplatform version supports Wayland
natively; every Compose Desktop app (including JetBrains' own Toolbox) runs
under **XWayland** on Wayland sessions.

Our own application code is already ~95% Wayland-clean (see audit below), so
when Skiko lands Wayland support the migration is mostly a *runtime and
packaging* change, not an application rewrite.

## Current Linux release surface (how we ship today)

| Artifact | Mechanism | Display coupling |
|---|---|---|
| `.deb` / `.rpm` | Compose `nativeDistributions` → jpackage | Bundled JRE is **Temurin 21** (CI `create-release.yml` uses `distribution: temurin`), whose AWT has only **XToolkit** — X11 or XWayland, nothing else |
| AppImage | `createReleaseAppImage` wraps `createReleaseDistributable` with appimagetool | Same Temurin tree; `AppRun` sets only `LD_LIBRARY_PATH`/`PATH` |
| Flatpak (CI bundle + Flathub variant) | Packages the prebuilt jpackage tree | `--socket=x11` **only**, deliberately (manifest comment): `fallback-x11` grants X11 only on non-Wayland sessions, and the app can't use a `wayland` socket, so unconditional `x11` is the correct grant today |
| `tar.gz` portable | tarred distributable | Same Temurin tree |
| CI | `smoke-test-desktop.yml`, desktop tests | `xvfb-run` (X11 virtual display) |

Because we bundle Temurin, users **cannot** accidentally opt into a broken
Wayland toolkit — Temurin has no `WLToolkit` (upstream OpenJDK/Project
Wakefield has merged only XWayland-mode fixes, e.g. ScreenCast-portal Robot
screenshots; the Wayland toolkit itself lives only in the JetBrains Runtime
fork).

## Ecosystem status (July 2026)

- **JBR WLToolkit**: shipped in JBR 21 (and carried forward in JBR 25);
  opt-in via `-Dawt.toolkit.name=WLToolkit`, and since IntelliJ **2026.1**
  it is the **default** for the IDE in Wayland sessions
  (`-Dawt.toolkit.name=auto`). So Swing/Java2D on Wayland is real now.
- **But Compose ≠ Swing**: Skiko's `HardwareLayer` locks a JAWT
  `DrawingSurface`, which WLToolkit cannot provide →
  `IllegalStateException: Can't lock DrawingSurface` at startup
  (SKIKO-890). Changing `skiko.renderApi` (SOFTWARE/OPENGL/VULKAN) does not
  help. IntelliJ's embedded Compose panels (Jewel) survive because they render
  through the Swing-graphics offscreen path, not a standalone `ComposeWindow`.
- **Rendering**: even in 2026.1, WLToolkit renders in software; Vulkan
  acceleration is in development
  ([JBR-7558](https://youtrack.jetbrains.com/issue/JBR-7558),
  [SKIKO-1138](https://youtrack.jetbrains.com/issue/SKIKO-1138)).
- **jpackage + JBR**: the plain `jbr`/`jbr_jcef` tarballs ship **no jmods**
  (jlink/jpackage fail); the **`jbrsdk`** flavour does. When the time comes,
  point the Compose plugin's `javaHome` at a jbrsdk. (License: JBR is
  GPLv2 + Classpath exception — same terms as the Temurin/OpenJDK runtime we
  already bundle, so no licensing change.)
- **Watch list** (any of these turning is the migration trigger):
  - [SKIKO-890](https://youtrack.jetbrains.com/issue/SKIKO-890) — the blocker
  - [skiko PR #1224](https://github.com/JetBrains/skiko/pull/1224) —
    Wayland/EGL SkiaLayer backend (community, targets Kotlin/Native so far)
  - [SKIKO-1138](https://youtrack.jetbrains.com/issue/SKIKO-1138) — Vulkan
    DirectContext
  - [JBR-9966](https://youtrack.jetbrains.com/issue/JBR-9966) — SystemTray
    under WLToolkit
  - [JBR-6969](https://youtrack.jetbrains.com/issue/JBR-6969) — fractional
    scaling (`wp_fractional_scale_v1`)

## Codebase audit — what would break on native Wayland

Surveyed all AWT/window-system touchpoints in `desktopApp/` and
`commons/src/jvmMain/`. The app is in remarkably good shape:

**Already Wayland-safe:**

- **Video/audio playback** — kdroidFilter ComposeMediaPlayer renders frames
  into a **Compose Canvas** (Skia `ImageBitmap`), not a native X11 overlay
  (that was an explicit criterion in the vlcj replacement plan). GStreamer
  decode is display-server-agnostic.
- **OS notifications** — primary path is `NucleusNotificationDispatcher` →
  freedesktop **D-Bus** on Linux; works under any compositor. The
  `AwtTrayNotifier` fallback already guards on `SystemTray.isSupported()`
  and degrades silently (WLToolkit reports unsupported → clean no-op).
- **Dark mode / accent detection** (`PlatformAppearance`, `PlatformAccent`) —
  shells out to `gsettings`; display-server independent.
- **Platform detection** (`PlatformInfo`) — reads `XDG_CURRENT_DESKTOP` etc.,
  not the display server.
- **Taskbar icon** — guarded by `Taskbar.isTaskbarSupported()`.
- No `java.awt.Robot`, no `MouseInfo` global-position reads, no screen
  capture (the privacy-lock plan already documents capture-block as
  non-portable on Linux).

**Would need attention under WLToolkit (all small):**

| Code | Issue on Wayland | Fix when migrating |
|---|---|---|
| `DesktopFilePicker` (AWT `FileDialog`) | No confirmed WLToolkit implementation; no XDG-portal backing | Swap to an xdg-desktop-portal file chooser (also lets the Flatpak drop filesystem grants). Worth doing **now** — portals work fine on X11 too |
| `FullscreenHelper` (`GraphicsEnvironment.fullScreenWindow`) | Fullscreen basically works under WLToolkit, but verify exit + multi-monitor edge cases | Test; fall back to `WindowState.placement = Fullscreen` |
| `Main.kt` `WindowPosition.Aligned(Center)` + any position save/restore | `setLocation` is a **no-op by design** on Wayland (no global coordinates) | Accept compositor placement; don't persist window position |
| Clipboard (`ClipboardExt`, paste handler) | Works, but writes may be ignored unless triggered by recent real user input ("silent protocol") | Ensure copy actions run directly from input handlers (they do) |

## Plan

### Phase 0 — now (no upstream dependency)

1. **Nothing to change in how we ship.** XWayland is the supported,
   JetBrains-sanctioned path; our Flatpak `--socket=x11`-only grant is
   correct and stays.
2. **(Optional, independently useful)** Replace AWT `FileDialog` with an
   xdg-desktop-portal file picker. Benefits today: native GTK/KDE dialogs,
   tighter Flatpak sandbox; removes the biggest app-side Wayland unknown.
3. **(Optional)** Document the XWayland fractional-scaling workaround for
   users on scaled Wayland displays (blurriness, same as JetBrains Toolbox:
   TBX-12552): launch with `-Dsun.java2d.uiScale=2` or session-level
   XWayland scaling. A FAQ entry is enough.
4. Re-check the watch list every few months (or when a Compose Multiplatform
   release announces Linux/Wayland work).

### Phase 1 — when SKIKO-890 is fixed and a Compose release supports WLToolkit

1. **Runtime swap**: build release legs with **jbrsdk** (has jmods) instead
   of Temurin — `compose.desktop { application { javaHome = … } }` or
   `JAVA_HOME` in the three Linux CI legs. Keep Temurin for non-Linux legs
   unless there's a reason to unify.
2. **Toolkit selection**: start with explicit opt-in
   (`AMETHYST_WAYLAND=1` → `-Dawt.toolkit.name=WLToolkit`) for one release,
   then flip to `-Dawt.toolkit.name=auto` once stable.
3. **Flatpak**: change `--socket=x11` → `--socket=wayland` +
   `--socket=fallback-x11` (this combination is exactly what the manifest
   comment says to use "if the app ever renders natively on Wayland").
4. **App fixes**: the four rows in the table above (file picker if not
   already done in Phase 0, fullscreen verification, drop window-position
   persistence, clipboard audit).
5. **CI**: add a Wayland smoke leg — run the packaged app under a headless
   compositor (`weston --backend=headless` or `wlheadless-run`) alongside
   the existing xvfb leg.
6. **Perf check**: WLToolkit renders in software until Vulkan lands —
   measure scrolling/feed performance before making Wayland the default;
   if it regresses, hold at opt-in until JBR-7558/SKIKO-1138 ship.

## Decision record

- **Do not** set `-Dawt.toolkit.name=WLToolkit` or `auto` on any current
  release: instant startup crash (SKIKO-890).
- **Do not** switch the Flatpak to `wayland`/`fallback-x11` sockets yet: on a
  Wayland session that leaves the app with no usable display.
- Bundling JBR *today* buys nothing (Skiko still crashes) and adds a
  nonstandard runtime; defer to Phase 1.
