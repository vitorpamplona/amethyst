# Concord spec conformance review (spec `b84554e`, 2026-08-15)

## Why

Our Concord implementation last tracked the spec at `bbc67b6` (2026-08-06, CORD-02 §2
`control_root`). Since then the spec moved on, and several sections that predate that
point were never implemented. This review compares every CORD against
<https://github.com/concord-protocol/concord> at `b84554e`, cross-checked against the
Armada reference client (`soapbox-pub/armada` at `7588884`, 2026-09-28), and records what
we fixed and what is left.

Method: one audit per spec area (CORD-01/03, CORD-02 §2/§5/§6 + CORD-04, CORD-02 §7/§9 +
CORD-05/06, CORD-07/08), each reading our code against the spec text and against
Armada. CORD-02 §8 (the Community List) was reviewed by hand against Armada's
`listFrag.ts` / `communityList.ts`. Status legend: **fixed** (this branch, with tests),
**open** (not done, with the reason).

## What changed upstream since our last pass

| Commit | Date | Change | Our state before this review |
|---|---|---|---|
| `d584686` | 07-27 | CORD-02 §9: the dissolution tombstone's `eid` is the `community_id`; verifiers MUST refuse anything else, including the old all-zero `eid` | Not implemented at all: we never derived `dissolved_pk`, never read or wrote a tombstone, and folded a vsk-10 *Control Plane* edition as dissolution with no `eid` check |
| `e8c4eeb` | 08-02 | CORD-04 §7: provable, rotation-proof Pins (vsk 11, `PIN_MESSAGES` bit 11, `concord/pins` coordinate, NIP-44 key-disclosure proof bundles) | Missing (only the permission bit existed) |
| `93fbecf` | 08-03 | CORD-08: Disappearing Messages (`message_expiration` in metadata, NIP-40 tags on rumor + wrap, kind 1740 timer notice) | Missing, and worse: a metadata edit dropped the field, silently turning off Armada's default 30-day timer for everyone |
| `96f0647`, `bbc67b6` | 08-06 | CORD-02 §2 `control_root` write gate | Implemented (v1.14.0) |
| `759448a`, `ee6f639` | 08-11/15 | CORD-02 §8: the Community List becomes fragmented kind **33302** (13302 retired), unpadded base64url for every 32-byte value, `seed` omitted when equal to `current`, mandatory tombstones, read-modify-write, a byte ceiling instead of a 50-entry cap | Still on 13302, hex, no tombstones (leaving just dropped the entry) |
| `01.md` | 08-11 | Encrypted application documents may choose their own encoding (only §8 does) | n/a |

Still-open upstream PRs worth tracking: **#23** (authenticate `community_root` at accept;
would make our stranded-recovery root move an explicit MUST violation), **#22**
(community-owned `av_brokers` in metadata), **#17** (Community Signals, vsk 12),
**#16** (CORD-09 blinded mention locators), **#7** (drop the `ms` tag). #12 (split
read/write planes) is superseded by the merged `control_root` design.

## Findings

Ranked security > interop > feature inside each group.

### Security

| # | Spec | Finding | Status |
|---|---|---|---|
| S1 | 02 §9 | Dissolution: no `dissolved_pk` plane, no `eid` binding, spec tombstone (chainless, no `ev`) could not even parse; a vsk-10 Control Plane edition dissolved with no binding check | **fixed** — `ConcordDissolution` (derive, build, verify with `eid == community_id`, 20014 seal, owner author); session + planner subscribe the plane; CLI `amy concord dissolve`; the Control Plane fold no longer reads vsk 10 |
| S2 | 03 §1-2 | Posts into a `private:true` channel go to the **root-derived** plane every member can decrypt, under a Lock icon; real private channels can't be read | open → chat-plane batch |
| S3 | 01 Deletions | Deleting your own Concord message sends a *signed* NIP-17 kind 5 to the p-tagged users (leaks the rumor id outside the community) and never reaches the channel | open → chat-plane batch |
| S4 | 06 §2 | Stranded recovery adopts a bundle's newer `community_root` with no continuity or authority check — a link creator can relocate every member who joined through their link | open → rekey/invite batch |
| S5 | 04 §1 | Grant `eid` never checked against `grant_locator(cid, member)`; a second grant chain at a random coordinate overrides the canonical one, order-dependent | open → control-plane batch |
| S6 | 04 §4 | Banlist unions every fork instead of folding to one head; a ban on a losing fork can never be undone; banlist `eid` unchecked | open → control-plane batch |
| S7 | 04 §1 | Equal-version ties break on rumor id only, not authority-first; a low-ranked holder can grind an id to beat the owner | open → control-plane batch |
| S8 | 04 §1 | Metadata `eid` not required to equal `community_id`; a fresh coordinate at a high version bypasses the chain | open → control-plane batch |
| S9 | 02 §5 / App. B | Seal kind never enforced on read (Control must be 20014, Chat/rekey 20013); any rumor kind from a channel lands in `LocalCache` | open → control-plane + chat-plane batches |
| S10 | App. B | NIP-44 65,535-byte plaintext cap not enforced; quartz silently switches to the extended format strict readers reject | open → chat-plane batch |
| S11 | 05 §1 | Bundle bounds (channel count, relay cap) not enforced; the join fetches from every relay a bundle names | open → rekey/invite batch |
| S12 | 06 §3 | Compaction doesn't abort on an incomplete fold, and republishes the compacted plane before the root roll is confirmed | open → rekey/invite batch |
| S13 | 02 §9 | "Death wins every race": rekey adoption / recovery / refounding don't check for dissolution | open → rekey/invite batch |
| S14 | 05 §2 | `classify` trusts the relay filter: no signature, `pubkey == link_signer`, or `d == ""` check; a relay can forge a revocation | open → rekey/invite batch |

