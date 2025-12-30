# Shared Composables Catalog

This catalog documents shared UI components in `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/`.

## Directory Structure

```
commons/src/commonMain/kotlin/.../commons/ui/
├── components/    # Reusable UI components
├── screens/       # Screen-level composables
├── theme/         # Theming and styling
└── feed/          # Feed-specific components
```

## Components (`ui/components/`)

### State Visualization

**LoadingState** - Centered loading indicator with message
```kotlin
@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier)
```
- Use for: Async operations, data fetching
- Pattern: fillMaxSize, centered Column, CircularProgressIndicator
- Works on: Android, Desktop

**EmptyState** - Centered empty state with optional refresh
```kotlin
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshLabel: String = "Refresh"
)
```
- Use for: Empty lists, no data scenarios
- Pattern: Centered Column, optional OutlinedButton
- Works on: Android, Desktop

**ErrorState** - Centered error message with retry
```kotlin
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try Again"
)
```
- Use for: Error handling, failed operations
- Pattern: error color, optional Button
- Works on: Android, Desktop

### Feed-Specific States

**FeedEmptyState** - Pre-configured empty state for feeds
```kotlin
@Composable
fun FeedEmptyState(
    modifier: Modifier = Modifier,
    title: String = "Feed is empty",
    onRefresh: (() -> Unit)? = null
)
```

**FeedErrorState** - Pre-configured error state for feeds
```kotlin
@Composable
fun FeedErrorState(
    errorMessage: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
)
```

### Action Buttons

**Shared Constants**:
```kotlin
val ActionButtonShape = RoundedCornerShape(20.dp)
val ActionButtonPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp)
```

**AddButton** - Consistent "Add" action button
```kotlin
@Composable
fun AddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Add",
    enabled: Boolean = true
)
```
- Pattern: OutlinedButton with consistent shape/padding
- Works on: Android, Desktop

**RemoveButton** - Consistent "Remove" action button
```kotlin
@Composable
fun RemoveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Remove",
    enabled: Boolean = true
)
```

### Custom Images

**RobohashImage** - Deterministic avatar generation
```kotlin
@Composable
fun RobohashImage(
    robot: String,  // Seed (e.g., pubkey)
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loadRobohash: Boolean = true
)

// Overload with more options
@Composable
fun RobohashImage(
    robot: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
    loadRobohash: Boolean = true
)
```
- Use for: User avatars, deterministic graphics
- Pattern: Uses CachedRobohash.get(), isLightTheme() detection
- Fallback: Icons.Default.Face
- Works on: Android, Desktop (pure ImageVector)

**Theme Detection Helper**:
```kotlin
@Composable
private fun isLightTheme(): Boolean {
    val background = MaterialTheme.colorScheme.background
    return (background.red + background.green + background.blue) / 3 > 0.5f
}
```

## Feed Components (`ui/feed/`)

### FeedHeader

**FeedHeader** - Screen header with title and relay status
```kotlin
@Composable
fun FeedHeader(
    title: String,
    connectedRelayCount: Int,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Pattern: Row with SpaceBetween, title + RelayStatusIndicator
- Works on: Android, Desktop

**RelayStatusIndicator** - Compact relay connection indicator
```kotlin
@Composable
fun RelayStatusIndicator(
    connectedCount: Int,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Pattern: Status icon + count text + refresh button
- Colors: RelayStatusColors.{Disconnected, Connecting, Connected}
- Visual cues: Check icon (connected), Close icon (disconnected)

## Screens (`ui/screens/`)

### Placeholder Pattern

**PlaceholderScreen** - Generic placeholder
```kotlin
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier
)
```
- Pattern: Column with title (headlineMedium) + description
- Use for: Unimplemented screens, coming soon features

**Specific Placeholders**:
- `SearchPlaceholder()` - Search screen
- `MessagesPlaceholder()` - DMs screen
- `NotificationsPlaceholder()` - Notifications screen

Pattern: Specific implementations wrap PlaceholderScreen with preset text.

## Custom Icons (`robohash/parts/`)

### ImageVector Builder Pattern

Amethyst uses a custom DSL for building ImageVector assets:

```kotlin
@Composable
fun Face0C3po() {
    Image(
        painter = rememberVectorPainter(
            roboBuilder {
                face0C3po(SolidColor(Color.Blue), this)
            }
        ),
        contentDescription = ""
    )
}

fun face0C3po(fgColor: SolidColor, builder: Builder) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    // ...
}

private val pathData1 = PathData {
    moveTo(144.5f, 87.5f)
    reflectiveCurveToRelative(-51.0f, 3.0f, -53.0f, 55.0f)
    // ... path commands
}
```

**roboBuilder** - Custom ImageVector.Builder DSL
- Located in: `commons/robohash/`
- Pattern: Builder-based, composable paths
- Parts: Face, Eyes, Mouth, Body, Accessory (0-9 variants each)
- Colors: Dynamic (fgColor parameter) + Black constants

### CachedRobohash

```kotlin
CachedRobohash.get(seed: String, isLight: Boolean): ImageVector
```
- Deterministic: Same seed → same avatar
- Theme-aware: Different colors for light/dark
- Cached: Performance optimization
- Pure ImageVector: Works on all platforms

## Sharing Guidelines

### Always Share
- State visualization (Loading, Empty, Error)
- Action buttons with consistent styling
- Generic placeholders
- Custom ImageVector icons
- Material3 themed components
- Theme utilities (isLightTheme)

### Platform-Specific (Delegate to Experts)
- Navigation structure (android-expert, desktop-expert)
- Screen layouts and scaffolds
- Platform system integrations
- Gesture handling specifics

### Decision Framework
1. **Can it use Material3 primitives?** → Share
2. **Does it need platform system APIs?** → Platform-specific
3. **Is it a visual component without navigation?** → Share
4. **Does it require platform UX patterns?** → Ask platform expert

## Material3 Usage

All shared composables use Material3:
- `MaterialTheme.colorScheme.*` for colors
- `MaterialTheme.typography.*` for text styles
- `OutlinedButton`, `Button`, `IconButton` for actions
- `CircularProgressIndicator` for loading
- `Icon`, `Image` for visuals

This ensures consistent theming across Android and Desktop.
