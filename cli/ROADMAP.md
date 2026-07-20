# Amy Roadmap

The live checklist for growing `amy` from a Marmot test-bed into a
full command-line mirror of Amethyst.

**How to use this file:** update it in the same PR that ships a
feature. Move rows between tables, adjust ordering, add non-goals.
This is the single source of truth for "what's left".

- What amy is + how to use it: [README.md](./README.md)
- The public contract + how to extend amy: [DEVELOPMENT.md](./DEVELOPMENT.md)
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
| Account logoff (`amy logoff`) — delete key + per-account state + the account's events in the shared store | ✅ | `LogoffCommand`. `--yes`-gated; `--keep-events` skips the shared-cache purge. |
| Status overview (`amy status`) — every account, current pin, signer type + can-sign, per-account Marmot/Cashu/alias/cursor footprint, shared event-store size | ✅ | `StatusCommand`. Cross-account, read-only, metadata-only (no keychain prompt, no network). Store stats via shared `StoreStats`. |
| Relay config — every relay-list bucket (nip65 10002 via `outbox`/`inbox`/`nip65` nouns with spec read/write merge, dm 10050, key-package 10051, search 10007, private-outbox 10013, blocked 10006, trusted 10089, proxy 10087, indexer 10086, broadcast 10088, favorite 10012) — noun-first `relay <noun> add/remove/set/clear/list` + fan-out `relay add/remove` + publish | ✅ | `RelayCommands`. Mirrors the Android relay-settings screen. Local relays (device pref) + relay sets (30002) intentionally out of scope. |
| MLS KeyPackage publish + fetch | ✅ | `commons/marmot/MarmotManager` |
| Marmot group create / add / rename / promote / demote / remove / leave | ✅ | `commons/marmot/` |
| Marmot message send / list | ✅ | `commons/marmot/` |
| `await` polling (KP / group / member / admin / message / rename / epoch) | ✅ | `AwaitCommands` |
| NIP-01 note publish (`amy notes post TEXT`) | ✅ | `PostCommand` — outbox via `RelayCommands` configured set. |
| NIP-13 proof of work (`amy notes post --pow N`, `amy pow check/mine/bench`) | ✅ | `PostCommand` + `PowCommands` — mines pre-signature via quartz `PoWMiner`; `pow mine --pubkey` covers delegated PoW; `pow check` applies the commitment cap. |
| NIP-01 feed read (`amy notes feed [--following \| --author NPUB]`) | ✅ | `FeedCommand`. Hashtag / community feeds still pending. |
| NIP-02 follow list add / remove / list | ✅ | `FollowCommand` — `amy follow USER` / `amy unfollow USER` (fetches the freshest kind:3 first). |
| NIP-09 event deletion | 🆕 | Builder exists in quartz. |
| NIP-17 DMs send / list / await | ✅ | `DmCommands` — reuses Quartz `NIP17Factory` + `RecipientRelayFetcher`; filter extracted to `commons/relayClient/nip17Dm/`. Plan: [`cli/plans/2026-04-23-nip17-dm.md`](./plans/2026-04-23-nip17-dm.md). |
| NIP-18 reposts / quotes | 🆕 | |
| NIP-25 reactions | ✅ in groups · 🆕 elsewhere | `marmot message react` covers MLS group reactions; outer-event reactions still pending. |
| NIP-29 relay groups (`amy relaygroup`) | ✅ | `RelayGroupCommands` — list/browse/info/create/join/leave/message/edit/invite/put-user/remove-user against a host relay; kind:10009 joined-list kept in sync. |
| NIP-51 lists (bookmarks, mute, follow sets) | 🆕 | `amethyst/model/nip51Lists/` |
| NIP-57 zaps (send) | ✅ partial | `ZapCommand` — `zap user`/`zap event` build the kind:9734 request and fetch the BOLT11 (zap splits honored, one invoice per recipient); `--with NDEBIT` auto-pays through a CLINK debit pointer. Receipt (kind:9735) verification still 🆕. |
| NIP-65 outbox model queries | ✅ | `OutboxCommand` — `amy outbox USER [--refresh]`, cache-first. |
| CLINK offers + debits (`amy offer` / `amy debit`) | ✅ | `OfferCommands` + `DebitCommands` — pointer decode, NIP-05 discover, kind:21001/21002 round-trips, `offer pay --with NDEBIT` end-to-end settlement. `--timeout` is SECONDS. |
| Geochat (Bitchat geohash, ephemeral kind:20000) | ✅ | `GeochatCommands` — listen/send/keys with per-geohash throwaway identity + geo-nearest relay routing; doubles as the Bitchat interop harness. |
| Concord Channels (encrypted communities) | ✅ | `ConcordCommands` — 13 sub-verbs (create/list/import/channels/send/read/invite/join/roles/role/grant/ban/unban) over shared `commons` `ConcordActions`; secrets in `concord.json`. |
| NIP-5A nsites + NIP-5D napplets | ✅ | `NsiteCommands` + `NappletCommands` — fetch/publish/serve/list with sha256 + aggregate-hash verification and `requires` capability reporting. |
| Podcasting 2.0 / podstr (`amy podcast20`) | ✅ | `Podcast20Commands` — kind:30078 metadata, 30054 episodes, 30055 trailers, list. |
| Follows-of-follows (`amy fof get/list/sync`) | ✅ | `FofCommand` — single-hop social proof from the local store (`wot` kept as deprecation alias). |
| NIP-85 GrapeRank web-of-trust (`amy graperank`) | ✅ | `GrapeRankCommand` — outbox-model crawl + scoring engine in `commons/wot/` (`GrapeRank`, `TrustGraph`, `TrustGraphBuilder`); every score run persists kind:30382 `ContactCardEvent` cards to the local store (diffed against prior ranks, kind:5 retractions), `publish` mirrors that set to the operator relays via NIP-77 up-sync, `rank` reads cards back, plus `register` / `unregister` / `providers` for the kind:10040 `TrustProviderListEvent` discovery layer. |
| NIP-72 communities | 🆕 | |
| NIP-78 app-specific data (settings sync) | 🆕 | |
| Long-form (NIP-23) publish / read | 🆕 | |
| Live activities / chess (NIP-53 / NIP-64) | 🆕 | |
| Blossom blobs (NIP-B7) | ✅ | `BlossomCommands` — upload/download/list/delete/check/mirror on shared `commons` `BlossomClient`; live-server harness at `cli/tests/blossom/`. |
| NIP-60 / 61 Cashu wallet + nutzaps | ✅ | Full surface: `cashu wallet {create,show,export-key,destroy}`, `mint {ping,info}`, `balance`, `receive {ln,complete,resume,token,nutzap-sweep}`, `send {ln,token,nutzap}`, `maintenance {scrub,restore,migrate-keysets}`, `mint-rec {show,add,remove}` — all on shared `commons` `CashuWalletOps` + `CashuWalletReader` (the exact path the Android wallet runs). Interop harness pending. Plan: [`cli/plans/2026-05-28-cashu-cli.md`](./plans/2026-05-28-cashu-cli.md). |
| NIP-47 Wallet Connect | 🆕 | |
| NIP-46 bunker signer | ✅ | `BunkerCommand` + `NostrConnect` + `LoginCommand` — host (`amy bunker[ connect]`) and client (`amy login bunker://` / `--nostrconnect`) sides, `--perms`/`--interactive` gating, `auth_url` challenges. |
| Profile view (`amy profile show NPUB`) + edit | ✅ | `ProfileCommands`. Cache-first; `--refresh` forces a relay drain. |
| Thread view (`amy thread show EVENT_ID`) | 🆕 | No `thread` verb yet — needs the event-renderer read path (Order of operations §1/§3). |
| Notifications feed | 🆕 | |
| Search (NIP-50) | ✅ | `SearchCommand` — `search user` (kind:0) + `search note` (`--kind`, `--kinds` alias) over the kind:10007 search-relay list; default limit 50. |
| Namecoin NIP-05 resolve (`amy namecoin resolve .bit\|d/\|id/`) | ✅ | `NamecoinCommand` — reuses Quartz `NamecoinNameResolver` + `ElectrumXClient` + the default ElectrumX server set the Android/Desktop apps ship with. Stateless. On-chain `name_history` + Core RPC backend pending separate PRs. |

