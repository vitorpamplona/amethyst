# Concord: soft-ban and Control Plane audit

**Scope:** what a removed member — or a moderator who turns — can still do to a Concord community,
assuming a **malicious client** (no client-side rule binds them; only cryptography, the fold, and
the relay do).
**Date:** 2026-08-08. **Status:** findings only, nothing fixed yet.
**Companion:** `docs/concord-banlist-rank-conformance.md` (the rank half of CORD-04 §4, already
reported to Armada and fixed here).

Each finding says how it was established. **Verified** means a test in this repo reproduces it;
**Read** means it follows from the code but no test was written. Every "Verified" line names the
test.

---

## Summary

| # | Finding | Severity | Needs a ban? | Recoverable? |
|---|---------|----------|--------------|--------------|
| [V1](#v1) | One edition at `version = Long.MAX_VALUE` pins an entity forever | **Critical** | No — any bit-holder | **No** |
| [V2](#v2) | A banned staffer keeps Role/Grant/Banlist authority | **Critical** | Yes | Yes (Refounding) |
| [V3](#v3) | A rogue rotator compacts the banlist away | High | Via V2 | Partly |
| [V4](#v4) | The Refounding recipient set is attacker-inflatable | High | No | Yes |
| [V5](#v5) | The ban is a per-pubkey display rule; the channel key is not revoked | High | Yes | Yes (Refounding) |
| [V6](#v6) | Channel history is deletable on a naive third-party relay | High | Yes | **No** (history) |
| [V7](#v7) | Banlist rank rule diverges from Armada | Medium | — | — |
| [V8](#v8) | A soft ban revokes no read access and no live invite | Medium | Yes | Yes (Refounding) |
| [V9](#v9) | The base-rekey plane is writable by every member | Low | Yes | Yes |
| [V10](#v10) | Stranded recovery: either broken, or a removal bypass | **Critical** | Yes | — |
| [V11](#v11) | Voice rooms are key-gated, not roster-gated | High | Yes | Yes (Refounding) |
| [V12](#v12) | Typing indicators are not ban-filtered | Low | Yes | Yes |

The two structural causes worth naming up front, because most of the list collapses into them:

- **Authority is checked in two places that disagree.** `ConcordCommunityState.fold` gates
  METADATA/CHANNEL/INVITE through `authority.hasPermission` (`!isBanned && …`), while ROLE, GRANT
  and BANLIST are gated *inside* `AuthorityResolver.resolve` by `holdsManageRoles` / `bitsOf` /
  `effectivePermissionsOf`, none of which consult the banlist. That is V2, and V3 follows from it.
- **A ban removes standing, never keys.** Everything a member holds — `community_root`, channel
  keys, `control_root` if staff, live invite links — survives it. Only a CORD-06 Refounding rotates
  those, which is why V4 (making Refounding expensive) is worth more to an attacker than it looks.

---

## <a name="v1"></a>V1 — One edition at `Long.MAX_VALUE` pins an entity forever

**Critical. Does not require a banned user, a sockpuppet, or the owner's absence. Unrecoverable.**

*Verified:* `quartz/…/cord04Roles/ControlPlaneVersionExhaustionTest.kt` (3 tests).

Any current holder of an entity's permission bit publishes one edition at `version =
Long.MAX_VALUE`. For every client that holds an `EntityFloor` for that entity, that edition becomes
the permanent head:

1. it wins, so the entity shows the attacker's content;
2. `authorizedHeads` raises the entity's floor to `Long.MAX_VALUE`;
3. no honest edition can ever exceed that floor, so the entity can never be repaired;
4. a Refounding that drops the poison does not help — nothing is offered at or above the floor, the
   fold reports a gap, and falls back to `EntityFloor.known`, which *is* the poison.

The chain walk is not the weakness (it advances only to `head.version + 1` citing the head's hash,
so a fresh joiner is unaffected). The weakness is the **compaction arm** of `EditionFold.foldEntity`:
once a floor exists and the entity is in the epoch snapshot — which `fold` always builds from the
editions handed to it — the head comes from `bootstrapHead`, i.e. *highest version at or above the
floor*, with no `prev`, no hash, no contiguity. Version becomes the whole contest.

Concretely: a moderator with `MANAGE_CHANNELS` deletes `#general` permanently for everyone; one
with `MANAGE_METADATA` renames the community permanently. Demoting or banning them afterwards
changes nothing — the damage is in every client's floor. `ConcordRefounding.compactControlPlane`
also selects the head per entity by raw highest version, ungated, so an honest rotator carries the
poison into every future epoch, where fresh joiners then anchor on it as their baseline.

The banlist survives, by accident: `AuthorityResolver` folds it on its own floor-less chain walk and
re-heals the union across authorized editions, so an honest ban still lands. That accident is the
only thing separating this from a permanently unmoderatable community, and it is now pinned by
`aPoisonedBanlistStillAcceptsTheOwnersBan`.

**Fix direction.** The compaction arm needs a bound, since it is the arm that trades contiguity for
cross-epoch tolerance. Options, roughly in order of preference:

- Cap the version delta the arm will accept in one step (a compacted head is legitimately ahead of
  the floor, but by a chain's worth, not by 2^63). Anything above the cap is a gap, not a head.
- Make `bootstrapHead` prefer the highest version *reachable by a chain* among the offered editions,
  falling back to raw version only when no chain connects.
- Have `compactControlPlane` select the authority-gated fold head rather than raw max version, so a
  poison is at least not propagated by honest rotators.

The first is the smallest change and closes the unrecoverability; the third should happen regardless.

## <a name="v2"></a>V2 — A banned staffer keeps Role, Grant and Banlist authority

**Critical.** *Verified:* `quartz/…/cord04Roles/BannedStaffEscalationTest.kt` (13 tests).

`hasPermission` is ban-aware; the resolver's internal gates are not, and structurally cannot be as
written — the roles/grants fixpoint settles before `banned` is computed. So a banned member who
still holds `control_root` keeps the roster. In the reproduction they:

- ban every member they outrank, directly, with no puppet;
- revoke the surviving moderators' grants and retire the roles beneath them;
- **mint a fresh, unbanned npub** at the next position down, which then passes every ban-aware gate:
  deletes every channel, rewrites the metadata, bans the rest of the community, creates invites;
- and, because `drainConcordRekeys` authorizes a rotator by `hasPermission(rotator, BAN)`, that
  puppet can publish a Refounding omitting the owner — every honest client follows it and the owner
  is stranded on a dead root.

Self-unban is *not* reachable and neither is a puppet-unban: the delta rule gates removals and
strict outranking means nobody outranks themselves, while no edition may claim a position at or
above its signer, so the delegation chain only descends. Two hand-crafted attempts that avoid
removal entirely — forking the banlist at genesis, and building a private chain — are also refused,
by CORD-04 §4's re-heal union rather than by the rank rule. **The union is load-bearing security
here, not just convergence.**

**Fix direction.** Make the resolver's gates ban-aware. The ordering problem is real (you cannot
know who is banned before folding the banlist, nor who may write it before knowing who is banned),
so resolve it as a bounded two-pass where authority only ever *shrinks*: pass A settles the roster
as today and computes the banlist; pass B re-resolves roles/grants dropping editions whose author is
banned in pass A; then recompute the banlist under pass B's roster, keeping only bans still
authorized. Deterministic, terminates, no oscillation on mutual bans. **Consensus-affecting**: until
Armada ships the same rule, we will drop editions they honor.

## <a name="v3"></a>V3 — A rogue rotator compacts the banlist away

**High.** *Verified:* `aRogueRotatorCompactsTheBanAwayForEveryClientWithoutAFloor`.

A CORD-06 §3 compaction re-wraps one edition per entity and the *rotator* picks it, so a rotator can
decline to carry the banlist forward. Every edition it serves is genuine, so no signature check sees
the omission — `EntityFloor`'s own KDoc names this case ("clearing a banlist"). A banned member
cannot rotate, but the V2 puppet can.

The result is not a clean unban but a **split community**: clients that already folded the ban
refuse the rollback and still see it, fresh joiners have no floor and see no ban at all. Two
populations permanently disagreeing about who is a member, with no event either side can call
forged. Closing V2 removes the puppet and takes this with it; floors alone do not, since they only
protect people who were already there.

## <a name="v4"></a>V4 — The Refounding recipient set is attacker-inflatable

**High.** *Read:* `ConcordCommunitySession.allMembers()` / `emitChannelRumors`;
`AccountConcordActions.refoundConcordCommunity` step 2; `ConcordRefounding.buildBaseRekeyWraps`.

`allMembers()` = Guestbook joins ∪ `observedAuthors` ∪ roster ∪ owner, and it *is* the Refounding
recipient set. Both contributing sets are unbounded and both are attacker-writable: Guestbook joins
are self-signed (any key, no authority), and every author we decrypt is folded into
`observedAuthors` by design (CORD-02 §5, "observably present").

So each throwaway npub an attacker posts from, or announces, is one more mandatory NIP-44 blob in
the next Refounding, chunked 120 per event. 100k identities ⇒ ~100k encryptions and ~830 published
events — while they keep posting. **The attack inflates the cost of its own remedy**, and the remedy
is the only hard removal Concord has.

This is the cheapest thing on the list to fix and the only one that is not consensus-affecting: cap
the recipient set, prefer recent/attested members when over the cap, and surface what was dropped
(a silent truncation strands real members). Worth doing first.

## <a name="v5"></a>V5 — The ban is a per-pubkey display rule and the channel key is not revoked

**High.** *Read:* `Account.consumeConcordRumorGated` (`isBanned(rumor.pubKey)`), `Account.isAcceptable`.

Writing to a channel needs the channel key, which the ban does not take away; the seal author is
whatever key the client feels like using. A malicious client therefore posts every message from a
fresh npub and `isBanned` never matches — moderation is whack-a-mole against an infinite identity
supply. Each message also costs every member two NIP-44 decrypts and two signature verifications
*before* the banlist check runs, and each fresh author inflates V4.

There is no client-side answer; only a Refounding rotates the key out from under them. That is the
correct design, which is why V4 matters so much.

## <a name="v6"></a>V6 — Channel history is deletable on a naive third-party relay

**High, external.** *Verified (that we are safe):*
`geode/…/ConcordPlaneKeyDeletionTest.kt` (3 tests).

CORD-01 signs every wrap with the shared stream key, so on the wire a Concord channel is one author
publishing everything — and every member holds that author's secret. NIP-09 and NIP-62 authorize on
the outer `pubkey`. Read the obvious way, that hands any ex-member a one-event wipe of the whole
community's history, and geode's own guarantee ("a kind-5 from pubkey X cannot delete pubkey Y's
events") is vacuous inside a plane.

**On our relay it is refused, but only because of a rule written for something else:**
`Event.owner()` gives a kind-1059 to its *p-tag recipient* rather than its signer, and
`ConcordStreamEnvelope` stamps a freshly random p-tag on every wrap, so each wrap is owned by a
one-time key nobody holds. Both halves are load-bearing, neither was written for this, and either
one silently re-opens the hole — all three are now pinned, including a counterfactual showing a wrap
addressed to a *real* key is deletable by its holder.

A community publishes wherever its metadata points. Any relay that authorizes deletion by matching
`pubkey` still hands every ex-member the wipe button, and a Refounding protects only the future.
Worth a note in the CORD-01 spec and a line in the relay-selection guidance.

## <a name="v7"></a>V7 — Banlist rank rule diverges from Armada

**Medium, known, deliberate.** See `docs/concord-banlist-rank-conformance.md`, already reported.

We enforce §3's rank half on the Banlist and Armada does not, so the two clients can show different
banlists. Shipped knowingly. Row 3 of that report ("a banned `BAN` holder unbans themselves") was
left open as a fixpoint-ordering question — V2 is the general form of it, and the fix proposed there
resolves both.

## <a name="v8"></a>V8 — A soft ban revokes no read access and no live invite

**Medium, inherent.** *Read:* CORD-02/05.

Until a Refounding, a banned member decrypts everything published — the ban only stops honest
clients from *showing* their posts, not from delivering the group's posts to them. They also keep
any invite links they created while privileged; those still resolve to bundles carrying the current
root. Publishing the root, or one live link, invites an unbanned crowd that each has to be banned
individually (and see V5).

Not a bug so much as the definition of a soft ban, but it belongs on the list because the UI should
say so: "Ban" and "Remove from community" are very different promises and users will read the first
as the second.

## <a name="v9"></a>V9 — The base-rekey plane is writable by every member

**Low.** *Read:* `ConcordKeyDerivation.baseRekeyAddress`, `AccountConcordActions.drainConcordRekeys`.

The base-rekey address derives from `community_root`, so any member — banned included — can mint
valid wraps there. Authorization happens after the blobs are scanned, so a flood costs every member
a locator scan per blob on every revision tick. Bounded work per wrap and no correctness impact;
listed for completeness.

## <a name="v10"></a>V10 — Stranded recovery: either broken, or a removal bypass

**Critical, and it forks — one of the two halves is true and both are bad.**
*Read:* `ConcordStrandedRecovery`, `AccountConcordActions.recoverStrandedConcordCommunities`,
`AccountConcordActions.mintConcordInvite`.

`ConcordStrandedRecovery.isStranded` / `mergeForward` take only `(entry, bundle)`. There is **no
banlist check and no check that we were legitimately re-keyed** — the entire test is "the bundle at
my stored `inviteRef` sits at a higher epoch than I do". The unlock token lives in the link
fragment, which an ex-member keeps forever. So whether a removed member walks back in with the new
root depends *only* on whether the bundle at that coordinate ever advances an epoch.

In Amethyst it never does: `mintConcordInvite` mints a **fresh link signer per mint**, so nothing
re-publishes at an existing coordinate, and `refoundConcordCommunity` does not re-mint or revoke
anything. Two consequences, and they are the fork:

- **If nothing re-mints** — today's behaviour — then stranded recovery never fires *for anyone*.
  That makes it dead code, and the cure `drainConcordRekeys`' own KDoc points to for "a BAN-holder
  can evict anyone (the owner included) by omission" does not exist. An owner evicted by a rogue
  admin has no way back.
- **If anything re-mints at a stable coordinate** — which is what CORD-05's design describes ("the
  community keeps publishing its bundle at that same addressable coordinate, re-minted at the
  current epoch"), so plausibly Armada in a cross-client community — then every removed member who
  joined through a still-live link auto-recovers the new root on the 15-minute sweep, and
  re-announces a Guestbook join so they look current again. **Refounding, the only hard removal,
  is silently undone.**

Note also that `refoundConcordCommunity` never revokes the invite links the removed member created
or joined through, even though `ControlEntityKind.INVITE_REVOKED` exists and `classifyInvite`
already honors it.

**Fix direction.** Decide the intended semantics first — this needs a spec answer, not a patch.
Then: gate `mergeForward` on not being banned in the epoch we are merging *from*, have the
Refounding revoke the removed members' links, and either implement re-minting (so legitimate
recovery works) or drop the mechanism and give evicted owners a different route.

## <a name="v11"></a>V11 — Voice rooms are key-gated, not roster-gated

**High.** *Read:* `ConcordBrokerToken`, CORD-07 §2.

A member proves voice-room membership by signing a NIP-98 kind-27235 request with the channel's
**derived voice signer key**, whose pubkey is the SFU room name. The broker is stateless and holds
no community secret, so it cannot consult the Control Plane and has no idea a banlist exists. A
banned member keeps that key until a Refounding, so they can join the voice room and stay in it.
Nothing on the client side can evict them — kicking them from the UI does not kick them from the SFU.

This is the one place where a ban fails *audibly*, in real time, in front of everyone. Worth ranking
above its technical severity for that reason alone.

## <a name="v12"></a>V12 — Typing indicators are not ban-filtered

**Low.** *Read:* `ConcordCommunitySession.ingestTyping`.

`ingestTyping` checks the rumor is a typing heartbeat, is bound to the channel/epoch, and is not our
own — and nothing else. A banned member (or any fresh npub holding the channel key, see V5) shows
in the "… is typing" row indefinitely. Cheap to fix and user-visible: the promise a ban makes is
that the member disappears, and here they do not.

---

## What was NOT examined

This audit is bounded by what was opened. Checked and found sound: the wrap/seal envelope (no author
impersonation — `rumor.pubKey == seal.pubKey` and `rumor.verifyId()`), Concord chat edits
(`Note.latestConcordEdit` is author-gated, so a member cannot rewrite someone else's message), and
self-unban (V2).

Not looked at at all:

- **Private channels** (CORD-03 derived keys) — key delivery on grant, and channel-scoped rekey.
  Note that no channel-scoped rekey *receive* path appears to exist: `drainConcordRekeys` handles
  `ROOT_SCOPE` only, and `entry.privateChannels` is carried forward but never populated by a
  delivery path. If that is right, the only removal Amethyst can perform is a full-community
  Refounding — which is exactly what V4 makes expensive.
- **In-plane reactions and deletes** — the edit path is author-gated; the delete path was not read.
- **Guestbook kicks** (kind 3309) — the builder documents a KICK-bit + rank rule; the receive side
  was not verified against it.
- Unread counts and notification triggers, media/upload references from messages, the NIP-53 nests
  overlap, and the desktop client's Concord paths.

## Suggested order

1. **V4** — cheapest, not consensus-affecting, and it protects the remedy every other fix depends on.
2. **V1** — worst blast radius and the only unrecoverable one; does not need an attacker to be
   banned or privileged beyond a single ordinary bit.
3. **V2** (+V3, +V7 row 3) — one two-pass change closes all three. Coordinate with Armada first;
   this one splits consensus.
4. **V6** — spec note + relay guidance; our own behaviour is already correct and now pinned.
5. **V5 / V8** — UI honesty about what a ban does, and a "Remove from community" affordance that
   Refounds rather than bans.
