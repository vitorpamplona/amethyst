---
name: compose-modifier-and-layout-style
description: Use when writing or reviewing Jetpack Compose layout APIs, modifier parameters, modifier chain construction, hardcoded root layout decisions, or layout wrappers around a single conditional. Technique-layer skill — complements the codebase-specific compose-expert.
---

# Compose modifier and layout style

## Core principle

A composable that emits layout is a leaf the *parent* places — the parent decides position, size, alignment, padding. The composable's job is structure (what's inside), not placement (where it goes). Three rules follow:

- **Declare a `modifier` parameter and apply it to the root**, so the parent can actually do its job. Hardcoding `.fillMaxWidth()` on a composable's root takes that decision away from every future caller.
- **Construct modifier chains as one fluent expression**, not stepwise reassignments. Both compile to the same thing, but the chain *reads* as intent in one pass.
- **Conditional rendering belongs where the condition applies.** A layout call whose only content is one `if` exists solely to hold the condition — push the `if` outside instead.

These travel together because the same composable usually triggers all three: you declare its parameters (rule 1), the caller constructs a chain to position it (rules 2), and the body has a conditional you might be tempted to wrap (rule 3).

## When to use this skill

- You're writing a `@Composable fun` that calls a layout (`Box`, `Column`, `Row`, `LazyColumn`, `Text`, `Image`, `Surface`, `Card`, `Layout { … }`, anything from `compose.foundation.layout` or `compose.material*`) and its signature has no `modifier` parameter, or has one that isn't applied to the root, or has a hardcoded `.fillMaxWidth()`/`.padding(...)` on the root.
- You see `var m = Modifier` followed by `m = m.padding(…)`, `m = m.background(…)`, etc.
- A `modifier = …` argument has three or more chained calls on a single line.
- A composable's body is `Layout { if (cond) Content() }` — one conditional, nothing else.

## 1. Declare a `modifier` parameter

For composables that emit layout, prefer a `modifier` parameter after required parameters and before content/lambda parameters, with a default of `Modifier`. The name is exactly `modifier` — not `mod`, not `m`, not `wrapperModifier`.

```kotlin
// ❌ BAD — no modifier param; caller can't position, size, or constrain this
@Composable
fun HomeScreenHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
```

```kotlin
// ✅ GOOD — parent decides width and padding; the composable describes structure only
@Composable
fun HomeScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
```

The caller now writes `HomeScreenHeader(title, subtitle, Modifier.fillMaxWidth().padding(horizontal = 16.dp))` once, at the home screen — the only place that knows the layout actually wants those.

## 2. Apply the caller's modifier to the root, and apply it first

