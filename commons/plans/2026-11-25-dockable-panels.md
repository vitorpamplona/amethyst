# Dockable panels: tool windows for the shared shell

Status: **Built (desktop and shared UI), 2026-11-25, phases 0 to 3 and 5.** Android gets the same
dock from the shared shell, with the notification feed as its only panel and no windows. The
emulator panel (phase 4) moved to a separate effort and is not on this branch; the dock keeps
`PanelId.DEVICES` and its Ctrl/Cmd+Shift+3 shortcut for it. Checked under a real window manager;
see "Checked after the first cut" below. Not run: macOS, Windows, an Android device.

## What was built

- **Model** (`commons/…/model/dock/`): `DockLayout` (columns of slots, slots of tabs; open,
  folded or in a window; overlay mode per panel), `DropTarget`, `DockView`, and
  `DockLayoutPreferences` (device-wide JSON in DataStore, migrating the old notification
  panel's width and fold). The limits are **three columns of three slots**: the strip shows a
  panel that cannot fit anyway, so a looser cap costs nothing and lets a phone-shaped device
  panel take its own column.
- **Fitting:** `fitPanes` in `ScreenLayout.kt`; `fitPaneWidths` now delegates to it.
- **Shell** (`commonsUI/…/navigation/dock/`): `DockController` (session state, every panel's
  content built once as movable content with its own ViewModels and saved state), `DockColumns`
  with column and slot splitters, `PanelHeader` with tabs, the panel menu and the put-away
  button, `DockStrip` with badges, `DockOverlayLayer` (Overlay and Maximize), `DockDragState` +
  `dockHitTest` + `DockDragOverlay` (drag and drop), `PanelWindows`, and `DockRailItems` for
  railed windows. `MultiPaneShell` draws it in place of the notification panel.
  `PanelFreeRoutes` became `FullWidthRoutes` (the dock folds to its strip there) plus each
  panel's own `hidesOn`.
- **Panels:** `NotificationsDockPanel` (shared) and `AssistantDockPanel` (desktop, through
  `AppRoot.dockPanels`), plus any screen pinned as a panel (`ScreenDockPanel`). Settings' "ask the
  assistant" goes to the panel.
- **Windows:** `AppRoot.PanelWindow` / `supportsPanelWindows` / `mainWindowScreenOrigin`; on the
  desktop `DesktopAppRoot.PanelWindow`, `DesktopWindowManager.initialState(PanelWindowSpec)` and
  `panelWindowDrag` (drag a panel window's header over the main window to dock it).
- **Keyboard and menus (desktop):** Ctrl/Cmd+Shift+1/2 toggle the notification and assistant
  panels (3 is kept for a device panel), Ctrl/Cmd+Shift+\ hides or restores all, Shift+Esc folds
  the focused panel, Esc closes an overlay; a *Panels* menu in the menu bar with *Pin this screen
  as a panel* and the saved layouts.

## Phase 5 (2026-11-25)

- **A quieter strip.** 40 dp wide, 30 dp buttons with 18 dp icons in `onSurfaceVariant` at 70%: no
  filled pill, only a faint wash for the panel showing and under the pointer, and a soft flash
  when something waits. Badges are a 6 dp dot or a 9 sp count. At its foot, two quieter buttons:
  pin this screen, and the layouts menu.
- **Beside the deck.** The deck no longer takes the dock's place: the main screen, the deck and
  the dock share the width, the dock giving way first (`fitPanes` keeps the main screen's
  420 dp, the deck's toolbar and its first column whole, and folds dock columns past that). A
  workspace with a Notifications column hides the notification panel, which would repeat it.
- **Any screen as a panel.** *Pin this screen as a panel* (strip, a tab's menu, the Panels
  menu) adds a `screen:<uuid>` panel whose route is stored in `DockLayout.screens` as JSON. It
  opens under the first column's panels while there is a slot free, else in a column of its own,
  else as a tab. It runs the screen on its own back stack (`ScreenPanel`: a `Nav` over
  `NavBackStacks`, links opening in the main screen as deck columns do), takes the screen's
  title and icon (`routeTitle`, `routeIcon`), and has *Remove from the panels* in its menu.
  Pinning a route that is pinned already shows that panel.
- **Named layouts.** *Save this layout…* keeps the dock as it stands under a name, pinned
  screens included; the layouts menu restores or forgets one. Stored device-wide next to the
  layout (`ui.dock.saved`), each layout as its own JSON, so one that no longer reads is dropped
  alone.

## Checked after the first cut (2026-11-25)

- **A panel keeps its state everywhere.** Saved UI state (rememberSaveable) is kept by the dock per
  panel and handed from one place to the next; a panel window draws the same movable content
  object as the dock, so the saved values are found under the same keys, after the dock has let
  go (a lease: one place draws a panel at a time). Checked: the notification panel's open chart
  and the assistant's half-typed message survive a pop-out and a dock-back
  (`DockPanelStateUiTest`, and by hand). The assistant's draft also lives on its chat
  (`ClaudeChat.unsent`), so a tab or a later visit shows it too.
- **Window managers.** Under openbox (a real window manager, title bars and all) in the headless
  container: dragging a panel out of the main window opens its window where it was dropped, kept
  whole on that screen; dragging that window's header over the main window docks it again.
  macOS and Windows were not run.
- **The emulator panel** was run against a real emulator (token and JWT access, frames, touches
  in all four rotations, keys) before it moved to its own effort; that code and its findings
  (`emulatorClient/README.md`) are in this branch's history, commit `8e4a3057`.

## Goal

The space right of the main screen becomes a **dock** that holds several panels at once, the
way Android Studio's right side holds Notifications, Gemini and Running Devices. The user can:

- show the **notification feed**, the **Amethyst assistant** and, on the desktop, an **Android
  emulator** together on the right, split between them;
- resize each panel, fold it to an icon, drag it to another place in the dock, pop it out into
  its own window and put it back, without losing its state;
- save the arrangement and get it back after a restart.

Phones don't change. The dock only exists where the notification panel docks today
(`ScreenLayoutSpec.hasRoomForNotificationPanel`, 1,200 dp and up).

## How Android Studio does it

Android Studio's window management is IntelliJ's. It splits the frame into two kinds of area,
and the rules for each differ.

**The editor area (centre).** It holds documents in tabs. You can split it right or down, and
drag a tab to an edge of the area to split there. Drag a tab out of the frame and it becomes its
own window. Drag it back over a tab strip and it goes back in. This is what our tabs layout and
deck already are (`commons/plans/2026-11-04-tabs-and-windows-layouts.md`).

**Tool windows (the sides and the bottom).** Project, Logcat, Gemini, Running Devices and the
rest are singletons, each anchored to the left, the right or the bottom:

- **Tool window bars.** Each side has a bar of icons. Clicking an icon shows or hides that tool
  window, and the bar stays visible when every window is closed. Icons carry badges (problem
  counts, unread notifications). Right-clicking an icon opens a menu to hide or move it, and
  dragging an icon to another bar or group moves the window.
- **Two groups per side.** Each bar has a top group and a bottom group (left and right on the
  bottom bar). One window from each group can be open at a time. When both are open they split
  that side, with a divider between them, so a side shows at most two windows.
- **Content tabs inside a window.** One tool window can hold several views as tabs: Terminal
  sessions, Run configurations, or one Running Devices tab per device.
- **View modes**, set from the header's options menu or *Window › Active Tool Window › View Mode*:
  - *Dock Pinned* (the default): stays open beside the editor.
  - *Dock Unpinned*: docked, but hides when it loses focus.
  - *Undock*: slides over the editor while it is active and hides when another window becomes
    active.
  - *Float*: a separate window owned by the IDE frame, which can go to another monitor.
  - *Window*: a full OS window of its own, independent of the frame.
- **Sizing and focus.** Dragging the edge resizes. The *Stretch to* shortcuts
  (Ctrl+Alt+Shift+arrows) resize from the keyboard. Esc sends focus back to the editor,
  Shift+Esc hides the active window, F12 returns to the last one, and Ctrl+Shift+F12 hides or
  restores them all.
- **Layouts.** The anchor, group, order, size, split weight, view mode and floating bounds of
  each tool window are saved per project. *Window › Layouts* saves the current arrangement
  under a name and restores it or the default.

**Running Devices**, the emulator inside the IDE, is a tool window with one content tab per
device. The emulator runs without a window of its own. Studio talks to it through the
emulator's gRPC `EmulatorController` service: `streamScreenshot` sends a new frame whenever the
display changes, and mouse, touch and key events go back the same way. The service is off by
default and turned on from the emulator's command line. Physical devices are mirrored by a
video stream from an agent the IDE pushes to the phone.

### What carries over, and what doesn't

| Android Studio | Amethyst |
|---|---|
| Editor area: tabs, splits, drag a tab out to a window | Already built: the main pane, the Tabs and Windows layouts, deck columns, `ScreenWindows` |
| Left tool window (Project) | The docked drawer. It stays as it is |
| Right tool windows: Notifications, Gemini, Running Devices | **New:** the dock, with the notification feed, the assistant and the device panel |
| Tool window bar with badges | **New:** the dock's strip. `CollapsedNotificationPanel` is already a one-icon version of it |
| Two groups per side, split by a divider | Generalised: up to two **columns** on the right, each split into up to three **slots** stacked top to bottom. Three panels were asked for, and a phone-shaped emulator needs a tall column of its own |
| Content tabs in a tool window | Panels dropped onto the same slot share it as tabs |
| Dock Pinned / Unpinned / Undock | **Docked**, **Folded** (an icon in the strip, as the notification panel folds today) and **Overlay** (slides over the main screen, closes when you click outside; also how narrower windows get panels) |
| Float / Window | **Window**: the panel in its own OS window, on the desktop. Float is a Window that stays on top, kept as an option |
| Maximize, hide all | *Maximize* (the panel takes the main screen's place until restored) and *Hide all panels* |
| Layouts per project | Device-wide, like pane widths; named layouts join the deck's workspaces (a decision below) |

The bottom dock is left out on purpose. A wide window has room to spare across, not down, and
every Amethyst screen is a column.

## What exists today (survey)

| Piece | Where | Use in this plan |
|---|---|---|
| `MultiPaneShell`: drawer, centre and notification panel in a `Row`; `PaneSplitter`s between them; the fold animation; `PanelFreeRoutes` | `commonsUI/…/navigation/shell/AppShellLayout.kt` | **Extended.** The notification panel's code becomes the general dock. The fold animation and `widthShare` are kept as they are |
| `fitPaneWidths`, `MIN_CENTER_PANE_DP`, the width ranges | `commonsUI/…/layouts/ScreenLayout.kt`, tested by `FitPaneWidthsTest` | **Generalised** to a list of panes in narrowing order. The two-pane case must give the same numbers, so the existing tests stay green |
| `PaneWidthPreferences` (device-wide, debounced DataStore) and `NotificationPanelCollapsePreferences` | `commons/…/model/preferences/` | **Replaced** by `DockLayoutPreferences`, with a one-time migration: the notification width and fold carry over |
| `CollapsedNotificationPanel` (the badged bell strip, 48 dp) | `commonsUI/…/notifications/NotificationSidePanel.kt` | **Becomes the strip**: one icon per panel instead of one bell |
| Deck: `DeckLayout` (JSON, drops columns it can't decode, named `DeckWorkspace`s), `DeckArea` (per-column back stack, ViewModel store, emoji panel, resize edge) | `commons/…/model/deck/`, `commonsUI/…/navigation/deck/` | **The template** for panels' own state and for persistence. Workspaces are the precedent for named layouts |
| `ScreenTabStrip` drag-to-reorder, with its own slop (Compose's mouse slop of about 2 px turned wobbly clicks into drags) | `commonsUI/…/navigation/tabs/ScreenTabStrip.kt` | **Reused** for the panel header and strip-icon drags |
| `ScreenWindows`, `ScreenWindowSpec`, `Nav.hostedEntries` (a moved-out tab is drawn by its window and keeps its ViewModels and draft) | `commonsUI/…/navigation/windows/ScreenWindows.kt`, `…/host/NavigationHost.kt` | **The mechanism** for popping a panel out and back without losing state |
| `DesktopWindowManager` (placement beside the opener, saved size per kind, focus), `ScreenWindowSizes` | `desktopApp/…/desktop/app/` | **Reused** for panel windows. Gains the main frame's screen bounds, for dropping a panel back in |
| `AppRoot.ScreenWindow` | `commonsUI/…/app/AppRoot.kt` | **Reused.** Android doesn't draw windows yet, so there a panel can't be popped out |
| Assistant: `Route.ClaudeCodeSettingsAssistant`, `ClaudeCodeSettingsAssistantScreen`, `BoundToolServer`, `ScreenToolServer.what_is_on_screen` over `OnScreenNavs` | `commons/…/navigation/Routes.kt`, `commonsUI/src/jvmMain/…/claudecode/` | **Hosted in a panel.** The assistant plan already says it belongs beside the screen (`2026-11-24-amethyst-assistant.md`, third slice) |
| `Command` shortcuts | `desktopApp/…/app/DesktopMenuBar.kt` | **Extended.** Ctrl/Cmd+Shift+digit is free (Alt+digit is the deck's focus, Ctrl/Cmd+digit the sections) |
| Emulator or device streaming | none | **New**, desktop only (see the device panel below) |

**Duplicates to avoid:** don't write a second splitter, a second width store, a second window
manager or a second "keep the state while it moves" scheme. A panel is a deck column with fixed
chrome and a fixed place, and a popped-out panel is a moved-out tab.

## Design

### Model (headless, `commons/…/model/dock/`)

```kotlin
@Serializable enum class PanelKind { NOTIFICATIONS, ASSISTANT, DEVICE }   // ROUTE later: any screen pinned as a panel

@Serializable data class PanelId(val kind: PanelKind, val arg: String? = null)  // DEVICE: the emulator's serial

@Serializable enum class PanelMode { DOCKED, FOLDED, OVERLAY, WINDOW }

@Serializable data class DockSlot(val panels: List<PanelId>, val active: PanelId, val weight: Float = 1f)
@Serializable data class DockColumn(val slots: List<DockSlot>, val width: Float? = null)  // dp; null = the kind's default

@Serializable data class DockLayout(
    val columns: List<DockColumn>,          // left to right; at most 2
    val modes: Map<PanelId, PanelMode>,     // a panel not in `columns` is FOLDED or WINDOW
    val strip: List<PanelId>,               // the strip's icon order
) {
    fun move(id: PanelId, to: DropTarget): DockLayout
    fun fold(id: PanelId): DockLayout
    fun popOut(id: PanelId): DockLayout
    fun dockBack(id: PanelId): DockLayout   // to where it was before it left
    fun resizeColumn(index: Int, width: Float): DockLayout
    fun resizeSlot(column: Int, slot: Int, weight: Float): DockLayout
    // encode / decode: drops panels whose kind no longer decodes, like DeckLayout
}

sealed interface DropTarget {
    data class Tab(val column: Int, val slot: Int) : DropTarget                  // share a slot
    data class Split(val column: Int, val slot: Int, val below: Boolean) : DropTarget
    data class NewColumn(val index: Int) : DropTarget
    data object Strip : DropTarget                                               // fold
    data object Window : DropTarget                                              // dropped outside the frame
}
```

- A pure, immutable value, like `DeckLayout`, so every move is a unit test.
- Limits: two columns, three slots per column. A drop that would break a limit isn't offered
  (its zone doesn't light up).
- An empty slot or column is removed. A panel the platform can't show (no assistant without a
  backend, no device panel on Android) stays in the model and is left out when drawing, so a
  layout moved between machines doesn't lose it.
- **Default:** one column with the notification feed alone, which is exactly today's panel.
  The assistant and the device panel start folded in the strip.
- **Stored** by `DockLayoutPreferences`: device-wide, JSON in DataStore, debounced like
  `PaneWidthPreferences`. A dock is about the monitor, not the account. Migration: the stored
  notification width becomes the column's width, and a folded notification panel becomes
  `FOLDED`.

### Fitting the widths

`fitPaneWidths` becomes `fitPanes(windowWidth, panes)`, with the panes listed in the order they
give up room: the outer dock column, then the inner one, then the drawer. The centre keeps
`MIN_CENTER_PANE_DP` (480). The strip's 48 dp is reserved first, as the folded panel's is now.
When a column can't fit even at its minimum, it folds into the strip for as long as the window
is that narrow. The stored layout doesn't change, so it comes back when the window widens. The
choice the user made is never rewritten by a narrower window, the same rule as today.

At 1,440 dp with the drawer at 300, the dock gets 660: two columns of 360 and 300. At 1,200 dp
it gets 420: one column, and the second folds to the strip.

Within a column, slots share the height by `weight`, with at least 160 dp each. Dividers between
slots are horizontal `PaneSplitter`s.

### Drawing (`commonsUI/…/navigation/dock/`)

- `DockArea(layout, …)` replaces the notification branch of `MultiPaneShell`: a `PaneSplitter`,
  then the columns, each a `Column` of slots, then `DockStrip` at the far right edge.
- `DockSlot` draws a 32 dp header (the panel's title, or tabs when several share the slot, plus
  a ⋮ menu) and the active panel under it.
- `DockStrip` draws one icon per panel with its badge: unread notifications, an approval
  waiting in the assistant, a device that is running. Clicking an icon shows a folded panel
  where it last docked, and clicking it again folds it. Right-clicking gives the same menu as
  the header.
- **Header menu:** *Move to* (top, bottom, new column, other column), *View mode* (Docked,
  Overlay, Window), *Maximize*, *Fold*, *Hide*. The whole layout can be done from menus, so
  dragging is a shortcut and keyboard and screen-reader users lose nothing.
- **Panels keep their state wherever they draw.** Each panel runs under a `PanelHost` owned by
  the shell, not by its slot. It gets its own `ViewModelStoreOwner`, a `SaveableStateHolder`
  entry keyed by `PanelId`, and an `EmojiPanelHost`, as deck columns do. Inside the main window
  the content is `movableContentOf`, as `AppShellLayout` already does for the main screen, so
  moving between slots keeps even plain `remember`. A popped-out panel is drawn by its window
  the way a moved-out tab is (`hostedEntries`): it keeps its ViewModels and saved state. The
  long-lived parts, the assistant's session and the emulator's stream, live in services or
  ViewModels, never in `remember`.
- **Panels that step aside.** `PanelFreeRoutes` splits in two. Each panel names the routes it
  duplicates (the notification panel hides on `Route.Notification`). Screens that want the whole
  width (the paper, napplets, the browser, Claude Code) fold the dock to its strip instead of
  hiding it outright, so the assistant is one click away beside a coding session. It still
  happens at once, not animated, for the reason in `MultiPaneShell`: Chromium pages draw off
  screen and stretch while their pane changes size.
- **The deck.** Today the deck takes the notification panel's place. In the first phases it
  keeps doing so: with the deck on, the dock shows only its strip, and panels open as Overlay
  over the deck. Moving a panel into a deck column and back is phase 5.

### Dragging

- **What you can drag:** a slot's header (or one of its tabs), and a strip icon. The press
  becomes a drag past the same slop `ScreenTabStrip` uses.
- **While dragging:** a `DockDragState` held by the shell keeps the panel and the pointer.
  Every slot, the strip and the dock's edges register their bounds in a `DropZoneRegistry`
  (`onGloballyPositioned`, in the window's coordinates). An overlay draws the panel's title as
  a ghost and highlights the zone under the pointer: the middle of a slot means share it as a
  tab, its top or bottom quarter means split above or below, the left or right edge of a
  column means a new column, and the strip means fold. Esc cancels.
- **Out of the frame:** releasing outside the main window opens the panel in a window at that
  spot (desktop only). This needs Compose on the desktop to keep reporting a drag to the
  component that took the press after the pointer leaves the window, as AWT's drag grab should.
  Phase 3 checks that first; if it doesn't hold, *View mode › Window* stays the way out, and dragging stays inside the frame.
- **Back in:** a panel window has a slim header of its own with a *Dock* button, which puts it
  back where it was. Dragging that header over the main window lights up the same drop zones:
  `DesktopWindowManager` checks the pointer's screen position against the main frame's bounds
  and passes the drag on to that window's `DockDragState`. Closing a panel window folds the
  panel, and its mode stays Window, so showing it again opens the window again. That's how
  IntelliJ treats a closed tool window.

### Narrow windows and Android

- **Rail tier, or under 1,200 dp:** no docking. The strip isn't drawn. Its icons go to the rail,
  and a panel opens as Overlay: a sheet from the right edge, `NotificationPanelWidth` wide,
  closed by clicking outside or pressing Esc. A wide portrait tablet gets the assistant this
  way.
- **Phones:** unchanged. Notifications stay a bottom-bar section, and the assistant stays a
  screen.
- **Android at 1,200 dp and up** (landscape tablets, Android on laptops) gets the same dock,
  since it is shared UI, but no Window mode until the Android windows phase of the tabs plan
  (`ScreenActivity`). The device panel is desktop only.

### Keyboard (desktop)

Ctrl/Cmd+Shift+1, 2 and 3 show, focus or fold the notification, assistant and device panels.
Shift+Esc folds the focused panel. Ctrl/Cmd+Shift+\ hides or restores every panel. Esc in a
panel sends focus back to the main screen, unless a text field or menu takes it first, the same
rule the tabs plan chose. A *View › Panels* menu lists them with their shortcuts.

## The three panels

### Notifications

`NotificationSidePanel` moves into the dock unchanged. Its `onCollapse` becomes `fold`. The
bell's badge (`collapsedAt`) becomes the strip's badge. The fold animation carries over to every
panel.

### Assistant

- The panel draws the assistant's chat, the content of `ClaudeCodeSettingsAssistantScreen`,
  without its top bar. It is offered when the destinations have the assistant, the check
  `AllSettingsScreen` already makes.
- Its `BoundToolServer` binds to the **main window's** `Nav`, not the panel's own. That way
  `what_is_on_screen` and `screenshot` describe the screen beside the panel, which is what the
  assistant plan's third slice is waiting for.
- *Ask the assistant* in Settings opens the panel and sends the question there, when the dock
  is available. Otherwise it opens a tab, as it does now.
- An approval card waiting while the panel is folded sets the strip's badge and flashes it once,
  so a write or a payment never waits unseen.

### Device (Android emulator, desktop only)

*Moved to a separate effort; not on this branch. Kept here as the design that was reviewed.*

- **A new JVM module, `emulatorClient`**, beside `claudeCodeClient`, with no Amethyst or Nostr
  dependencies. It finds the SDK, lists AVDs, starts one without a window and with gRPC on, and
  finds running emulators and their gRPC port and token through the emulator's discovery
  files. The exact flags and file locations are checked against the installed emulator's
  `-help` in phase 4, not taken from here. The client speaks `EmulatorController`:
  `streamScreenshot` for frames, and `sendMouse`, `sendTouch` and `sendKey` for input.
- **A `DeviceStreamService` in `commons/jvmMain`** holds one stream per device for the whole
  process. The panel subscribes while it is drawn, so moving or popping it out never drops the
  connection. It asks for frames at the panel's size in pixels. While a splitter is being
  dragged it scales the last frame and asks for the new size only once the drag stops; a stream
  asked for a new size every frame would stall, as Chromium did.
- **The panel** (`commonsUI/src/jvmMain`) draws the frame letterboxed at the device's aspect
  ratio. It turns pointer events into touches, the wheel into scrolls, and keys into key
  events. Its header has rotate, back, home and overview buttons, a device picker, and *Start*
  for a stopped AVD. Each running device is a tab in its slot, as in Running Devices.
- **Where it comes from:** the Claude Code sessions already build this repository. A *Run on
  device* action on a session (install the debug APK with `adb`, then launch it) is the natural
  next step, but it is not part of this plan.
- **Licensing, verified before wiring in** (AGENTS.md): grpc-java or grpc-kotlin and protobuf,
  and `emulator_controller.proto` from AOSP, which is understood to be Apache-2.0 and BSD and
  must be read from the actual LICENSE files. Mirroring physical devices needs an H.264
  decoder. FFmpeg builds are LGPL at best (a WARN), and GPL builds are a STOP, so that part is
  left out of this plan.
- The panel is generic: a napplet, the cart player or the browser could become a panel of the
  same kind later (the `ROUTE` panel kind).

## Phasing

| Phase | Ships | Module(s) | Done when |
|---|---|---|---|
| 0 | `DockLayout` + tests, `fitPanes` (with `FitPaneWidthsTest` unchanged and green), `DockLayoutPreferences` with the migration. The notification panel drawn through `DockArea` as the only panel | commons, commonsUI | No visible change. A headless run at 1440×900 looks like today; the stored width and fold carry over |
| 1 | The strip, the assistant panel, slots split top and bottom, the header menu, Overlay mode, the rail icons below 1,200 dp, keyboard shortcuts | commonsUI, desktopApp | Headless run at 1440×900 shows notifications over the assistant; at 1100×900 the assistant opens as Overlay; `what_is_on_screen` from the panel names the main screen |
| 2 | Dragging inside the frame: drop zones, tabs in a slot, a second column, reordering the strip | commonsUI | Drag tests with `DockDragState`; screenshots of each drop zone highlighted |
| 3 | Window mode: pop out by menu or by dragging out of the frame, dock back by button or by dragging in, sizes and places saved per panel | commonsUI, desktopApp | Pop out the assistant mid-conversation and back: same session, same draft. Tested by hand under a real window manager (Xvfb has none) |
| 4 | `emulatorClient`, `DeviceStreamService`, the device panel | new module, commons, commonsUI | Moved to a separate effort |
| 5 | Panels and deck columns side by side; *Pin as panel* for any screen; named layouts | commons, commonsUI | Done: `DockLayoutTest`, `DockLayoutPreferencesTest`, and a headless run (pin, save, reset, restore, restart, deck on) |

Phases 0 and 1 are worth shipping alone. Phase 1 already gives the requested split of the right
side between notifications and the assistant.

## Testing

- **Headless, no Compose:** `DockLayoutTest` covers every `DropTarget`, the limits, removing
  empty slots and columns, `dockBack` returning a panel where it was, decoding an unknown kind,
  and the migration. `FitPanesTest` covers narrowing order, folding a column that can't fit,
  and giving it back when the window widens.
- **Compose UI tests (`commonsUI` jvmTest):** the strip's badges, folding and unfolding, the
  header menu's moves, and a drag over each zone driving `DockDragState`. Write them the way
  `NotificationSidePanelTest` is written.
- **Run the app** (`tools/desktop-run/run-headless.sh`): 1440×900 with two panels split, 1920×1080
  with two columns, 1100×900 with Overlay, and 480×900, which must look exactly as it does
  today. Screen windows overlap under Xvfb, which is enough to check titles and content but not
  placement.

## Decisions for the maintainer

1. **Three panels in one column, or columns?** The proposal allows two columns of up to three
   slots. That's more than IntelliJ's two groups per side, because a phone-shaped emulator
   squashed under two other panels is unusable. The simpler choice is IntelliJ's rule: one
   column, two slots.
2. **Device-wide or per account?** The proposal is device-wide, like pane widths, since the dock
   follows the monitor. Named layouts could instead join the deck's per-account workspaces.
3. **Full-width screens:** should they fold the dock to its strip (proposed) or hide it
   completely, as they hide the notification panel now?
4. **The deck:** settled in phase 5: the dock shares the width with the deck and gives way first.
5. **Emulator scope:** for the separate effort that now owns the device panel.
