# commons plans

_Audited 2026-06-30 (+ 2026-09-12 split entry, 2026-09-27 migration and one-UI entries)._

## In progress
| Plan | Summary |
| ---- | ------- |
| [2026-05-04-custom-feeds-plan.md](2026-05-04-custom-feeds-plan.md) | Custom feed creation/discovery/management for Desktop; core model + builder + kind 31890 + desktop UI shipped, but relay-filter layer, DVM marketplace, kind 10090 sync, and list resolution still pending. |
| [2026-05-06-nest-subscription-manager-extraction.md](2026-05-06-nest-subscription-manager-extraction.md) | Split the per-speaker subscription state machine out of `NestViewModel`; only the `ActiveSubscription` stepping-stone is extracted so far. |
| [2026-09-27-one-ui-android-desktop.md](2026-09-27-one-ui-android-desktop.md) | **The target.** Android ships on laptops, so the whole UI (screens + navigation shell) moves to `commonsUI`, `amethyst` becomes an Android shim, and a new JVM `desktopApp` renders the same UI. Measures Wave 4: `Account`'s 77-file move-group, its 5 blocked files and 14 seams, and `AccountViewModel`'s dependencies; sequences the rest. |
| [2026-08-30-commons-migration-sweep.md](2026-08-30-commons-migration-sweep.md) | The running log of moving `amethyst/` code into `commons`/`commonsUI`: waves, what moved each round. `LocalCache` has moved. Its STAY list and Wave 2 part B are superseded by the one-UI plan above. |
| [2026-05-30-amethyst-to-commons-migration.md](2026-05-30-amethyst-to-commons-migration.md) | The original roadmap for the same move; superseded in practice by the sweep tracker above. |
| [2026-04-21-event-renderer.md](2026-04-21-event-renderer.md) | Cross-platform UI-agnostic `RenderedEvent` subsystem shared by Amy, Desktop, and Android. Core, 12 kind renderers and the JSON formatter shipped (amy `notes show/thread/feed`, `notifications`); the Compose formatter and the Android/Desktop switch-over remain. |

## Queued
| Plan | Summary |
| ---- | ------- |
| [2026-10-07-wot-network-index.md](2026-10-07-wot-network-index.md) | Download every NIP-85 kind 30382 card from the user's trust provider (~300k) into a 6 MB on-disk sorted index (not LocalCache), kept current with small updates, to gate DM Known/New, Curated notifications and replies by a minimum trust score; includes Brainstorm onboarding. Decisions under review, not started. |
| [2026-08-03-poll-results-page.md](2026-08-03-poll-results-page.md) | Extended NIP-88 poll results page (per-option counts + who voted for what) for Android and Desktop; also specifies four tally-correctness fixes and the missing poll-relay subscription. Proposed, not started. |

## Shipped
| Plan | Summary |
| ---- | ------- |
| [2026-09-12-audit-findings.md](2026-09-12-audit-findings.md) | Bug/performance audit of `commons` + `commonsUI` after the split: 34 verified fixes shipped, 4 deferred with rationale, 6 rejected. |
| [2026-09-12-commons-ui-split.md](2026-09-12-commons-ui-split.md) | Split the Compose half of `commons` into the new `:commonsUI` module (same packages, `api(:commons)`), so `cli` no longer carries Compose/Skiko; records the classification method and follow-ups. |

## Archived (shipped)
| Plan | Summary |
| ---- | ------- |
| [archive/2026-05-04-custom-feeds-testing-sheet.md](archive/2026-05-04-custom-feeds-testing-sheet.md) | Test sheet recording the completed custom-feeds phases (unit + manual coverage). |
| [archive/2026-05-05-feed-builder-enhancements-plan.md](archive/2026-05-05-feed-builder-enhancements-plan.md) | Six FeedBuilderDialog enhancements (npub decode, author search, kind filters, edit/delete, save-as-feed, exclude authors) — all landed. |
