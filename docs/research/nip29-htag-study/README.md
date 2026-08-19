# Do NIP-29 group ids collide across relays?

A network-wide survey run on **2026-08-19**, seeded from
`wss://search-staging.brainstorm.world` and widened through NIP-66 relay
discovery.

**Short answer: yes, but rarely, and almost never by accident.**
Of **3,962** distinct group ids observed across **1,271** NIP-29 relay
instances, **142** appear on more than one relay and **42** of those are
backed by signature-verified, relay-signed group metadata. Every confirmed
collision is explained by a default id, a client-generated id reused across
relays, or a relay that cloned another relay's group — not by two operators
independently drawing the same random id.

---

## Why relay URLs can't answer this question

The obvious method — "ask every relay for kind 39000 and group by the `d`
tag" — produces nonsense, and it took three passes to work out why.

1. **kind 39000/39001/39002 are ordinary addressable events.** Any relay may
   hold a copy. `nos.lol` alone returned metadata for **1,116** distinct
   groups it does not host (2,821 events). Of the 1,942 relay URLs that answered a kind-39000
   query in this survey, only **37** held no kind-1 notes at all — the rest are
   general-purpose relays mirroring other people's group metadata.
2. **One relay instance answers on many URLs.** Path-routed deployments
   (`wss://groups.0xchat.com/<hash>`), aliases, and ports mean a naive URL
   count inflates by an order of magnitude.
3. **NIP-11 `pubkey` is not the relay's signing key.** On `pyramid.fiatjaf.com`
   it is the operator's npub; on `groups.0xchat.com` it happens to be the relay
   key. It cannot be trusted without cross-checking.

The way through is that **NIP-29 has the relay sign the group metadata**, and
that signature travels with every mirrored copy. So this study defines

> **relay instance ≡ the pubkey that signs the kind-39000**

and treats URLs as a secondary, best-effort rendering of those keys. Under that
definition the numbers stop moving around: 1,271 relay instances, of which
**1,082 host exactly one group** — the one-relay-per-group pattern that
`*.communities.buzz.xyz`, `*.spaces.coracle.social` and `layer3.news/<path>`
deployments have made the norm.

## What was collected

| stage | result |
|---|---|
| kind 10009 (user group lists) from 25 seed relays, paged by `until` | **33,547** events → 893 relay URLs referenced |
| kind 30166 (NIP-66 relay discovery) | **66,104** events → **25,419** distinct relay URLs, 296 self-declaring NIP-29 |
| cheap `kinds:[39000] limit:1` probe across all reachable NIP-66 relays | 22,045 probed → **2,694** returned group metadata |
| full 39000/39001/39002 fetch on every hit + every 10009-referenced relay | **3,050** URLs probed, **1,942** answered |
| NIP-11 documents | **2,764** fetched, 251 declaring NIP-29 |
| kind-0 lookup for every colliding signer | 384 pubkeys checked |

The crawler implements NIP-42 AUTH with a throwaway key (94 relays refused
reads otherwise; 75 accepted the throwaway key), adaptive `limit` back-off for relays capping below 500, and
`until`-pagination past each relay's cap.

## Evidence tiers

Not every collision is equally solid, so each row carries a tier:

| tier | meaning | count |
|---|---|---:|
| **A** | ≥2 distinct pubkeys with a **signature-verified kind 39000** carrying `d = h` | **42** |
| **B** | ≥2 distinct verified pubkeys, but only via kind **39001/39002** | 89 |
| **C** | ≥2 pubkeys seen in a relay's answer; the raw event could not be re-fetched | 11 |

Tier B is genuinely weaker: a permissive general-purpose relay will accept a
kind-39001 from anyone. `poker-game` is the clearest example — 11 distinct
pubkeys published a kind-39001 with `d=poker-game` to `cobrafuma.com/relay`
and `henhouse.social/relay`. Those are eleven *users* writing group-admin
events, not eleven relays.

**Every one of the 664 events behind the tables was re-fetched and checked:
event id recomputed from the serialization, BIP-340 signature verified. All 664
passed — no forged or malformed group metadata anywhere in the corpus.**

## What the confirmed collisions actually are

Tier A breaks into four causes, and none of them is a random-id birthday
collision:

**Default ids baked into software.** `clan-test` is signed by **44 verified
distinct relay keys** (49 observed), every copy carrying the identical content
`{"about":"","name":"test"}` and both an `h` and a `d` tag. That is one piece
of software's quickstart default, deployed 44+ times, each instance generating
a fresh relay key and creating a group literally named `clan-test`. The same
family gives `clan-test2` and `clan-scuffed-crew`. Short generic slugs behave
the same way: `general`, `support`, `h1`, `orbee`, `mynd-labs`, `nostrord`.

**Relays that cloned another relay's groups.** `wss://strfry.ymir.cloud` runs
its own relay key and has re-signed group metadata for ids that also live on
`basspistol.org` (`h1`), `relay.zap.stream` (`acleprush`) and
`chat.wisp.talk` (`zoxqa84y6lv7`). The group id survived the copy; the signing
key did not.

