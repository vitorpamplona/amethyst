# quartz plans

_Audited 2026-06-30. 11 plans: 7 shipped (archived), 0 in-progress, 3 queued, 1 closed (negative result)._

## Queued
| Plan | Summary |
| ---- | ------- |
| [2026-05-08-local-headers-explorer.md](2026-05-08-local-headers-explorer.md) | Headers-only Bitcoin P2P client to verify NIP-03 OTS attestations without a trusted block explorer. |
| [2026-06-12-giftwrap-deletion-requests.md](2026-06-12-giftwrap-deletion-requests.md) | Let a recipient-authored kind-5 delete/block a gift wrap (kind 1059) addressed to them. |
| [2026-07-03-incremental-negentropy-storage.md](2026-07-03-incremental-negentropy-storage.md) | Always-current (created_at, id) index so cold NEG-OPENs stop paying a full scan + seal (~340 ms at 50k vs strfry's ~21 ms). |
| [2026-07-04-small-req-floor.md](2026-07-04-small-req-floor.md) | Small-REQ dispatch floor: decomposed, inline fast path tried and reverted (no wire-level win); floor is transport-side. |

## Archived (shipped)
| Plan | Summary |
| ---- | ------- |
| [archive/2026-06-03-fix-nip46-bunker-double-resume-plan.md](archive/2026-06-03-fix-nip46-bunker-double-resume-plan.md) | Fix NIP-46 bunker double-resume crash and retry id-reuse races via Channel-per-request + fresh id per attempt. |
| [archive/2026-06-04-auth-scope-vs-policy.md](archive/2026-06-04-auth-scope-vs-policy.md) | Move relay-server authenticated-identity state from the policy into the engine-owned connection scope. |
| [archive/2026-06-09-clink.md](archive/2026-06-09-clink.md) | Implement CLINK (Offers/Debits/Manage) Lightning-over-Nostr pointers, events, and client/server in Quartz. |
| [archive/2026-06-11-runstr-interop.md](archive/2026-06-11-runstr-interop.md) | RUNSTR kind-1301 workout events and supporting fitness kinds in Quartz plus Amethyst fitness screens. |
| [archive/2026-06-19-napplet-nip5a-resolver.md](archive/2026-06-19-napplet-nip5a-resolver.md) | Platform-agnostic NIP-5A static-site resolver verifying content-addressed Blossom blobs against signed manifests. |
| [archive/2026-06-20-powr-interop.md](archive/2026-06-20-powr-interop.md) | Parse and render the POWR/NIP-101e kind-1301 strength-workout dialect alongside the existing RUNSTR dialect. |
| [archive/2026-06-28-git-smart-http-browser.md](archive/2026-06-28-git-smart-http-browser.md) | Git smart-HTTP v2 client to browse NIP-34 repo file trees and render source from the clone URL. |
