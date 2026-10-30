# Chess on the app's feeds

Status: implemented 2026-10-30 (desktop checked headless, read-only account; a full game between
two signing accounts and Android are not yet run).

## Why

The chess screens loaded games differently from the rest of the app. A `ChessLobbyLogic` polled a
hard-coded list of three relays (`ChessConfig`) every 2 to 15 seconds with one-shot REQs
(`ChessRelayFetchHelper`). It ran a second, callback-based subscription beside that, and kept its
own lobby state (`ChessLobbyState`, `LiveChessGameState`) that had to be reconciled with both.
It drew relay progress, sync and broadcast banners and a relay settings sheet. Moves went to the
same three relays through a custom broadcaster.

## What it is now

The game is the data: a Jester start note in `LocalCache`. Every move is linked to it as a reply
(`computeReplyTo`), so a game is `startNote` plus `startNote.replies`.

- **Lobby** (`ChessLobbyScreen`): two tabs, like Polls. *Your Games* (`ChessMyGamesFeedFilter`)
  and *Lobby* (`ChessLobbyFeedFilter`), both `FeedContentState`s over start notes, newest
  activity first. The lobby follows the top-bar list (`defaultChessFollowList`, `FollowListSlot.CHESS`,
  `liveChessFollowListsPerRelay`):
  - `ChessFilterAssembler` (a `TopNavFeedFilterAssembler`) asks chosen authors for their kind 30,
    and Global or one relay for Jester starts only (`#e` START_REF), since kind 30 is shared with
    NKBIP-03;
  - `ChessLobbyGamesFilterAssembler` asks for the user's own games (outbox `authors`; inbox `#p`
    and jesterui's private challenge ref) and streams the moves of the listed games (`#e`).
- **Card**: a start note renders as `ChessGameCard` in any feed: players, status (your turn, whose
  turn, result), board, and Accept on a seat the user can take. Its click goes to `Route.ChessGame`.
- **Board** (`ChessGameScreen`): `ChessGameViewModel` rebuilds a `ChessGameSnapshot` from the
  start note and its replies whenever one arrives. `ChessGameFilterAssembler` keeps one REQ open
  per open board, on the relays the game was seen on plus the players' and the user's, so those
  relays stay connected and moves stream in. There is no polling.
- **Publishing**: `account.signAndComputeBroadcast`, consumed into LocalCache like any event.
  `BroadcastRelayPlanner` adds the relays the game's start was seen on, where a jesterui opponent
  listens.
- **Rules** (`ChessStateReconstructor`): only the two seated players' moves count. An open seat
  goes to the earliest mover; on a jesterui direct challenge, only to the player whose private ref
  matches.

No connection, sync or broadcast status is shown: chess behaves like every other feed.

## Next

Protocol gaps (resign/draw messages, colour field names, chain validation, notifications) are in
quartz's `quartz/plans/2026-10-30-jester-chess-gaps.md`, waiting on a decision.
