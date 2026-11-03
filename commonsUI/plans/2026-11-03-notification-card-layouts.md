# Notification card layouts

Status: design, round 2. Mockups: https://claude.ai/artifact/Jk5Zpe8Ay1devpbxF6uvyF

## Problem

`MultiSetCompose` draws the reacted note in full (`NoteCompose(isBoostedNote = true)`): text, media
and its own reactions row. A single like is as tall as the post. The user wrote that note; they need
who reacted, not the note again. Replies have the same problem upside down: `ReplyRenderType.FULL`
draws the user's parent note in full above the reply they actually need to read.

## Design

**Activity** (reactions, boosts, zaps, nutzaps). Two lines, single or grouped:

```
[faces with kind badges] +N · 2h        42.1k   [thumb]
one muted line of your note
```

- Each face carries its own badge (heart, emoji, boost, zap), so mixed kinds share one row.
- Zap total in orange on the right. A single zap shows amount + its comment on the first line.
- Thumbnail only when the note has media. No full note, no reactions row, no sentence.

**Conversation** (replies, mentions, quotes, comments, DMs). The normal feed note, plus at most one
muted line:

- Reply: `↩ your note's first line` above the body, instead of the full parent.
- Quote: `❝ your note's first line` below the body, instead of the full embed.
- Mention: nothing extra.
- DM: one card per chat, latest message (2 lines), unread count pill, no reactions row.

**Layouts.** Indented keeps the 55dp column (kind icon for activity, picture for conversation).
Full Width drops the column. Borderless is Full Width with conversation media to the edges; activity
cards never bleed.

## Code map

- `MultiSetCompose`: replace the inner `NoteCompose` and the `singleZap`/`singleNutzap` branch with
  the two-line card. New facepile with badges replaces the per-kind gallery rows.
- New `NotificationNoteLine` (one line of a note + optional thumbnail), reused by replies, quotes and
  profile zaps (`ZapUserSetCompose`).
- `NoteCardCompose`: `ReplyRenderType.EXCERPT` (new) for replies; a CompositionLocal to shrink quotes
  of the user's own notes to the one line.
- `MessageSetCard`: hold the notes of one chat per day; `convertToCard` groups them like reactions.
