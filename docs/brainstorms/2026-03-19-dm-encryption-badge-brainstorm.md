# Brainstorm: Per-Message Encryption Badge in DMs

**Date:** 2026-03-19
**Status:** Ready for implementation

## What We're Building

Per-message encryption indicator in DM chat bubbles — lock icon (NIP-17) or lock-open icon (NIP-04) next to the timestamp. Matches Android's approach with incognito badges.

## Why This Approach

Users need to know which messages are truly private (NIP-17: relay can't see sender/recipient) vs legacy encrypted (NIP-04: relay sees metadata). Android already does this with incognito badges. Desktop uses lock/lock-open icons (already imported in ChatPane) for consistency with the existing NIP-17 toggle.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Indicator type | Lock icon per message | Matches existing lock icon pattern in desktop NIP-17 toggle |
| NIP-17 icon | Lock (filled) in primary color | Private, secure |
| NIP-04 icon | LockOpen in muted gray | Legacy, weaker privacy |
| Placement | Next to timestamp in detailRow | Matches Android's IncognitoBadge placement |
| Tooltip | None (match Android) | Keep it subtle, not alarming |

## Implementation

The badge goes in `MessageWithReactions` in ChatPane.kt, in the `detailRow` slot of `ChatMessageCompose`. Check `note.event` type:
- `is PrivateDmEvent` → NIP-04 → lock-open gray
- `is ChatMessageEvent` or `is ChatMessageEncryptedFileHeaderEvent` → NIP-17 → lock primary
- else → no badge

## Key Finding: NIP-04 + NIP-17 Messages Merge

For 1-on-1 chats, both protocols produce identical `ChatroomKey({otherPubkey})`. Messages from both protocols appear in the same conversation. The per-message badge is the only way to tell them apart.
