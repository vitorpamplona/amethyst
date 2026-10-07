# Android Navigation (Navigation 3)

Amethyst runs on JetBrains' multiplatform build of Navigation 3
(`org.jetbrains.androidx.navigation3:navigation3-ui` plus
`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3`). The app owns the back
stack as plain snapshot state, and `NavDisplay` renders it. There is no `NavController`, no
`NavHost` and no navigation graph.

## Files

| File | Role |
|------|------|
| `commons/.../model/navigation/Routes.kt` | `@Serializable sealed class Route`: every destination and its arguments |
| `commons/.../model/navigation/NavBackStacks.kt` | `NavStackEntry` and `NavBackStacks`: the back stack and its rules (headless) |
| `commonsUI/.../ui/navigation/navs/Nav.kt` | `Nav`: the shared `INav` over `NavBackStacks`, plus `LocalNavStackEntry` |
| `commonsUI/.../ui/navigation/navs/RememberNavs.kt` | `rememberNav()`: saves the back stack across config changes and process death |
| `commonsUI/.../ui/navigation/navs/ImeSettler.kt` | Waits for the keyboard to close before every navigation |
| `commonsUI/.../ui/navigation/host/NavDestinations.kt` | `NavDestinations` registry, the builders, and the transitions |
| `commonsUI/.../ui/navigation/host/NavigationHost.kt` | `NavigationHost`: the `NavDisplay` every front end shows |
| `commonsUI/.../ui/navigation/routes/RouteNavController.kt` | `isBaseRoute<T>(nav)`, `getRouteWithArguments`, `consumesSharesInPlace` |
| `amethyst/.../ui/navigation/AppNavigation.kt` | Android: `BuildNavigation`, `appDestinations` (every screen), screen time, intents |

## The back stack

`NavBackStacks` holds two pieces of state:

- `stack`: what is on screen, bottom to top. It is never empty and starts at `Route.Home`.
- `savedTabs`: the root entry of every bottom-bar tab the user left, keyed by its full route.

Each `NavStackEntry` has a unique `id`. Its `contentKey` (`"nav-$id"`) keys the screen's
`rememberSaveable` state and its ViewModelStore, so the same route opened twice is two
independent screens. Two flags live on the entry as snapshot state:

- `tabRoot`: reached from the bottom bar or the rail. It has no back arrow, and tab switches fade.
- `drawerRoot`: opened from the navigation drawer. It can pop, but it keeps the bottom bar.

Operations (each one in `Nav` runs after `ImeSettler.settle()`):

| `INav` call | `NavBackStacks` | Effect |
|-------------|-----------------|--------|
| `nav(route)` | `push` | Pushes `route`, unless it is already on top |
| `navDrawer(route)` | `push(drawerRoot = true)` | Same push, marked as a drawer screen |
| `newStack(route)` | `newStack` | Drops the latest copy of `route` and everything above it, then shows it once. A same-class top is replaced and keeps its flags |
| `popUpTo(route, klass)` | `popUpTo` | Drops the latest `klass` entry and everything above it, then pushes `route` |
| `navBottomBar(route)` | `switchTab` | Drops pushes above the current tab root. Keeps the left tab root in `savedTabs`, then restores `route`'s saved entry or opens it fresh |
| `popBack()` | `pop` | Pops the top. The last entry is never popped |

`Route.Home` stays at the bottom, so back from any tab returns to Home and back from Home leaves
the app. Nothing above a tab root is ever saved, so returning to a tab always lands on the tab
itself.

## Rendering

`NavigationHost` passes `stack + savedTabs` to `rememberDecoratedNavEntries` with the
saveable-state and ViewModel-store decorators. Saved tabs therefore keep their state while they
are out of sight. Only `stack` goes to `NavDisplay`. When an entry leaves both lists, its saved
state and ViewModels are cleared.

Each entry provides `LocalNavStackEntry`. `INav.canPop()` and `showsBottomBar()` read it, so
during a predictive-back swipe a screen that is leaving keeps its own back arrow.

## Destinations

Register each screen once in `appDestinations`. The builder decides the motion and whether the
screen is capped to the reading column on wide panes:

| Builder | Motion | Cap |
|---------|--------|-----|
| `composableCapped<T> { }` / `composableCappedArgs<T> { route -> }` | fade | capped |
| `composable<T> { }` | fade | full width |
| `composableArgs<T>(capWidth) { route -> }` | fade | capped by default |
| `composableFromEnd<T>(capWidth) { }` / `…Args` | drill-in: slides from the end | capped by default |
| `composableFromBottom<T>(capWidth) { }` / `…Args` | modal: rises from the bottom | capped by default |

`pushTransition` and `popTransition` in `NavDestinations.kt` read each entry's family and
`tabRoot` flag:

- A tab root fades instead of sliding.
- A drill-in slides in from the end and back out to it.
- A modal rises and drops.
- The screen behind steps back with a slight scale.

The predictive back gesture runs the pop motion, scrubbed by the finger. On large screens
(`NavTransitionTier.isLargeScreen`) the slides become short shared-axis nudges.

## Reading the current route

`nav.currentRoute` is snapshot state. Read it directly, as the rail does to pick the selected
item. Where only a yes/no is needed, wrap it in `derivedStateOf` so the caller recomposes only
when the answer changes:

```kotlin
val onNotifications by remember(nav) { derivedStateOf { nav.currentRoute is Route.Notification } }
```

Outside composition (intent handlers), use `isBaseRoute<Route.X>(nav)` or
`getRouteWithArguments(Route.X::class, nav)`.

## Intents

`NavigateIfIntentRequested` in `AppNavigation.kt` turns `ACTION_VIEW`, `ACTION_SEND` and NFC
intents into `newStack(...)` calls. It skips the navigation when the screen on top is already
that route. Composers that take re-delivered shares in place (`consumesSharesInPlace`) are left
alone.

## Testing

The back stack is plain state, so tests drive `NavBackStacks` and `Nav` directly with
`runTest`. They need no Compose and no mocks. See `NavBackStacksTest`, `NavBottomBarStackTest`,
`NavDrawerTest` and `NavImeSettleTest` (commons and commonsUI `commonTest`).
