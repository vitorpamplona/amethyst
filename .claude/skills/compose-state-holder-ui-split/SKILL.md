---
name: compose-state-holder-ui-split
description: Use when a Jetpack Compose screen-level composable takes a ViewModel/component/controller, collects state or effects, handles navigation/snackbars, or wires callbacks while also rendering layout. Technique-layer skill — complements the codebase-specific compose-expert and feed-patterns.
---

# Compose: state holder/UI split

## Core principle

Separate state-holder wiring from UI rendering. The state-holder composable talks to ViewModels, components, flows, navigation, and side effects. The UI composable takes plain immutable UI state plus callbacks and describes layout.

This keeps screens previewable, testable, and easier to reuse across Android, Desktop, TV, and KMP/CMP targets.

## When to use this skill

Use this when a Compose screen:

- Takes a ViewModel, component, controller, navigator, repository, or service directly.
- Collects app/business state or side effects in the same function that lays out most UI.
- Passes a whole state holder into child composables instead of explicit state and callbacks.
- Is hard to preview because it needs dependency injection, navigation, lifecycle, or fake services.
- Has UI tests that must construct a full app stack to verify a simple layout branch.

## The pattern

Use a small public state-holder composable:

```kotlin
@Composable
fun ProfileScreen(component: ProfileComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsStateWithLifecycle()

    ProfileScreen(
        state = state,
        onNameChange = component::onNameChange,
        onSaveClick = component::save,
        onBackClick = component::back,
        modifier = modifier,
    )
}
```

Then put UI in a plain composable that knows nothing about the state holder:

```kotlin
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onNameChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileContent(
        name = state.name,
        isSaving = state.isSaving,
        canSave = state.canSave,
        onNameChange = onNameChange,
        onSaveClick = onSaveClick,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
```

Private content functions can break up layout:

```kotlin
@Composable
private fun ProfileContent(
    name: String,
    isSaving: Boolean,
    canSave: Boolean,
    onNameChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Layout only.
}
```

## Rules of thumb

| Concern | State-holder composable | UI composable |
|---|---|---|
| Collect ViewModel/component state | Yes | No |
| Collect one-shot effects | Yes, or a tiny sibling effect handler | Usually no |
| Hold dependency-injected objects | Yes | No |
| Accept immutable UI state | Usually passes it through | Yes |
| Accept lambdas for user events | Wires them | Calls them |
| Own layout, modifiers, semantics, test tags | No/minimal | Yes |
| Own UI-local state like scroll, focus, text input, animation, interaction | Sometimes seeds it | Yes |
| Preview/screenshot friendly | Not necessarily | Yes |

The "no collection in UI composables" rule is about app/business state and side-effect streams. Plain UI composables can still own UI-local framework state: `rememberScrollState`, `rememberLazyListState`, `FocusRequester`, focus state, animation state, `TextFieldState`, `MutableInteractionSource.collectIsPressedAsState()`, and similar behavior that belongs to the rendered widget.

If that UI-local state grows into coordinated behavior with multiple related fields and operations, consult `compose-expert` (state hoisting section) to decide whether it should become a plain state holder class remembered in composition.

## What to pass

Pass the smallest useful UI contract:

- Prefer a dedicated `UiState`/`State` object over many unrelated primitives when the screen has real state.
- Prefer explicit lambdas (`onRetryClick`, `onItemSelected`) over passing a whole component.
- Keep domain models out of the UI composable if they force business rules into UI. Map to UI models when the UI needs a different shape.
- Keep navigation as callbacks. The UI composable says "user clicked back", not "navigate to route X".
- Frame-rate or UI-local values that should not force whole-tree recomposition when they change: prefer provider lambdas and deferred reads per [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md).

## Side effects

[`compose-side-effects`](../compose-side-effects/SKILL.md) covers effect APIs (`LaunchedEffect`, `DisposableEffect`, `SideEffect`), keys, cleanup, and `rememberUpdatedState`.

Handle effects near the state holder, where the effect source and imperative target are both available:

```kotlin
@Composable
fun ProfileScreen(component: ProfileComponent, snackbarHostState: SnackbarHostState) {
    val state by component.state.collectAsStateWithLifecycle()

    LaunchedEffect(component) {
        component.effects.collect { effect ->
            when (effect) {
                ProfileEffect.Saved -> snackbarHostState.showSnackbar("Saved")
            }
        }
    }

    ProfileScreen(state = state, onSaveClick = component::save)
}
```

If effect handling grows, extract `ProfileEffects(component, snackbarHostState)` rather than pushing the component into the UI composable.

## Common mistakes

| Mistake | Why it hurts | Fix |
|---|---|---|
| `fun Screen(viewModel: MyViewModel)` contains all layout | Hard to preview/test without Android lifecycle and DI | Add a plain UI overload that takes `state` and callbacks |
| Child composables take `component` | Dependencies leak through the tree | Pass only the state/callbacks that child needs |
| UI composable launches navigation | UI becomes coupled to app routing | Expose `onBackClick`, `onItemClick`, etc. |
| UI composable collects app/business flows | Collection lifecycle is hidden in layout | Collect near the state holder and pass values down |
| UI-local state is hoisted into the state holder for no reason | State holder starts owning layout mechanics | Keep scroll/focus/animation/text-field interaction state in the UI composable when it is only UI behavior |
| Every tiny composable gets a state-holder overload | Too much ceremony | Split at screen/section boundaries, not every `Row` |

## When NOT to apply

- Tiny one-off composables that already take plain values and callbacks.
- Design-system primitives such as `Button`, `Card`, or `ListItem`; those should expose slots and modifiers, not state holders.
- Cases where the state-holder composable would only forward one primitive and add no isolation.

## Related

- [`compose-expert`](../compose-expert/SKILL.md) — Amethyst's shared-UI patterns, including state hoisting for UI element state and plain state holder classes.
- [`compose-side-effects`](../compose-side-effects/SKILL.md) — effect keys and cleanup in Compose.
- [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) — deferred reads for frame-rate / UI-local values passed across boundaries.
- [`kotlin-multiplatform`](../kotlin-multiplatform/SKILL.md) — platform services, native views, and expect/interface boundaries when shared UI meets platform-specific leaves.
