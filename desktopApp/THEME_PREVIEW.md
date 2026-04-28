# Manual Testing: Desktop Native Theming

The desktop app adapts its colors, fonts, shapes, and accent to the host OS
(macOS, Windows, GNOME, KDE, other Linux). This guide shows how to preview
each platform's theme without leaving your dev machine, and what to look at
when reviewing a theming change.

## Quick Start

Three environment variables drive the preview overrides. Each one also has
a `-Damethyst.<key>=<value>` system-property form, forwarded from gradle to
the launched app's JVM.

| Variable | Values | Effect |
|---|---|---|
| `AMETHYST_PLATFORM` | `MACOS`, `WINDOWS`, `GNOME`, `KDE`, `LINUX_OTHER`, `UNKNOWN` | Forces in-app theming for that OS |
| `AMETHYST_APPEARANCE` | `light`, `dark` | Forces dark/light mode |
| `AMETHYST_ACCENT` | `#RRGGBB`, `RRGGBB`, or libadwaita name (`blue`, `teal`, `green`, `yellow`, `orange`, `red`, `pink`, `purple`, `slate`) | Forces accent color |

Examples:

```bash
# Native (no override) — uses your real OS
./gradlew :desktopApp:run

# GNOME light theme with the libadwaita default blue accent
AMETHYST_PLATFORM=GNOME AMETHYST_APPEARANCE=light AMETHYST_ACCENT=blue ./gradlew :desktopApp:run

# KDE Breeze dark with a custom accent
AMETHYST_PLATFORM=KDE AMETHYST_APPEARANCE=dark AMETHYST_ACCENT=#3DAEE9 ./gradlew :desktopApp:run

# Windows 11 (WinUI 3 mica tones)
AMETHYST_PLATFORM=WINDOWS ./gradlew :desktopApp:run

# Equivalent system-property form
./gradlew :desktopApp:run -Damethyst.platform=GNOME -Damethyst.appearance=light
```

## What Changes vs. What Doesn't

The override swaps **in-app theming only**. The window chrome (title bar,
traffic lights / minimize-maximize buttons, screen menu bar on macOS) is
drawn by AWT from the actual host OS, not by our theme code. So:

| Element | Follows override? | Notes |
|---|---|---|
| `colorScheme` (background, surface, primary…) | ✅ | Per-OS reference palettes |
| Body / heading fonts | ✅ | SF Pro on macOS, Cantarell on GNOME, Noto Sans on KDE, Segoe UI Variable on Windows |
| Button / card / dialog rounding | ✅ | macOS 8/10/14, libadwaita 9/12/16, Breeze 6/8/12, WinUI 4/8/8 |
| Accent color | ✅ | Threaded through `MaterialTheme.colorScheme.primary` |
| Sidebar density (56 dp) | ✅ | Same on all OSes (desktop convention) |
| Native title bar / traffic lights | ❌ | Drawn by host OS — to see the real GNOME header bar or KDE Breeze title, you need a real Linux machine or VM |
| macOS screen menu bar | ❌ | Only active when host OS is macOS |
| `apple.awt.transparentTitleBar` content extension | ❌ | macOS-host-only |

## Review Checklist

When reviewing a theming change, launch each preview and verify:

### macOS (`AMETHYST_PLATFORM=MACOS`, or no override on a Mac)

- [ ] Sidebar background reads as `surfaceContainer` — slightly lighter than the deck background, not jarringly different
- [ ] Body text renders in SF Pro Text (check by zooming a screenshot — SF has distinctive 'a', 'g', 'k' shapes)
- [ ] Card / dialog corners ~10 dp (a hair tighter than libadwaita)
- [ ] Letter spacing is slightly tight at large headings (SF tightens at display sizes)
- [ ] On a real Mac: traffic lights sit at top-left over the sidebar color, NOT over a white default-OS strip
- [ ] On a real Mac: menu bar appears at the top of the screen, not inside the window

### GNOME (`AMETHYST_PLATFORM=GNOME`)

- [ ] Surfaces match libadwaita references: `#242424` window bg dark, `#FAFAFA` window bg light
- [ ] Cards have 12 dp medium rounding (visibly more rounded than macOS)
- [ ] If Cantarell or Adwaita Sans is installed locally, body text uses it; otherwise falls through to Inter / Noto Sans
- [ ] Try `AMETHYST_ACCENT=blue` and confirm primary color is `#3584E4` (libadwaita default)

### KDE (`AMETHYST_PLATFORM=KDE`)

- [ ] Surfaces match Breeze references: `#1B1E20` background dark, `#EFF0F1` background light
- [ ] Rounding is tighter than macOS / GNOME (8 dp medium, 6 dp small)
- [ ] Body text renders in Noto Sans if installed
- [ ] Default accent (when nothing forced) is the Amethyst purple fallback — KDE accent detection won't run on macOS

### Windows (`AMETHYST_PLATFORM=WINDOWS`)

- [ ] Surfaces match WinUI 3 mica tones: `#202020` background dark, `#F3F3F3` background light
- [ ] Rounding is the tightest of any platform: 4 dp small, 8 dp medium
- [ ] Body text uses Segoe UI Variable Text only if installed locally (not present on macOS by default — falls back to FontFamily.Default)

## Side-by-side Comparison

The launched app is a single window. To compare two themes you currently
need to launch the app twice:

```bash
# Terminal 1
AMETHYST_PLATFORM=GNOME ./gradlew :desktopApp:run

# Terminal 2 (after the first finishes building)
AMETHYST_PLATFORM=MACOS ./gradlew :desktopApp:run
```

Each launch opens its own window — drag them next to each other.

## Known Limitations

1. **No native chrome on host OS.** The window frame, title bar buttons,
   and (on macOS) screen menu bar always come from the real host OS.
   To see a real GNOME header bar or KDE title bar, use a Linux machine
   or VM.

2. **OS detection shell-outs return defaults when their CLI is missing.**
   On macOS, `gsettings` and `kreadconfig5` aren't installed, so
   `AMETHYST_PLATFORM=GNOME` without `AMETHYST_APPEARANCE` defaults to
   dark and without `AMETHYST_ACCENT` defaults to Amethyst purple. Pass
   the explicit overrides to control them.

3. **Font fallback chain is deterministic but not always satisfying.**
   The chain (e.g. for GNOME: Adwaita Sans → Cantarell → Inter → Noto
   Sans → DejaVu Sans) walks Skia's font manager and picks the first
   installed family. If none of the candidates are installed,
   `FontFamily.Default` is used (looks like Roboto-ish). To install the
   GNOME family on macOS for testing:
   ```bash
   brew install --cask font-cantarell
   ```

4. **Accent name list is libadwaita-only.** Apple's named accents (red,
   orange, etc. as integers) and Windows registry accents resolve only
   when the corresponding host OS is the real OS. Use hex
   (`AMETHYST_ACCENT=#FF6B35`) for arbitrary colors.

## Where the Code Lives

All preview behavior is in `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/platform/`:

- `PlatformInfo.kt` — OS detection + `amethyst.platform` override
- `PlatformAppearance.kt` — dark/light detection + `amethyst.appearance` override
- `PlatformAccent.kt` — accent detection + `amethyst.accent` override
- `PlatformFonts.kt` — system font resolution via Skia FontMgr
- `PlatformShapes.kt` — per-OS Material3 Shapes
- `PlatformTypography.kt` — per-OS Material3 Typography
- `PlatformColorScheme.kt` — per-OS dark/light ColorSchemes
- `PlatformTheme.kt` — `PlatformMaterialTheme` composable + `applyNativeWindowChrome()`
