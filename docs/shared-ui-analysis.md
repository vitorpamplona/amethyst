# Shared UI Components Analysis

Analysis of Android vs Desktop UI to identify reusable Compose Multiplatform components.

## Executive Summary

| Category | Android Files | Shareable As-Is | Needs Abstraction |
|----------|---------------|-----------------|-------------------|
| Formatters | 4 | 2 | 2 |
| Theme | 4 | 4 | 0 |
| Note Components | 35 | ~10 | ~25 |
| Total Potential | ~450+ | ~100+ | ~200+ |

## Immediately Shareable Components

These have **zero Android dependencies** and can be moved to a shared module today:

### Formatters (Pure Kotlin)
```
amethyst/ui/note/PubKeyFormatter.kt     -> shared/ui/formatters/
amethyst/ui/note/ZapFormatter.kt        -> shared/ui/formatters/
amethyst/ui/note/ZapFormatterNoDecimals.kt -> shared/ui/formatters/
```

### Theme (Pure Compose)
```
amethyst/ui/theme/Color.kt   -> shared/ui/theme/
amethyst/ui/theme/Shape.kt   -> shared/ui/theme/
amethyst/ui/theme/Type.kt    -> shared/ui/theme/
```

### Layout Primitives
```
amethyst/ui/theme/ButtonBorder (padding values)
amethyst/ui/theme/Size* (dimension constants)
```

## Components Requiring Abstraction

### 1. String Resources (R.string.*)

**Problem:** Android uses `R.string.xxx` for localized strings.

**Solution:** Create `StringProvider` interface:

```kotlin
// commonMain
interface StringProvider {
    fun get(key: String): String
    fun get(key: String, vararg args: Any): String
}

// androidMain
class AndroidStringProvider(private val context: Context) : StringProvider {
    override fun get(key: String) = context.getString(keyToId(key))
}

// jvmMain
class DesktopStringProvider : StringProvider {
    private val strings = mapOf(
        "post_not_found" to "Post not found",
        "now" to "now",
        // ...
    )
    override fun get(key: String) = strings[key] ?: key
}
```

**Affected files:**
- TimeAgoFormatter.kt
- BlankNote.kt
- All UI components with text

### 2. AccountViewModel

**Problem:** Most UI components depend on `AccountViewModel` for:
- User profile data
- Settings (image loading, NSFW, etc.)
- Actions (follow, mute, report)

**Solution:** Create interface in shared module:

```kotlin
// commonMain
interface IAccountState {
    val userPubKey: String
    val userNpub: String
    val settings: IUserSettings
}

interface IUserSettings {
    val showImages: Boolean
    val showSensitiveContent: Boolean
}
```

### 3. Navigation (INav)

**Problem:** Android uses custom `INav` interface tied to Compose Navigation.

**Solution:** Already abstracted! `INav` is an interface - just needs to be in shared module.

### 4. Image Loading

**Problem:** Uses Coil with Android-specific caching.

**Solution:** Coil 3.x supports Compose Multiplatform. Create shared wrapper:

```kotlin
// commonMain
@Composable
expect fun AsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
)
```

## Recommended Shared Module Structure

```
shared-ui/
├── src/
│   ├── commonMain/kotlin/
│   │   ├── formatters/
│   │   │   ├── PubKeyFormatter.kt
│   │   │   ├── ZapFormatter.kt
│   │   │   └── TimeAgoFormatter.kt (abstracted)
│   │   ├── theme/
│   │   │   ├── Color.kt
│   │   │   ├── Shape.kt
│   │   │   ├── Type.kt
│   │   │   └── Dimensions.kt
│   │   ├── components/
│   │   │   ├── NoteCard.kt
│   │   │   ├── UserAvatar.kt
│   │   │   └── LoadingIndicator.kt
│   │   └── providers/
│   │       ├── StringProvider.kt
│   │       └── ImageProvider.kt
│   ├── androidMain/kotlin/
│   │   └── providers/
│   │       └── AndroidStringProvider.kt
│   └── jvmMain/kotlin/
│       └── providers/
│           └── DesktopStringProvider.kt
└── build.gradle.kts
```

## Implementation Priority

### Phase 1: Low-Hanging Fruit (1-2 days)
1. Create `shared-ui` module
2. Move pure Kotlin formatters (PubKeyFormatter, ZapFormatter)
3. Move theme colors and dimensions
4. Update both apps to use shared module

### Phase 2: String Abstraction (2-3 days)
1. Create StringProvider interface
2. Implement for Android and Desktop
3. Migrate TimeAgoFormatter
4. Create composable wrappers for common strings

### Phase 3: Core UI Components (1 week)
1. Abstract AccountViewModel to IAccountState
2. Move simple components (BlankNote, LoadingIndicator)
3. Create NoteCard shared component
4. Integrate with both apps

### Phase 4: Complex Components (2+ weeks)
1. Image loading abstraction
2. RichTextViewer
3. ReactionsRow
4. Full NoteCompose migration

## Desktop vs Android Comparison

| Feature | Android | Desktop | Share Strategy |
|---------|---------|---------|----------------|
| Note display | NoteCompose.kt (51KB) | NoteCard in FeedScreen | Extract common layout |
| Time formatting | TimeAgoFormatter.kt | SimpleDateFormat inline | Abstract with interface |
| Key display | PubKeyFormatter.kt | Manual truncation | Use shared formatter |
| Colors | Color.kt | darkColorScheme() | Share Color.kt |
| Navigation | INav + Compose Nav | enum Screen | Abstract navigation |
| Images | Coil + OkHttp | Not implemented | Coil Multiplatform |

## Current Desktop Implementation Gaps

Desktop currently has basic implementations that could benefit from sharing:

1. **NoteCard** - Has inline date formatting, could use TimeAgoFormatter
2. **Key display** - Manual truncation, should use PubKeyFormatter
3. **Theme** - Uses default darkColorScheme, could use shared theme
4. **No image loading** - Needs Coil integration

## Quick Wins

Immediate improvements to desktop using patterns from Android:

```kotlin
// Instead of:
val npub = event.pubKey.take(16) + "..."

// Use shared formatter:
val npub = event.pubKey.toDisplayHexKey()

// Instead of:
val format = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

// Use shared formatter:
val timeAgo = timeAgoNoDot(event.createdAt, stringProvider)
```

## Next Steps

1. **Create shared-ui module** in project structure
2. **Move formatters** as proof of concept
3. **Create StringProvider** abstraction
4. **Iterate** on more complex components
