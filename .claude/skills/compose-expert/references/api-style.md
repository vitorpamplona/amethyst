# Component API review checklist

Use this when you write or review the public signature of a reusable composable in `commonsUI`.
It covers slots and modifiers. Strings, immutable collections and theme tokens are in
`SKILL.md`.

Good in-repo shapes to copy:

- `UserAvatar` (`ui/components/UserAvatar.kt`): required data first, then
  `modifier: Modifier = Modifier`, optional settings with defaults, and an optional
  `badge: @Composable (BoxScope.() -> Unit)? = null` slot.
- `RoundedDropdownMenu` and `M3ActionDialog`: `content: @Composable ColumnScope.() -> Unit`
  because the content lands in a `Column`.
- `RefresheableBox`: `content: @Composable BoxScope.() -> Unit`.

## Slots

| Symptom | Fix |
|---|---|
| `title: String, subtitle: String?, leadingIcon: ImageVector?` on a component with several call sites | Turn the content regions into slots: `headlineContent: @Composable () -> Unit`, `supportingContent: (@Composable () -> Unit)? = null`, ... |
| Boolean or enum flags that only pick what is drawn in one area (`showChevron`, `showSwitch`, `mode`) | One `trailingContent` slot |
| Leading area is a primitive while trailing is already a slot | Slot the leading area too |
| Optional slot written as `slot: @Composable () -> Unit = {}` | `slot: (@Composable () -> Unit)? = null`, and skip the slot's padding / spacer when it is null |
| Slot rendered inside a `Row` / `Column` / `Box` but declared without a receiver | Give it the matching receiver (`RowScope.() -> Unit`) so callers can use `weight` / `align`. Only add a receiver that matches the real parent |
| The same trailing content repeated at every call site, or `color: Color = MaterialTheme.colorScheme.x` defaults spelled out inline | Put them in an `XxxDefaults` object next to the component |
| Mixed slot names | `xxxContent` for free-form slots (Material 3 style), or a constrained noun (`badge`, `actions`) when the component name makes it clear. Don't mix `content` with `xxxContent` |

Keep primitives when the component is used once, when it exists to force one look (a heading
style), or when the parameter is state rather than content (`checked: Boolean` +
`onCheckedChange`). In hot feed rows, measure before trading primitives for lambdas (see
"Performance" in `SKILL.md`).

## Modifiers

| Symptom | Fix |
|---|---|
| A composable that emits layout has no `modifier` parameter | Add `modifier: Modifier = Modifier`, after the required parameters and before the trailing content lambda. Name it exactly `modifier` |
| `modifier` is declared but unused, or applied to a child | Apply it to the root layout |
| `Modifier.size(48.dp).then(modifier)` | Put the caller's modifier first: `modifier.clip(CircleShape).size(48.dp)`. The outer (earlier) modifier wins on size and padding |
| Root hardcodes `.fillMaxWidth()` / outer padding on a general component | Remove it and let callers add it. Keep only what defines the component (an avatar's circular clip) |
| `var m = Modifier; m = m.padding(..)` | One `val` chain. For conditions use `.then(if (c) Modifier.x() else Modifier)` |
| Three or more chained calls on one line | One call per line |
| A layout whose only child is one `if` and which passes no modifier, alignment or arrangement | Move the `if` outside the layout |

Exempt: `@Preview` functions, test-only composables, and composables that emit no layout.
