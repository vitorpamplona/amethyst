# Desktop Polls (NIP-88) — Manual Testing Sheet

**Feature:** render + vote + create polls on Amethyst Desktop
**Branch:** `worktree-feat+desktop-polls` (worktree `.claude/worktrees/feat+desktop-polls`)
**Plan:** `docs/plans/2026-07-16-feat-desktop-polls-nip88-plan.md`
**Status when written:** code-complete; compile + unit tests + spotless GREEN; **manual run not yet done.**

## Automated gates already passing
- [x] `./gradlew :commons:compileKotlinJvm :desktopApp:compileKotlin` — clean
- [x] `./gradlew :commons:jvmTest --tests "*nip88Polls*"` — 5 pass (dedup, tally %, WoT sort, hasVoted, idempotency)
- [x] `./gradlew :desktopApp:test --tests "*Poll*"` — 2 pass (response links into tally; relay-echo dedup)
- [x] `./gradlew :commons:spotlessKotlinCheck :desktopApp:spotlessKotlinCheck` — clean

## How to run
```bash
cd .claude/worktrees/feat+desktop-polls
./gradlew :desktopApp:run
```
Log in (existing account, or NIP-46 bunker). Use a relay set that carries polls — good sources: relays where clients post NIP-88 polls, or create one yourself (Test C) and read it back. A **second client** (Amethyst Android, or `amy`) is useful to cross-verify events on the wire.

---

## A. Rendering (feed + thread)
- [ ] A1. A kind-1068 poll appears in the **Home/Global feed as a poll card** (description + options), NOT as plain text. *(If polls never show: verify `DesktopFeedFilters.isFeedNote` includes `PollEvent` and `FEED_KINDS` has 1068.)*
- [ ] A2. Open the poll in a **thread column** → renders as a poll card there too.
- [ ] A3. Single-choice poll shows **radio**-style option rows; multi-choice shows **checkbox**-style rows with a **Submit** button.
- [ ] A4. Before voting, **no percentages/tally are shown** — only actionable options + a **"View results"** button.
- [ ] A5. Tapping **"View results"** reveals the tally without casting a vote; a way back to voting exists (unless ended/author).
- [ ] A6. A poll authored by **you**, seen in your own feed, shows **results-only** (you cannot vote).
- [ ] A7. An **ended** poll (deadline in the past) shows results-only, no vote controls.
- [ ] A8. Media/description of the poll render via the normal note card (links, images in the description behave as usual).

## B. Voting
- [ ] B1. Cast a **single-choice** vote → card immediately flips to results (optimistic), your option marked as your vote.
- [ ] B2. Results show a **% bar per option**, a **winning** highlight, and **voter avatars** (up to ~4) + "+N".
- [ ] B3. Voter avatars are **ordered with people you follow first** (WoT). Verify by having a followed account vote — their avatar should sort ahead of strangers.
- [ ] B4. The bar does **not** do a distracting 0→N sweep when opening an already-tallied poll (first-frame animation guard).
- [ ] B5. **Multi-choice**: select 2 options → Submit → both recorded; results reflect both.
- [ ] B6. **Multi-choice empty submit is rejected** — with nothing selected, Submit does nothing / is disabled (no empty response event sent).
- [ ] B7. **Change vote**: after voting, use **"Change vote"** → re-open options → pick a different option → tally updates so the **new** choice wins for you (newest response wins).
- [ ] B8. Cross-check on a second client (Android/amy): your vote is a **kind-1018** event referencing the poll via a lowercase `e` tag.
- [ ] B9. **Scroll-away during send** (stress the scope fix): cast a vote and immediately scroll the poll out of view. Re-find it / check a second client — the vote should have **broadcast to relays**, not just shown locally. *(This validates `voteOnPoll` runs on the long-lived `appScope`, not the card scope.)*

## C. Creating a poll
- [ ] C1. Open the composer; toggle **Poll** on → poll option editor appears; image attachment is disabled while Poll is on.
- [ ] C2. Add/remove options; **minimum 2 non-blank** options enforced before send is allowed.
- [ ] C3. Toggle **Single vs Multiple** choice.
- [ ] C4. Set a **duration** (Never / 1d / 3d / 7d). "Never" = open-ended (no deadline).
- [ ] C5. Send → a **kind-1068 PollEvent** is published (verify on a second client): correct options, `polltype`, and `endsAt` (absent for "Never").
- [ ] C6. The poll you created appears in your feed and is votable from **another** account/client; its tally updates as votes arrive.