### `nak` parity — army-knife primitives

Tracking [`fiatjaf/nak`](https://github.com/fiatjaf/nak)'s command surface. amy
adapts the verbiage where its own conventions differ (`req` → one-shot `fetch`
vs streaming `subscribe`). Stateless verbs run with no account or network.

| nak command | amy verb | Status | Notes |
|---|---|---|---|
| `decode` | `amy decode` | ✅ | NIP-19/21 → JSON. Quartz `Nip19Parser`. |
| `encode` | `amy encode` | ✅ | npub/nsec/note/nevent/nprofile/naddr. |
| `verify` / `validate` | `amy verify` | ✅ | id-hash + signature, reported separately. |
| `key` | `amy key generate\|public\|encrypt\|decrypt\|validate` | ✅ | generate/derive + NIP-49 encrypt/decrypt (bidirectionally nak-verified) + `validate` (npub/hex parse check). `expand`/`combine`(MuSig2)/`default` still 🆕. |
| `event` | `amy event` | ✅ | build/sign an arbitrary event, optional `--publish`/`--relay`. |
| `publish` | `amy publish` | ✅ | broadcast a pre-made event JSON (verified first). |
| `req` (one-shot) | `amy fetch` | ✅ | filter → collect-until-EOSE, dedupe, sort, cap. Also accepts a nip19/nip05 code and resolves relays via the outbox model (code hints + author's NIP-65 write relays), like nak's `fetch`. |
| `req` (stream) | `amy subscribe` | ✅ | filter → live NDJSON stream to stdout. |
| `count` | `amy count` | ✅ | NIP-45, per-relay counts. |
| `encrypt` / `decrypt` | `amy encrypt\|decrypt` | ✅ | raw NIP-44 (default) / NIP-04. |
| `gift` | `amy gift wrap\|unwrap` | ✅ | NIP-59 seal+wrap / unwrap+unseal. |
| `relay` (NIP-11) | `amy relay info` | ✅ | stateless NIP-11 doc fetch. |
| `outbox` | `amy outbox` | ✅ | NIP-65 read/write relays, cache-first. |
| `filter` | `amy filter` | ✅ | stateless — assemble + print a filter JSON. |
| `blossom` | `amy blossom` | ✅ | upload/download/list/delete/check/mirror (reuses commons `BlossomClient`). |
| `nip` | `amy nip` | ✅ | repo-first lookup + Nostr fallback (NipText kind:30817, wiki:30818, long-form:30023); `nip list`. |
| `kind` | `amy kind` | ✅ | quartz `KindNames` registry (kind → English label + NIP) covering **every** event kind quartz defines (280 entries); number lookup + name search. |
| `sync` | `amy sync` | ✅ | NIP-77 Negentropy reconcile with the local store (down/up/both). |
| `git` | `amy git` | ✅ (events + read) | NIP-34: `init` bootstraps a repo from the local `git` checkout (announce + state, like `ngit init`); repo announce (30617) + state (30618), patches (1617), pull requests (1618/1619), issues (1621), NIP-22 comments (1111), NIP-32 labels (1985), status open/applied/closed/draft (1630-1633), GRASP server list (10317); `issues`/`patches`/`prs`/`thread` reads derive status; `apply` applies a fetched patch to the local tree (`git am`); `browse`/`cat`/`log` read git objects over smart-HTTP v2 (quartz `GitHttpClient`, the same shallow-clone path the Android browser uses). Only git-packfile **push** (writing objects to clone/GRASP servers) and NIP-34 cover notes (1624, no quartz builder yet) are out of scope. |
| `podcast` | `amy podcast` | ✅ | NIP-F4 show metadata (10154) + episode publish (54) + list. |
| `bunker` | `amy bunker[ connect]` + `amy login bunker://`/`--nostrconnect` | ✅ | NIP-46 remote signer + login, both the `bunker://` and `nostrconnect://` flows, each direction, plus `auth_url` challenge handling (client surfaces the URL + keeps waiting). Interop-verified vs real `nak`. |
| `admin` | `amy admin RELAY METHOD` | ✅ | NIP-86 Relay Management over NIP-98 HTTP auth — full method set (ban/allow pubkey + event, kinds, IP block, change name/desc/icon, list-*). Reuses quartz `Nip86Client` + shared `commons` `Nip86Retriever`. Interop-verified against `amy serve`. |
| `serve` | `amy serve` | ✅ | Embeds **geode** (the standalone Ktor relay on quartz's relay-server code) — in-memory by default, `--db FILE` for SQLite, account is admin so `amy admin` works against it. NIP-86 + NIP-77 included. |
| `wallet` (NIP-60 Cashu) | `amy cashu` | ✅ | See the Cashu row above — full NIP-60/61 wallet + nutzaps. |
| `mcp` / `fs` / `spell` | — | 🆕 (niche) | MCP server, FUSE mount, MuSig2/FROST; some pull new deps. |

### Full nak comparison (introspected both binaries)

nak has 34 functional commands (introspected from `nak --help`). Coverage:

- **Full / equivalent (24):** `event`, `req`(→`fetch`+`subscribe`), `fetch`
  (nip19/nip05-hint resolution), `filter`, `count`, `decode`, `encode`,
  `verify`, `relay`, `bunker`(+nostrconnect+auth_url), `encrypt`, `decrypt`,
  `gift`, `publish`, `sync`, `profile`, `podcast`, `nip`, `kind`, `blossom`,
  `nsite`(NIP-5A), `admin`(NIP-86), `serve`(geode), `wallet`(NIP-60/61 Cashu).
  Protocol-sensitive ones (`bunker`, `sync`, `key` NIP-49, `encode`/`decode`,
  `admin`) are interop-verified against the real `nak` binary or `amy serve`.
- **Partial / adapted (3):** `key` (no `expand`/`combine`(MuSig2)/`default`),
  `git` (full NIP-34 event surface — announce/state/patch/PR/issue/comment/status
  + status-deriving reads + GRASP list, plus `browse`/`cat`/`log` reading git
  objects over smart-HTTP; only packfile **push** is out of scope), `outbox`
  (NIP-65 model vs nak's local hints DB).
- **Missing (6):** `dekey` (NIP-4E), `mcp`, `curl` (NIP-98), `fs` (FUSE),
  `spell` (MuSig2/FROST), and `validate` (event-schema validation).

**Design differences (not gaps):** amy is a *stateful client* (accounts,
`~/.amy/`, shared event store) with a stable JSON contract; nak is a *stateless*
per-invocation tool that prints bare values for shell substitution. amy also has
a large surface nak lacks: Marmot/MLS, NIP-29 relay groups (offered side by
side with MLS, not substituting for it), Concord communities, geochat, NIP-17
DMs, zaps, CLINK offer/debit, NIP-02 follow, NIP-50 search, nsites/napplets,
podcast20, GrapeRank/fof, profile edit, store management, account management.

**Cheap remaining wins:** the `key` `expand` (hex left-pad) and `default`
(print the active account's key) sub-verbs. `key combine` needs MuSig2.

---

## Order of operations

Proposed sequencing. Each step is one PR. Each step should extract
at least one file from `amethyst/` into `commons/`; if it doesn't
move anything, re-audit — you're probably duplicating logic.

1. **Event rendering core** in `commons/commonMain/.../rendering/`
   with renderers for kinds 0 / 1 / 3 / 6 / 7 / 10002 / 10050.
   Unblocks the remaining 🆕 read-path rows (thread view, notifications).
   Design: `commons/plans/2026-04-21-event-renderer.md`.
2. **`amy notes post` / `amy notes show` / `amy notes react`** —
   smallest end-to-end write+read loop outside Marmot. Post + feed
   ✅ shipped; `notes show` and outer-event `react` still pending.
3. **`amy notes feed home|profile|hashtag|thread`** reading through the
   renderer. `--following` and `--author NPUB` ✅; hashtag/thread
   variants still pending.
4. **`amy follow|unfollow`** (NIP-02) — ✅ shipped. A standalone
   `follow list` view is still pending.
5. **`amy dm send|list`** (NIP-17) — ✅ shipped. Reuses the gift-wrap
   path also exercised by Marmot.
6. **`amy list bookmarks|mute|pin …`** (NIP-51).
7. **`amy zap send|verify`** (NIP-57) — send ✅ (invoice fetch +
   optional CLINK auto-pay via `--with NDEBIT`); receipt verify pending.
8. **Distribution** — Homebrew + Scoop + `.deb` in the same release
   pipeline as desktop. Plan: `cli/plans/2026-04-21-cli-distribution.md`.
9. **Test suite** — largely in place, two layers:
   - **Shell harnesses** under `cli/tests/` — ten suites: `blossom`
     (live servers), `cache`, `clink`, `dm`, `git` (NIP-34 vs `amy serve`),
     `marmot` (vs whitenoise-rs), `nests` (manual audio-rooms matrix), `pow`,
     `relaygroup`, `sync`, plus the shared `headless/` helpers. See
     `cli/tests/README.md`.
     None run in CI yet (the relay-backed ones need Rust + a ~3 min
     cold `nostr-rs-relay` build).
   - **JVM unit suite** at `cli/src/test/kotlin/` — `Args` parsing,
     exit-code contract, and `--json` shape tests driving `runCli`
     in-process via the `amy.home` isolation seam.
10. **Everything else in the matrix.**

---

## Non-goals

- Interactive TUI or REPL.
- Native image (GraalVM) until Quartz has a pure-Kotlin signer
  fallback — `secp256k1-kmp-jni-*` needs JNI today.
- A Gradle dependency on `:amethyst` or `:desktopApp`. Ever.
- Re-implementing any Nostr protocol piece that's already in
  `quartz/` or in another client's library.
