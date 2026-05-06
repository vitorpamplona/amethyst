# Material Symbols Subset

The font shipped at
`commons/src/commonMain/composeResources/font/material_symbols_outlined.ttf`
is a **subset** of Google's [Material Symbols
Outlined](https://github.com/google/material-design-icons) variable font,
trimmed to only the codepoints referenced from
`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/icons/symbols/MaterialSymbols.kt`.

| | Full upstream | Subset |
|---|---|---|
| Size | ~11 MB | ~410 KB |
| Glyphs | ~3500 | ~210 |

The subset cuts >10 MB out of every commons build artifact, every Android APK,
and every Desktop app bundle.

## When to regenerate

Run `subset.sh` whenever you:

- add or remove a `MaterialSymbol("\uXXXX")` entry in `MaterialSymbols.kt`
- pin a newer upstream font version

## Prerequisites

```bash
pip install fonttools brotli
```

## Regenerating

From the repo root:

```bash
./tools/material-symbols-subset/subset.sh
```

That downloads the upstream variable font, intersects its codepoints with the
ones referenced from `MaterialSymbols.kt`, and overwrites the checked-in
`material_symbols_outlined.ttf`.

If you already have an upstream `.ttf` on disk:

```bash
./tools/material-symbols-subset/subset.sh /path/to/MaterialSymbolsOutlined-Regular.ttf
```

Commit the regenerated `.ttf` alongside the `MaterialSymbols.kt` change.

## How it works

`pyftsubset` (from `fonttools`) reads the codepoints we care about, drops every
glyph and OpenType feature not reachable from those codepoints, and rewrites
`name`, `cmap`, and `GSUB` tables accordingly. The Compose resource pipeline
treats the result as a normal TTF — no code changes needed.
