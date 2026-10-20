# Nostr developer tools in Amethyst

Status: prototype (2026-10-20). The screens are shared UI in `commonsUI`, so Android and Desktop
both have them: a hub, a NIP-19 converter, an event inspector, a kind lookup (type a number, get
its name and a recent example crawled from relays), a kind browser, "find this event on relays",
and a raw relay console. The rest of this plan is proposed.

## Why

Developers building on Nostr keep a browser tab of single-purpose tools open: nak web for
decoding, nostr-tools.com for keys and signatures, kinds.amethyst.social for "is anyone using kind
N", and websocketking.com to poke a relay. Amethyst already holds every primitive these tools
need. Quartz has bech32/TLV, id hashing and Schnorr verify, the kind registry, NIP-11/45/66/77, and
the fan-out accessories. `amy` already exposes most of them on the command line. The app only
lacked the screens.

## What exists elsewhere (surveyed 2026-10-20)

| Tool | What it does | Primitive it needs |
|--|--|--|
| [kinds.amethyst.social](https://kinds.amethyst.social) (`vitorpamplona/nostrkinds`) | Fans `{kinds:[k]}` out to ~990 hard-coded relays, 10 at a time. Pages each with `until`, with a 5 s idle timeout and ephemeral-key AUTH. Shows a per-relay status/count/last-seen table and the newest event. | REQ fan-out |
| [nostr-tools.com](https://nostr-tools.com) | Key generator, NIP-19 converter, event creator, fetcher, republisher, verifier, NIPs reference. | bech32, sign/verify, REQ, publish |
| [nak](https://github.com/fiatjaf/nak) / [nak web](https://nak.nostr.com) | The de-facto developer tool. The web version decodes anything pasted, compares the implied id to the stated one, checks the signature and fetches through hints. | everything |
| [Grimoire](https://grimoire.rocks) | A developer-mode client with tiling windows: `req`, `count`, `relay`, `conn`, `log`, `kinds`, `nips`, `decode`. | REQ console, relay log |
| noStrudel tools | Event Console (a REQ playground) and a raw-JSON publisher. | REQ, publish |
| [websocketking.com](https://websocketking.com) | A generic WebSocket client: connect, send frames, see frames. | raw socket |
| [nostr.watch](https://nostr.watch) | NIP-66 monitors (kind 30166/10166), dashboard, API. | NIP-66, NIP-11 |
| [metadata.nostr.com](https://metadata.nostr.com) | Kind 0/3/10002 history, diff and restore. | fan-out of old versions, publish |
| [Lightning Decoder](https://lightningdecoder.com) | LNURL / BOLT11 / BOLT12 decode. No NIP-57 debugger exists anywhere. | LNURL HTTP, BOLT11 |
| [Nostrbook](https://nostrbook.dev), [nips.nostr.com](https://nips.nostr.com) | Kind and NIP registries. | none |

Not reachable from the survey container (outage or proxy, unverified): nostr.band, nostrcheck.me,
api.nostr.watch.

## Survey of this repo

| Need | Already here | Use |
|--|--|--|
| NIP-19/21 decode and encode | `quartz/nip19Bech32` (`Nip19Parser`, `NPub`…`NEmbed`) | reused |
| Id and signature checks | `nip01Core/crypto/EventExt.kt` (`generateId`, `verifySignature`) | reused |
| Kind names and NIPs | `quartz/kinds/KindNames` (378 kinds); localized overlay `kindName()` in commonsUI | reused |
| Relay fan-out with per-relay outcome | `fetchAllWithHooks` → `FetchAllResult.doneReasons` (eose / closed / cannot / auth-refused) | reused |
| NIP-45 COUNT | `INostrClient.count(relay, filter)` | reused |
| Throwaway relay client | `BaseAccountViewModel.buildCrawlClient()`: relay-authenticated, blocked relays removed, events kept out of `LocalCache` | reused |
| Raw sockets that obey Tor | `AccountViewModelHost.websocketBuilder` | reused |
| NIP-66 relay discovery | `RelayDiscoveryEvent` (30166) | reused |
| Relay info / NIP-11 / latency | `RelayInformationScreen` (`Route.RelayInfo`) | linked, not duplicated |
| Active subscriptions | `ActiveSubscriptionsScreen` | linked from the hub |
| CLI twins | `amy decode`, `encode`, `verify`, `kind`, `nip`, `count`, `relay info` | logic kept in step |

No screen in the app decoded NIP-19 or verified an event. The only in-app tool was the "Copy raw
JSON" note action.

## What the prototype ships

Entry points:
- Settings › App settings › **Developer Tools** (searchable).
- A note's menu › **Inspect event**, next to "Copy raw JSON".

Headless code in `commons/…/devtools/` (CLI-safe, unit-tested in `DevToolsInspectorsTest`):

- `Nip19Inspector` takes npub, nsec, note, nevent, nprofile, naddr, nrelay or nembed, with or
  without `nostr:`, or a 64-char hex.
  - It returns typed rows (key, id, author, kind, d tag, `a` tag, relay hints, embedded JSON) and
    every other encoding of the same value.
  - Raw hex is ambiguous, so it is offered as npub, nprofile, note and nevent alike.
  - An nsec is flagged secret.
- `EventInspector` takes a bare event, a relay frame `["EVENT",sub,{…}]`, a client command
  `["EVENT",{…}]` or an nembed.
  - It recomputes the id and reports a mismatch with the computed value, and checks the signature.
  - It flags a `created_at` in the future and kinds missing from the registry.
- `RelayProbeViewModel` is the kinds.amethyst.social crawler, generalized.
  - It sends one REQ per relay, 24 relays in parallel, with a 6 s idle timeout and a 20 s cap.
  - Each relay ends as one of found / none / auth required / refused / offline / timed out, taken
    from `doneReasons`.
  - `forKind` sends `{kinds:[k],limit:1}`, plus NIP-45 COUNT where a relay serves it.
  - `forEvent` sends `{ids:[id]}`.
  - The relay set is the app's pool, the indexer, search and finder defaults, and, by default,
    every relay NIP-66 monitors reported on in the last day. The monitors are read from
    `relay.nostr.watch`, `relaypag.es` and `monitorlizard.nostr1.com`; on 2026-10-20 each returned
    ~390 relays from 11–13 monitors.
- `RelayConsoleViewModel` is a raw WebSocket to one relay with every frame both ways, unparsed.
  - Templates are provided for REQ, COUNT, CLOSE and EVENT. CLOSE closes the last subscription
    sent.
  - When the relay sends a challenge, it offers "Sign and send AUTH" using the account signer. It
    never authenticates on its own.

Screens in `commonsUI/…/ui/screen/loggedIn/devtools/`:

| Route | Screen |
|--|--|
| `DevTools` | `DevToolsHubScreen` |
| `DevToolsNip19(input?)` | `Nip19ToolScreen`: decode, re-encode, open in Amethyst, crawl the kind |
| `DevToolsEventInspector(noteKey?)` | `EventInspectorScreen`: verdict, fields, tag chips, content, JSON, then find on relays / crawl kind / open |
| `DevToolsKinds` | `KindBrowserScreen`: search by number, name or NIP; an unknown number still offers a crawl |
| `DevToolsKindUsage(kind?)` | `KindUsageScreen` ("Kind Lookup"): type a kind number. It shows the name and NIP when the registry knows the kind (or says it doesn't), the newest example any relay holds, then the per-relay table, with "only relays that have it" |
| `DevToolsEventFinder(eventId)` | `EventFinderScreen`: the same table for one event id |
| `DevToolsRelayConsole(url?)` | `RelayConsoleScreen` |

Tor: the desktop and Android socket builders do not dial a Tor-routed relay until Tor is up
(`WebsocketBuilder.canConnect`). With the default "Untrusted Relays → Tor", every relay outside
the user's lists is Tor-routed. So the probe marks such a relay **Waiting for Tor** at once,
instead of burning the 20 s deadline and calling it a timeout. The console says the same instead
of hanging on "Connecting".

Checked by hand on the desktop app at phone size, read-only npub login:
- The note menu opens the inspector on that note, which reports a valid id and signature.
- The NIP-19 converter decodes an nevent and re-encodes it.
- The kind browser search works.
- Kind Lookup on 1010, 4242 (unregistered) and 30023 returns names and examples. With Tor off,
  30023 was found on 257 of 769 answering relays out of 1,464 within a minute.
- Kind 9735 shows NIP-45 counts (~3.4M on relay.ditto.pub).
- The relay console exchanged REQ, COUNT and CLOSE with nos.lol.

Not checked on an Android device.

Choices worth reviewing:
- **No full-history paging in the kind crawl.** One `limit:1` REQ plus COUNT costs two small frames
  per relay. The web crawler pages each relay's whole history, which on a popular kind is
  thousands of events per relay. A count from a relay without NIP-45 shows as blank, not as zero.
- **The crawl runs on `buildCrawlClient()`**, so crawled events never enter `LocalCache`, and the
  account's blocked relays (kind 10006) are never contacted.
- **The console bypasses the relay pool** on purpose: no reconnects, no automatic AUTH, no dedup. A
  developer sees exactly what the relay sent.

## Next (proposed), ranked by value for cost

1. **Rebroadcast from the event finder.** "Send to the relays missing it" through
   `publishAndConfirmDetailed`, with a per-relay `OK` and message. This is the companion users ask
   for, and nostr-tools.com's republisher.
2. **REQ playground.** A filter editor (kinds, authors, tags, since/until, limit) over chosen relays,
   with EVENT/EOSE/CLOSED/NOTICE live, sitting on `subscribeAsFlow`. The console covers the raw case;
   this is the structured one (Grimoire `req`, noStrudel Event Console).
3. **Replaceable history and restore** for kinds 0, 3 and 10002. Collect every version from
   reachable relays, diff them, and republish one. This answers the recurring lost-follow-list
   crisis (metadata.nostr.com, nak).
4. **Zap / LNURL debugger.** Fetch lnurlp, check `allowsNostr`/`nostrPubkey`, decode BOLT11, and
   validate a receipt with `ZapReceiptValidator`. No tool for this exists anywhere, and zap
   failures are a top support issue.
5. **NIP-05 checker.** Fetch `nostr.json`, check the pubkey, CORS and redirects, and list relays and
   `nip46`. Reuses `Nip05Client`.
6. **Outbox inspector for a user.** Their NIP-65 read/write relays, whether each is reachable, and
   where Amethyst looks for their notes, for "why can't I see X" reports.
7. **Relay traffic log.** The last N frames per pooled relay, from `RelayLogger`, beside Active
   Subscriptions.
8. **Event creator / signer.** Raw JSON to a signed event, with an option to sign with a throwaway
   key. Publishing on the user's key needs a confirm step.
9. **NIP-46 bunker tester.** Connect, `get_public_key`, then sign/encrypt round trips with timings.
10. **Blossom check.** BUD-01/02 capabilities, a test upload with hash check, and blob presence
    across the kind 10063 servers.
11. **NIPs reference.** `amy nip` already fetches NIP text. It could be a screen, linked from every
    "NIP-xx" chip.

Also proposed:
- A desktop menu bar item (`DesktopMenuBar`) for the hub.
- "Open in relay console" from `RelayInformationScreen`.
- Moving `RelayProbeViewModel.MONITOR_RELAYS` into `defaults/` once a second caller needs it.

## Open questions

- Should the hub be hidden behind a "developer mode" switch, or always reachable from settings? It
  is always visible in this prototype.
- Should the event inspector offer "re-sign as me" for an id-mismatch event? That would publish
  under the user's key, so it is left out for now.
