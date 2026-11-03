# Notification card layouts

Status: implemented (round 2 design). Mockups: https://claude.ai/artifact/Jk5Zpe8Ay1devpbxF6uvyF

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

## Code map (as built)

- `ui/note/NotificationActivityCard.kt`: the two-line card, `NotificationFacePile` (a custom
  `Layout` that fits as many 28dp faces as the width allows and draws "+N" itself, no subcomposition),
  per-face badges (`ActivityGlyph`, `ReactionGlyph`), `NotificationNoteLine` (title/subject or the
  text tidied by `ChatPreviewText`, media links dropped when there are words) and the 44dp thumbnail
  (skipped for sensitive notes or with image loading off).
- `MultiSetCompose`, `ZapUserSetCompose`, `NutzapUserSetCompose`: now just build the actors, total and
  newest comment and hand them to the card. The single-zap "large card" branch is gone.
- `ReplyRenderType.EXCERPT` (`ui/note/types/Text.kt`): a reply to the user's own note shows it as one
  line under a reply arrow; a reply to anyone else keeps "replying to @name". `NoteCardCompose` uses
  it and wraps `LocalInlineQuoteRenderer` with `notificationInlineQuoteRenderer`, which turns a quote
  of the user's note into one line under a quote mark (`NotificationConversationLines.kt`).
- `MessageSetCard` holds a chat's messages per day (`CardFeedContentState.convertToCard` groups them
  by chatroom key or Marmot group). `MessageSetCompose` is a Messages-list row with an unread count;
  its preview comes from `ChatMessagePreviewText`, extracted from `ChatroomHeaderCompose`.
