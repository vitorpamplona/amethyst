# Amy Roadmap

The live checklist for growing `amy` from a Marmot test-bed into a
full command-line mirror of Amethyst.

**How to use this file:** update it in the same PR that ships a
feature. Move rows between tables, adjust ordering, add non-goals.
This is the single source of truth for "what's left".

- How the CLI is used today: [README.md](./README.md)
- How to implement an item: [DEVELOPMENT.md](./DEVELOPMENT.md)
- Ongoing design plans: [plans/](./plans/)
- Shared work consumed here: [../commons/plans/](../commons/plans/)

---

## North-star goal

> For every feature of the Amethyst Android app, there is a way to
> exercise it through `amy`, with byte-identical on-relay behaviour.

Why:

- Interop tests against the ~100 other Nostr clients need a
  reproducible harness that does not require running Android.
- Agents and LLMs can script real Amethyst flows without a GUI.
- Regressions in shared logic (signing, encryption, filter building,
  event parsing) become shell-scriptable.
- Power users get a command-line Amethyst for free.

**Non-goal:** Amy is not a second Nostr client implementation. It is
a thin assembly layer over `quartz` + `commons`. Protocol and business
logic in `cli/` are bugs.

---

## Parity matrix

Status legend: ✅ shipped · 📦 logic lives in `commons/`, needs a command ·
🆕 needs extraction from `amethyst/` first · ⚠️ blocked (see Notes).

| Area | Status | Notes |
|---|---|---|
| Identity create / import (`nsec`, `ncryptsec`, mnemonic, `npub`, `nprofile`, hex, NIP-05) | ✅ | `LoginCommand` + Quartz NIP-05 / NIP-06 / NIP-49 |
| Account bootstrap (nine events) | ✅ | `commons/account/AccountBootstrapEvents.kt` |
| Relay config + NIP-65 / NIP-10050 publish | ✅ | `RelayCommands` |
| MLS KeyPackage publish + fetch | ✅ | `commons/marmot/MarmotManager` |
| Marmot group create / add / rename / promote / demote / remove / leave | ✅ | `commons/marmot/` |
| Marmot message send / list | ✅ | `commons/marmot/` |
| `await` polling (KP / group / member / admin / message / rename / epoch) | ✅ | `AwaitCommands` |
| NIP-01 note publish (`amy note publish TEXT`) | 🆕 | Needs a `commons/` builder wrapper. |
| NIP-01 feed read (`amy feed home`, `amy feed hashtag #X`, `amy feed profile NPUB`) | 🆕 | Extract `FeedFilter` usage from `amethyst/ui/dal/` into `commons/` entry points. |
| NIP-02 follow list add / remove / list | 🆕 | Logic in `amethyst/model/nip02FollowLists/`. |
| NIP-09 event deletion | 🆕 | Builder exists in quartz. |
| NIP-17 DMs send / list / await | ✅ | `DmCommands` — reuses Quartz `NIP17Factory` + `RecipientRelayFetcher`; filter extracted to `commons/relayClient/nip17Dm/`. Plan: [`cli/plans/2026-04-23-nip17-dm.md`](./plans/2026-04-23-nip17-dm.md). |
| NIP-18 reposts / quotes | 🆕 | |
| NIP-25 reactions | 🆕 | |
| NIP-51 lists (bookmarks, mute, follow sets) | 🆕 | `amethyst/model/nip51Lists/` |
| NIP-57 zaps (send + verify) | 🆕 | Needs LN-URL plumbing; `amethyst/service/lnurl/`. |
| NIP-65 outbox model queries | 🆕 | |
| NIP-72 communities | 🆕 | |
| NIP-78 app-specific data (settings sync) | 🆕 | |
| Long-form (NIP-23) publish / read | 🆕 | |
| Live activities / chess (NIP-53 / NIP-64) | 🆕 | |
| Blossom uploads (NIP-B7) | 🆕 | |
| NIP-47 Wallet Connect | 🆕 | |
| NIP-46 bunker signer | 🆕 | Needs a signers abstraction in Amy. |
| Profile view (`amy profile show NPUB`) | ⚠️ | Blocked on event-renderer plan in `commons/plans/`. |
| Thread view (`amy thread show EVENT_ID`) | ⚠️ | Same. |
| Notifications feed | 🆕 | |
| Search (NIP-50) | 🆕 | |

---

## Order of operations

Proposed sequencing. Each step is one PR. Each step should extract
at least one file from `amethyst/` into `commons/`; if it doesn't
move anything, re-audit — you're probably duplicating logic.

1. **Event rendering core** in `commons/commonMain/.../rendering/`
   with renderers for kinds 0 / 1 / 3 / 6 / 7 / 10002 / 10050.
   Unblocks all the 🆕 and ⚠️ read-path rows below.
   Design: `commons/plans/2026-04-21-event-renderer.md`.
2. **`amy note publish` / `amy note show` / `amy note react`** —
   smallest end-to-end write+read loop outside Marmot.
3. **`amy feed home|profile|hashtag|thread`** reading through the
   renderer.
4. **`amy follow add|remove|list`** (NIP-02) — proves extraction of
   list-building logic from `amethyst/model/`.
5. **`amy dm send|list`** (NIP-17) — reuses the gift-wrap path already
   exercised by Marmot.
6. **`amy list bookmarks|mute|pin …`** (NIP-51).
7. **`amy zap send|verify`** (NIP-57).
8. **Distribution** — Homebrew + Scoop + `.deb` in the same release
   pipeline as desktop. Plan: `cli/plans/2026-04-21-cli-distribution.md`.
9. **Test suite** — end-to-end against a local relay. Marmot interop is
   covered by `tools/marmot-interop/marmot-interop-headless.sh`; NIP-17
   DM interop between two `amy` clients is covered by
   `tools/marmot-interop/dm-interop-headless.sh` (text + file + strict
   10050 + fallback + cursor-advance). Neither runs in CI yet (both
   need Rust + ~3 min cold relay build).
10. **Everything else in the matrix.**

---

## Non-goals

- Interactive TUI or REPL.
- Native image (GraalVM) until Quartz has a pure-Kotlin signer
  fallback — `secp256k1-kmp-jni-*` needs JNI today.
- A Gradle dependency on `:amethyst` or `:desktopApp`. Ever.
- Re-implementing any Nostr protocol piece that's already in
  `quartz/` or in another client's library.
