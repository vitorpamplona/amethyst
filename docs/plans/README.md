# docs plans (frozen legacy)

_Audited 2026-06-30. 52 plans: 48 shipped (archived), 2 in-progress, 2 abandoned._

> This is the frozen global plans folder — new plans go in the owning module's `plans/` folder instead.

## In progress
| Plan | Summary |
| ---- | ------- |
| [2026-04-29-perf-viewport-aware-metadata-loading-plan.md](2026-04-29-perf-viewport-aware-metadata-loading-plan.md) | Viewport-aware feed metadata loading — base preloader/rate-limiter infra exists but the LazyListState/snapshotFlow viewport selection isn't clearly wired. |
| [2026-06-18-fix-desktop-macos-bunker-relogin-plan.md](2026-06-18-fix-desktop-macos-bunker-relogin-plan.md) | macOS cold-boot bunker re-login — PR 1 defense-in-depth shipped, but the actual root cause is still open/unidentified. |

## Abandoned
| Plan | Summary |
| ---- | ------- |
| [2026-04-23-feat-desktop-relay-config-single-source-plan.md](2026-04-23-feat-desktop-relay-config-single-source-plan.md) | Single `DesktopRelayConfig` class — never built; relay state landed as `DesktopRelayCategories`/`LocalRelayCategories` instead. |
| [2026-05-18-fix-macos-vlc-bundled-discovery-plan.md](2026-05-18-fix-macos-vlc-bundled-discovery-plan.md) | macOS bundled-VLC `setenv` discovery fix — moot after VLC/VLCJ was removed entirely in the kdroidFilter migration. |

