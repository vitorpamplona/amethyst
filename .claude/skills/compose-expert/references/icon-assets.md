# Custom Icon Assets and ImageVector Patterns

Guide to creating and using custom ImageVector icons in Compose Multiplatform.

## Why ImageVector?

ImageVector is the native Compose format for vector graphics:
- **Pure Kotlin**: No XML, no asset files
- **Multiplatform**: Works on Android, Desktop, iOS without conversion
- **Performant**: Lightweight, composable, GPU-accelerated
- **Type-safe**: Compile-time checking, no resource IDs

## Amethyst Pattern: Robohash

Amethyst generates deterministic avatars using ImageVector builders.

### Architecture

```
commons/robohash/
├── RobohashAssembler.kt    # Main assembly logic
├── CachedRobohash.kt        # Caching layer
└── parts/
    ├── Face0C3po.kt         # Face variants (0-9)
    ├── Eyes2Single.kt       # Eye variants (0-9)
    ├── Mouth3Grid.kt        # Mouth variants (0-9)
    ├── Body2Thinnest.kt     # Body variants (0-9)
    └── Accessory7Antenna.kt # Accessory variants (0-9)
```

**Pattern**: 10 variants per feature × 5 features = 100,000+ unique combinations

### roboBuilder DSL

Custom ImageVector builder with sensible defaults:

```kotlin
fun roboBuilder(block: Builder.() -> Unit): ImageVector {
    return ImageVector.Builder(
        name = "Robohash",
        defaultWidth = 300.dp,
        defaultHeight = 300.dp,
        viewportWidth = 300f,
        viewportHeight = 300f
    ).apply(block).build()
}
```

**Usage**:
```kotlin
@Composable
fun CustomIcon() {
    Image(
        painter = rememberVectorPainter(
            roboBuilder {
                // Add paths here
            }
        ),
        contentDescription = "Custom icon"
    )
}
```

### Path Building Pattern

```kotlin
fun face0C3po(fgColor: SolidColor, builder: Builder) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData6, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData7, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.75f)
}

private val pathData1 = PathData {
    moveTo(144.5f, 87.5f)
    reflectiveCurveToRelative(-51.0f, 3.0f, -53.0f, 55.0f)
    curveToRelative(0.0f, 0.0f, 0.0f, 27.0f, 5.0f, 42.0f)
    reflectiveCurveToRelative(10.0f, 38.0f, 10.0f, 38.0f)
    lineToRelative(16.0f, 16.0f)
    // ...
    close()
}
```

**Key elements**:
- `pathData` variables for path commands
- `addPath()` for each layer
- Parameterized colors (`fgColor`)
- Constant colors (`Black`)
- Alpha for shadows/highlights

### PathData DSL

Compose's PathData builder provides SVG-like commands:

| Command | Description | Example |
|---------|-------------|---------|
| `moveTo(x, y)` | Move pen without drawing | `moveTo(100f, 100f)` |
| `lineTo(x, y)` | Draw line to point | `lineTo(200f, 150f)` |
| `curveToRelative(...)` | Relative cubic Bézier | `curveToRelative(10f, 20f, 30f, 40f, 50f, 60f)` |
| `reflectiveCurveToRelative(...)` | Smooth curve | `reflectiveCurveToRelative(-51f, 3f, -53f, 55f)` |
| `horizontalLineTo(x)` | Horizontal line | `horizontalLineTo(250f)` |
| `verticalLineTo(y)` | Vertical line | `verticalLineTo(300f)` |
| `close()` | Close path | `close()` |

**Relative vs Absolute**:
- `moveTo` / `lineTo` - Absolute coordinates
- `moveToRelative` / `lineToRelative` - Relative to current position

## Creating Custom Icons

### Method 1: From SVG (Recommended)

1. **Export SVG** from design tool (Figma, Illustrator)
2. **Convert to ImageVector** using Android Studio's Vector Asset tool
3. **Extract path data** and adapt to roboBuilder pattern

```kotlin
// SVG path: M 10 10 L 20 20 ...
// Becomes:
private val myIconPath = PathData {
    moveTo(10f, 10f)
    lineTo(20f, 20f)
    // ...
}
```

### Method 2: Programmatic

Build paths programmatically for simple shapes:

```kotlin
fun simpleIcon(): ImageVector = roboBuilder {
    addPath(
        pathData = PathData {
            moveTo(50f, 50f)
            lineTo(150f, 50f)
            lineTo(150f, 150f)
            lineTo(50f, 150f)
            close()
        },
        fill = SolidColor(Color.Blue),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f
    )
}
```

### Method 3: Material Icons Extensions

Extend Material Icons when you need platform-consistent icons:

```kotlin
// For standard icons, use Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

Icon(Icons.Default.Refresh, contentDescription = "Refresh")
Icon(Icons.Default.Check, contentDescription = "Success")
Icon(Icons.Default.Close, contentDescription = "Error")
```

## CachedRobohash Pattern

Performance optimization for generated icons:

