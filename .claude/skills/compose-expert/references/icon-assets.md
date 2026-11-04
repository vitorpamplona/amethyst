# Robohash avatars

Icons are fonts, not ImageVectors: see `AGENTS.md` "Icons" (Amethyst's `icons/*.kt` compile
into `amethyst_icons.ttf` and draw through `AmethystIconGlyph`; Material icons are
`MaterialSymbols` from a subset font). Robohash is the one generated vector asset.

## Files

Under `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/robohash/`:

- `RobohashAssembler.kt`: `class RobohashAssembler` with `build(msg, isLightTheme): ImageVector`,
  plus `inline fun roboBuilder(name, autoMirror, block)` (an `ImageVector.Builder` on the shared
  viewport).
- `CachedRobohash.kt`: `object CachedRobohash`, an `LruCache` of 100 vectors.
  `get(msg, isLightTheme)` builds or hits, `cached(...)` only hits, `trimToSize(maxItems)`.
  The whole cache is evicted when the theme flips.
- `parts/`: 50 part files, 10 variants each of `Accessory*`, `Body*`, `Eyes*`, `Face*`, `Mouth*`
  (for example `Face0C3po.kt`). Each adds layered paths to the builder with a seed-derived
  foreground color and fixed black strokes / alpha shading.

## Behaviour worth knowing

- The seed is a hex pubkey. Hex of at least 22 chars is decoded directly (the assembler reads
  bytes 0..10). Anything shorter or non-hex is SHA-256'd first, and a `Log.w` is emitted. A blank
  seed falls back to an all-zero key.
- The color range depends on the theme: light theme uses bright channels (150+), dark uses
  dim ones (50-100). Always pass `MaterialTheme.colorScheme.isLight`.

## Composables

In `commonsUI/.../commons/ui/components/`:

- `RobohashImage(robot, modifier, contentDescription, loadRobohash)` and an overload with
  `alignment`, `contentScale`, `colorFilter`. When `loadRobohash` is false it draws the
  `MaterialSymbols.Face` placeholder instead.
- `RobohashFallbackAsyncImage(robot, model, ...)` (`RobohashAsyncImage.kt`): loads the profile
  picture `model` through Coil and falls back to the robohash. `RobohashAsyncImage` there is the
  robohash-only variant. For a user's avatar prefer `UserAvatar(userHex, pictureUrl, size, ...)`
  (`UserAvatar.kt`), which reads the display settings and draws through a platform
  `expect fun AvatarImage`.

Benchmarks: `benchmark/src/androidTest/java/com/vitorpamplona/amethyst/benchmark/RobohashBenchmark.kt`
and `RobohashPartsBenchmark.kt`.
