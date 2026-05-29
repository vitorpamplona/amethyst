---
title: "feat: Desktop Visual Personality Overhaul"
type: feat
status: active
date: 2026-05-28
origin: docs/brainstorms/2026-05-27-desktop-visual-personality-brainstorm.md
---

# feat: Desktop Visual Personality Overhaul

## Enhancement Summary

**Deepened on:** 2026-05-28
**Research agents used:** compose-expert, desktop-expert, compose-stability-diagnostics, compose-modifier-and-layout-style, best-practices-researcher, color-audit-explorer

### Critical Fixes (from research)
1. **`ColorScheme.isLight` doesn't exist in M3** — use `LocalIsDarkTheme` CompositionLocal instead
2. **Hover modifier won't compile** — `@Composable` lambda can't invoke in `drawBehind`; `shape.topStart` invalid on generic Shape. Rewrite using `onPointerEvent` (codebase convention)
3. **Sidebar animation thrashes column widths** — `fitColumnsToWidth()` fires every frame during 240→56dp transition. Add 300ms debounce
4. **NoteCard/NoteCardSkeleton missing `modifier` parameter** — required for reusable composables
5. **AccountSwitcher dropdown offset hardcoded to 48dp** — needs dynamic offset based on sidebar width

### Key Improvements
- Use `onPointerEvent` for hover (project convention, not `composed{}`)
- Add `clipToBounds()` on sidebar during animation
- Add keyboard shortcuts: `Cmd/Ctrl+B` (sidebar toggle), `Cmd/Ctrl+K` (search focus)
- Gate SegmentedButton on column width > 400dp
- `remember` BorderStroke to avoid instance churn defeating skip optimization
- ShimmerPlaceholder in `commons/commonMain` (pure M3, no platform APIs)
- Use `LocalScrollbarStyle` for scrollbar customization (built-in API)
- Add `LocalIsDarkTheme` CompositionLocal alongside `LocalSpacing`
- SegmentedButton confirmed available and stable (already used in AppDrawer.kt)
- 32 inline `Color()` literals to migrate across 10 files

### Color Audit Results
- **32 inline Color() constructors** across 10 desktop files (status indicators: green/red/amber)
- **48 `.copy(alpha=)` patterns** across 18 files (standardize with alpha constants)
- **Key files:** DevSettingsSection (10), LoginProgressSteps (5), NamecoinSettingsSection (4), TorStatusIndicator (3)

## Overview

Transform Amethyst Desktop from an OS-native-adaptive look into a distinctly branded experience. Replace per-OS color schemes with a unified Amethyst identity (cyan/blue accent), redesign the sidebar to 240dp with labels, restyle cards to flat+border, add hover effects, skeleton loading, and polish all UI components.

