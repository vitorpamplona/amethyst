---
name: kotlin-types-value-class
description: Use when writing or reviewing Kotlin type declarations to choose @JvmInline value class over data class where appropriate, including Compose stability implications. Technique-layer skill — complements the codebase-specific kotlin-expert.
---

# Kotlin value class vs data class

## Core principle

Prefer `@JvmInline value class` for single-field types that carry domain meaning. Data classes are for aggregating multiple fields. A value class gives you type safety (you can't mix up `UserId` and `String`) without the allocation overhead of a data class.

## When to use this skill

- Writing a new Kotlin type that wraps a single value
- Reviewing a data class that has only one property
- Seeing primitive types (`String`, `Long`, `Int`, etc.) used where a domain type would prevent misuse
- Compose compiler reports showing unstable parameters that could be value classes

## Decision flow

| Situation | Prefer |
|---|---|
| Single field + domain-meaningful (`UserId`, `EmailAddress`, `Percentage`) | `@JvmInline value class` |
| Single field + no domain meaning (just grouping) | Type alias or keep the primitive |
| Multiple fields | Data class |
| Needs custom `equals`/`hashCode`/`toString` beyond the wrapped value | Data class (value classes delegate to the underlying type) |
| Used as a generic type argument or nullable in hot paths | Data class or primitive (autoboxing cost) |

```kotlin
// GOOD: domain-meaningful single field
@JvmInline value class UserId(val value: String)
@JvmInline value class EmailAddress(val value: String)
@JvmInline value class Percentage(val value: Float)

// BAD: data class wrapping a single field
data class UserId(val value: String) // unnecessary allocation
data class EmailAddress(val value: String) // type safety without the overhead is available

// BAD: value class with no domain meaning
@JvmInline value class Wrapper(val value: String) // just use the String, or a type alias

// BAD: value class needing custom equality
@JvmInline value class CaseInsensitiveString(val value: String)
// value class equals delegates to String equals, which IS case-sensitive
// Use a data class if you need different equality semantics
```

## Compose stability

`@JvmInline value class` is treated as `Stable` by the Compose compiler when its underlying type is stable (primitives, `String`, and other stable types). This means:

- Value classes passed as composable parameters avoid "unstable parameter" warnings
- No need for `@Immutable` annotations at Compose boundaries when wrapping primitives or strings
- Replacing single-field data classes with value classes at UI boundaries improves skippability

```kotlin
// Before: data class wrapping a single field
data class UiState(val userId: String) // works, but allocates a wrapper object

// After: value class is stable and zero-allocation at runtime
@JvmInline value class UserId(val value: String)
data class UiState(val userId: UserId)
```

## Gotchas

- **Autoboxing**: Value classes are unboxed at compile time but boxed (allocated) when used as nullable (`UserId?`), generic type arguments (`List<UserId>`), or vararg parameters. In hot paths these allocations matter; in most code they don't.
- **No backing fields**: You cannot use `init` blocks, `lateinit`, or delegated properties like `by lazy`. The class body is extremely constrained — only the single constructor parameter exists.
- **No data-class conveniences**: No `copy()`, no `component1()` for destructuring, and no way to customize `toString()`. If you need any of these, use a data class.
- **No custom equals/hashCode/toString**: These always delegate to the underlying type. Need custom equality → use a data class.
- **when exhaustiveness**: Sealed hierarchies of value classes work differently than data class hierarchies. Test `when` branches carefully.
- **Serialization semantics**: With kotlinx.serialization, a `@Serializable data class A(val value: String)` serializes as `{"value":"..."}`, but a `@Serializable value class A(val value: String)` serializes as the underlying value (`"..."`). Replacing a single-field data class with a value class is a breaking change for your API/JSON contract.
- **Serialization**: Some serialization frameworks need explicit support for value classes (e.g., kotlinx.serialization's `@Serializable` works, but Jackson may need configuration).
- **Interoperability**: From Java, value classes appear as their underlying type. Java callers bypass the type-safety wrapper.
- **Reflection and runtime erasure**: When passed as `Any` or used in generic contexts, value classes box into a synthetic wrapper class. Java reflection sees mangled method signatures, and frameworks that rely on raw runtime types (some ORMs, DI containers, or serializers) may see the underlying type rather than the value class.

## Packing multiple values

A value class can only declare one field, but Compose provides `packFloats`, `packInts`, and matching `unpack*` functions in `androidx.compose.ui.util` to store multiple primitives in a single `Long`. This lets you represent composite values (e.g., a 2D point, size, or padding) as a zero-allocation value class instead of a multi-field data class.

```kotlin
@JvmInline value class Offset(val packedValue: Long)

fun Offset(x: Float, y: Float): Offset = Offset(packFloats(x, y))
val Offset.x: Float get() = unpackFloat1(packedValue)
val Offset.y: Float get() = unpackFloat2(packedValue)
```

- **Only use this in performance-critical paths** — manual bit-packing is error-prone. A data class is simpler and safer for most UI types.
- **Available in `androidx.compose.ui.util`** — `packFloats`, `packInts`, `unpackFloat1`, `unpackFloat2`, `unpackInt1`, `unpackInt2`.

## Common mistakes

| Mistake | Fix |
|---|---|
| Data class wrapping a single domain field | Replace with `@JvmInline value class` |
| Value class with no domain meaning (just a wrapper) | Use a type alias or the primitive directly |
| Value class needing custom equality | Use a data class instead |
| Value class as generic type argument in hot path | Accept autoboxing cost or use the primitive |
| `@Immutable` annotation on a type that could be a value class | Replace with value class — it's Stable by default |
| Forgetting `@JvmInline` annotation | Always pair `value class` with `@JvmInline` for single-field classes |

## Red flags during review

- A data class with exactly one property
- A `String`, `Long`, or `Int` used where different values should not be interchangeable (e.g., `fun transfer(from: String, to: String, amount: Long)`)
- An `@Immutable` annotation on a single-field wrapper
- A type alias used for domain distinction where value-class semantics are needed (type aliases are type-erased, no runtime protection)

## When NOT to apply

- The type needs multiple fields → data class
- The type needs custom `equals`/`hashCode`/`toString` → data class
- The type is used heavily as a nullable or generic in performance-critical code → measure autoboxing cost first
- The project does not need the type-safety distinction → a type alias or primitive is sufficient

## Related

- [`compose-stability-diagnostics`](../compose-stability-diagnostics/SKILL.md) — diagnose unstable Compose parameters; value classes are one fix
