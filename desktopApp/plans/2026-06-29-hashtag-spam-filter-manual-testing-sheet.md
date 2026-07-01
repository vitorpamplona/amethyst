# Manual testing sheet — Hashtag-Spam Filter (Desktop)

Plan: `docs/plans/2026-06-29-feat-desktop-hashtag-spam-filter-plan.md`

Run with `./gradlew :desktopApp:run`. Sign in with any account that has a
follow list (or use a fresh nsec — global feed still works without follows).

## Setup

1. Confirm default state: filter is **ON**, threshold is **5** on first
   launch. Settings → Content Filters shows Switch=On, slider at 5.
2. Confirm a follow list is populated (Home column should display notes
   from people you follow). Otherwise the follow-exemption test (T4) is a
   no-op.

## Scenarios

| # | Scenario | Expected |
|---|----------|----------|
| **T1** | **Slider live re-collapse.** Open a Home column. Locate a visible note that has the spammy pattern (e.g. 8+ distinct hashtags). Drag the threshold slider to a value below its hashtag count. | Card collapses to placeholder after drag release. No mid-drag jank. |
| **T2** | **Repost spam (precomputed).** Find a `nostr:nevent…` link to a kind-6 repost whose wrapped event has many hashtags, paste into a hashtag column or use Search. After the cache materialises the inner event (give it a second), confirm the repost wrapper renders collapsed. | Wrapper card shows `CollapsedSpamNote` with the inner event's author. |
| **T3** | **Repost spam (uncached fallback).** Same as T2 immediately on first paste, before the cache resolves the inner event. | Wrapper shows collapsed (via `containedPost()` fallback) OR shows normal until cache populates — both acceptable; nothing crashes. |
| **T4** | **Followed-author exemption.** Locate (or temporarily follow) someone who posts 10+ hashtag stuff. Reload Home. | Their posts render normally, never collapsed. |
| **T5** | **Self exemption.** Compose and publish a note with 10 distinct hashtags from the active account. | The note appears normally in your own activity / Home, not collapsed. |
| **T6** | **Long-form exemption.** Open a longform article (kind 30023) that uses many topical tags — via Search → Articles tab, or via a quoted `naddr1…`. | Article renders normally. |
| **T7** | **Thread root auto-expand.** Find a collapsed card in a feed and click it to open the thread. | Root note auto-expands (`forceReveal = true`); 12-hashtag replies inside the thread still appear collapsed. |
| **T8** | **Embedded quote.** Read a note that quotes a hashtag-spam note via `nostr:nevent…`. | The inline embedded card is collapsed. Revealing it does NOT cascade to other places — open the same quoted note in Search and confirm it's still collapsed there (per-call-site `rememberSaveable`). |
| **T9** | **Settings persistence across restart.** Toggle Switch to Off. Restart Desktop. | Open Settings → Content Filters: still Off. Spam notes render uncollapsed. |
| **T10** | **`amy` parity.** From a terminal, run `java -cp <path> com.vitorpamplona.amethyst.cli.Main …` (or any utility that reads the shared `Preferences` node). Or write a one-line Kotlin REPL: `Preferences.userRoot().node("com/vitorpamplona/amethyst/filters").getBoolean("hashtag_spam_enabled", true)`. | Returns the same value Desktop wrote. Reverse: write `false` via the API, relaunch Desktop, observe filter is off. |
| **T11** | **Malformed inner repost JSON.** Test event with a kind-6 wrapper whose `content` field is not parseable JSON. Quickest reproduction: use the unit test `repostWithoutReplyToFallsBackToContainedPost` ✓ already covers the `displayedEvent()` path; manually you can paste a deliberately-broken nevent and watch nothing crash. | No exception; wrapper renders normally (since `displayedEvent()` returns null and `isSpam = false`). |
| **T12** | **Hashtag-feed column.** Subscribe to a hashtag-feed column (e.g. `#bitcoin`). | Posts that are tagged `#bitcoin` AND have many other hashtags still collapse — filter applies inside hashtag columns. |
| **T13** | **Notifications tab (negative).** Receive a mention from someone who used many hashtags in the parent note. Open Notifications. | Compact notification card (56dp) renders normally — does NOT apply the filter (v1 scope: Notifications-tab card is a custom composable, deferred). Documented as a known limitation. |
| **T14** | **Slider does not cause feed recomposition storm.** Open a feed with several visible notes. Open Settings, drag threshold quickly back and forth. | Slider thumb moves smoothly; feed contents do not re-render per-tick (only on drag-end). No frame drops. |
| **T15** | **Toggle off → notes uncollapse live.** With filter ON and a collapsed card visible, toggle the Switch to Off in Settings. | Card immediately uncollapses (StateFlow re-evaluates, `isSpam` becomes false). |
| **T16** | **Threshold change updates label live.** Drag the slider with the Settings sheet open. | The "Hide notes with more than N hashtags" text updates per drag tick. |

## Known v1 limitations to surface during testing

- Cross-call-site reveal: revealing a collapsed card in Home does NOT reveal
  the same card in a Hashtag column or in a thread — each render site has
  its own `rememberSaveable` reveal flag.
- Notifications compact card does NOT respect the filter (v2).
- Follow/unfollow during a session does not re-evaluate already-rendered
  cards in place (follow set is non-reactive at the check site v1; refresh
  feed to update).

## Sign-off

- [ ] T1 Slider live re-collapse
- [ ] T2 Repost spam (precomputed)
- [ ] T3 Repost spam (uncached)
- [ ] T4 Followed-author exemption
- [ ] T5 Self exemption
- [ ] T6 Long-form exemption
- [ ] T7 Thread root auto-expand
- [ ] T8 Embedded quote
- [ ] T9 Settings persistence
- [ ] T10 `amy` parity
- [ ] T11 Malformed inner repost JSON
- [ ] T12 Hashtag-feed column
- [ ] T13 Notifications tab limitation (known)
- [ ] T14 No recomposition storm during drag
- [ ] T15 Toggle off live
- [ ] T16 Threshold label live update

Tester: ________________  Date: ________________