Desktop-only scope — Android unchanged. (see brainstorm: Decision #5)

## Problem Statement

The current desktop theme adapts to each OS (macOS, GNOME, KDE, Windows) with per-OS colors, shapes, and fonts. While technically impressive, this makes the app visually neutral — it doesn't feel like "Amethyst." The 56dp icon-only sidebar wastes desktop screen space and hurts discoverability. Cards use shadow elevation that renders inconsistently on desktop JVM/Skia.

## Proposed Solution

A 6-phase implementation that progresses from theme foundation → spacing → sidebar → cards → column headers → polish. Each phase builds on the previous, and the app remains functional throughout.

## Technical Approach

### Architecture

The overhaul touches 3 layers:

1. **Theme layer** (`desktopApp/.../platform/`) — ColorScheme, Typography, Shapes
2. **Layout layer** (`desktopApp/.../ui/deck/`) — Sidebar, ColumnHeader, DeckLayout
3. **Component layer** (`desktopApp/.../ui/note/`, shared composables) — NoteCard, action bar, dialogs

New additions:
- `AmethystSpacing` CompositionLocal for design tokens
- `Modifier.hoverHighlight()` shared hover utility
- `ShimmerPlaceholder` composable for loading states

### Implementation Phases

---

#### Phase 1: Theme Foundation

**Goal:** Replace all per-OS color schemes with unified Amethyst brand. Unified shapes and typography weights.

**Files:**

| File | Action |
|------|--------|
| `desktopApp/.../platform/PlatformColorScheme.kt` | Replace 10 per-OS functions with `amethystLight()` + `amethystDark()` |
| `desktopApp/.../platform/PlatformShapes.kt` | Replace per-OS shapes with unified tokens |
| `desktopApp/.../platform/PlatformTypography.kt` | Standardize weight scale across all OS |
| `commons/.../ui/theme/Colors.kt` | Add cyan/blue brand constants |

**Color Scheme (see brainstorm: Layer 1):**

```kotlin
// commons/ui/theme/Colors.kt — new constants
val AmethystBlue = Color(0xFF0096FF)      // light primary
val AmethystBlueDark = Color(0xFF4DB8FF)  // dark primary
val AmethystPurple = Color(0xFF9A82DB)    // tertiary (heritage)

// PlatformColorScheme.kt — amethystLight()
fun amethystLight() = lightColorScheme(
    primary = AmethystBlue,                           // #0096FF
    onPrimary = Color.White,
    primaryContainer = AmethystBlue.copy(alpha = 0.12f).compositeOver(Color.White),
    onPrimaryContainer = AmethystBlue,
    secondary = Color(0xFF5E8FAD),                    // desaturated blue
    onSecondary = Color.White,
    tertiary = AmethystPurple,                        // heritage purple
    onTertiary = Color.White,
    background = Color(0xFFF2F2F7),                   // light gray
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF0F0F5),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainer = Color(0xFFF7F7FA),
    surfaceContainerHigh = Color(0xFFEEEEF2),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    surfaceContainerLow = Color(0xFFFAFAFC),
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFEBEBEB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// amethystDark() — same structure with dark values
fun amethystDark() = darkColorScheme(
    primary = AmethystBlueDark,                       // #4DB8FF
    onPrimary = Color.White,
    primaryContainer = AmethystBlueDark.copy(alpha = 0.16f).compositeOver(Color(0xFF1E1E1E)),
    onPrimaryContainer = AmethystBlueDark,
    secondary = Color(0xFF7EAEC8),
    onSecondary = Color.White,
    tertiary = Color(0xFFB6A0E0),                     // lighter purple
    onTertiary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE5E5EA),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE5E5EA),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFF9E9EA3),
    surfaceContainer = Color(0xFF252525),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF383838),
    surfaceContainerLow = Color(0xFF1A1A1A),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2E2E2E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
```

**`resolve()` simplification:**
```kotlin
fun resolve(isDark: Boolean): ColorScheme =
    if (isDark) amethystDark() else amethystLight()
```

Remove: `macOsLight()`, `macOsDark()`, `gnomeLight()`, `gnomeDark()`, `kdeLight()`, `kdeDark()`, `windowsLight()`, `windowsDark()`, `genericLight()`, `genericDark()`, `darkenForLight()`, accent parameter from `resolve()`. Keep `onAccent()` only if still needed elsewhere.

**Shapes (unified):**
```kotlin
// PlatformShapes.kt
val current = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
```

Remove all per-OS shape functions.

**Typography weights:**
- Display: `FontWeight.Light` (300)
- Headline: `FontWeight.SemiBold` (600) — keep
- Title: `FontWeight.SemiBold` / `FontWeight.Medium` — keep
- Body: `FontWeight.Normal` — keep
- Label: `FontWeight.Medium` — keep
- Standardize letter spacing to `-0.3sp` for display/headline across all OS

Keep per-OS font family detection — fonts ARE platform-specific.

**PlatformTheme.kt changes:**
- Remove accent resolution (`PlatformAccent.systemAccent()`)
- Simplify to `PlatformColorScheme.resolve(isDark)` (no accent param)
- Keep `titleBarInsetTop` and `applyNativeWindowChrome()`

**Migration:** Grep `desktopApp/` for inline `Color(0xFF...)` constructors and replace with `MaterialTheme.colorScheme.*` references where possible.

**Success criteria:**
- [ ] App compiles with unified color scheme on all OS
- [ ] Dark/light mode toggle works correctly
- [ ] No per-OS color/shape variance remains
- [ ] Typography weights follow standardized scale

---

#### Phase 2: Spacing System

**Goal:** Create a design token system for consistent spacing across all desktop UI.

**Files:**

| File | Action |
|------|--------|
| New: `desktopApp/.../ui/theme/AmethystSpacing.kt` | Create spacing tokens + CompositionLocal |
| `desktopApp/.../platform/PlatformTheme.kt` | Provide `LocalSpacing` |

**Implementation:**

```kotlin
// AmethystSpacing.kt
@Immutable
data class AmethystSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val cardPadding: Dp = 16.dp,
    val cardGap: Dp = 8.dp,
    val sidebarExpandedWidth: Dp = 240.dp,
    val sidebarCollapsedWidth: Dp = 56.dp,
    val columnHeaderHeight: Dp = 48.dp,
)

val LocalSpacing = staticCompositionLocalOf { AmethystSpacing() }

val MaterialTheme.spacing: AmethystSpacing
    @Composable @ReadOnlyComposable
    get() = LocalSpacing.current
```

**In PlatformTheme.kt:**
```kotlin
val LocalIsDarkTheme = staticCompositionLocalOf { false }

// In PlatformMaterialTheme:
CompositionLocalProvider(
    LocalSpacing provides AmethystSpacing(),
    LocalIsDarkTheme provides isDark,
) {
    MaterialTheme(colorScheme, typography, shapes, content)
}
```

> **Research insight:** `ColorScheme.isLight` does not exist in Material3 Compose (M2 only). Provide `LocalIsDarkTheme` alongside `LocalSpacing` so dialogs/components can detect dark mode. `staticCompositionLocalOf` is correct — matches `LocalDesktopCache`, `LocalRelayManager` pattern in Main.kt.

**Success criteria:**
- [ ] `MaterialTheme.spacing.*` accessible throughout desktop app
- [ ] `LocalIsDarkTheme.current` available for dark mode detection
- [ ] No functional changes — this is infrastructure for subsequent phases

---

#### Phase 3: Sidebar Redesign

**Goal:** Transform the 56dp icon-only sidebar into a 240dp labeled navigation with avatar, custom feeds, and animated collapse.

**Files:**

| File | Action |
|------|--------|
| `desktopApp/.../ui/deck/DeckSidebar.kt` | Major rewrite — wide layout, labels, sections |
| `desktopApp/.../ui/account/AccountSwitcherDropdown.kt` | Move avatar to sidebar top, restyle |
| `desktopApp/.../ui/deck/DeckLayout.kt` | Adjust sidebar width to use animated state |

**Current sidebar structure (DeckSidebar.kt):**
- 56.dp wide Column
- AccountSwitcherDropdown (person icon) → Add Column → Import → Spacer → Bunker/Tor → Settings
- No labels, no active state indicator, no feeds section

**New sidebar structure:**

```
┌─────────────────────────┐
│ [Avatar 40dp] Username  │  ← tappable, opens account switcher
│         @npub...        │
├─────────────────────────┤
│ 🏠 Home                │  ← main nav items
│ 🔍 Search              │
│ ✉️ Messages             │
│ 💰 Wallet              │
│ 🔖 Bookmarks           │
│ ⚙️ Settings             │
├─────────────────────────┤
│ FEEDS                   │  ← section header
│ 📷 Photography          │  ← from FeedDefinitionRepository
│ 🏡 Homestead            │
│ 🏛️ Architecture         │
├─────────────────────────┤
│                         │  ← spacer
│ [Bunker] [Tor]          │  ← status indicators
│ ◀ Collapse              │  ← collapse toggle
└─────────────────────────┘
```

**Key implementation details:**

1. **Animated width:** `animateDpAsState(if (expanded) 240.dp else 56.dp, tween(300, easing = FastOutSlowInEasing))` + `Modifier.clipToBounds()` on sidebar container
2. **Column width debounce:** DeckLayout's `fitColumnsToWidth()` fires via `LaunchedEffect(availableWidthDp)`. During sidebar animation, this thrashes every frame. Add `snapshotFlow { availableWidthDp }.debounce(300)` or gate on animation completion.
3. **Collapse persistence:** `java.util.prefs.Preferences` key `sidebar_collapsed`
3. **Nav items:** Map `DeckColumnType` to sidebar entries with icon + label
4. **Active state:** `primaryContainer` background pill with `primary` tinted icon + `SemiBold` text
5. **Hover state:** `onSurface.copy(alpha = 0.08f)` background on hover (see Phase 6 for shared utility)
6. **Feeds section:** Read from `LocalFeedRepository.current` → `groupedFeeds.pinned + myFeeds`
7. **Feed icons:** Each `FeedDefinition` gets an icon field (Material Symbol codepoint). Default to a generic feed icon.
8. **Avatar:** 40dp circular, loaded from `LocalCache` user metadata. Fallback to `Person` icon.
9. **Account switcher:** Tap avatar opens existing `AccountSwitcherDropdown` (restyled with rounded corners). **Fix dropdown offset:** current hardcoded `DpOffset(x = 48.dp)` must become dynamic based on sidebar width.
10. **Collapsed mode:** Only icons shown, no labels, tooltip on hover. Width animates to 56dp. **Accessibility:** Add `contentDescription` to icons when labels are hidden.
11. **Keyboard shortcut:** `Cmd/Ctrl+B` to toggle sidebar collapse (add to MenuBar in Main.kt).

**Label fade animation:**
```kotlin
AnimatedVisibility(
    visible = expanded,
    enter = fadeIn(tween(200, delayMillis = 100)),
    exit = fadeOut(tween(100)),
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
}
```

**Success criteria:**
- [ ] Sidebar shows 240dp with avatar + nav items + feeds
- [ ] Active item has cyan pill indicator
- [ ] Collapse toggle animates smoothly
- [ ] Collapse state persists across restarts
- [ ] Custom feeds appear in FEEDS section
- [ ] Account switcher works from avatar tap

---

#### Phase 4: Card & Action Bar Refinement

**Goal:** Restyle NoteCard to flat+border, update action bar to match screenshot, add image corner treatment.

**Files:**

| File | Action |
|------|--------|
| `desktopApp/.../ui/note/NoteCard.kt` | Flat + border, 16dp padding, action bar, image corners |

**Card changes:**

> **Research fix:** `BorderStroke` creates new instance each recomposition, defeating skip. Remember it. NoteCard must accept `modifier` parameter.

```kotlin
@Composable
fun NoteCard(
    // ... other params,
    modifier: Modifier = Modifier,
) {
    val border = remember(MaterialTheme.colorScheme.outlineVariant) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = border,
        shape = MaterialTheme.shapes.medium,  // 12dp
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.cardPadding)) { ... }
    }
}
```

> **Scope note:** 10+ files use `CardDefaults.cardColors` across desktop (NotificationsScreen, ChessScreen, ProfileInfoCard, WalletColumnScreen, etc.). All need consistent conversion to OutlinedCard.

**Action bar redesign:**
- Icon size: 20dp (down from current)
- Layout: `Row` with `Arrangement.spacedBy(16.dp)`
- Each action: Icon + count `Text(labelSmall)` in a `Row(spacing = 4.dp)`
- Active states: filled icon + `primary` color (liked heart, zapped lightning, reposted)
- Icons: comment, heart, zap (lightning bolt), repost, share

**Image treatment:**
- All inline images: `clip(RoundedCornerShape(8.dp))`
- Images get `padding(top = 8.dp)` for spacing from text above
- Already partially implemented (8.dp corners exist) — ensure consistency

**Card gaps:**
- Remove any `HorizontalDivider` between cards in feed lists
- Use `Arrangement.spacedBy(MaterialTheme.spacing.cardGap)` (8dp) in `LazyColumn`

**Success criteria:**
- [ ] Cards show flat white surface with subtle border
- [ ] 16dp internal padding
- [ ] Action bar uses smaller icons with count badges
- [ ] Images have rounded corners
- [ ] No divider lines between cards

---

#### Phase 5: Column Headers & Search

**Goal:** Restyle per-column headers to match screenshot aesthetic, add rounded pill search bar, segmented feed toggles.

**Files:**

| File | Action |
|------|--------|
| `desktopApp/.../ui/deck/ColumnHeader.kt` | Restyle: height, background, typography |
| `desktopApp/.../ui/deck/FeedScreen.kt` (or equivalent) | Add SegmentedButton for My Feed / Global toggle |
| Search composable | Rounded pill styling |

**ColumnHeader changes:**
```kotlin
// Before: surfaceVariant.copy(alpha = 0.5f), height = 40.dp
// After:
Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.height(MaterialTheme.spacing.columnHeaderHeight) // 48dp
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // icon + title with titleMedium style
    }
}
```

**Search bar (rounded pill):**
```kotlin
TextField(
    modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    colors = TextFieldDefaults.colors(
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
    ),
    leadingIcon = { Icon(Search) },
    trailingIcon = { Text("⌘K", style = labelSmall, color = onSurfaceVariant) },
    placeholder = { Text("Search notes, profiles, hashtags...") },
)
```

**Feed toggles (SegmentedButton):**
```kotlin
SingleChoiceSegmentedButtonRow {
    SegmentedButton(selected = isMine, onClick = { ... }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
        Text("My Feed")
    }
    SegmentedButton(selected = !isMine, onClick = { ... }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
        Text("Global")
    }
}
```

> **Research insight:** SegmentedButton confirmed available and stable in CMP 1.10.3 (already used in AppDrawer.kt). At `MIN_COLUMN_WIDTH` (300dp), SegmentedButton + icon + close button is tight. Gate visibility on column width > 400dp.

> **Keyboard shortcut:** Wire `Cmd/Ctrl+K` to focus the search column's text field in MenuBar (Main.kt).

**Success criteria:**
- [ ] Column headers have consistent 48dp height with surfaceContainer background
- [ ] Search bar is rounded pill with keyboard shortcut hint (Cmd+K wired)
- [ ] Feed toggle uses M3 SegmentedButton (hidden in narrow columns < 400dp)

---

#### Phase 6: Polish & Micro-interactions

**Goal:** Add hover effects, skeleton loading, styled tooltips/dialogs/context menus/scrollbars/snackbars.

**Files:**

| File | Action |
|------|--------|
| New: `desktopApp/.../ui/theme/HoverModifiers.kt` | `Modifier.hoverHighlight()` (desktop-only, `onPointerEvent`) |
| New: `commons/.../ui/components/ShimmerPlaceholder.kt` | Skeleton shimmer (pure M3, shared) |
| `desktopApp/.../ui/note/NoteCard.kt` | Add hover effect |
| `desktopApp/.../ui/deck/DeckSidebar.kt` | Add hover to nav items |
| Dialog composables | Surface color in dark mode |
| DropdownMenu usages | Styled corners + border |

**Hover utility (uses `onPointerEvent` — codebase convention from AppDrawer.kt, ChatPane.kt):**

> **Research fix:** Original plan used `composed{}` + `@Composable` lambda in `drawBehind` — won't compile. `shape.topStart` is invalid on generic `Shape`. Rewritten to match project patterns.

```kotlin
// HoverModifiers.kt (desktopApp/jvmMain — desktop-only, uses ExperimentalComposeUiApi)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.hoverHighlight(
    hoverColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    shape: Shape = MaterialTheme.shapes.medium,
): Modifier {
    val color = remember { mutableStateOf(Color.Transparent) }
    return this
        .onPointerEvent(PointerEventType.Enter) { color.value = hoverColor }
        .onPointerEvent(PointerEventType.Exit) { color.value = Color.Transparent }
        .drawBehind { drawRect(color.value) }
    // drawBehind reads color.value in draw phase only — no recomposition on hover
}
```

> **Performance note:** State read deferred to draw phase via `drawBehind`. No recomposition on hover enter/exit — only draw invalidation. Safe for LazyColumn with 50+ cards.

**Skeleton shimmer:**
```kotlin
// ShimmerPlaceholder.kt
@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        start = Offset(translateAnim - 500f, 0f),
        end = Offset(translateAnim, 0f),
    )
    Box(modifier.background(brush, MaterialTheme.shapes.medium))
}

@Composable
fun NoteCardSkeleton() {
    OutlinedCard(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerPlaceholder(Modifier.size(32.dp).clip(CircleShape))  // avatar
                Column(Arrangement.spacedBy(4.dp)) {
                    ShimmerPlaceholder(Modifier.width(120.dp).height(12.dp))  // name
                    ShimmerPlaceholder(Modifier.width(60.dp).height(10.dp))   // timestamp
                }
            }
            ShimmerPlaceholder(Modifier.fillMaxWidth().height(14.dp))  // text line 1
            ShimmerPlaceholder(Modifier.fillMaxWidth(0.7f).height(14.dp))  // text line 2
            ShimmerPlaceholder(Modifier.fillMaxWidth().height(180.dp))  // image
        }
    }
}
```

**Dialog styling (dark mode):**

> **Research fix:** `ColorScheme.isLight` does not exist in M3. Use `LocalIsDarkTheme` from Phase 2.

```kotlin
// Wrap existing Dialog composables
Dialog(onDismissRequest = ...) {
    Surface(
        color = if (LocalIsDarkTheme.current)
            MaterialTheme.colorScheme.surfaceContainerHighest
        else
            MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,  // 16dp
        tonalElevation = 6.dp,
    ) { ... }
}
```

**Styled context menus:**
```kotlin
DropdownMenu(
    modifier = Modifier
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
        .clip(MaterialTheme.shapes.medium),
    ...
)
```

**Styled tooltips:**
```kotlin
TooltipBox(
    tooltip = {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.small,
            shadowElevation = 2.dp,
        ) {
            Text(text, modifier = Modifier.padding(8.dp, 4.dp), style = labelSmall)
        }
    },
    ...
)
```

**Snackbar (extract shared composable — two SnackbarHost instances exist: Main.kt and ChatPane.kt):**
```kotlin
// commons/commonMain — pure M3, no platform APIs
@Composable
fun AmethystSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.primary,
        )
    }
}
```

> **Research insight:** Plan originally used `surfaceContainerHighest` — may lack WCAG AA contrast in light mode. `inverseSurface`/`inverseOnSurface` provides high contrast in both modes (M3 standard snackbar pattern).

**Scrollbar styling (via built-in `LocalScrollbarStyle`):**
```kotlin
// Provide at theme root in PlatformMaterialTheme
CompositionLocalProvider(
    LocalScrollbarStyle provides ScrollbarStyle(
        minimalHeight = 48.dp, thickness = 4.dp,
        shape = RoundedCornerShape(2.dp), hoverDurationMillis = 300,
        unhoverColor = Color.Black.copy(alpha = 0.12f),
        hoverColor = Color.Black.copy(alpha = 0.50f),
    )
) { ... }
```

**Success criteria:**
- [ ] Cards highlight on hover
- [ ] Sidebar items highlight on hover
- [ ] Skeleton shimmer shown during feed loading
- [ ] Dialogs use elevated surface in dark mode
- [ ] Context menus have rounded corners + border
- [ ] Tooltips are styled
- [ ] Snackbar matches brand

---

## System-Wide Impact

### Interaction Graph

Theme changes cascade through `PlatformMaterialTheme` → all `MaterialTheme.colorScheme.*` / `.shapes.*` / `.typography.*` consumers. No callbacks or middleware — purely declarative recomposition.

### Error Propagation

No new error paths. Theme resolution is pure function (no IO). Sidebar collapse preference uses `java.util.prefs.Preferences` (already used for custom feeds — failure falls back to expanded).

### State Lifecycle Risks

- Sidebar collapse state: stored in `Preferences`, read once at composition. No partial state risk.
- Feed definitions: already managed by `FeedDefinitionRepository` with `StateFlow`. Sidebar reads same flow.
- Account avatar: loaded from `LocalCache` metadata — may be null initially (fallback to icon).

### API Surface Parity

No external API changes. All changes are internal UI.

### Integration Test Scenarios

1. **Dark/light toggle:** Switch modes mid-session → all colors update (no cached old scheme)
2. **Sidebar collapse + window resize:** Collapse sidebar, resize window narrow → columns should not clip
3. **Custom feed CRUD:** Create/delete feed → sidebar FEEDS section updates live
4. **Account switch:** Switch account → avatar + username in sidebar update
5. **Feed loading:** Navigate to a feed → skeleton shimmer shows → cards render

## Acceptance Criteria

### Functional Requirements

- [ ] Unified Amethyst color scheme (cyan/blue accent) on all platforms
- [ ] Dark/light mode fully functional with proper contrast
- [ ] 240dp sidebar with icon + label navigation items
- [ ] Sidebar collapse/expand with smooth animation
- [ ] Collapse state persists across app restarts
- [ ] Avatar + username at top of sidebar with account switcher
- [ ] Custom feeds section in sidebar reading from FeedDefinitionRepository
- [ ] Flat cards with subtle border (no shadow)
- [ ] 16dp card padding, 12dp card corners
- [ ] Action bar with smaller icons and count badges
- [ ] Rounded images with 8dp corners
- [ ] Per-column headers with consistent 48dp height
- [ ] Rounded pill search bar with keyboard shortcut hint
- [ ] M3 SegmentedButton for feed toggles
- [ ] Hover effects on cards and sidebar items
- [ ] Skeleton shimmer during feed loading
- [ ] Styled tooltips, context menus, dialogs, snackbars

### Non-Functional Requirements

- [ ] App compiles and runs on macOS, Windows, Linux
- [ ] No regression in existing functionality
- [ ] Font rendering quality maintained (per-OS fonts preserved)
- [ ] Smooth animations (sidebar collapse, hover) at 60fps

### Quality Gates

- [ ] `./gradlew :desktopApp:compileKotlin` passes
- [ ] `./gradlew spotlessApply` clean
- [ ] Manual visual QA in dark and light modes
- [ ] Sidebar collapse/expand tested

## Dependencies & Prerequisites

- No external library additions — everything uses existing Material3 + Compose APIs
- `FeedDefinitionRepository` and custom feeds infrastructure already exist
- `AccountSwitcherDropdown` already exists — needs restyling, not rewriting
- Material Symbols font subset may need new icons for feed types (run `./tools/material-symbols-subset/subset.sh`)

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Color contrast issues in dark mode | Medium | Medium | Test with accessibility contrast checker |
| Sidebar animation jank on Linux | Low | Low | Use `tween(250)` easing, test on GNOME |
| Breaking existing hover behavior | Low | Medium | Audit all `pointerInput` / `onPointerEvent` usages first |
| Missing Material Symbol icons | Medium | Low | Run subset script, add needed codepoints |
| Inline Color() literals bypassing theme | Medium | Low | Grep audit + replace in Phase 1 |

## Migration Checklist

- [ ] Remove per-OS color scheme functions (macOsLight, gnomeDark, etc.)
- [ ] Remove `PlatformAccent` accent resolution (no longer needed)
- [ ] Remove per-OS shape variants
- [ ] Grep for `Color(0xFF` in `desktopApp/` — replace with theme references
- [ ] Remove `darkenForLight()` helper
- [ ] Update any tests referencing old color values

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-05-27-desktop-visual-personality-brainstorm.md](docs/brainstorms/2026-05-27-desktop-visual-personality-brainstorm.md) — Key decisions: cyan/blue accent (#0096FF), flat+border cards, 240dp collapsible sidebar, Amethyst-branded over OS-native

### Internal References

- `PlatformColorScheme.kt` — current per-OS scheme implementation
- `PlatformTheme.kt` — theme composition entry point
- `DeckSidebar.kt` — current 56dp icon sidebar
- `NoteCard.kt` — current card styling
- `ColumnHeader.kt` — current column header (40dp, surfaceVariant)
- `AccountSwitcherDropdown.kt` — existing account switcher
- `FeedDefinitionRepository.kt` — custom feed data source
- `FeedsDrawerTab.kt` — current feed browsing UI

### External References

- [Material Design 3 Color System](https://m3.material.io/styles/color/system/overview)
- [Material Design 3 Elevation](https://m3.material.io/styles/elevation/applying-elevation)
- [Custom Design Systems in Compose](https://developer.android.com/develop/ui/compose/designsystems/custom)