## D. Edge cases
- [ ] D1. Poll with an unusually **long option label** wraps/renders without breaking layout.
- [ ] D2. A poll received with **0 options** (malformed) does not crash the feed (renders degraded / skipped).
- [ ] D3. Receiving **many responses from multiple relays** converges to a stable, non-inflated tally (no double counting of the same response).
- [ ] D4. Late votes arriving **after** a poll's deadline: they may still count in the tally, but the card stays results-only (no re-vote UI).

## E. Regression (nothing else broke)
- [ ] E1. Normal text notes, reposts, and reactions still render + behave in the feed.
- [ ] E2. Composing a normal note (Poll toggle OFF) works exactly as before, including image attachment.
- [ ] E3. Thread view still loads reactions/zaps/reposts for non-poll notes.

---

---

## F. Search "Polls" content-type filter (added 2026-07-20)
*Feature: filter search to only polls + interact with them. Plan: `docs/plans/2026-07-20-feat-desktop-search-polls-facet-plan.md`.*

- [ ] F1. Open the **Search** column → advanced filter panel shows a **"Polls"** chip alongside Notes/Articles/Media/Channels/Communities/Wiki.
- [ ] F2. Enter a query, select **Polls** → results contain **only** poll notes (kind 1068); other content types are excluded.
- [ ] F3. Results appear under a dedicated **"Polls" section** (poll icon) and render as **interactive `DesktopPollCard`** — options visible, not plain text.
- [ ] F4. **Vote from search** (dedicated Search screen): cast a vote on a poll in results → flips to results/optimistic tally, and a kind-1018 event is published (cross-check on a 2nd client).
- [ ] F5. Deselect the Polls chip → results return to mixed content; other facets still work.
- [ ] F6. Section collapse/expand + "Show all N more" work like the other search sections.
- [ ] F7. **Feed header quick-search** (the search box in the feed header): polls render as cards **and are now votable** (account threaded 2026-07-20).

## G. Cross-context consistency fixes (2026-07-20)
*Fixes for reported bugs: "can't always tap depending on how it's opened" + "only see my own answer, no other tallies".*

- [ ] G1. **Thread view:** open a poll into a thread → you can vote, and **after voting the card correctly shows your choice** as selected (previously your vote-state didn't register — missing `myPubKeyHex`).
- [ ] G2. **Thread tallies:** a poll opened in a thread shows **other people's votes**, not just yours.
- [ ] G3. **Profile tabs (Notes/Replies):** polls on a user's profile are votable and reflect your vote correctly.
- [ ] G4. **Dedicated Search tallies:** filter to Polls → results now show **existing tallies from others** (search fetches kind-1018 responses via `requestInteractions`), not just your own vote.
- [ ] G5. **Feed header quick-search:** polls there are now **votable** (account threaded).
- [ ] G6. **Consistency:** the SAME poll shows consistent vote-state + tallies whether opened in feed, thread, profile, or search.

**Remaining known gaps (expected):**
- **Notifications tab** renders polls as the compact notification card (not interactive) — out of scope.
- **Poll posted as a thread *reply*** (not root) renders via the thread's custom reply card (not interactive) — edge case, deferred.
- Feed-header quick-search fetches tallies only after the poll is also seen in a context that requests interactions; the **dedicated Search** column always fetches them.

## Known caveats (expected, not bugs)
- **Same-second re-vote:** if you change your vote **within the same 1-second** as the first, the tally may not flip until a later-second vote (tie-break on `createdAt` uses strict `>` with no id fallback). Wait ~1s between re-votes to see B7 flip reliably.
- **Option labels are plain text (v1):** links/custom-emoji inside an option label are shown literally, not hyperlinked/rendered (no desktop rich-text path for option labels yet).
- **Deadline = preset chips (v1):** Never/1d/3d/7d instead of a full date/time picker.
- **Not wired this PR (deferred):** poll rendering in profile/bookmarks/search/notifications tabs (still show as plain notes there); poll-draft round-trip; zap-weighted polls.

## If something fails — where to look
| Symptom | Check |
|---|---|
| Polls never appear in feed | `feeds/DesktopFeedFilters.kt` `isFeedNote` (PollEvent), `subscriptions/FilterBuilders.kt` `FEED_KINDS`=…,1068 |
| Poll shows but tally always empty | kind-1018 sub: `ui/FeedScreen.kt` fetch-interactions filter + `DesktopRelaySubscriptionsCoordinator.requestInteractions` (`e` tag) |
| Vote shows locally but never reaches relays | `DesktopPollCard.castVote` must launch on `localCache.appScope`; `voteOnPoll` in `ui/NoteActions.kt` |
| Double-counted votes | `DesktopLocalCache.consumePollResponse` new-event gate (line ~385) |
| Created poll malformed | `ComposeNoteDialog.publishPoll` → `PollEvent.build` options/type/endsAt |
