# Amethyst plans index

_Cross-module roll-up of every `plans/` folder. Surveyed 2026-06-30._

Plans in this repo are **decentralized**: each module keeps its own design docs
in `<module>/plans/YYYY-MM-DD-<slug>.md` (see `.claude/CLAUDE.md` →
"Plans per module"). There is no single plans directory — **this file is the
master index** that stitches the per-folder indexes together.

Each plan carries a `Status:` header (shipped | in-progress | queued |
abandoned) backed by codebase evidence. **Shipped** plans are moved into each
folder's `archive/`; live work stays at the top level. For the full per-folder
listing (including archived plans), open that folder's `README.md`.

> `docs/plans/` is the **frozen** legacy global folder — it is indexed here for
> completeness, but new plans must go in the owning module's `plans/` folder.

## Totals

**142 plans** across 10 folders:

| Status | Count |
| ------ | ----: |
| shipped (archived) | 122 |
| in-progress | 9 |
| queued | 8 |
| abandoned | 3 |

## By module

| Module | Plans | Shipped | In-prog | Queued | Aband. | Index |
| ------ | ----: | ------: | ------: | -----: | -----: | ----- |
| amethyst | 21 | 19 | 1 | 1 | 0 | [amethyst/plans](amethyst/plans/README.md) |
| nestsClient | 26 | 23 | 1 | 2 | 0 | [nestsClient/plans](nestsClient/plans/README.md) |
| desktopApp | 13 | 10 | 2 | 1 | 0 | [desktopApp/plans](desktopApp/plans/README.md) |
| quartz | 9 | 7 | 0 | 2 | 0 | [quartz/plans](quartz/plans/README.md) |
| commons | 6 | 2 | 2 | 2 | 0 | [commons/plans](commons/plans/README.md) |
| cli | 6 | 5 | 1 | 0 | 0 | [cli/plans](cli/plans/README.md) |
| quic | 4 | 3 | 0 | 0 | 1 | [quic/plans](quic/plans/README.md) |
| quic/interop | 1 | 1 | 0 | 0 | 0 | [quic/interop/plans](quic/interop/plans/README.md) |
| geode | 4 | 4 | 0 | 0 | 0 | [geode/plans](geode/plans/README.md) |
| docs (frozen) | 52 | 48 | 2 | 0 | 2 | [docs/plans](docs/plans/README.md) |

## Live work (not shipped)

Everything still open, across all modules. Shipped plans are omitted here — find
them under each folder's `archive/` via the per-module index above.

### In progress (9)

| Module | Plan | Summary |
| ------ | ---- | ------- |
| amethyst | [ios-support](amethyst/plans/2026-05-24-ios-support.md) | KMP-to-iOS port; quartz/commons iOS targets configured (Phase 1) but no `iosApp` module yet. |
| commons | [custom-feeds-plan](commons/plans/2026-05-04-custom-feeds-plan.md) | Custom feeds; model + builder + kind 31890 + desktop UI shipped, but relay-filter layer, DVM marketplace, kind 10090 sync, list resolution pending. |
| commons | [nest-subscription-manager-extraction](commons/plans/2026-05-06-nest-subscription-manager-extraction.md) | Split per-speaker subscription state machine out of `NestViewModel`; only the `ActiveSubscription` stepping-stone extracted. |
| desktopApp | [wallet-zapping-test-coverage](desktopApp/plans/2026-05-12-feat-desktop-wallet-zapping-test-coverage-plan.md) | NWC handler + RPC round-trip tests shipped; wallet-column-state and zap-dialog-logic tests still missing. |
| desktopApp | [napplet-desktop-host](desktopApp/plans/2026-06-21-napplet-desktop-host.md) | Desktop NIP-5A/5D host; shared core extractions done, desktop engine/scheme-handler/transport/UI edge not built. |
| cli | [cashu-cli](cli/plans/2026-05-28-cashu-cli.md) | NIP-60/61/87 Cashu wallet verbs in amy; full command surface ships, production-mint interop harness pending. |
| nestsClient | [t16-closure-roadmap](nestsClient/plans/2026-05-07-t16-closure-roadmap.md) | Priorities 1 & 2 closed and suite passes, but CI gating deferred and framesPerGroup rerun + two upstream items open. |
| docs | [viewport-aware-metadata-loading](docs/plans/2026-04-29-perf-viewport-aware-metadata-loading-plan.md) | Base preloader/rate-limiter infra exists but the LazyListState/snapshotFlow viewport selection isn't clearly wired. |
| docs | [macos-bunker-relogin](docs/plans/2026-06-18-fix-desktop-macos-bunker-relogin-plan.md) | PR 1 defense-in-depth shipped, but the cold-boot root cause is still open/unidentified. |

### Queued (8)

| Module | Plan | Summary |
| ------ | ---- | ------- |
| amethyst | [napplet-inter-applet](amethyst/plans/2026-06-20-napplet-inter-applet.md) | NAP-INC / NAP-INTENT inter-applet messaging; prerequisites (multi-applet hosting, archetype registry, `MESSAGING` capability) not built. |
| quartz | [local-headers-explorer](quartz/plans/2026-05-08-local-headers-explorer.md) | Headers-only Bitcoin P2P client to verify NIP-03 OTS attestations without a trusted block explorer. |
| quartz | [giftwrap-deletion-requests](quartz/plans/2026-06-12-giftwrap-deletion-requests.md) | Let a recipient-authored kind-5 delete/block a gift wrap (kind 1059) addressed to them. |
| commons | [event-renderer](commons/plans/2026-04-21-event-renderer.md) | Cross-platform UI-agnostic `RenderedEvent` subsystem shared by Amy, Desktop, Android; not started. |
| commons | [amethyst-to-commons-migration](commons/plans/2026-05-30-amethyst-to-commons-migration.md) | Roadmap to move shared `amethyst` Android code into `commons`; keystone `Account`/`LocalCache` extraction not begun. |
| desktopApp | [embedded-wallet-phase2-research](desktopApp/plans/2026-05-21-embedded-wallet-phase2-research.md) | Research for an embedded self-custodial Lightning wallet (Breez/ldk-node/lightning-kmp); parked, no code. |
| nestsClient | [cross-stack-interop-ci-gating](nestsClient/plans/2026-05-07-cross-stack-interop-ci-gating.md) | CI gating for the cross-stack interop suite; infra built then removed over wallclock cost, kept as a ready revisit target. |
| nestsClient | [framespergroup-production-rerun](nestsClient/plans/2026-05-07-framespergroup-production-rerun.md) | Re-run the two-phone field tests to settle the framesPerGroup test-pin (5) vs default (50); needs prod-rig access. |

### Abandoned (3)

| Module | Plan | Summary |
| ------ | ---- | ------- |
| quic | [congestion-control](quic/plans/2026-05-05-congestion-control.md) | NewReno congestion control parked indefinitely; the real concern was solved by the smaller `SendBuffer.bestEffort` fix instead. |
| docs | [desktop-relay-config-single-source](docs/plans/2026-04-23-feat-desktop-relay-config-single-source-plan.md) | Single `DesktopRelayConfig` class never built; relay state landed as `DesktopRelayCategories`/`LocalRelayCategories` instead. |
| docs | [macos-vlc-bundled-discovery](docs/plans/2026-05-18-fix-macos-vlc-bundled-discovery-plan.md) | macOS bundled-VLC `setenv` discovery fix; moot after VLC/VLCJ was removed entirely in the kdroidFilter migration. |
