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
| S2 | 03 §1-2 | Posts into a `private:true` channel go to the **root-derived** plane every member can decrypt, under a Lock icon; real private channels can't be read | **fixed** — `ConcordActions.currentChannelPlane`/`historicalChannelPlanes` derive a private channel only from the entry's held `privateChannels` key at its channel epoch (never the root plane); session, planner, plane registry, app verbs and CLI all go through it; a keyless private channel has no plane (`ConcordChannel.keyHeld`/`canPost()` false, composer replaced by a notice, `amy concord send` → `no_channel_key`); held keys adopt in place, and invite-delivered keys are stored on join. Creating private channels stays open (F7) |
| S3 | 01 Deletions | Deleting your own Concord message sends a *signed* NIP-17 kind 5 to the p-tagged users (leaks the rumor id outside the community) and never reaches the channel | **fixed** — `ChannelChat.delete` (examples §2.4: binding + `e` per target + `k` per kind) sealed 20013 on the channel plane (`ConcordActions.buildChannelDelete`); `Account.delete`/`deletePrivately` route Concord notes (messages, replies, reactions) to `AccountConcordActions.deleteConcordRumors`, each target on the plane of its bound epoch (Armada always uses the current plane); tapping an own Concord reaction retracts it the same way |
| S4 | 06 §2 | Stranded recovery adopts a bundle's newer `community_root` with no continuity or authority check — a link creator can relocate every member who joined through their link | **fixed** — `ConcordStrandedRecovery` only detects (`isStranded`); the background sweep never moves the base (exposes `strandedConcordCommunities`); moving forward from a bundle needs the user to re-open the link (`joinConcordViaInvite`, `amy concord recover --rejoin`). PR #23's accept-time root gate not implemented (text unavailable; genuine owner editions re-wrap into any plane, so it needs the PR's exact rule) |
| S5 | 04 §1 | Grant `eid` never checked against `grant_locator(cid, member)`; a second grant chain at a random coordinate overrides the canonical one, order-dependent | **fixed** — `AuthorityResolver.resolve` / `ConcordCommunityState.fold` / `authorizedHeads` take the `community_id`; a Grant edition counts only when its `eid == grant_locator(cid, content.member)` |
| S6 | 04 §4 | Banlist unions every fork instead of folding to one head; a ban on a losing fork can never be undone; banlist `eid` unchecked | **fixed** — the Banlist folds to ONE authority-gated head at `banlist_locator(cid)`; the rank-delta rule is kept; re-heal = the writer re-applying atop the winner (`ConcordModeration.ban` again after refold) |
| S7 | 04 §1 | Equal-version ties break on rumor id only, not authority-first; a low-ranked holder can grind an id to beat the owner | **fixed** — `EditionFold.foldEntityGated`/`foldGated` take an author rank: authority first, then rumor id, both in the head pick (Armada `pickHead`) and in the walk's anchor/next-link choice. The walk part goes past Armada (whose `version.fold` walks on rumor id only, so a lower-id fork can strand an edition chained on the owner's sibling) — spec followed |
| S8 | 04 §1 | Metadata `eid` not required to equal `community_id`; a fresh coordinate at a high version bypasses the chain | **fixed** — metadata is read only at `eid == community_id` (a fold gate, `AuthorityResolver.isWellFormed`) |
| S9 | 02 §5 / App. B | Seal kind never enforced on read (Control must be 20014, Chat/rekey 20013); any rumor kind from a channel lands in `LocalCache` | **fixed** — control: `ControlEdition.fromOpened` refuses a non-20014 seal; used by the session, `ConcordActions.controlEditions` (CLI) and Refounding compaction; chat: `ChannelChat.acceptOpened` requires a 20013 seal, a `CHAT_KINDS` rumor (9, 1111, 7, 5, 3302, 23311, 1740), the strict binding and a well-formed `ms`, for stored and typing wraps; `EventCache.consumeConcordRumor` refuses non-chat kinds too |
| S10 | App. B | NIP-44 65,535-byte plaintext cap not enforced; quartz silently switches to the extended format strict readers reject | **fixed** — `ConcordStreamEnvelope` seal/wrap refuse a plaintext over 65,535 UTF-8 bytes (Armada `encryptChecked`), and open refuses an extended-format payload before decrypting |
| S11 | 05 §1 | Bundle bounds (channel count, relay cap) not enforced; the join fetches from every relay a bundle names | **fixed** — `ConcordInviteBundle.bound`: >256 channels refused, `relays` de-duplicated + truncated to 5, applied in `parse` (link bundles) and `ConcordDirectInvite.parse`; fragments decode ≤3 relays |
| S12 | 06 §3 | Compaction doesn't abort on an incomplete fold, and republishes the compacted plane before the root roll is confirmed | **fixed** — `compactControlPlane(…, mustCarry)` throws `IncompleteControlPlaneException` on a missing honored head; app sweeps the plane paged (majority of relays DRAINED) and CLI pages it; rekey chunks are `publishAndConfirm`ed first, compaction published only after |
| S13 | 02 §9 | "Death wins every race": rekey adoption / recovery / refounding don't check for dissolution | **fixed** — dissolved check in `drainConcordRekeys`, `recoverStrandedConcordCommunities`, `refoundConcordCommunity`, the explicit rejoin, and CLI `rekey`/`recover`/`refound` (`ConcordCommands.isDissolved`) |
| S14 | 05 §2 | `classify` trusts the relay filter: no signature, `pubkey == link_signer`, or `d == ""` check; a relay can forge a revocation | **fixed** — `classify(wraps, linkSignerPubKey, token)` keeps only events with kind 33301, author == link signer, `d == ""` and a valid signature (`isAtCoordinate`); every caller passes the signer |

