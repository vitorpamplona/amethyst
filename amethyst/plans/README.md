# amethyst plans

_Audited 2026-06-30. 21 plans: 19 shipped (archived), 1 in-progress, 1 queued, 0 abandoned._

## In progress
| Plan | Summary |
| ---- | ------- |
| [2026-05-24-ios-support.md](2026-05-24-ios-support.md) | Incremental KMP-to-iOS port; quartz/commons iOS targets are configured (Phase 1) but no `iosApp` module exists yet. |

## Queued
| Plan | Summary |
| ---- | ------- |
| [2026-06-20-napplet-inter-applet.md](2026-06-20-napplet-inter-applet.md) | NAP-INC / NAP-INTENT inter-applet messaging — deferred; prerequisites (multi-applet hosting, archetype registry, `MESSAGING` capability) not yet built. |

## Archived (shipped)
| Plan | Summary |
| ---- | ------- |
| [archive/2026-05-14-onchain-zaps.md](archive/2026-05-14-onchain-zaps.md) | NIP-BC (kind 8333) onchain Bitcoin zaps — hand-rolled `quartz/nipBCOnchainZaps/` consensus layer plus Android send/receive/display. |
| [archive/2026-05-25-appfunctions-signer-prompts.md](archive/2026-05-25-appfunctions-signer-prompts.md) | How AppFunctions write verbs acquire signatures across the three signer types; write verbs now ship. |
| [archive/2026-05-26-appfunctions-gemini-discovery.md](archive/2026-05-26-appfunctions-gemini-discovery.md) | Verifying Gemini-side discovery of Amethyst's AppFunctions; description-based (`isDescribedByKDoc`) discovery shipped. |
| [archive/2026-05-26-appfunctions-screens-as-verbs.md](archive/2026-05-26-appfunctions-screens-as-verbs.md) | Map every Amethyst screen's feed filter to an AppFunction/MCP verb; 46 verbs now ship. |
| [archive/2026-05-26-avif-implementation-plan.md](archive/2026-05-26-avif-implementation-plan.md) | Task-by-task plan for AVIF support via a `MediaMimeTypes` helper across the upload pipeline. |
| [archive/2026-05-26-avif-support.md](archive/2026-05-26-avif-support.md) | Comprehensive design making AVIF a first-class image format on every Amethyst surface. |
| [archive/2026-05-27-avif-instrumented-tests-design.md](archive/2026-05-27-avif-instrumented-tests-design.md) | Design for on-device AVIF upload-pipeline regression tests with committed fixtures. |
| [archive/2026-05-27-avif-instrumented-tests-plan.md](archive/2026-05-27-avif-instrumented-tests-plan.md) | Implementation plan for the AVIF instrumented + JVM unit tests and their fixtures. |
| [archive/2026-06-01-dm-live-tail-and-history-slices.md](archive/2026-06-01-dm-live-tail-and-history-slices.md) | DM loading split into a fixed live tail plus per-relay backward history paging (`WindowLoadTracker` / `RelayLoadingCursors`). |
| [archive/2026-06-19-napplet-sandbox-host.md](archive/2026-06-19-napplet-sandbox-host.md) | Keyless `:napplet`-process WebView host with brokered, consent-gated capabilities for NIP-5A/5D content. |
| [archive/2026-06-20-napplet-ecosystem-audit.md](archive/2026-06-20-napplet-ecosystem-audit.md) | Audit of our napplet shell against the upstream `@napplet` SDK; wire-compat gaps subsequently closed. |
| [archive/2026-06-21-napplet-code-audit.md](archive/2026-06-21-napplet-code-audit.md) | Code audit of the napplet subsystem recording correctness/perf/refactor fixes and deferred items. |
| [archive/2026-06-21-napplet-sdk-conformance-audit.md](archive/2026-06-21-napplet-sdk-conformance-audit.md) | Feature-by-feature SDK conformance audit; the four conformance breakers were fixed and pinned by tests. |
| [archive/2026-06-22-napplet-nsite-security.md](archive/2026-06-22-napplet-nsite-security.md) | Security review of the nsite/napplet attack surface; launch-token identity and per-applet origins landed. |
| [archive/2026-06-23-napplet-nap-theme-notify-inc.md](archive/2026-06-23-napplet-nap-theme-notify-inc.md) | Add the `theme`, `notify`, and `inc` NAP domains so demo napplets boot; capabilities + `NappletIncBus` shipped. |
| [archive/2026-06-24-napplet-embedded-tabs.md](archive/2026-06-24-napplet-embedded-tabs.md) | Embedded warm bottom-bar napplet/nsite/browser tabs via `SurfaceControlViewHost`, in the new `:nappletHost` module. |
| [archive/2026-06-25-embed-text-selection-native-parity.md](archive/2026-06-25-embed-text-selection-native-parity.md) | Host-drawn text selection (handles, magnifier, toolbar, IME proxy) for embedded sandboxed surfaces. |
| [archive/2026-06-25-web-app-naming.md](archive/2026-06-25-web-app-naming.md) | Naming overhaul for web-app / nApplet / nSite / favorite surfaces (route, screen, controller renames). |
| [archive/2026-06-26-nsite-napplet-favorite-icons.md](archive/2026-06-26-nsite-napplet-favorite-icons.md) | Derive favorited nSite/nApplet bottom-nav icons from verified manifest blobs (`NappletIconPath`). |
