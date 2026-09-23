# quartz plans

_Audited 2026-09-17. 13 plans: 7 shipped (archived), 0 in-progress, 5 queued, 1 closed (negative result)._

## Queued
| Plan | Summary |
| ---- | ------- |
| [2026-05-08-local-headers-explorer.md](2026-05-08-local-headers-explorer.md) | Headers-only Bitcoin P2P client to verify NIP-03 OTS attestations without a trusted block explorer. |
| [2026-06-12-giftwrap-deletion-requests.md](2026-06-12-giftwrap-deletion-requests.md) | Let a recipient-authored kind-5 delete/block a gift wrap (kind 1059) addressed to them. |
| [2026-07-03-incremental-negentropy-storage.md](2026-07-03-incremental-negentropy-storage.md) | Always-current (created_at, id) index so cold NEG-OPENs stop paying a full scan + seal (~340 ms at 50k vs strfry's ~21 ms). |
| [2026-07-04-small-req-floor.md](2026-07-04-small-req-floor.md) | Small-REQ dispatch floor: decomposed, inline fast path tried and reverted (no wire-level win); floor is transport-side. |
| [2026-08-13-gpu-pow-mining.md](2026-08-13-gpu-pow-mining.md) | GPU NIP-13 mining declined (ARMv8 has SHA-256 in silicon, mobile GPUs do not). Midstate is ~3x on JVM targets; Android hinges on Conscrypt per-digest JNI cost, still unmeasured. created_at refresh while mining shipped. |
| [2026-09-17-cordn-interop.md](2026-09-17-cordn-interop.md) | Cordn (cordn.net) is an alternative binding of MLS onto Nostr, not an alternative to MLS: same ciphersuite `0x0001`, byte-identical ChaCha20-Poly1305 seal and NIP-01 envelope, but the delivery service is an MCP server over ContextVM with no key-package event kind. Only the RFC 9420 engine is shareable, and it is not yet Marmot-clean (3 files, 10 imports, 3 hardcoded policies). ContextVM must be written from scratch: full review of the spec + all 12 CEPs, with a per-CEP compliance matrix, `CVM-*` rule ids and a 5-tier test method (including a fixture server that misbehaves on demand, and RFC 8785 JCS which Quartz lacks). Blocked on the credential-identity encoding (raw 32 bytes vs 64-byte hex ASCII). Includes a coordinator metadata-exposure analysis. |
| [2026-09-08-marmot-spec-resync.md](2026-09-08-marmot-spec-resync.md) | Marmot moved off the MIP-era spec (2026-07-02): group state split into `app_data_dictionary` components, account identity proof v2, and a convergence engine. Current MDK rejects our groups outright. Gap analysis + 8-stage plan; Stages 0-4 done (mdk interop reference, app_data_dictionary, identity proof v2, the six group components, transport corrections); lifecycle + branch selection landed. |
| [2026-09-22-cyberspace-region-bags.md](2026-09-22-cyberspace-region-bags.md) | Open `kind 33330` region bags: §2 coordinates, §4 Cantor roots, §7.2 region keys, §7.7 hint sweeps, §7.6 item verification. Reverses SNO's D2 — §7.7 says a seeker's position never enters the cost, and a key is 1.2 ms at h8 against the spec's own 1.3 ms. Three layers: quartz, `amy`, and a tap-to-search card. |

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