### Interop (Armada drops or diverges)

| # | Spec | Finding | Status |
|---|---|---|---|
| I1 | 02 §8 | Community List on retired 13302, hex, no fragments, no tombstones | in progress (this branch) |
| I2 | 02 §6 | Metadata/Channel edits rebuilt from scratch, wiping `custom`, `message_expiration` (CORD-08), `av_brokers` | **fixed** — `ConcordJson.encodePreserving` lays every edit over the authorized head; metadata/channel forms start from the folded entity |
| I3 | 03 §2 | Per-channel `voice` flag still modeled and rendered (every Channel is callable since `23dcea5`) | **fixed** — field removed (rides through as an unknown key), Mic icon and blank-preview special case removed |
| I4 | 04 §1/§5 | `vac` never written or verified — Armada drops every non-owner edition we author | **fixed** — `ConcordModeration` stamps every non-owner edition with `AuthorityCitations.forActor` (own grant coordinate, folded head version + hash); every fold gate (roles, grants, banlist, metadata, channels, unmodeled kinds, floors/compaction) requires it per Armada `citationSatisfied` |
| I5 | 04 §2 | Role content lacks `role_id`; Armada ignores every role we mint | **fixed** — `RoleEntity.roleId` written by `defineRole`; read accepts a legacy role without it, refuses a mismatching one |
| I6 | 04 §1 | First edition is v0; spec says versions start at 1 | **fixed** — genesis and every new entity start at v1; v0 chains still read |
| I7 | 04 §7 | Unknown-vsk editions (pins, signals) dropped by our compaction | **fixed** — a canonical unmodeled `vsk` parses with `entityKind == null` + raw `vsk`; floors and compaction carry its gated head verbatim (11 by `PIN_MESSAGES`, 12 by `MANAGE_CHANNELS`, others by any staff bit). vsk 6/7/9/10 are no longer parsed as Control editions |
| I8 | 06 | Rekey `chunk` index is 0-based; Armada requires 1-based and drops all our Refoundings | **fixed** — `ConcordRekey.tags` takes a 1-based index (`require 1..n`); `chunkOf` parses strict decimals and refuses 0 / `i > n`; receivers drop malformed chunks |
| I9 | 06 §3 | Rotations carry no `vac` | **fixed** — rotations carry `vac` on every chunk (`ConcordRotationAuthority.citationFor`, owner none); receivers require `ConcordReceive.isHonoredRotation` (BAN + `citationSatisfied`, Armada semantics) in the app drain and CLI `rekey`; a rotator whose chunks cite different Grants is dropped |
| I10 | 06 | 120 base blobs per chunk can overflow NIP-44; Armada budgets 99 @104 B / 90 @136 B | **fixed** — `ConcordRekey.chunkBlobs` budgets the rumor JSON at 40,960 bytes (Armada `REKEY_RUMOR_MAX_BYTES`) plus the 120 count cap; test pins the seal (wrap plaintext) ≤ 65,535 with 136-byte blobs |
| I11 | 05 §3 | Invite links carry more than 3 bootstrap relays; Armada's decoder throws | **fixed** — `encodeFragment` truncates non-stock lists to 3 (stock set stays a flag); `decodeFragment` refuses count > 3 |
| I12 | 06 §3 | No race convergence (lowest new root), not idempotent on retry | **fixed** — `findNewRoot` converges on the lowest authorized root (`accept` filter before `converge`); sessions watch the current epoch's own rekey address and `drainConcordRekeys` heals down-only (`ConcordReceive.withHealedRoot`), keeping the losing root as a same-epoch held root (only the lowest per epoch is folded: `canonicalHeldRoots`); retries reuse reserved keys (`ConcordRefounding.reserveKeys`; in-memory in the app, persisted in amy's store). Not done: the CLI has no heal step; re-issuing a losing branch's channel keys (no private channels yet, F7) |
| I13 | 03 §3 | Binding check not strict (duplicates accepted, `"04"`/`"+4"` parse) | **fixed** — exactly one `channel` and one `epoch` tag, epoch compared as its canonical decimal string (Armada `uniqueTag`/`checkChannelBinding`); builders drop binding tags smuggled in `extraTags` |
| I14 | 03 §2 | Channel deletion not terminal across the chain; no 64-byte name cap | **fixed** — any gated channel edition with `deleted:true` retires the channel for good (Armada `everDeleted`); the channel gate refuses an empty or >64-byte name so the fold falls back to the previous candidate, and `defineChannel`/create/rename refuse to mint one |
| I15 | 02 §4 | No `ms` tag on chat rumors | **fixed** — every `ChannelChat` rumor carries `["ms", 0..999]` after the binding; malformed/duplicated `ms` drops the rumor; `channelMessages` and edit recency order by `created_at*1000+ms` (the shared feed still sorts by `created_at`; open PR #7 may drop `ms`) |
| I16 | examples §2.1 | Inline quote `q` tag is 2-element, Armada writes `["q", id, "", author]` | **fixed** — four-element `q` (the `p` credit stays, as Armada keeps it). Also CORD-03 §3: your own kind-1111 thread replies can now be edited (kind 3302) like kind-9 messages |
| I17 | 04 §2, 02 §6 | Caps (role name, roles per member/community, metadata name/description) not enforced | **fixed** — `ConcordLimits`; refused on write (factory, `ConcordModeration`, app verbs, CLI) and enforced at fold like Armada: over-cap role/metadata editions fall back, a Grant's `role_ids` trim to 64, the Community keeps its 100 lowest `role_id`s. Also: `ev`/`vac`/`vsk` must be canonical decimals and a duplicate `vsk`/`eid`/`ev`/`ep`/`vac` invalidates the edition; CLI knows `VIEW_AUDIT_LOG`/`MENTION_EVERYONE`/`PIN_MESSAGES`; the app's Admin role matches Armada's `ADMIN_ALL` |
| I18 | 05 §1, §4 | Join doesn't echo invite attribution; CLI join publishes no Guestbook Join; Invite List merge lets the patch win; malformed tombstones dropped | **fixed** — join echoes `creator_npub`/`label` in the Guestbook Join (mints now set `creator_npub`); `amy concord join` publishes a Guestbook Join; Invite List merge is first-wins per token; untyped tombstones carried as `opaqueTombstones` and still retire their token |

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

- CORD-06 §1 counts rekey capacity in blobs ("up to 120 participants per event"), but 120 base
  blobs overflow NIP-44's 65,535-byte plaintext once wrapped; the spec should state the byte budget
  (Armada uses a 40,960-byte rumor ceiling).
- The rekey blob plaintext is base64-encoded before NIP-44 (signer APIs take strings); unpinned by
  the spec, as Armada's own comment notes.
- Rekey seal kind (20013) and the `chunk` indexing base are implied by examples only; worth a MUST.
- 02 §8 and examples §6.2 cite "a dissolution payload (CORD-06 §1)", but CORD-06 defines no
  such payload, and Armada has none. Dangling reference.
- CORD-07 §2 should require a nonce in the 27235 grant (Armada already adds one): two
  members requesting in the same second otherwise produce the same event id and collide
  in the broker's mandatory replay set.
