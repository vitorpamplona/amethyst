# Navigation 3 in the shared UI

Amethyst runs JetBrains' multiplatform Navigation 3 (`jetbrains-navigation3-ui` and
`jetbrains-lifecycle-viewmodel-navigation3` in `gradle/libs.versions.toml`). The app owns the back
stack as plain snapshot state and `NavDisplay` renders it. There is no `NavController`, no
`NavHost` and no navigation graph. Android and Desktop run the same code.

## Files

`commons/…` = `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/`,
`commonsUI/…` = `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/`.

| File | Role |
|------|------|
| `commons/…/model/navigation/Routes.kt` | `@Serializable sealed class Route`: every destination and its arguments |
| `commons/…/model/navigation/NavBackStacks.kt` | `NavStackEntry`, `NavBackStacks`: the back stack and its rules (headless) |
| `commonsUI/…/ui/navigation/navs/Nav.kt` | `Nav`, the shared `INav` over `NavBackStacks`, plus `LocalNavStackEntry` |
| `commonsUI/…/ui/navigation/navs/RememberNavs.kt` | `rememberNav()`: saves the back stack across config changes and process death |
| `commonsUI/…/ui/navigation/navs/ImeSettler.kt` | Waits for the keyboard to close before every navigation |
| `commonsUI/…/ui/navigation/host/NavDestinations.kt` | `NavDestinations` registry, the builders, `NavFamily`, the transitions, `canOpen` |
| `commonsUI/…/ui/navigation/host/SharedDestinations.kt` | `NavDestinations.sharedDestinations`: every shared screen |
| `commonsUI/src/jvmAndroid/…/ui/navigation/host/JvmDestinations.kt` | `jvmDestinations`: shared screens that need the JVM (git browser, cordn group chat); Android and Desktop both register them |
| `commonsUI/…/ui/navigation/host/NavigationHost.kt` | `NavigationHost`: the `NavDisplay` every front end shows |
| `commonsUI/…/ui/navigation/routes/RouteNavController.kt` | `isBaseRoute<T>(nav)`, `getRouteWithArguments`, `consumesSharesInPlace` |
| `commonsUI/…/ui/app/AmethystApp.kt` | Builds `NavDestinations` (shared + `AppRoot.registerDestinations`) for the main pane and each deck column |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/AppNavigation.kt` | Android: `androidDestinations` (Android-only screens), `NavigateIfIntentRequested` |
| `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAppRoot.kt` | Desktop: `jvmDestinations` + `unavailable { UnavailableScreen(nav) }` |

## The back stack

`NavBackStacks` holds two pieces of state:

- `stack`: what is on screen, bottom to top. It is never empty and starts at `Route.Home`.
- `savedTabs`: the root entry of every bottom-bar tab the user left, keyed by its route.

Each `NavStackEntry` has a unique `id`. Its `contentKey` (`"nav-$id"`) keys the screen's
`rememberSaveable` state and its ViewModelStore, so the same route opened twice is two
independent screens. The rest is snapshot state, because it can change on an entry already on
screen:

- `route`: `newStack` onto the same destination hands the entry new arguments in place.
- `tabRoute`: the bar route this entry is the root of, if any (`tabRoot` is `tabRoute != null`).
  A tab root has no back arrow, and tab switches fade. Saved tabs are filed under it, so a tab
  re-argued by a deep link is still found by the bar.
- `drawerRoot`: opened from the navigation drawer. It can pop, but it keeps the bottom bar.

Operations (each one in `Nav` runs after `ImeSettler.settle()`):

| `INav` call | `NavBackStacks` | Effect |
|-------------|-----------------|--------|
| `nav(route)` | `push` | Pushes `route`, unless it is already on top |
| `navDrawer(route)` | `push(drawerRoot = true)` | Same push, marked as a drawer screen |
| `newStack(route)` | `newStack` | Drops the latest copy of `route` and everything above it, then shows it once. A same-class top takes the new arguments in place |
| `popUpTo(route, klass)` | `popUpTo` | Drops the latest `klass` entry and everything above it, then pushes `route` |
| `navBottomBar(route)` | `switchTab` | Drops pushes above the current tab root, keeps the left tab root in `savedTabs`, then restores `route`'s saved entry or opens it fresh |
| `popBack()` | `pop` | Pops the top. The last entry is never popped |

`Route.Home` stays at the bottom, so back from any tab returns to Home and back from Home leaves
the app. Nothing above a tab root is saved, so returning to a tab always lands on the tab itself.

## Rendering

`NavigationHost` passes `stack + savedTabs` to `rememberDecoratedNavEntries` with
`rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`.
Saved tabs therefore keep their state while out of sight. Only `stack` goes to `NavDisplay`. When
an entry leaves both lists, its saved state and ViewModels are cleared.

Each entry provides `LocalNavStackEntry`. `INav.canPop()` and `showsBottomBar()` read it, so
during a predictive-back swipe a screen that is leaving keeps its own back arrow.

## Destinations

Register each screen once. Shared screens go in `sharedDestinations`; a platform adds its own
through `AppRoot.registerDestinations` (Android: `androidDestinations`). The builder decides the
motion (`NavFamily`) and whether the screen is capped to the reading column on wide panes
(`MaybeCappedScreen`):

| Builder | Motion | Cap |
|---------|--------|-----|
| `composableCapped<T> { }` / `composableCappedArgs<T> { route -> }` | fade | capped |
| `composable<T> { }` | fade | full width |
| `composableArgs<T>(capWidth) { route -> }` | fade | capped by default |
| `composableFromEnd<T>(capWidth) { }` / `composableFromEndArgs` | drill-in: slides from the end | capped by default |
| `composableFromBottom<T>(capWidth) { }` / `composableFromBottomArgs` | modal: rises from the bottom | capped by default |

`pushTransition` / `popTransition` in `NavDestinations.kt` read each entry's family and `tabRoot`
flag: a tab root fades, a drill-in slides in from the end and back out to it, a modal rises and
drops, and the screen behind steps back with a slight scale. The predictive-back gesture scrubs
the pop motion. On large screens (`NavTransitionTier.isLargeScreen`) the slides become short
shared-axis nudges.

A route the registry does not know fails the first time it is opened, unless the front end set
`unavailable { }` (the desktop does, since it lacks Health Connect, the QR camera, ...). Use
`canOpen(route)` to hide an entry point to a screen this front end may not have.

## Reading the current route

`nav.currentRoute` reads the top of the snapshot-backed stack. Read it directly, as the rail does
to pick the selected item. Where only a yes/no is needed, wrap it in `derivedStateOf` so the
caller recomposes only when the answer changes:

```kotlin
val onNotifications by remember(nav) { derivedStateOf { nav.currentRoute is Route.Notification } }
```

Outside composition (intent handlers), use `isBaseRoute<Route.X>(nav)` or
`getRouteWithArguments(Route.X::class, nav)`.

## Intents (Android)

`NavigateIfIntentRequested` in `AppNavigation.kt` (called from `AndroidAppRoot`) turns
`ACTION_VIEW`, `ACTION_SEND` / `SEND_MULTIPLE` and NFC intents into `newStack(...)` calls. It
skips the navigation when the screen on top is already that route, and leaves composers that take
re-delivered shares in place (`consumesSharesInPlace`) alone.

## Testing

The back stack is plain state, so tests drive `NavBackStacks` and `Nav` with `runTest`, no
Compose and no mocks: `NavBackStacksTest` (commons `commonTest`), and `NavBottomBarStackTest`,
`NavDrawerTest`, `NavImeSettleTest`, `NavDestinationsTest` (commonsUI `commonTest`, package
`com.vitorpamplona.amethyst.commons.ui.navigation`).
