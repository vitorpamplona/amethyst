---
name: compose-state-deferred-reads
description: Use when Jetpack Compose code reads scroll, animation, gesture, or other frame-rate State in composition, passes changing values across composable boundaries, or uses value-form layout/draw modifiers. Technique-layer skill — complements the codebase-specific compose-expert.
---

# Compose state deferred reads

## Core principle

State reads invalidate the phase that reads them. If a `State<T>` is read in a composable body, changes invalidate composition. If it is read in layout or draw, changes can invalidate only layout or draw. Frame-rate state such as scroll offsets, animations, and drag positions usually belongs in layout/draw, not composition.

The fix is structural: keep the `State<T>` or a provider lambda, and read the value inside a layout/draw callback.

## When to use this skill

- `val x by animate*AsState(...)` is passed to `Modifier.offset(x = ...)`, `Modifier.size(...)`, `Modifier.graphicsLayer(...)`, or another value-form modifier.
- `LazyListState.firstVisibleItemScrollOffset`, `ScrollState.value`, `Animatable.value`, or gesture state is read in a composable body.
- A composable takes `scrollOffset: Int`, `progress: Float`, `dragOffset: Offset`, or similar frame-rate values.
- Recomposition counters climb during scroll, animation, or gestures even when data is stable.

## 1. Prefer block-form modifiers

Several modifiers have value forms and block forms. The value form receives values already read in composition; the block form can read during layout or draw.

```kotlin
// Before: animated value read in composition by the `by` delegate
@Composable
fun SelectionPill(selectedIndex: Int) {
    val offsetX by animateDpAsState(120.dp * selectedIndex)
    Box(Modifier.offset(x = offsetX))
}

// After: State is kept, value is read in the layout-phase offset block
@Composable
fun SelectionPill(selectedIndex: Int) {
    val offsetX = animateDpAsState(120.dp * selectedIndex)
    Box(
        Modifier.offset {
            IntOffset(offsetX.value.roundToPx(), 0)
        },
    )
}
```

Common replacements:

| Composition read | Deferred read |
|---|---|
| `Modifier.offset(x = animatedX)` | `Modifier.offset { IntOffset(animatedX.value.roundToPx(), 0) }` |
| `Modifier.graphicsLayer(translationY = y)` | `Modifier.graphicsLayer { translationY = yProvider() }` |
| `val radius by animateFloatAsState(...); drawBehind { drawCircle(radius = radius) }` | `val radius = animateFloatAsState(...); drawBehind { drawCircle(radius = radius.value) }` |

The `drawBehind` block is already draw-phase; the important part is that the `State.value` read also happens inside that block.

## 2. Pass providers across composable boundaries

If the fast-changing value would cross a composable boundary, pass a provider lambda instead of a snapshot value:

```kotlin
// Before: HomeScreen reads scroll offset in composition and passes the value down
@Composable
fun HomeScreen() {
    val listState = rememberLazyListState()
    LazyColumn(state = listState) {
        item { HeroImage(scrollOffset = listState.firstVisibleItemScrollOffset) }
    }
}

@Composable
fun HeroImage(scrollOffset: Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = "...",
        modifier = modifier.graphicsLayer(translationY = -scrollOffset / 2f),
    )
}

// After: the only read happens inside graphicsLayer
@Composable
fun HomeScreen() {
    val listState = rememberLazyListState()
    LazyColumn(state = listState) {
        item {
            HeroImage(
                scrollOffsetProvider = {
                    if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset
                    } else {
                        0
                    }
                },
            )
        }
    }
}

@Composable
fun HeroImage(scrollOffsetProvider: () -> Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = "...",
        modifier = modifier.graphicsLayer {
            translationY = -scrollOffsetProvider() / 2f
        },
    )
}
```

Suffix provider parameters with `Provider` when that clarifies the deferred-read contract.

## 3. Other layout/draw read sites

State reads can also be deferred inside:

- `Modifier.layout { measurable, constraints -> ... }`
- Custom `Alignment.align(...)`
- `drawWithContent`, `drawBehind`, and other draw modifiers
- Block-form layer/layout modifiers such as `graphicsLayer { ... }` and `offset { ... }`

Use these when the state changes where something is placed or painted. If the state decides *which composables exist*, it belongs in composition.

## Quick reference

| Symptom | Diagnosis | Fix |
|---|---|---|
| `val x by animateFloatAsState(...)` then `Modifier.offset(...)` | `by` reads in composition | Keep `State<Float>` and read `.value` in `offset {}` |
| `Modifier.graphicsLayer(translationY = animatedY)` | Property-argument form uses composition values | Use `graphicsLayer { translationY = ... }` |
| `Child(scrollOffset = listState.firstVisibleItemScrollOffset)` | Fast-changing value crosses boundary | `Child(scrollOffsetProvider = { ... })` |
| Draw block still recomposes every frame | Value was read before draw block | Move the `State.value` read inside the draw block |
| State chooses between different UI branches | Composition decision | Keep the read in composition |

## When NOT to apply

- The state controls which composables are emitted.
- The animation is one-shot, cheap, and clarity wins.
- You are writing tests where direct value assertions are simpler.
- Runtime evidence shows recomposition is not the bottleneck.

## Related

- [`compose-state-holder-ui-split`](../compose-state-holder-ui-split/SKILL.md) - where state-holder vs plain UI split applies when passing providers/lambdas across boundaries.
- [`compose-stability-diagnostics`](../compose-stability-diagnostics/SKILL.md) - parameter stability and compiler reports.
- [`compose-modifier-and-layout-style`](../compose-modifier-and-layout-style/SKILL.md) - child composables need a normal `modifier` parameter before callers can move visual reads into modifiers.
