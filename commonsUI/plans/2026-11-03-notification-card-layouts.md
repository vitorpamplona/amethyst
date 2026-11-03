# Notification card layouts: activity vs conversation

Status: design round, not implemented. Mockups (Full Width, Indented, Borderless and a card
anatomy sheet): https://claude.ai/artifact/Jk5Zpe8Ay1devpbxF6uvyF

## Problem

In the notifications feed a single like takes about as much screen as the liked post itself.
`MultiSetCompose` draws the gallery rows (icon + faces) and then the whole reacted note through
`NoteCompose(isBoostedNote = true)`: full text, full media and its own reactions row. Full Width and
Borderless make it worse, because the note's media now runs the whole width (and to the screen edges
in Borderless). The user already knows that note (they wrote it), so the space goes to the part
they need least, and what happened (who reacted, with what) is a thin strip above it.

The reverse happens for replies: `NoteCardCompose` uses `ReplyRenderType.FULL`, which draws the
user's own parent note as a full quote box above the reply. Again the user's own words take the
space, and the reply, which they have not read, is pushed down.

## Principle

Two card families, chosen by who wrote the content the user has to read:

| Family | Kinds | Headline | The user's note |
|---|---|---|---|
| **Activity** | reactions, boosts, zaps, nutzaps (on a note or on the profile) | The actors and the action | A reference: 2 lines + a 44dp thumbnail, no reactions row |
| **Conversation** | replies, mentions, quotes, NIP-22 comments, DMs | Someone else's full note | One line (reply) or a 2-line box (quote) |

A zap comment is written by someone else, so inside an activity card it stays readable text, not a
tooltip.

## Activity card (grouped and single are the same card)

```
[icon] [face][face][face] +N                  2h      <- header row
Mara, Theo and 12 others reacted to your note          <- one sentence, names bold
(❤ 8) (🔥 3) (🤙 3)                                     <- chips, only with 2+ kinds
Juno 21k  Best write-up on relay economics...           <- zap comments, newest 2, then "+N comments"
┌ 2-line excerpt of your note ...........  [thumb] ┐   <- NotificationNoteReference
└──────────────────────────────────────────────────┘
```

- One row per kind when several kinds hit the same note (zaps first, then boosts, then reactions),
  each `[icon][faces][count]`. The sentence row only appears for a single kind.
- Single: the same card with one face: "Mara reacted 🤙 to your note", "Juno zapped 5,000 sats" + the
  comment, "Rafa boosted your note". This replaces the current "a lone zap renders as a full
  `NoteCompose` of the receipt" branch (`singleZap` / `singleNutzap` in `MultiSetCompose`), so a
  fresh additive card and the rebuilt grouped card look alike.