```kotlin
object CachedRobohash {
    private val cache = mutableMapOf<Pair<String, Boolean>, ImageVector>()

    fun get(seed: String, isLight: Boolean): ImageVector {
        return cache.getOrPut(seed to isLight) {
            RobohashAssembler.assemble(seed, isLight)
        }
    }
}
```

**Pattern**:
- Key: `(seed, theme)` pair
- Value: Assembled ImageVector
- Lifecycle: Application lifetime (never cleared)

**Usage**:
```kotlin
@Composable
fun RobohashImage(robot: String) {
    Image(
        imageVector = CachedRobohash.get(robot, isLightTheme()),
        contentDescription = "Avatar for $robot"
    )
}
```

## Color Management

### Dynamic Colors

Pass colors as parameters for theme adaptation:

```kotlin
fun themedIcon(fgColor: SolidColor, bgColor: SolidColor, builder: Builder) {
    builder.addPath(pathData1, fill = bgColor)
    builder.addPath(pathData2, fill = fgColor)
}

@Composable
fun ThemedIcon() {
    val fg = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surface

    Image(
        painter = rememberVectorPainter(
            roboBuilder {
                themedIcon(SolidColor(fg), SolidColor(bg), this)
            }
        ),
        contentDescription = null
    )
}
```

### Static Colors

Define constants for colors that don't change:

```kotlin
val Black = SolidColor(Color.Black)
val White = SolidColor(Color.White)
val Transparent = SolidColor(Color.Transparent)
```

## Advanced Techniques

### Layering

Build complex icons with multiple layers:

```kotlin
fun complexIcon(builder: Builder) {
    // Layer 1: Background
    builder.addPath(bgPath, fill = SolidColor(Color.White))

    // Layer 2: Shadow
    builder.addPath(shadowPath, fill = SolidColor(Color.Black), fillAlpha = 0.2f)

    // Layer 3: Main shape
    builder.addPath(mainPath, fill = SolidColor(Color.Blue))

    // Layer 4: Highlight
    builder.addPath(highlightPath, fill = SolidColor(Color.White), fillAlpha = 0.3f)

    // Layer 5: Stroke
    builder.addPath(outlinePath, stroke = SolidColor(Color.Black), strokeLineWidth = 1f)
}
```

**Render order**: Bottom to top (first addPath = bottom layer)

### Alpha for Visual Effects

```kotlin
// Shadow
builder.addPath(shadowPath, fill = Black, fillAlpha = 0.4f)

// Highlight
builder.addPath(highlightPath, fill = White, fillAlpha = 0.2f)

// Glass effect
builder.addPath(glassPath, fill = White, fillAlpha = 0.1f)
```

### Stroke Styles

```kotlin
// Outline only
builder.addPath(path, stroke = Black, strokeLineWidth = 1.5f)

// Fill + outline
builder.addPath(path, fill = fgColor, stroke = Black, strokeLineWidth = 1f)

// Dashed (not supported directly, use multiple segments)
```

## Composable Icon Pattern

Wrap ImageVector in a Composable for reusability:

```kotlin
@Composable
fun MyCustomIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Image(
        painter = rememberVectorPainter(myIconVector()),
        contentDescription = "My custom icon",
        modifier = modifier,
        colorFilter = if (tint != Color.Unspecified) {
            ColorFilter.tint(tint)
        } else null
    )
}
```

**Usage**:
```kotlin
MyCustomIcon(
    modifier = Modifier.size(24.dp),
    tint = MaterialTheme.colorScheme.primary
)
```

## Best Practices

### DO
✅ Cache generated ImageVectors for performance
✅ Use PathData DSL for readability
✅ Parameterize colors for theme support
✅ Use Material Icons for standard icons
✅ Keep viewport size consistent (e.g., 300×300)
✅ Layer paths from back to front
✅ Use alpha for shadows and highlights

### DON'T
❌ Generate ImageVectors in @Composable without caching
❌ Hardcode theme-specific colors
❌ Create custom icons for standard Material icons
❌ Use extreme viewport sizes (stay 24-1000dp)
❌ Mix absolute and relative coordinates unnecessarily
❌ Forget to close() paths

## Icon Organization

### Structure
```
commons/icons/
├── CustomIcons.kt           # Icon collection object
├── icons/
│   ├── Zap.kt              # Lightning bolt
│   ├── Relay.kt            # Relay indicator
│   └── Bitcoin.kt          # Bitcoin symbol
└── builders/
    └── IconBuilder.kt       # Shared builder utilities
```

### Collection Object
```kotlin
object CustomIcons {
    val Zap: ImageVector by lazy { ZapIcon.create() }
    val Relay: ImageVector by lazy { RelayIcon.create() }
    val Bitcoin: ImageVector by lazy { BitcoinIcon.create() }
}

// Usage
Icon(CustomIcons.Zap, contentDescription = "Zap")
```

## Resources

- [Compose ImageVector API](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/vector/ImageVector)
- [SVG Path Commands](https://developer.mozilla.org/en-US/docs/Web/SVG/Tutorial/Paths)
- [Material Icons](https://fonts.google.com/icons)
- Robohash implementation: `commons/robohash/` in AmethystMultiplatform