## Archived (shipped)
| Plan | Summary |
| ---- | ------- |
| [archive/2026-03-05-fix-nip46-relay-isolation-and-init-plan.md](archive/2026-03-05-fix-nip46-relay-isolation-and-init-plan.md) | NIP-46 remote signer wired into desktop with dedicated relay isolation and pool-init fixes. |
| [archive/2026-03-05-nip46-bunker-login-deepened.md](archive/2026-03-05-nip46-bunker-login-deepened.md) | Desktop bunker:// / nostrconnect:// login over quartz NostrSignerRemote. |
| [archive/2026-03-05-nip46-improvements-plan.md](archive/2026-03-05-nip46-improvements-plan.md) | Post-MVP NIP-46 items — auth_url handling and connect validation in quartz. |
| [archive/2026-03-05-nip46-tdd-tests-plan.md](archive/2026-03-05-nip46-tdd-tests-plan.md) | TDD tests reproducing the three NIP-46 relay-isolation bugs. |
| [archive/2026-03-10-feat-desktop-advanced-search-plan.md](archive/2026-03-10-feat-desktop-advanced-search-plan.md) | Desktop advanced search with query operators and form UI (AdvancedSearchPanel/SearchQuery). |
| [archive/2026-03-16-desktop-media-manual-testing-plan.md](archive/2026-03-16-desktop-media-manual-testing-plan.md) | Manual-testing sheet for the desktop media stack (all rows PASS). |
| [archive/2026-03-16-feat-desktop-media-full-parity-plan.md](archive/2026-03-16-feat-desktop-media-full-parity-plan.md) | Desktop media full parity — images, upload, gallery, video/audio playback. |
| [archive/2026-03-18-feat-desktop-cache-navigation-persistence-plan.md](archive/2026-03-18-feat-desktop-cache-navigation-persistence-plan.md) | Cache-centric desktop architecture for navigation persistence (DesktopLocalCache). |
| [archive/2026-03-18-feat-desktop-dm-encrypted-media-plan.md](archive/2026-03-18-feat-desktop-dm-encrypted-media-plan.md) | NIP-17 encrypted DM media send/receive in desktop chat. |
| [archive/2026-03-19-feat-deck-messages-stacked-layout-plan.md](archive/2026-03-19-feat-deck-messages-stacked-layout-plan.md) | Stacked messages layout for narrow deck columns. |
| [archive/2026-03-20-feat-remote-signer-loading-error-ux-plan.md](archive/2026-03-20-feat-remote-signer-loading-error-ux-plan.md) | Remote-signer loading/error UX — commons SigningState holder. |
| [archive/2026-03-22-feat-clean-cache-single-source-of-truth-plan.md](archive/2026-03-22-feat-clean-cache-single-source-of-truth-plan.md) | Desktop feeds migrated to cache-centric DesktopFeedViewModel + FeedContentState. |
| [archive/2026-03-24-feat-article-highlights-notes-plan.md](archive/2026-03-24-feat-article-highlights-notes-plan.md) | Desktop article highlights & note-taking (NIP-84) UI + store. |
| [archive/2026-03-24-feat-weakref-cache-state-extraction-plan.md](archive/2026-03-24-feat-weakref-cache-state-extraction-plan.md) | WeakReference cache (LargeSoftCache) + state-class extraction to commons. |
| [archive/2026-03-24-long-form-reads-manual-testing.md](archive/2026-03-24-long-form-reads-manual-testing.md) | Manual-testing sheet for the long-form Reads feature. |
| [archive/2026-03-30-feat-app-drawer-v1a-plan.md](archive/2026-03-30-feat-app-drawer-v1a-plan.md) | Desktop App Drawer with categories and search (AppDrawer.kt). |
| [archive/2026-03-30-feat-desktop-tor-support-plan.md](archive/2026-03-30-feat-desktop-tor-support-plan.md) | Embedded Tor support on desktop (DesktopTorManager + kmp-tor). |
| [archive/2026-04-01-fix-tor-traffic-leaks-plan.md](archive/2026-04-01-fix-tor-traffic-leaks-plan.md) | Fail-closed Tor-aware client in DesktopHttpClient — no direct connections when Tor ON. |
| [archive/2026-04-02-feat-tor-toggle-restart-ux-plan.md](archive/2026-04-02-feat-tor-toggle-restart-ux-plan.md) | Tor toggle restart UX — confirmation dialog + appRestartKey app rebuild. |
| [archive/2026-04-17-feat-customizable-nav-bar-v1b-plan.md](archive/2026-04-17-feat-customizable-nav-bar-v1b-plan.md) | Customizable nav bar — PinnedNavBarState (pin/unpin/reorder). |
| [archive/2026-04-17-feat-workspace-management-ux-plan.md](archive/2026-04-17-feat-workspace-management-ux-plan.md) | Workspace management UX — CRUD via WorkspaceManager + App Drawer tabs. |
| [archive/2026-04-17-feat-workspaces-v1c-plan.md](archive/2026-04-17-feat-workspaces-v1c-plan.md) | Named layout-preset workspaces with layout-mode switching. |
| [archive/2026-04-17-test-desktop-distribution.md](archive/2026-04-17-test-desktop-distribution.md) | Test matrix for multi-platform desktop distribution (DMG/MSI/DEB/RPM/AppImage). |
| [archive/2026-04-20-feat-relay-power-tools-plan.md](archive/2026-04-20-feat-relay-power-tools-plan.md) | Relay dashboard screen + compose relay picker. |
| [archive/2026-04-21-feat-relay-config-parity-plan.md](archive/2026-04-21-feat-relay-config-parity-plan.md) | Relay config parity — NIP-65/DM/Search/Blocked category editors. |
| [archive/2026-04-21-feat-relay-subscription-wiring-plan.md](archive/2026-04-21-feat-relay-subscription-wiring-plan.md) | Wire relay-config categories into desktop subscriptions (superseded by 04-22 deepened version). |
| [archive/2026-04-22-feat-relay-persistence-counts-per-screen-picker-plan.md](archive/2026-04-22-feat-relay-persistence-counts-per-screen-picker-plan.md) | Relay config persistence, correct counts, per-screen relay editors. |
| [archive/2026-04-22-feat-relay-subscription-wiring-plan.md](archive/2026-04-22-feat-relay-subscription-wiring-plan.md) | Relay-category subscription wiring via LocalRelayCategories CompositionLocal. |
| [archive/2026-04-22-nip-audio-rooms-draft.md](archive/2026-04-22-nip-audio-rooms-draft.md) | NIP draft for audio-rooms join (moq-lite Lite-03), implemented in :nestsClient. |
| [archive/2026-04-22-pure-kotlin-quic-webtransport-plan.md](archive/2026-04-22-pure-kotlin-quic-webtransport-plan.md) | Pure-Kotlin QUIC + HTTP/3 + WebTransport — the :quic module. |
| [archive/2026-04-23-feat-desktop-multi-account-support-plan.md](archive/2026-04-23-feat-desktop-multi-account-support-plan.md) | Desktop multi-account support — AccountSwitcherDropdown + encrypted storage. |
| [archive/2026-04-27-fix-deb-libicu-dependency-plan.md](archive/2026-04-27-fix-deb-libicu-dependency-plan.md) | Relax libicu dependency in .deb packages via post-process script + CI wiring. |
| [archive/2026-04-28-multi-account-testing-sheet.md](archive/2026-04-28-multi-account-testing-sheet.md) | Testing sheet for desktop multi-account. |
| [archive/2026-05-04-fix-bunker-timeouts-and-decryption-plan.md](archive/2026-05-04-fix-bunker-timeouts-and-decryption-plan.md) | Fix bunker timeouts + broken decryption (BunkerResponseDecrypt + same-id retry). |
| [archive/2026-05-14-fix-account-security-hardening-plan.md](archive/2026-05-14-fix-account-security-hardening-plan.md) | Desktop account security hardening — encrypted accounts.json.enc, keychain secrets. |
| [archive/2026-05-22-feat-desktop-note-action-bar-ux-plan.md](archive/2026-05-22-feat-desktop-note-action-bar-ux-plan.md) | Note action bar — long-press details + right-click customize. |
| [archive/2026-05-22-fix-desktop-note-action-counters-and-quote-plan.md](archive/2026-05-22-fix-desktop-note-action-counters-and-quote-plan.md) | Fix reactive counters, quote boost, and boost detail popup. |
| [archive/2026-05-26-feat-desktop-profile-editing-plan.md](archive/2026-05-26-feat-desktop-profile-editing-plan.md) | Desktop profile editing full parity (EditProfileScreen + EditProfileFields). |
| [archive/2026-05-28-feat-desktop-search-spotlight-plan.md](archive/2026-05-28-feat-desktop-search-spotlight-plan.md) | Search spotlight overlay (Cmd+F) + unified feed header bar. |
| [archive/2026-05-28-feat-desktop-visual-personality-plan.md](archive/2026-05-28-feat-desktop-visual-personality-plan.md) | Desktop visual personality overhaul (theme, shimmer, hover, shortcuts). |
| [archive/2026-05-28-fix-feed-header-search-ux-plan.md](archive/2026-05-28-fix-feed-header-search-ux-plan.md) | Feed header bar layout + inline search UX fixes. |
| [archive/2026-06-02-feat-new-posts-chip-desktop-feed-plan.md](archive/2026-06-02-feat-new-posts-chip-desktop-feed-plan.md) | "New posts" chip for the desktop feed (commons NewPostsChip + state). |
| [archive/2026-06-02-fix-desktop-feed-review-findings-plan.md](archive/2026-06-02-fix-desktop-feed-review-findings-plan.md) | Resolve 5 review findings on PR #3124 desktop feed UI refresh. |
| [archive/2026-06-05-feat-profile-replies-section-plan.md](archive/2026-06-05-feat-profile-replies-section-plan.md) | Desktop profile Replies tab (DesktopProfileFeedFilter). |
| [archive/2026-06-05-fix-desktop-feed-reply-context-plan.md](archive/2026-06-05-fix-desktop-feed-reply-context-plan.md) | Desktop feed reply-context rendering (commons ReplyContext). |
| [archive/2026-06-10-feat-unhealthy-relay-review-plan.md](archive/2026-06-10-feat-unhealthy-relay-review-plan.md) | Unhealthy relay review banner + sheet (RelayHealthStore + UnhealthyRelayBanner). |
| [archive/2026-06-11-feat-replace-vlcj-with-kdroidfilter-plan.md](archive/2026-06-11-feat-replace-vlcj-with-kdroidfilter-plan.md) | Replace vlcj with kdroidFilter ComposeMediaPlayer + JCodec/Jaffree. |
| [archive/2026-06-11-vlcj-replacement-testing-sheet.md](archive/2026-06-11-vlcj-replacement-testing-sheet.md) | Manual-testing sheet for the vlcj → kdroidFilter migration. |