When the root layout already takes other arguments (alignment, arrangement, padding *that's intrinsic to the composable*), the caller-provided modifier still goes on the root layout's `modifier` parameter — and the composable's local chain is appended after.

```kotlin
// ❌ BAD — modifier accepted but never applied
@Composable
fun Avatar(url: String, modifier: Modifier = Modifier) {
    Image(painter = rememberAsyncImagePainter(url), contentDescription = null)
}

// ❌ BAD — applied to a child, not the root; caller's size/position changes don't take
@Composable
fun Avatar(url: String, modifier: Modifier = Modifier) {
    Box {
        Image(
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            modifier = modifier,
        )
    }
}

// ❌ BAD — caller's modifier ends up last, so the composable's own size wins
@Composable
fun Avatar(url: String, modifier: Modifier = Modifier) {
    Image(
        painter = rememberAsyncImagePainter(url),
        contentDescription = null,
        modifier = Modifier
            .clip(CircleShape)
            .size(48.dp)
            .then(modifier),
    )
}
```

```kotlin
// ✅ GOOD — caller's modifier first, then the composable's intrinsic chain
@Composable
fun Avatar(url: String, modifier: Modifier = Modifier) {
    Image(
        painter = rememberAsyncImagePainter(url),
        contentDescription = null,
        modifier = modifier
            .clip(CircleShape)
            .size(48.dp),
    )
}
```

Order matters: in a modifier chain, the *earlier* segment is the outer wrapper. The caller's modifier should be the outermost so caller-provided `.size(...)` or `.padding(...)` can override the composable's defaults rather than being overridden by them.

## 3. Don't hardcode layout decisions on the root

If the composable's root has `.fillMaxWidth()`, `.padding(horizontal = 16.dp)`, `.height(56.dp)`, etc., the caller can't *not* have them. Those are layout choices the parent should own.

```kotlin
// ❌ BAD — every caller now fills max width whether they want to or not
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),   // ← hardcoded
    ) { Text(text) }
}

// ✅ GOOD — caller adds .fillMaxWidth() if (and only if) they want it
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) { Text(text) }
}
```

The carve-out is for modifiers that are part of the **identity** of the composable — what makes an `Avatar` an avatar (the `.clip(CircleShape)` and a default `.size(48.dp)`), not where it sits on the screen. Test: can you imagine a caller wanting a version of this composable *without* that modifier? If yes, push it out. If no (an avatar without `clip(CircleShape)` isn't an avatar), keep it — but put it *after* the caller's modifier in the chain (see §2).

## 4. Construct modifier chains as one fluent expression

Recomposition re-runs the composable body — every modifier expression is re-evaluated. Reassigning `var modifier =` step-by-step looks plausible but breaks the visual flow, invites further mutation, and produces nothing a chain doesn't.

```kotlin
// ❌ BAD — visual flow broken into reassignments; `var` invites more mutation
@Composable
fun Demo() {
    var m = Modifier
    m = m.padding(16.dp)
    m = m.fillMaxSize()
    Box(m) { }
}

// ❌ ALSO BAD — same shape, dressed up with .then()
@Composable
fun Demo() {
    var m = Modifier
    m = m.padding(16.dp)
    m = m.then(Modifier.fillMaxSize())
    Box(m) { }
}
```

```kotlin
// ✅ GOOD
@Composable
fun Demo() {
    val m = Modifier
        .padding(16.dp)
        .fillMaxSize()
    Box(m) { }
}
```

`val`, not `var`: once the chain is built, nothing should re-bind it. The reassignment shape is what makes `var` look necessary; the chain shape doesn't need it.

### Inline at the call site is fine for short chains

For one or two calls, build the modifier inline. The "extract to a `val`" rule only earns its keep when the chain is long enough to be worth naming, or when the same chain repeats.

```kotlin
// ✅ GOOD — short chain inline
Box(modifier = Modifier.fillMaxWidth()) { … }
Box(modifier = Modifier.padding(8.dp).background(Color.Red)) { … }
```

### Conditional segments stay on the chain

A common reason to reach for `var` is "the modifier depends on a condition." It doesn't — splice the condition inline:

```kotlin
// ✅ GOOD — conditional inside the chain, still one expression
Box(
    modifier = Modifier
        .fillMaxWidth()
        .then(if (selected) Modifier.background(Color.Red) else Modifier),
)
```

`Modifier` (the empty modifier) is the identity element for `.then` — it lets you keep the chain shape when one branch contributes nothing.

## 5. Multiline formatting at the call site

When a `modifier` argument's chain has **three or more** calls, format multiline with one call per line. Indent the chain so the dotted calls align beneath the value.

```kotlin
// ❌ BAD — three+ calls on one line; hard to scan
Box(
    modifier = modifier.fillMaxSize().padding(16.dp).weight(1f),
)

// ✅ GOOD
Box(
    modifier = modifier
        .fillMaxSize()
        .padding(16.dp)
        .weight(1f),
)
```

One or two calls stay on a single line — the threshold is the call count, not the character count. If a single call has very long arguments, that's a different problem (extract a `val`, or shorten the arguments).

This applies *only* to a parameter named `modifier`. Other fluent-style arguments aren't covered here.

## 6. Hoist single conditionals out of the layout

When a layout's *only* content is one `if`, the layout exists solely to "hold" the conditional. Move the `if` outside — the layout will only exist when it has something to show.

```kotlin
// ❌ BAD — Column always emitted; only its inner content is conditional
@Composable
fun A() {
    Column {
        if (showHeader) {
            Text("Title")
            Text("Subtitle")
        }
    }
}

// ✅ GOOD — Column only exists when it has content
@Composable
fun A() {
    if (showHeader) {
        Column {
            Text("Title")
            Text("Subtitle")
        }
    }
}
```

The benefit isn't a performance win — the runtime handles both fine — it's that the second form *reads* as "header section, conditionally." The first reads as "always-on column that may or may not have content."

### The carve-outs (and why)

- **Layout carries visual semantics that aren't conditional.** When the layout call passes `modifier`, `contentAlignment`, `horizontalArrangement`, or `verticalAlignment`, those arguments describe the *container*, not the content. Hoisting the conditional either loses those (the container collapses with the content) or duplicates them into both branches. Leave it.

  ```kotlin
  // ✅ KEEP AS-IS — modifier on the container is doing visible work
  @Composable
  fun A(modifier: Modifier = Modifier) {
      Box(modifier = modifier) {
          if (something) {
              Text("Bleh1")
              Text("Bleh2")
          }
      }
  }
  ```

- **There are siblings to the `if`.** The layout has other content; the `if` is just one piece. Hoisting either pulls the siblings out (changing the layout) or leaves a different shape behind. Leave it.

- **`if … else …` with both branches contributing composables.** Both branches do work; nothing to hoist; the layout *is* the shared container.

  ```kotlin
  // ✅ KEEP AS-IS — both branches contribute to the layout
  Box {
      if (something) Text("Hint") else innerTextField()
  }
  ```

## Quick reference

| Symptom | Diagnosis | Fix |
|---|---|---|
| `@Composable fun Foo(text: String)` with `Column`/`Box`/`Text` in body | No `modifier` param (§1) | Add `modifier: Modifier = Modifier`; pass to root |
| `modifier: Modifier = Modifier` declared but never referenced | Param ignored (§2) | Apply to root layout's `modifier` arg |
| `modifier` passed to a child, not the root | Wrong target (§2) | Move to the outermost layout's `modifier` |
| `modifier = Modifier.x().y().then(modifier)` | Caller's modifier last (§2) | Reorder: `modifier = modifier.x().y()` |
| `modifier = modifier.fillMaxWidth().padding(...)` on a general-purpose component | Layout hardcoded (§3) | Remove the hardcoded calls; let callers add them |
| Sibling composables in the file don't have `modifier` either | Spreading anti-pattern | Fix this one; fix siblings opportunistically |
| `mod: Modifier = Modifier` or `wrapperModifier: Modifier = Modifier` | Wrong name (§1) | Rename to exactly `modifier` |
| `var m = Modifier` followed by `m = m.xxx()` reassignments | Stepwise modifier construction (§4) | One fluent chain on a `val`, or build inline |
| `var m = Modifier; m = m.then(Modifier.xxx())` | Same shape via `.then` (§4) | Collapse `.then(Modifier.x())` to `.x()` in the chain |
| Modifier branch needs a condition | Reaching for `var` (§4) | `.then(if (c) Modifier.x() else Modifier)` inside the chain |
| `modifier = modifier.a().b().c()` on one line | Long chain not formatted (§5) | One call per line, indented under the value |
| `Layout { if (cond) X() }` with no other content and no layout-tuning args | Hoist (§6) | Move the `if` outside the layout |
| `Box(modifier = …) { if (cond) X() }` | Layout carries semantics — leave (§6 carve-out) | Keep as-is |
| `Box { if (cond) X() else Y() }` | Both branches contribute — leave (§6 carve-out) | Keep as-is |

## When NOT to apply

- **Composables that don't emit layout.** A `@Composable fun computeColor(): Color` or a `@Composable @ReadOnlyComposable` accessor doesn't emit a layout node. No `modifier` parameter needed (and a `@ReadOnlyComposable` couldn't accept one anyway).
- **`@Preview` functions.** Previews are throwaway entry points; the framework calls them with no caller. A `modifier` parameter would be unused dead weight.
- **Test-only composables** inside `*Test` sources whose only caller is `composeTestRule.setContent { … }`. Same reasoning as previews.
- **Internal layout primitives that take a `modifier` as their *first required* parameter** (very rare; framework-level). The rule is "first *optional* param"; some private utilities legitimately have `modifier` upfront as required.
- **Modifier assembled imperatively from animation state.** A modifier built by appending values from `Animatable` or other procedural sources may legitimately need intermediate variables. The chain isn't the goal; readability is. If the chain becomes a worse expression, write the imperative form.
- **Slot APIs that store modifiers** in a data class or builder (rare; usually framework-level code). The fluent-chain idea is about user-site construction.
- **Test composables** pinning specific recomposition shapes — usually fine either way; don't refactor test composables purely for style.

The declaration-side rules (§1–§3) should not be skipped merely because "this composable is internal", "only used in one place", "I'd rather not have the extra parameter on the signature", or "we know all the callers already". Those are exactly the rationalisations that produce composables that become single-use the day someone wants to call them twice.

## Red flags during review

| Thought | Reality |
|---|---|
| "This composable is internal-only — adding `modifier` is over-engineering" | The parameter is eight characters and a default. It's not over-engineering; it's the convention. Skipping it is the over-engineering — it's a custom decision against the grain of every Compose API. |
| "It's only used in one place, so I know the layout requirements" | "Only used in one place" describes today. The cost of the parameter is paid once; the cost of refactoring callers when the second use site appears is paid per caller. |
| "The sibling composables in this file don't have `modifier` either, so I'm matching style" | Spreading an anti-pattern isn't matching style. Fix this one. Fix the siblings opportunistically. |
| "The parent always wants `.fillMaxWidth()` here" | Then the parent passes `.fillMaxWidth()`. The composable doesn't decide that for callers it hasn't met yet. |
| "I'll add it when someone needs it" | You're someone. You need it now (for the convention). The next caller won't add it either — they'll work around its absence. |
| "It's a tiny composable — the modifier param is noise" | The param is eight characters at the declaration and zero characters at any call site that doesn't need it. The "noise" is imagined. |
| "I added `modifier` but kept `.fillMaxWidth()` on the root so the home screen doesn't have to" | Then the *not*-home-screen caller can't unset it. Move the `.fillMaxWidth()` to the caller. |
| "I need `var` for the modifier because the chain depends on a condition" | A conditional segment is `.then(if (c) Modifier.x() else Modifier)`, still on one chain. No `var` needed. |
| "Three lines is too few to make multiline" | Three chained calls *is* the threshold. Below three, one line. At or above three, multiline. |
| "The Column adds nothing but I'll keep it for symmetry" | Then hoist the conditional and keep the Column inside the consequent — symmetry preserved, no always-on container. |
| "I'll put the `if` inside because the layout already exists" | "Already exists" is the bug. The layout shouldn't exist when the condition is false. |

## Related

- [`compose-slot-api-pattern`](../compose-slot-api-pattern/SKILL.md) — the other half of declaring a reusable composable's public API: take `@Composable () -> Unit` slots for variable content. A reusable component takes both a `modifier` parameter *and* slots — caller owns placement *and* what to place.
