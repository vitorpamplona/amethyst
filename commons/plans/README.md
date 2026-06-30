# commons plans

_Audited 2026-06-30. 6 plans: 2 shipped (archived), 2 in-progress, 2 queued, 0 abandoned._

## In progress
| Plan | Summary |
| ---- | ------- |
| [2026-05-04-custom-feeds-plan.md](2026-05-04-custom-feeds-plan.md) | Custom feed creation/discovery/management for Desktop; core model + builder + kind 31890 + desktop UI shipped, but relay-filter layer, DVM marketplace, kind 10090 sync, and list resolution still pending. |
| [2026-05-06-nest-subscription-manager-extraction.md](2026-05-06-nest-subscription-manager-extraction.md) | Split the per-speaker subscription state machine out of `NestViewModel`; only the `ActiveSubscription` stepping-stone is extracted so far. |

## Queued
| Plan | Summary |
| ---- | ------- |
| [2026-04-21-event-renderer.md](2026-04-21-event-renderer.md) | Cross-platform UI-agnostic `RenderedEvent` subsystem shared by Amy, Desktop, and Android; not started. |
| [2026-05-30-amethyst-to-commons-migration.md](2026-05-30-amethyst-to-commons-migration.md) | Roadmap to move shared `amethyst` Android code into `commons`; keystone `Account`/`LocalCache` extraction not begun. |

## Archived (shipped)
| Plan | Summary |
| ---- | ------- |
| [archive/2026-05-04-custom-feeds-testing-sheet.md](archive/2026-05-04-custom-feeds-testing-sheet.md) | Test sheet recording the completed custom-feeds phases (unit + manual coverage). |
| [archive/2026-05-05-feed-builder-enhancements-plan.md](archive/2026-05-05-feed-builder-enhancements-plan.md) | Six FeedBuilderDialog enhancements (npub decode, author search, kind filters, edit/delete, save-as-feed, exclude authors) — all landed. |
