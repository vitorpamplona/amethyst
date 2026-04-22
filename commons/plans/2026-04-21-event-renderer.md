---
title: "feat(commons): cross-platform event renderer"
type: feat
status: proposed
date: 2026-04-21
owner: commons
consumers: cli, desktopApp, amethyst
---

# feat(commons): cross-platform event renderer

## Overview

Introduce a `commons/commonMain/.../rendering/` subsystem that turns a
`quartz` `Event` into a structured, UI-agnostic `RenderedEvent` value.
Three consumers plug into the same output:

- **Amy** (`cli/`) — serialises `RenderedEvent` to stable JSON.
- **Desktop** (`desktopApp/`) — feeds `RenderedEvent` into Compose views.
- **Amethyst Android** (`amethyst/`) — same, but Android-native layouts.

This is a `commons/`-owned subsystem because it's consumed by three
modules. Amy drives the need first (it can't read any event it can't
render), but the interface isn't Amy-specific.

## Problem

Today every surface that displays a Nostr event re-parses the raw
event structure. Kind:1 rendering, mention resolution, thread roots,
image extraction, hashtag indexing — each lives inline in Compose
components in `amethyst/ui/note/`. Amy cannot share any of it because
it can't depend on Compose or on `amethyst/`.

Consequences:
- `amy note show` cannot exist without either (a) duplicating the
  parsing or (b) extracting it somewhere `cli/` can call.
- Option (b) is the right answer and also fixes Desktop, which is
  currently duplicating Android's parsing in a slightly different way.

## Design

```kotlin
// commons/commonMain/.../rendering/EventRenderer.kt
interface EventRenderer<E : Event> {
    fun render(event: E, ctx: RenderContext): RenderedEvent
}

data class RenderedEvent(
    val kind: Int,
    val eventId: HexKey,
    val author: AuthorRef,
    val createdAt: Long,
    val title: String?,
    val summary: String?,
    val body: List<BodySpan>,       // text, mention, link, image, video, code
    val mentions: List<MentionRef>,
    val media: List<MediaRef>,
    val replyTo: EventRef?,
    val root: EventRef?,
    val raw: Event,                 // escape hatch
)

object EventRendererRegistry {
    fun register(kind: Int, renderer: EventRenderer<*>)
    fun render(event: Event, ctx: RenderContext): RenderedEvent
}
```

- Registry keyed by `event.kind`. Default renderer dumps raw tags +
  content so nothing is ever un-renderable.
- Per-kind specialised renderers cover what Amethyst displays
  specially: 0, 1, 3, 6, 7, 9, 445, 1059 (unwrapped), 10002, 10050,
  30023, 30043, 30311 …
- `RenderContext` carries anything kind-specific needs that's not on
  the event itself: a pubkey → metadata lookup, a NIP-05 resolver, a
  media-preview cache, etc. Default implementation in `commons/`
  with test-friendly no-op behaviour.
- Formatters are separate:
  - `JsonEventFormatter.format(rendered): String` — Amy's output.
  - `@Composable Render(rendered)` — Desktop + Android.
  - `TextEventFormatter.format(rendered): String` — optional
    human-readable terminal mode (`amy note show EID --format text`).

## Migration strategy

1. Land the interface and `RenderedEvent` data model. No consumers.
2. Port a single kind (kind:1) end-to-end:
   - Renderer in `commons/commonMain/.../rendering/`.
   - JSON formatter in `commons/commonMain/.../rendering/json/`.
   - Compose formatter in `commons/commonMain/.../rendering/compose/`.
   - Unit tests in `commonTest`.
3. Desktop and Amethyst switch their kind:1 path to the new renderer.
   Delete the old inline parsing.
4. Repeat for kind:0, kind:7, kind:3, kind:6, kind:10002, kind:10050
   (this unblocks Amy's feed commands).
5. Then the long tail.

Each step is small and reversible. Each step deletes duplicated
parsing somewhere.

## Consumer touch-points

- **Amy** — `cli/commands/NoteCommands.kt#show`:
  ```kotlin
  val rendered = EventRendererRegistry.render(event, ctx.renderCtx)
  Json.writeLine(JsonEventFormatter.toMap(rendered))
  ```
- **Desktop** — replace ad-hoc parsing in `desktopApp/.../note/*` with
  `EventRendererRegistry.render(event, renderCtx)` + `Render(rendered)`.
- **Android** — same swap in `amethyst/ui/note/*`.

## Test strategy

- `commonTest`: golden tests per kind. A fixture event → a fixture
  `RenderedEvent`. Same fixtures feed both formatters.
- JSON formatter tests become Amy's snapshot tests for free.

## Open questions

- `RenderContext` scope: should async lookups (pubkey metadata) be
  inside the renderer or pre-resolved by the caller? Leaning pre-
  resolved — renderers stay pure, caller decides hydration policy.
- Do we render gift-wraps at all, or only their unwrapped inner
  events? Leaning: unwrap first in `quartz`/`commons`, then render
  the inner event. Outer kind:1059 gets a trivial default renderer.
- `BodySpan` granularity: does rich-text parsing (hashtag, mention,
  link detection) live inside the kind-specific renderer or in a
  shared post-processor? Leaning post-processor so every text-bearing
  kind gets it for free.

## Risks

- Scope creep: this is a new subsystem. Keep the first PR tiny
  (interface + default renderer only, zero consumers).
- Compose lock-in: the `@Composable Render` surface must live in
  `commons/commonMain/` to avoid forcing Amy to see Compose. Verify
  that's possible with Compose Multiplatform 1.10.3 — if not, split
  into `commons-render-core` (pure Kotlin) + `commons-render-compose`.

## Out of scope

- Event authoring / composition (new-post editor). Renderer is
  read-only.
- Relay-subscription plumbing. Renderer takes an already-received
  `Event`, not a filter.
- Media loading (image download, video playback). Renderer emits
  `MediaRef`; the consumer decides what to do with it.
