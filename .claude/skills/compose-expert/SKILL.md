---
name: compose-expert
description: Shared Compose UI in `commonsUI/`: screens, components, the nav shell (`ScreenLayoutSpec`, `AppShellLayout`, Navigation 3 back stacks), component catalog, `RichTextParser`, Compose API/perf review. Use when writing or reviewing shared UI.
---

# compose-expert

Where shared Compose UI lives in Amethyst and the house rules for it. What goes in
`commons` vs `commonsUI` vs a platform shim is in `AGENTS.md` ("Sharing Philosophy",
"Feature Workflow") and `commonsUI/ARCHITECTURE.md`; this skill does not repeat it.

Delegate: Activity / manifest / flavors -> `android-expert`; Window, tray, menu bar ->
`desktop-expert`; source-set placement, expect/actual, extracting an Android composable ->
`kotlin-multiplatform`; feeds and `Account` state -> `account-state`; Flow and coroutine
scopes -> `kotlin-coroutines`.

## Where things live

All paths below are under
`commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/`.

| What | Where |
|------|-------|
| Screens | `ui/screen/loggedIn/<feature>/` (and `ui/screen/` for shared feed views) |
| Reusable components | `ui/components/` (catalog: `references/shared-composables-catalog.md`) |
| Feed scaffolding | `ui/feeds/` (`RenderFeedContentState`, `RefresheableBox`, `FeedEmpty`, `LoadingFeed`) |
| Note rendering | `ui/note/` (`NoteCompose`), rich text in `ui/richtext/` + `ui/components/RichTextViewer.kt` |
| Theme | `ui/theme/` (`AmethystColorScheme.kt` palettes + `ColorScheme.*` tokens, `ColorScheme.isLight`, `Shape.kt`, `Type.kt`) |
| Window tiers | `ui/layouts/ScreenLayout.kt` (`ScreenLayoutSpec`, `LocalScreenLayout`, `FeedContentMaxWidth`) |
| Shell + nav | `ui/app/AmethystApp.kt`, `ui/app/AppRoot.kt`, `ui/navigation/` (see below) |
| Platform ports for UI | `ui/platform/AppPlatform.kt` (`LocalAppPlatform`), `ui/components/PlatformPermission.kt` |
| Icons | `icons/` (Amethyst glyph font) and `icons/symbols/` (`MaterialSymbols`, `AmethystIcons`) |
| Robohash avatars | `robohash/` (`CachedRobohash`, `RobohashAssembler`, `parts/`) |
| Strings | `commonsUI/src/commonMain/composeResources/values/strings.xml` -> `Res.string.*` (rules: `AGENTS.md` "Strings") |

The Android app's `AmethystTheme` wrapper is still in
`amethyst/src/main/java/com/vitorpamplona/amethyst/ui/theme/Theme.kt`; the palettes it
applies are shared.

## The shell and window tiers

One shell for every window, chosen by size, not by platform (`AGENTS.md` "Navigation Shell"):

- `AmethystApp` (`ui/app/AmethystApp.kt`) is the root both front ends call. Android's
  `MainActivity` passes `AndroidAppRoot`; the desktop window (`AmethystDesktop.kt`) passes a
  `DesktopAppRoot`. `AppRoot`
  (`ui/app/AppRoot.kt`) is the port each platform implements: `createAccountViewModel`,
  `rememberScreenLayoutSpec`, `registerDestinations`, `ShellOverlay`, `NavigationEffects`, ...
- `ScreenLayoutSpec.navigationStyle` is `BOTTOM_BAR`, `NAV_RAIL` or `PERMANENT_DRAWER`;
  `hasRoomForNotificationPanel` docks the notification panel. `isLargeScreen` is "not the
  bottom bar". Read it with `LocalScreenLayout.current`, never with a platform check.
- `AppShellLayout` (`ui/navigation/shell/`) lays out the bar / rail / drawer;
  `ui/navigation/bottombars/` holds `AppBottomBar` and `AppNavigationRail`;
  `ui/navigation/deck/DeckArea.kt` adds the opt-in deck columns on windows of 1,200 dp+.

## Navigation 3

The app owns its back stack as snapshot state; `NavDisplay` only renders it. No
`NavController`, no graph. Full rules in `references/navigation.md`.

- Routes: `@Serializable sealed class Route` in
  `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/navigation/Routes.kt`.
  The back stack is saved through kotlinx-serialization, so every route argument must be
  serializable.
- `NavBackStacks` (same package) holds `stack` (never empty, starts at `Route.Home`) and
  `savedTabs` (the root entry of each bottom-bar tab you left, so its ViewModels and scroll
  survive). A `NavStackEntry`'s `contentKey` is `"nav-$id"`: saved state and ViewModels key
  on it, so the same route opened twice is two screens.
