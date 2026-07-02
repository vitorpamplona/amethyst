# Manual testing sheet — Desktop Web-of-Trust Score Badges

Plan: `docs/plans/2026-07-01-feat-desktop-wot-score-plan.md`

Run with `./gradlew :desktopApp:run`. Sign in with an account that has a
follow list (WoT is meaningless without one).

## Pre-flight

- **Followed authors' kind-3 events** need to be reachable via the
  configured index relays. On first launch, badges may take a couple of
  seconds to appear while the batch REQ completes.
- Hashtag-spam PR (#3431) providers must be live — WoTBadgedAvatar reads
  `LocalSpamExemptKeys` for the self+follows hide predicate.

## Scenarios

| # | Test | Expected |
|---|------|----------|
| **T1** | **Cold start with ≥50 follows.** Log in, watch avatars for 2 s. | Batch REQ fires once; badges appear on some strangers within ~2 s. |
| **T2** | **No badge on self.** Open own profile in a Profile column. | Own header avatar has no chip regardless of any follower kind-3s. |
| **T3** | **No badge on followed authors.** Any note by a person you follow. | Card renders with clean avatar, no chip. |
| **T4** | **Badge on a stranger who's followed by 2 of your follows.** Find such a note (or contrive one). | Small chip showing "2" bottom-right of avatar. |
| **T5** | **Tooltip on hover.** Hover the badge for ~1 s. | Plain tooltip: "N of the people you follow follow this person". |
| **T6** | **99+ overflow.** Simulate a pubkey scored 200+ (e.g. a well-connected celebrity in your graph). | Badge shows `99+`. |
| **T7** | **No layout shift.** Scroll a busy column while badges arrive mid-scroll. | Avatar sizes don't jump — badge overlays the existing avatar bounds. |
| **T8** | **Kind-3 churn.** Watch a followed author's kind-3 update mid-session (or manually publish one from another client). | Their contribution to affected pubkeys' scores diffs correctly; no double-counting. |
| **T9** | **Follow someone new.** Follow a fresh pubkey via the UI. | Their kind-3 is fetched via a subsequent `loadKind3Batched`; anyone they follow gets their score incremented once their kind-3 arrives. |
| **T10** | **Unfollow someone.** Unfollow an existing follow via the UI. | Every pubkey they were crediting has their score decremented; some may drop to 0 and lose their badge. |
| **T11** | **Guardrail (mega-follow account).** Log in with an account following ≥ 2 000 pubkeys. | `LocalWoTReady` becomes true immediately; no badges anywhere; no batch REQ fires. |
| **T12** | **Empty graph.** Log in with an account following 0 people. | `LocalWoTReady` becomes true after the 2 s fallback timeout; no badges. |
| **T13** | **`consumeContactList` prerequisite fix.** From another client, watch a kind-3 event from a follower arrive while you're logged in. | Your own `_followedUsers` (used by feed filters, sidebar) stays unchanged. Verify by opening a filtered feed and confirming it hasn't broken. |
| **T14** | **Account switch.** Switch to a different logged-in account. | Old scores gone; new account's follow set drives new WoT map. No leaked badges. |
| **T15** | **amy wot get.** `./gradlew :cli:installDist && cli/build/install/cli/bin/amy wot get <npub>` (against an account with kind-3 events in `~/.amy/shared/events-store/`). | Output: `pubkey=<hex> score=<n>` or JSON with `--json`. |
| **T16** | **amy wot list.** `amy wot list --threshold 3 --limit 20 --json`. | JSON of top-20 pubkeys with score ≥ 3, sorted desc. |
| **T17** | **amy wot sync.** `amy wot sync` (with follows in local store). | Runs a chunked kind-3 REQ against outbox relays, stores fresh events. Subsequent `amy wot get` reflects the update. |

## Known v1 limitations

- **Notification-tab avatars are not badged.** Notifications use a custom
  56 dp compact card that doesn't route through `NoteCard` / `UserAvatar`.
  Deferred to v2.
- **Search-result "person" cards** (via `UserSearchCard`) are not badged
  in v1 — they use a different composable.
- **Cross-column reveal state doesn't matter** (WoT is stateless per
  render; no reveal to persist).
- **No filter/threshold gating of feeds or notifications v1** — badges
  are display-only. v2 will add the threshold Setting.
- **`amy wot sync` uses outbox/inbox relays**, not index relays. The
  Desktop app uses `indexRelays`. If the two disagree, results may
  differ slightly. Follow-up ticket: unified `indexRelays` for both.

## Sign-off

- [ ] T1 Cold start
- [ ] T2 No self badge
- [ ] T3 No badge on follows
- [ ] T4 Stranger scored 2
- [ ] T5 Tooltip
- [ ] T6 99+ overflow
- [ ] T7 No layout shift
- [ ] T8 Kind-3 churn
- [ ] T9 New follow
- [ ] T10 Unfollow
- [ ] T11 Guardrail
- [ ] T12 Empty graph
- [ ] T13 Cache prerequisite fix
- [ ] T14 Account switch
- [ ] T15 amy wot get
- [ ] T16 amy wot list
- [ ] T17 amy wot sync

Tester: ________________  Date: ________________