**Client-generated ids replayed onto several relays.** `7c92b9a7956977b7`
(4 relay keys, incl. `groups.0xchat.com` and `groups.satsdisco.com`),
`997bcfd35d30dfa3`, `76099794e780b9cd` and ~16 other 16-hex ids. 64 bits will
not collide by chance at this scale — the client picked the id and published it
to more than one relay.

**Owner-namespaced ids.** `npub1…:groupName`. The namespace prevents collisions
*between users* but not *between relays*: the same owner creating the same-named
group on several relays produces the same id every time. 5 in tier A, 62 across
all tiers.

### Id shapes, all ids vs. confirmed collisions

| id shape | all 3,962 | tier A |
|---|---:|---:|
| random hex-64 | 969 | 2 |
| npub-namespaced | 968 | 5 |
| human-readable slug | 624 | 13 |
| random alnum | 437 | 1 |
| random hex-16 | 368 | 19 |
| uuid | 170 | — |
| random hex-32 | 161 | — |
| random letters | 104 | 2 |
| random hex-6 | 92 | — |
| numeric id | 49 | — |
| relay-wide `_` | 1 | — |

Human-readable slugs are 16% of all ids but 31% of confirmed collisions; 64-bit
hex is 9% of ids but 45% of collisions (all deliberate multi-relay publishing).
Long random ids — hex-32, hex-64, uuid — essentially never collide.

`_`, the NIP-29 relay-wide group, is declared by **20 different relays** in
users' kind-10009 lists. It is a collision by design, not by accident, and no
relay publishes a kind-39000 for it.

## Full tables

- **[`data/shared-htags.csv`](data/shared-htags.csv)** — all 142 shared ids with
  tier, relay instance count, verified signer counts, relay URLs and pubkeys.
- **[`data/all-htags.csv`](data/all-htags.csv)** — all 3,962 ids.
- **[`data/relay-instances.csv`](data/relay-instances.csv)** — the 1,271 relay
  instances by group count, with every URL each key was seen at.
- **[`data/summary.json`](data/summary.json)** — machine-readable summary.
- **[`tables.md`](tables.md)** — the 142 rows rendered inline.

### Largest relay instances

| groups | relay | signing key |
|---:|---|---|
| 1,378 | `wss://groups.0xchat.com` | `ad98dd84…` |
| 83 | `wss://groups.yugoatobe.com` | `a76bebdb…` |
| 69 | `wss://nip29.f7z.io` (also answers as `relay.highlighter.com`) | `7e1eabe2…` |
| 64 | *url not resolved* | `00bb97ab…` |
| 58 | *url not resolved* | `3c72addb…` |
| 56 | `wss://groups.satsdisco.com` | `a3cda8aa…` |
| 29 | `wss://newlay.relay.tools` | `41e1d75b…` |
| 27 | `wss://relay29.notoshi.win` | `e0ee85a6…` |
| 26 | `wss://chat.wisp.talk` | `d9e2be11…` |
| 20 | `wss://groups.fiatjaf.com` | `1dd006bd…` |

## Limitations

- **Only 126 of 1,271 relay instances could be tied to a live URL.** The rest
  are known solely by their signing key, recovered from metadata mirrored onto
  aggregators; their relays are dead, private, or absent from NIP-66. 31 of the
  142 shared ids have at least one resolvable URL.
- The NIP-66 corpus is the discovery ceiling. A NIP-29 relay that no monitor has
  ever seen and that no kind-10009 references is invisible here.
- `.onion` and `.i2p` relays were excluded (no Tor/I2P transport available).
- Tier B/C rows are reported for completeness but should not be cited as
  relay-level collisions without re-checking.
- Two keys signing one id is evidence of two relay *instances*, not necessarily
  two *operators* — a relay that rotated its key, or one operator running
  several deployments, looks identical from outside.

## Reproducing

`tools/` holds the crawler and analysis, in dependency order:

```
p1_10009.py     kind 10009 harvest          p9_probe3.py    full fetch on sweep hits
p2_extract.py   relay urls from 10009       p11_kind1.py    dedicated-vs-general test
p2b_nip66.py    kind 30166 harvest          p12/p13         re-fetch collisions raw
p5_probe2.py    39000 probe, 10009 relays   p14_profiles.py kind-0 check on signers
p6_sweep.py     cheap NIP-29 sweep          analyze5.py     identity model + tables
p8_nip11.py     NIP-11 documents            verify2.py      id + BIP-340 verification
                                            report.py       CSV / summary emission
```

`relaylib2.py` is the relay client (NIP-42 AUTH, adaptive limits, `until`
pagination); `schnorr.py` is a dependency-free BIP-340 implementation used for
both the AUTH events and the verification pass. Requires `websockets`; run from
a directory holding the intermediate JSON each stage writes.