- Faces 28dp, overlapping by 6dp, up to 6, then `+N`. Tapping the faces opens the existing
  reactions detail (today's `AuthorGallery` row, expanded).
- Target heights: single about 110dp, grouped 170 to 260dp, against one full post today.

## Conversation card

```
[icon] Replied to your note                            <- context line
[avatar] Theo  @theo · 12m                       ⋮     <- normal author row
┌ You: Shipping the new relay picker today… ┐          <- reply: one line
Full body text, unclipped                               <- normal note body
[ media, per post layout ]                              <- bleeds in Borderless
 💬   ↻   ♡   ⚡                                        <- reactions row
```

- Context line labels: Replied to your note / Replied in a thread you're in / Mentioned you / Quoted
  your note / Commented on your note / Direct message · N new.
- Quote: the quoted note, when it is the user's, is the same 2-line reference box instead of the
  full embedded quote.
- DMs: group by chatroom within a burst (same rule as reactions: one card per chat per day) and
  show the last 3 messages as bubbles plus "Open chat". Replies, mentions and quotes are never
  grouped.

## Per post layout

Built on one grid, `[slot | body]`, so the three layouts differ in two values only:

| | Indented | Full Width | Borderless |
|---|---|---|---|
| Icon / picture slot | 55dp column, icon top-end | 35dp, icon centred | 35dp, icon centred |
| Body | in the column beside the slot | full width under the header | full width under the header |
| Conversation media | inside the column, rounded | full width, rounded | edge to edge (`NoteHorizontalPadding` bleed) |
| Activity reference | in the column | full width | full width (never bleeds: it is a reference) |

`GalleryIconColumn` already encodes the slot rule; the activity reference box and the context line
reuse it.

## Code map

Survey of what exists, and what each piece becomes:

- `ui/note/MultiSetCompose.kt`: **rewrite the body**. Keep `MultiSetCard` and the click/long-press
  handling. Drop the inner `NoteCompose(isBoostedNote = true)` in both branches and the
  `singleZap`/`singleNutzap` large-card branch. Galleries become facepile rows; the zap gallery keeps
  `ParseAuthorCommentAndAmount` / `ZapAmountCommentNotification` for the comment text.
- **New** `ui/note/NotificationNoteReference.kt` (commonsUI): 2-line excerpt of a `Note` (its
  content through the existing rich-text parser in a plain, non-expanding mode), the first image or
  video thumbnail at 44dp, "Your note" caption when needed, click to `routeFor(note)`. Used by
  activity cards, profile zaps (`ZapUserSetCompose`, reference = "your profile") and quotes.
- `ui/screen/loggedIn/notifications/CardFeedView.kt` `NoteCardCompose`: wrap `NoteCompose` with a
  context line, and pass a new `ReplyRenderType.EXCERPT` (one-line "You: …" box) instead of `FULL`.
  `ReplyRenderType` lives in `ui/note/types/Text.kt`; `LINE` ("replying to @name") stays for others.
- Quote compaction: a `CompositionLocal` (e.g. `LocalCompactOwnQuotes`) read by the quote renderer so
  a quote of the logged-in user's note renders as `NotificationNoteReference`. Set only by
  `NoteCardCompose`.
- Context reason: computed in `commons` (headless) from the event: reply to a note of mine, reply in
  a thread I'm in, `q` tag of mine, kind 1111 on mine, else mention. Return an enum; `commonsUI` words
  it.
- `ui/note/MessageSetCompose.kt` + `MessageSetCard`: `MessageSetCard` carries a list of notes from
  one chatroom; `CardFeedContentState.convertToCard` groups them per chat per epoch day, as it does
  for reactions. `containsEventId` must search the list.
- `ZapUserSetCompose` / `NutzapUserSetCompose`: same activity card, reference = the user's profile
  row (picture + name), no about text.
- `BadgeCompose`, `ChannelInviteCard`, `ConcordDirectInviteNotificationCard`: unchanged.

## Strings (commonsUI `composeResources`)

New keys, positional arguments: `notification_replied_to_your_note`,
`notification_replied_in_thread`, `notification_mentioned_you`, `notification_quoted_your_note`,
`notification_commented_on_your_note`, `notification_dm_new` (plural), `notification_your_note`,
`notification_reacted_summary` (plural: "%1$s, %2$s and %3$d others reacted to your note"),
`notification_zapped_amount`, `notification_boosted_your_note`, `notification_more_comments`
(plural), `notification_open_chat`.

## Open questions

1. Should the activity reference stay at 2 lines for long-form (kind 30023), or show the title?
   Proposal: title + summary line.
2. Unread tint: keep today's `calculateBackgroundColor` per card. Should grouped cards also mark which
   faces are new since last read?
3. Do we still want a way to see the full liked note inline (long-press "Show note")? Tap already
   opens the thread.

## Verification when built

Run the desktop app headless at phone size (`AMETHYST_SCREEN=480x900x24`) and at wide size, switch
Settings › App › Post Layout through the three values, and compare the notifications feed with the
mockups. Check a single like, a 14-face group, a mixed zap/boost/like card, a reply, a quote and a
DM burst.
