# Concord: soft-ban and Control Plane audit

**Scope:** what a removed member — or a moderator who turns — can still do to a Concord community.
**Date:** 2026-08-09. **Status:** A1–A4, B1, B2 and B4 are **fixed** on this branch; the rest are
accepted, deferred to the spec, or belong to other people's relays. Each section carries its own
status line.
**Companion:** `docs/concord-banlist-rank-conformance.md` (the rank half of CORD-04 §4, already
reported to Armada and fixed here).

Each finding says how it was established. **Verified** means a test in this repo reproduces it;
**Read** means it follows from the code but no test was written. Every "Verified" line names the test.

---

## How to read this list

Findings are split by **what the attacker needs**, because that decides who owns the fix and how
urgent it is:

- **[Part A — reachable from stock Amethyst](#part-a).** A banned user opens the shipping app and
  taps a button, or our own client does it for them on a timer. These are straightforwardly *our
  bugs*, they need no attacker sophistication at all, and every one of them is fixable in this repo
  without touching the protocol or coordinating with anyone.
- **[Part B — requires a malicious client](#part-b).** The attacker writes their own events, so no
  client-side rule binds them. We cannot stop them from *authoring* anything; we can only refuse to
  *honor* it. Fixes live in the fold, the store, or the spec.
- **[Part C — interop and not-yet-shipped surfaces](#part-c).**

The distinction is not academic. Part A is where the realistic attacker is: an irritated user who
just got banned has the app already installed and is not going to write a Nostr client. Part B is
where the *damage ceiling* is. Fix Part A first because it is cheap and it is what will actually
happen; fix Part B because it is what ends communities.

Two structural causes account for most of both halves:

- **Authority is checked in several places that disagree.** `ConcordCommunityState.fold` gates
  METADATA/CHANNEL/INVITE through the ban-aware `authority.hasPermission`. `AuthorityResolver`
  gates ROLE/GRANT/BANLIST internally through `holdsManageRoles` / `bitsOf` /
  `effectivePermissionsOf`, which are ban-blind. The **UI** gates through `effectivePermissions`,
  also ban-blind. The **action layer** mostly does not gate at all. Same question, four answers.
- **A ban removes standing, never keys.** `community_root`, channel keys, `control_root` if staff,
  and live invite links all survive it. Only a CORD-06 Refounding rotates those — which is why
  anything that makes Refounding expensive (B4) or reversible (A2) is worth more to an attacker
  than it first looks.

---

## Summary

### <a name="part-a"></a>Part A — reachable from stock Amethyst (our bugs)

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| [A1](#a1) | Any member — banned included — mints a working invite in one tap | **Critical** | **Fixed** |
| [A2](#a2) | Stranded recovery runs on a timer and never checks the banlist | **Critical** | **Fixed** (security half; liveness half open) |
| [A3](#a3) | The action layer has no permission checks; the UI's are ban-blind | High | **Fixed** |
| [A4](#a4) | A banned member keeps broadcasting "typing", and we keep showing it | Low | **Fixed** |
| [A5](#a5) | A banned member's own client keeps reading and rendering everything | Medium | Inherent — product decision |

### <a name="part-b"></a>Part B — requires a malicious client

| # | Finding | Severity | Needs a ban? | Status |
|---|---------|----------|--------------|--------|
| [B1](#b1) | One edition at `version = Long.MAX_VALUE` pins an entity forever | **Critical** | No — any bit-holder | **Fixed** |
| [B2](#b2) | A banned staffer keeps Role/Grant/Banlist authority | **Critical** | Yes | **Fixed** (matches Armada) |
| [B3](#b3) | A rogue rotator compacts the banlist away | High | Via B2 | **Mitigated** by B2 |
| [B4](#b4) | The Refounding recipient set is attacker-inflatable | High | No | **Fixed** (bounded) |
| [B5](#b5) | The ban is per-pubkey; the channel key is not revoked | High | Yes | Inherent — Refounding is the answer |
| [B6](#b6) | Channel history is deletable on a naive third-party relay | High | Yes | Correct here; external relays at risk |
| [B7](#b7) | The base-rekey plane is writable by every member | Low | Yes | Accepted |

### <a name="part-c"></a>Part C — interop and not-yet-shipped

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| [C1](#c1) | Banlist rank rule diverges from Armada | Medium | Reported; B2 widens the divergence |
| [C2](#c2) | CORD-07 voice rooms are key-gated, not roster-gated | Design | Note for whoever ships voice |

---

# Part A — reachable from stock Amethyst

No custom tooling. A banned user with the shipping app, or our own background sweep.

## <a name="a1"></a>A1 — Any member, banned included, mints a working invite in one tap

**Status: fixed.** `mintConcordInvite` and its button now require `CREATE_INVITE` (or ownership).


**Critical. The single most likely thing an irritated banned user actually does.**
*Read:* `AccountConcordActions.mintConcordInvite`, `ConcordChannelListScreen` (the `PersonAdd`
`IconButton`).

`mintConcordInvite` checks exactly two things: that the account is writeable, and that we have the
community in our joined list. **No `CREATE_INVITE` check. No banlist check.** And unlike the Edit
and channel-management buttons beside it, the invite `IconButton` is rendered with no `canEdit`
guard at all — it is always there, for everyone.

So the flow is: get banned, stay in the app, tap the person-add icon, share the link. The minted
bundle carries the community root we still hold, so anyone who opens it joins for real. Every
invited account is a fresh unbanned npub that moderators then have to ban one at a time.

Two aggravating details. The mint publishes a **fresh link signer per invite**, so it is a brand-new
coordinate — revoking the links the banned member was given does not touch the ones they mint.
And `CREATE_INVITE` is a real permission bit that the fold enforces on `INVITE_*` Control entities,
but the actual invite mechanism is a standalone kind-33301 addressable event published *outside* the
Control Plane, so that gate never applies to it. The permission is, in practice, unenforced.

**Fix.** Gate `mintConcordInvite` on `hasPermission(me, CREATE_INVITE) || isOwner(me)`, and gate the
button on the same. This is contained, uncontroversial, and closes the realistic attack. Do it first.

## <a name="a2"></a>A2 — Stranded recovery runs on a timer and never checks the banlist

**Status: security half fixed; liveness half open — and the fork is now resolved.** `isStranded` /
`mergeForward` take `bannedAtCurrentEpoch` as a *required* argument, so a removed member is no longer
walked back in. The open question was whether anything re-mints at a stable coordinate. **Armada
does** — `useLinkRefreshWatch2` re-posts every bundle on each epoch change — so in any cross-client
community this was a *live* removal bypass, not a hypothetical, and the fix was load-bearing. The
liveness half stands: Amethyst re-mints nothing, so legitimate recovery never fires for an
Amethyst-only community. See [the Armada comparison](#armada) for the two ways out.


**Critical, and it forks.** *Read:* `ConcordStrandedRecovery`,
`AccountConcordActions.recoverStrandedConcordCommunities`, `AccountConcordActions.mintConcordInvite`.

This is in Part A because **our own client performs it, unprompted**: the recovery sweep runs on the
revision tick for every joined community holding an `inviteRef`, every 15 minutes. The banned user
does nothing but leave the app installed.

`ConcordStrandedRecovery.isStranded` / `mergeForward` take only `(entry, bundle)` — no banlist
check, no check that we were legitimately re-keyed. The whole test is "the bundle at my stored
`inviteRef` sits at a higher epoch than I do", and the unlock token lives in the link fragment an
ex-member keeps forever. So whether a removed member walks back in depends *only* on whether
anything re-mints at that coordinate:

- **If nothing re-mints** — today, since Amethyst mints a fresh link signer per invite and the
  Refounding neither re-mints nor revokes — stranded recovery never fires for anyone. It is dead
  code, and the cure `drainConcordRekeys`' own KDoc points to for "a BAN-holder can evict anyone
  (the owner included) by omission" does not exist. An owner evicted by a rogue admin has no way back.
- **If anything re-mints at a stable coordinate** — which is what CORD-05's design describes, so
  plausibly Armada in a cross-client community — every removed member auto-recovers the new root and
  re-announces a Guestbook join, looking current again. **The only hard removal is silently undone.**

Note also that `refoundConcordCommunity` never revokes the links the removed member created or
joined through, though `ControlEntityKind.INVITE_REVOKED` exists and `classifyInvite` honors it.

**Fix.** Decide the intended semantics first — this needs a spec answer. Then gate `mergeForward` on
not being banned in the epoch we merge *from*, have the Refounding revoke the removed members'
links, and either implement re-minting so legitimate recovery works, or drop the mechanism and give
evicted owners another route.

## <a name="a3"></a>A3 — The action layer has no permission checks; the UI's are ban-blind

**Status: fixed.** Authority now lives in `AccountConcordActions.isAuthorizedFor`, which every
moderation verb funnels through, and every authorization test uses the ban-aware `hasPermission`.


**High (defense in depth).** *Read:* `AccountConcordActions` (`banConcordMember`,
`unbanConcordMember`, `editConcordMetadata`, `deleteConcordChannel`, `refoundConcordCommunity`),
`ConcordMembersScreen`, `ConcordChannelListScreen`.

Every moderation verb checks `isWriteable()` and the Control write key, and **nothing else** — no
permission bit, no banlist. Authority lives entirely in the composable that draws the button. Two
consequences:

1. **The UI's own gates are ban-blind.** `iCanBan`, `canEdit` (metadata) and `canManageChannels` all
   use `effectivePermissions`, which ignores the banlist. A banned admin still sees the Edit and
   channel-management controls. Those particular editions are dropped by every client's fold
   (METADATA/CHANNEL are `hasPermission`-gated), so the result is a **silently no-op control** —
   which this codebase elsewhere explicitly calls out as worse than no control at all.
2. **Ban/Remove survive only because of a second, unrelated gate.** `canBan` is
   `viewerCanBan && canBanTarget`, and `canBanTarget` routes through `canActOn`, which *is*
   ban-aware. Remove the second condition and a banned admin gets a working Ban button. That is a
   thin margin for a Critical-severity outcome (B2).

`refoundConcordCommunity` is the sharpest instance: its own guard is
`isOwner || effectivePermissions(me).has(BAN)` — deliberately ban-blind — so a banned BAN-holder can
launch a full community Refounding from the shipping app. Honest receivers refuse it
(`drainConcordRekeys` checks the ban-aware `hasPermission`), so the blast radius today is noise plus
self-stranding — but it is a race against banlist propagation, and a fresh joiner who has not folded
the ban yet has no reason to refuse.

**Fix.** Move the authority check into the action layer where it cannot be bypassed by a new caller
(desktop, CLI, a future screen), and switch every `effectivePermissions` used as an authorization
test to `hasPermission`. Keep `effectivePermissions` only where the question really is "what do
their roles say", independent of standing.

## <a name="a4"></a>A4 — A banned member keeps broadcasting "typing", and we keep showing it

**Status: fixed on both ends.**


**Low, both halves ours.** *Read:* `AccountConcordActions.sendConcordTyping`,
`ConcordCommunitySession.ingestTyping`.

The send side checks `isWriteable()` and nothing else, so a banned member's stock app keeps emitting
kind-23311 heartbeats. The receive side checks that the rumor is a typing heartbeat, is bound to the
channel/epoch, and is not our own — and nothing else. So a banned member sits in the "… is typing"
row indefinitely, in a channel where every message they send is hidden. Cheap to fix on both ends,
and it directly contradicts what a ban promises the user.

## <a name="a5"></a>A5 — A banned member's own client keeps reading and rendering everything

**Status: inherent; no code change.** The cryptography cannot be fixed without a Refounding, so what
is left is a product decision about how "Ban" and "Remove from community" are presented. Left for a
design pass rather than guessed at here.


**Medium, partly inherent.** *Read:* CORD-02/05, `ConcordCommunitySession`.

Until a Refounding, a ban stops honest clients from *showing* the banned member's posts; it does not
stop delivering the community's posts *to* them. Their stock app keeps subscribing, decrypting and
rendering the whole community in real time. They also keep any invite links they hold (and can mint
more — A1).

The cryptography here is inherent to a soft ban, but the **product** side is ours: "Ban" and "Remove
from community" are very different promises and the UI presents them as neighbours in one menu.
Worth making the difference explicit at the point of choice, and worth defaulting destructive
moderation to the Refounding path.

---

# Part B — requires a malicious client

The attacker writes their own events, so nothing client-side binds them. We can only refuse to honor
what they publish.

## <a name="b1"></a>B1 — One edition at `Long.MAX_VALUE` pins an entity forever

**Status: fixed.** The compaction arm tries the floor-anchored chain first and bounds the bootstrap
jump at `EditionFold.MAX_COMPACTION_VERSION_JUMP`; `compactControlPlane` picks the chain head rather
than raw max version. The three reproductions now assert the fixed behaviour, and
`aGenuineCompactionJumpIsStillFollowed` pins the CORD-06 §3 tolerance the bound must not break.


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

## <a name="b2"></a>B2 — A banned staffer keeps Role, Grant and Banlist authority

**Status: fixed. Not consensus-affecting after all** — see [Armada comparison](#armada). Armada
already implements the same two-pass, so this brings us *into* line rather than out of it. One
narrower divergence remains, described there. `AuthorityResolver.resolve` is now a bounded two-pass
where authority only shrinks. Note the deliberate cascade it brings: every edition a banned member
ever authored is dropped, so banning an admin also demotes everyone that admin promoted. That is the
literal reading of CORD-04 §4 and it is what kills the sockpuppet, but a legitimate promotion by a
later-banned admin vanishes with it and has to be re-issued. 

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

## <a name="b3"></a>B3 — A rogue rotator compacts the banlist away

**Status: mitigated by B2.** The rotator this needed was the sockpuppet, which can no longer be
minted. A *legitimately* privileged rotator can still omit the banlist, and `EntityFloor` remains the
only defense for clients that already folded it — unchanged, and still worth a spec fix.


**High.** *Verified:* `aRogueRotatorCompactsTheBanAwayForEveryClientWithoutAFloor`.

A CORD-06 §3 compaction re-wraps one edition per entity and the *rotator* picks it, so a rotator can
decline to carry the banlist forward. Every edition it serves is genuine, so no signature check sees
the omission — `EntityFloor`'s own KDoc names this case ("clearing a banlist"). A banned member
cannot rotate, but the B2 puppet can.

The result is not a clean unban but a **split community**: clients that already folded the ban
refuse the rollback and still see it, fresh joiners have no floor and see no ban at all. Two
populations permanently disagreeing about who is a member, with no event either side can call
forged. Closing B2 removes the puppet and takes this with it; floors alone do not, since they only
protect people who were already there.

## <a name="b4"></a>B4 — The Refounding recipient set is attacker-inflatable

**Status: fixed (bounded).** The recipient set is capped, the owner-rooted roster is kept first, and
anything dropped is logged rather than silently truncated.


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

## <a name="b5"></a>B5 — The ban is a per-pubkey display rule and the channel key is not revoked

**Status: inherent.** No client-side fix exists; a Refounding is the answer, which is why B4 mattered.


**High.** *Read:* `Account.consumeConcordRumorGated` (`isBanned(rumor.pubKey)`), `Account.isAcceptable`.

Writing to a channel needs the channel key, which the ban does not take away; the seal author is
whatever key the client feels like using. A malicious client therefore posts every message from a
fresh npub and `isBanned` never matches — moderation is whack-a-mole against an infinite identity
supply. Each message also costs every member two NIP-44 decrypts and two signature verifications
*before* the banlist check runs, and each fresh author inflates B4.

There is no client-side answer; only a Refounding rotates the key out from under them. That is the
correct design, which is why B4 matters so much.

## <a name="b6"></a>B6 — Channel history is deletable on a naive third-party relay

**Status: correct on our relay and pinned; external relays remain exposed.** Needs a CORD-01 spec note
and relay-selection guidance, not code.


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

## <a name="b7"></a>B7 — The base-rekey plane is writable by every member

**Status: accepted.** Bounded work per wrap, no correctness impact.


**Low.** *Read:* `ConcordKeyDerivation.baseRekeyAddress`, `AccountConcordActions.drainConcordRekeys`.

The base-rekey address derives from `community_root`, so any member — banned included — can mint
valid wraps there. Authorization happens after the blobs are scanned, so a flood costs every member
a locator scan per blob on every revision tick. Bounded work per wrap and no correctness impact;
listed for completeness.


---

# Part C — interop and not-yet-shipped

## <a name="c1"></a>C1 — Banlist rank rule diverges from Armada

**Medium, known, deliberate.** See `docs/concord-banlist-rank-conformance.md`, already reported.

We enforce §3's rank half on the Banlist and Armada does not, so the two clients can show different
banlists. Shipped knowingly. Row 3 of that report ("a banned `BAN` holder unbans themselves") was
left open as a fixpoint-ordering question — B2 is the general form of it, and the fix proposed there
resolves both.

## <a name="c2"></a>C2 — Voice rooms are key-gated, not roster-gated

**Design-level; not currently reachable.** *Read:* `ConcordBrokerToken`, CORD-07 §2.

Downgraded from High on review: `ConcordBrokerToken` and `VoicePresence` are referenced nowhere
outside `quartz`, so Amethyst ships no Concord voice path yet. This is a note for whoever wires
one up, not a live hole.

A member proves voice-room membership by signing a NIP-98 kind-27235 request with the channel's
**derived voice signer key**, whose pubkey is the SFU room name. The broker is stateless and holds
no community secret, so it cannot consult the Control Plane and has no idea a banlist exists. A
banned member keeps that key until a Refounding, so they can join the voice room and stay in it.
Nothing on the client side can evict them — kicking them from the UI does not kick them from the SFU.
It would be the one place where a ban fails *audibly*, in real time, in front of everyone, so it is
worth designing the roster check in before shipping rather than after.



---

## <a name="armada"></a>Armada comparison (checked 2026-08-09)

Read against `gitlab.com/soapbox-pub/armada` at `src/concord-v2/`. Worth doing before shipping any of
this, and it changed two conclusions.

**B2 — they already do it, and we had it backwards.** `foldControlState` (`lib/control.ts`) runs the
same bounded two-pass: fold once, take the banlist, and if any edition was authored by someone on it,
re-fold with those editions excluded. Independently arrived at, same shape, same CORD-04 §4
justification in the comment. So this change brings us *into* line with Armada rather than out of it,
and the consensus warning in the earlier revision of this doc was wrong.

One real divergence remains, and it is ours to defend: Armada keeps **pass 1's** banlist as the final
word ("the first pass's Banlist stays the final word"), while we recompute the banlist in pass 2. So
a banned admin's mass-ban of everyone beneath them still stands in Armada and is dropped by us — the
`aBannedAdminBansEveryoneBeneathThemWithoutNeedingAPuppetAtAll` case. Their stated reason is to stop
the anti-roster erasing itself; ours is that an edition from a banned author should not survive its
own author's removal. Both are defensible; ours closes an attack theirs leaves open, and the
self-erasure they worry about is unreachable for us because the rank rule makes mutual bans
impossible (only someone who strictly outranks you can ban you). Worth raising with them.

**B1 — the same bug, unfixed, in exactly the same place.** `bootstrapHead` (`lib/version.ts:155`)
takes the highest version at or above the floor with no bound; `headCandidates` uses it for the
compaction arm; `pickHead` then raises the stored floor to whatever won. That is the whole
version-exhaustion chain. This is now the second bug both implementations share because both read
the same section the same way, and it deserves the same treatment as the rank rule: a written report.

**A1 — ours alone.** Armada gates invite creation on `CREATE_INVITE` in both the hook
(`useInvites2.ts`) and the page (`canCreateInvite`). We were the only client handing a banned member
a working invite button.

**A2 — different architecture, and it is better.** Armada's catch-up is **push**, not pull: a
privileged member sends a stranded member a direct invite carrying the fresher root
(`useDirectInvites2`, `catchUp`), so a human authorizes each re-admission. `useRekeyWatch2` merely
reports `{ stranded: boolean }` for the UI. They also ship `useBanSelfRemove2`: a banned member's own
client silently drops the community from their private list — network-silent, deliberately narrower
than rekey-exclusion, because "a rotation can be a mistake; a ban is a judgment". Our pull-from-my-own-
old-link design is what made the bypass possible, and their per-epoch bundle refresh is what would
have supplied the higher epoch to pull. Two ways forward: adopt a refresh of our own (restores
liveness, keeps the pull design and its risk), or move to their push model (safer, and it is what the
one existing implementation does).

**C1 — still open on their side.** `banlistGate` remains a bare `isAuthorized(roster, author, owner,
BAN)`: no rank check, no delta rule. The divergence from
`docs/concord-banlist-rank-conformance.md` is unchanged.

**A divergence in the other direction.** Armada's banlist takes only the gated head's content —
there is no §4 re-heal union. Ours unions in every authorized non-ancestor edition, which is what
defeats the genesis-fork laundering attempt in `BannedStaffEscalationTest`. So we honor concurrent
bans they drop. Worth a spec question about which is normative.

**A4 — shared gap.** No ban filter on typing there either.

**B4 — not established.** I could not locate a recipient-set bound in their rekey path, but I also
could not locate the recipient-set construction itself with confidence, so treat this as unchecked
rather than as a finding either way.


---

## Performance of the fixes

Measured on the JVM with a synthetic Control Plane (throwaway benchmark, not committed —
`ConcordCommunityState.fold` over 226 and 2059 editions, 200 reps after warmup). Pass A of the
two-pass resolver is byte-for-byte the old algorithm, so the single-pass rows below *are* the
before-numbers.

| Case | µs / fold |
|---|---|
| 226 editions, no bans | 1457 |
| 226 editions, 20 bans, none of them authors | 994 |
| 226 editions, 20 bans, one an author → pass B runs | 1881 |
| 2059 editions, no bans | 2194 |
| 2059 editions, 50 bans, none of them authors | 2001 |
| 2059 editions, 50 bans, one an author → pass B runs | 5697 |
| 2059 editions, with floors (B1's compaction arm) | 2015 |

Two things to take from it.

**B1 costs nothing measurable.** Trying the floor-anchored chain before the raw-version bootstrap
adds a per-entity version index on the compaction arm, but an entity carries a handful of editions,
and the floored fold measures the same as the unfloored one.

**B2 costs a further fold, but only when it can change the answer.** `resolve` skips pass B when
nobody is banned *or* when nobody banned ever authored a Control edition — the overwhelmingly common
shape, since bans land on plain members who hold no role and write nothing. Those rows show no
regression. When a banned member *did* author editions — a banned staffer, exactly the case B2 exists
for — the fold costs ~2–3× more, and one more pass again in the rare case where a banned member had
themselves authored a ban. That is the price of the fix and it is paid only by communities under the
attack.

**Worth knowing, unrelated to this work:** Amethyst re-folds the whole buffer from scratch on every
Control Plane change, and `resolve` runs once per held epoch inside `controlFloorsLocked` plus once
in `fold`, so a refresh is already several folds. Armada memoizes the fold by
`(community, owner, floors, snapshot, edition ids)`; we do not. That is the real optimization here,
it predates these fixes, and it would also absorb the pass-B cost. Left alone deliberately — it is a
change to make on its own merits, with its own measurements.

---

## What was NOT examined

This audit is bounded by what was opened. Checked and found sound: the wrap/seal envelope (no author
impersonation — `rumor.pubKey == seal.pubKey` and `rumor.verifyId()`), Concord chat edits
(`Note.latestConcordEdit` is author-gated, so a member cannot rewrite someone else's message), and
self-unban (B2).

Not looked at at all:

- **Private channels** (CORD-03 derived keys) — key delivery on grant, and channel-scoped rekey.
  Note that no channel-scoped rekey *receive* path appears to exist: `drainConcordRekeys` handles
  `ROOT_SCOPE` only, and `entry.privateChannels` is carried forward but never populated by a
  delivery path. If that is right, the only removal Amethyst can perform is a full-community
  Refounding — which is exactly what B4 makes expensive.
- **In-plane reactions and deletes** — the edit path is author-gated; the delete path was not read.
- **Guestbook kicks** (kind 3309) — the builder documents a KICK-bit + rank rule; the receive side
  was not verified against it.
- Unread counts and notification triggers, media/upload references from messages, the NIP-53 nests
  overlap, and the desktop client's Concord paths.

## What is left

1. **A2's liveness half** — decide whether a community re-mints its invite bundle at a stable
   coordinate. Today nothing does, so stranded recovery never fires for anyone, and an owner evicted
   by a rogue admin has no route back. Needs a spec answer before code.
2. **C1 / B2 interop** — tell Armada about the two-pass rule, as with the rank rule before it. The
   divergence is now wider: we drop editions they honor whenever a privileged member is banned.
3. **B6** — a CORD-01 note that a plane's wraps must stay owned by a key nobody holds, plus guidance
   that a relay authorizing NIP-09/62 by `pubkey` hands every ex-member a wipe button.
4. **B3's residue** — a legitimately privileged rotator can still omit an entity during compaction.
   `EntityFloor` catches it for clients that were present; fresh joiners have nothing.
5. **A5** — a design pass on how "Ban" and "Remove from community" are presented, since they promise
   very different things.
6. **The unexamined surfaces below**, particularly private channels — there appears to be no
   channel-scoped rekey receive path at all, which would mean the full-community Refounding is the
   only removal Amethyst can perform.