- `Nav` (`ui/navigation/navs/Nav.kt`) implements `INav`: `nav`, `navDrawer`, `newStack`,
  `navBottomBar`, `popBack`, `popUpTo`, each after the keyboard settles (`ImeSettler`).
- Register a screen once with a builder from `ui/navigation/host/NavDestinations.kt`. Shared
  screens go in `NavDestinations.sharedDestinations` (`ui/navigation/host/SharedDestinations.kt`);
  Android-only ones in `androidDestinations` in
  `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/AppNavigation.kt`. The builder
  picks the motion and the reading-column cap:

```kotlin
// from SharedDestinations.kt
composableCapped<Route.Home> { HomeScreen(accountViewModel, nav) }
composable<Route.Message> { MessagesScreen(accountViewModel, nav) }
composableFromEnd<Route.Polls> { PollsScreen(accountViewModel, nav) }
composableFromEnd<Route.Browser>(capWidth = false) { BrowserScreen(accountViewModel, nav) }
composableFromBottomArgs<Route.AwardBadge> { AwardBadgeScreen(it.kind, it.pubKeyHex, it.dTag, accountViewModel, nav) }
```

A route nobody registered fails the first time it is opened (the desktop installs an
`unavailable` screen instead). Gate a menu item on `canOpen(route)` when a front end may lack it.

## Composable conventions that differ from the default

- **Strings come from `Res`**, read with `stringRes(Res.string.x)`; no literal user-visible text
  in a shared composable, no Android `R.string`. Default label parameters are `StringResource`
  (see `AddButton` in `ui/components/ActionButtons.kt`).
- **UI-state collections are `kotlinx.collections.immutable`** (`ImmutableList`,
  `ImmutableMap`, `persistentListOf`), an `api` dependency of `commonsUI`. The parser output
  (`RichTextViewerState`, `ParagraphState`) and the platform ports already use them; pass those,
  not `List`, into composables.
- **`@Immutable` / `@Stable`** mark the state classes and port interfaces handed to composables
  (`ScreenLayoutSpec`, `AppPlatform`, `PlatformPermission`). Only mark a class you can keep
  truly immutable.
- **Theme-dependent values come from the color scheme**: `MaterialTheme.colorScheme.isLight`
  (memoized in `AmethystColorScheme.kt`), not a hand-rolled luminance check.
- **Icons** follow `AGENTS.md` "Icons": `icons/*.kt` compile into `amethyst_icons.ttf` and
  draw through `AmethystIconGlyph`; Material icons are `MaterialSymbols` from a subset font, so
  a new codepoint needs `tools/material-symbols-subset/subset.sh`. Robohash is the one
  ImageVector-built asset: `references/icon-assets.md`.
- **Parse rich text through `CachedRichTextParser`**, never per recomposition:
  `references/rich-text-parsing.md`.
- **Permissions in shared UI** go through an `expect fun rememberXPermission(): PlatformPermission`
  (see `ui/components/LocationPermission.kt`; the Android actual wraps Accompanist).

## Reviewing a component's public API

For slot parameters, `modifier` placement, and the other API-shape checks, use
`references/api-style.md`.

## Performance: measure first

Feed jank in this app has been measured as GC-bound and RenderThread-bound, with composition a
small share of frame time. Stability annotations, `derivedStateOf` and deferred reads only help
when a trace shows composition is the cost. Profile a release-like build (the `benchmark`
build type in `amethyst/build.gradle.kts` is minified and profileable) before you apply them, and
measure again afterwards.

Compose compiler reports are configured only for `:amethyst` (`composeCompiler { reportsDestination
... }` near line 551 of `amethyst/build.gradle.kts`, written to `amethyst/build/compose_compiler/`).
They cover only composables compiled in that module. Shared composables are compiled in
`:commonsUI`, which has no such block. To inspect their skippability, add the block to
`commonsUI/build.gradle.kts` temporarily, and don't commit it.

## See it running

A screen change is not done until you've looked at it: `tools/desktop-run/run-headless.sh start`
runs the desktop app, which is this same UI, on a virtual display and screenshots it.
`AMETHYST_SCREEN=480x900x24` shows the phone tier. See `tools/desktop-run/README.md` and
`desktop-expert`.

## References

- `references/navigation.md`: back-stack operations, rendering, transitions, intents, tests
- `references/shared-composables-catalog.md`: the reusable components and their signatures
- `references/rich-text-parsing.md`: `RichTextParser`, segments, `CachedRichTextParser`, NIP-92 imeta
- `references/icon-assets.md`: robohash avatars
- `references/api-style.md`: component API review checklist (slots, modifiers)