### Interop (Armada drops or diverges)

| # | Spec | Finding | Status |
|---|---|---|---|
| I1 | 02 §8 | Community List on retired 13302, hex, no fragments, no tombstones | in progress (this branch) |
| I2 | 02 §6 | Metadata/Channel edits rebuilt from scratch, wiping `custom`, `message_expiration` (CORD-08), `av_brokers` | **fixed** — `ConcordJson.encodePreserving` lays every edit over the authorized head; metadata/channel forms start from the folded entity |
| I3 | 03 §2 | Per-channel `voice` flag still modeled and rendered (every Channel is callable since `23dcea5`) | **fixed** — field removed (rides through as an unknown key), Mic icon and blank-preview special case removed |
| I4 | 04 §1/§5 | `vac` never written or verified — Armada drops every non-owner edition we author | open → control-plane batch |
| I5 | 04 §2 | Role content lacks `role_id`; Armada ignores every role we mint | open → control-plane batch |
| I6 | 04 §1 | First edition is v0; spec says versions start at 1 | open → control-plane batch |
| I7 | 04 §7 | Unknown-vsk editions (pins, signals) dropped by our compaction | open → control-plane batch |
| I8 | 06 | Rekey `chunk` index is 0-based; Armada requires 1-based and drops all our Refoundings | open → rekey/invite batch |
| I9 | 06 §3 | Rotations carry no `vac` | open → rekey/invite batch |
| I10 | 06 | 120 base blobs per chunk can overflow NIP-44; Armada budgets 99 @104 B / 90 @136 B | open → rekey/invite batch |
| I11 | 05 §3 | Invite links carry more than 3 bootstrap relays; Armada's decoder throws | open → rekey/invite batch |
| I12 | 06 §3 | No race convergence (lowest new root), not idempotent on retry | open → rekey/invite batch |
| I13 | 03 §3 | Binding check not strict (duplicates accepted, `"04"`/`"+4"` parse) | open → chat-plane batch |
| I14 | 03 §2 | Channel deletion not terminal across the chain; no 64-byte name cap | open → chat-plane batch |
| I15 | 02 §4 | No `ms` tag on chat rumors | open → chat-plane batch |
| I16 | examples §2.1 | Inline quote `q` tag is 2-element, Armada writes `["q", id, "", author]` | open → chat-plane batch |
| I17 | 04 §2, 02 §6 | Caps (role name, roles per member/community, metadata name/description) not enforced | open → control-plane batch |
| I18 | 05 §1, §4 | Join doesn't echo invite attribution; CLI join publishes no Guestbook Join; Invite List merge lets the patch win; malformed tombstones dropped | open → rekey/invite batch |

### Features

| # | Spec | Finding | Status |
|---|---|---|---|
| F1 | 04 §7 | Pins | open → pins batch |
| F2 | 08 | Disappearing Messages (sender tags, reader refusal/hiding/purge, 1740 notice, settings UI) | metadata field + parse **fixed**; the rest open → chat-plane batch |
| F3 | 07 | A/V calls: only key derivation, the 27235 grant and 23313 presence builders exist; no broker/SFU client, no media E2EE. Needs a LiveKit client whose license must be checked first | open — out of scope for this pass |
| F4 | 07 | Broker token has no nonce (same-second requests collide in the broker's replay set); presence fold doesn't take latest-per-author | open → chat-plane batch (quartz only) |
| F5 | 05 §5 | Invite Registry (vsk 8) not published or folded | open |
| F6 | 05 §6 | Direct invites: wire format only, no send/receive | open |
| F7 | 06 §1-2 | Channel-scope rekeys; private-channel keys in invites | open (depends on S2) |
| F8 | 06 §2, 02 §8 | Walk forward from `seed`; we still keep intermediate roots in a `held_roots` List extension the spec says doesn't belong there | open |
| F9 | 04 §6 | Kick (kind 3309) | open |
| F10 | 03 | WebXDC (kind 3310) | open |

## Spec issues to raise upstream

- 02 §8 and examples §6.2 cite "a dissolution payload (CORD-06 §1)", but CORD-06 defines no
  such payload, and Armada has none. Dangling reference.
- CORD-07 §2 should require a nonce in the 27235 grant (Armada already adds one): two
  members requesting in the same second otherwise produce the same event id and collide
  in the broker's mandatory replay set.
