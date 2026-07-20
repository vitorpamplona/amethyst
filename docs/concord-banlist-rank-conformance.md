# Concord: the Banlist is not rank-gated in any implementation (CORD-04 conformance)

**Status:** conformance bug. Reproduced in Amethyst and **fixed there** (see §6); present by
inspection in Armada.
**Severity:** privilege escalation. Any `BAN` holder can neutralise every authority above them,
including the owner.
**Reported by:** Amethyst (MIT), 2026-07-20. Findings verified by unit test; see "Evidence" below.

---

## 1. Summary

CORD-04 §3 requires that, for **every** action, the actor both hold the required permission bit
**and** strictly outrank its target. It names banning as the worked example. But CORD-04 §4 — the
section that actually defines the Banlist — states only the bit half of the rule:

> The Banlist is the one *anti*-roster: a signed list of npubs, honored only if its signer holds `BAN`.

Both known implementations implement §4 as written and omit §3's rank half at the Banlist write
path. The result is that **a low-ranked moderator can ban the admins above them, and the owner.**

This is not a spec gap. §3 is unambiguous and normative. It is a conformance bug that both clients
share, and it is almost certainly caused by §4 being readable in isolation.

## 2. What the spec requires

CORD-04 §3 (emphasis in original):

> `position` orders authority, **lower is higher**: the owner is position 0 (never a Role), a
> roleless member is effectively last, and a member's rank is the lowest position among their Roles.
> One hard rule binds every action: the actor must hold the required bit **and** *strictly* outrank
> its target — equal cannot act on equal (an admin cannot ban a peer admin) — and no edition may
> claim a position at or above its own signer, so nobody can promote themselves toward the top.

Two things stand out: the rule binds **every action**, and the parenthetical example it chooses is
*exactly a ban* — "an admin cannot ban a peer admin."

Restated as step 3 of the normative authorisation algorithm, CORD-04 §5:

> To honor an action, a reader:
> 1. Verifies the seal, learning the actor's real npub.
> 2. Folds the Roster and resolves that npub's effective permissions and position.
> 3. Confirms the actor holds the action's required bit **and** strictly outranks its target, traced
>    to the owner.

And on the owner, CORD-04 §2:

> The Roster is **owner-rooted**: … the chain terminates at the **owner**, who is proven by the
> `community_id` itself (CORD-02), occupies position 0, and is **supreme and unremovable**.

The Grant path states the same rule inline, which is presumably why it got implemented there:

> A **Grant** maps a member's npub to their Roles, honored only if its signer outranks every Role it
> hands out (§3).

CORD-02 §5 likewise restates it for Kicks: "a Kick honored only if its signer holds `KICK` and
outranks the target, CORD-04".

**§4 is the only permissioned entity whose section does not restate the rank half.** That asymmetry
looks like the proximate cause of the bug in both codebases.

## 3. What the implementations do

### Armada (`src/concord-v2/lib/control.ts`)

```ts
const banlistGate = (p: ParsedEdition): boolean => {
  if (!isAuthorized(roster, p.author, ownerHex, Permissions.BAN)) return false;
  if (!citationOk(p)) return false;
  try {
    return Array.isArray(JSON.parse(p.content));
  } catch {
    return false;
  }
};
```

with (`src/concord-v2/lib/roles.ts`):

```ts
export function isAuthorized(
  roles: CommunityRoles, actorHex: string, ownerHex: string | undefined, permission: bigint,
): boolean {
  if (ownerHex === actorHex) return true;
  return hasPermission(roles, actorHex, permission);
}
```

`isAuthorized` is a pure bit lookup. Notably Armada **already has** the correct primitive and uses
it on the role path:

```ts
export function canActOnPosition(
  roles: CommunityRoles, actorHex: string, ownerHex: string | undefined,
  targetPosition: number, permission: bigint,
): boolean {
  if (ownerHex === actorHex) return true;
  return hasPermission(roles, actorHex, permission) &&
    outranks(roles, actorHex, ownerHex, targetPosition);
}
```

So the fix is largely a matter of routing the Banlist gate through the primitive that already
exists — though see §5, because a whole-list entity needs a per-entry rule.

### Amethyst (`quartz/…/concord/cord04Roles/AuthorityResolver.kt`)

```kotlin
fun banGate(e: ControlEdition): Boolean =
    e.author.lowercase() == ownerLower ||
        effectivePermissionsOf(e.author.lowercase()).has(ConcordPermissions.BAN)
```

and the parallel gate in `ConcordCommunityState.authorizedHeads`:

```kotlin
EditionFold.foldGated(list, floors) {
    authority.isOwner(it.author) || (bit != null && authority.hasPermission(it.author, bit))
}
```

Same shape: bit only, no rank. Amethyst also has a correct `canActOn(actor, target, bit)` that
implements §3 faithfully, and uses it for Grants — and, since this report, for deciding what the UI
will *author*. Neither client rank-checks the *contents* of a Banlist edition.

## 4. Consequences

All of the following were reproduced against Amethyst's fold with unit tests. Community: owner,
`alice` = Admin at position 1 (holds `BAN`), `bob` = Mod at position 5 (holds `BAN`).

| # | Scenario | Result | Spec violated |
|---|---|---|---|
| 1 | `bob` (pos 5) bans `alice` (pos 1) | **ban is honored** | §3 "strictly outrank its target" |
| 2 | `bob` bans the **owner** | **ban is honored** | §2 "supreme and unremovable" |
| 3 | A banned member holding `BAN` unbans themselves | **honored** | §4 "drops every event from a banned npub … or authority action" |
| 4 | A forked ban survives an unban that does not chain onto it | **stays banned** | — (interacts with §4 re-heal; see below) |

**Why #1 is escalation, not a cosmetic ordering issue.** Once banned, a member loses *all*
authority: effective-permission checks are `!isBanned && …` in both clients, and §4 mandates that
honest clients drop every event from a banned npub. So a single edition from the most junior `BAN`
holder permanently silences every admin above them. In a community whose owner is inactive — the
common case CORD-06 Refounding exists to address — there is no one left who can undo it.

**#2 is the sharpest violation.** The spec goes out of its way to protect position 0, including the
clause "no Role may ever claim position 0, or an owner could create a peer nobody outranks." A
Banlist entry bypasses all of that, because the Banlist is just a list of keys and nothing checks
what is in it.

**#3 cuts the other way and is worth deciding deliberately.** Because the gate reads role-derived
permissions rather than post-ban ones, a banned moderator can lift their own ban. So bans do not
stick against anyone holding `BAN`. Note this one is a genuine fixpoint-ordering question, not a
plain oversight: you cannot know who is banned until you fold the Banlist, and you cannot decide who
may fold into the Banlist without knowing who is banned. The spec should say which order wins.

**#4 is a pre-existing interaction, not necessarily a bug.** §4's convergence design ("re-heal …
guarantees convergence to the union") means a ban that forks off the head is unioned in and is not
cleared by an unban that chains elsewhere. That is correct for *legitimate* concurrent bans. It
becomes a weapon once #1 holds, because a rogue's ban is both unauthorised and sticky.

## 5. Proposed fix

The difficulty is that the Banlist is a single whole-list document ("replaced entire on every
edit"), while §3's rule is stated per *target*. A gate can only be applied to a whole edition, so
the rule needs a per-entry formulation. We suggest specifying it as a **delta rule**:

> An edition of the Banlist is evaluated against the state its `prev` denotes. Let *delta* be the
> symmetric difference between the edition's list and that state's list — the npubs it adds and the
> npubs it removes. For each npub in *delta*, the signer MUST hold `BAN` and strictly outrank that
> npub, evaluated against the Roster as of that edition. The owner MUST NOT appear in the Banlist;
> an edition adding them is invalid. Entries in *delta* the signer may not act on are **ignored**;
> the remainder of the edition applies.

Rationale for each clause:

- **Delta, not whole list.** Otherwise every edition would have to re-justify every standing ban,
  and a moderator could never edit a list containing someone senior — including to add a peon.
- **Removals are gated too.** Unbanning is an authority action on the target; without this, the
  rank rule is trivially bypassed (ban is symmetric with unban here).
- **Ignore, don't reject.** Rejecting the whole edition would make one bad entry discard a
  legitimate bulk-ban — which §4 explicitly recommends as the collision remedy — and would let a
  rogue grief the list by forcing rejections. Ignoring converges and composes with re-heal.
- **"As of that edition."** Ranks change. The spec should state that a Banlist edition's authority
  is judged against the Roster the fold has settled behind it, which is what §5's "traced to the
  owner" already implies for the Roster and what both clients already do for Grants.
- **Owner excluded explicitly.** Today unbannability is only *derivable* from position 0 plus
  strict outranking. Since the Banlist bypasses rank entirely, an explicit prohibition is worth one
  sentence.

We'd also suggest **§4 restating the rank half inline**, the way §2 does for Grants and CORD-02 §5
does for Kicks. Both independent implementations read §4 in isolation and both got it wrong the same
way; that is strong evidence the section is the problem, not the readers.

Separately, please rule on **#3**: whether a banned npub's Banlist edition is honored. Our reading of
§4 ("drops every event from a banned npub — message, reaction, edit, or authority action") is that it
must not be, but the fixpoint ordering needs to be stated for that to be implementable consistently.

## 6. Rollout status

**Amethyst enforces the rule described in §5 as of this report** — both on the fold
(`AuthorityResolver`) and on what it will author (UI + the ban/unban write path, which now reads the
*honored* banlist so an unauthorized entry can never be laundered into a list we sign).

We're flagging the consequence plainly: this is consensus-affecting. Until Armada ships the same
rule, the two clients can show different banlists — Amethyst will ignore a ban that Armada honors
whenever the signer did not outrank the target. We judged shipping the spec-conformant behaviour
better than continuing to honor an escalation, but we recognise that is a decision with a cost for
your users as well as ours, and we're happy to discuss timing, or to adjust if you read §3/§5
differently than we do.

If it is useful, our implementation is MIT and the delta rule is about 40 lines in
`AuthorityResolver.resolve`; you're welcome to lift the approach outright.

## 7. Evidence

Reproductions live in Amethyst's `AuthorityResolverTest`
(`quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/concord/cord04Roles/`). Each failed before
the change and passes after it:

- `aBanHolderCannotBanAMemberItDoesNotOutrank`
- `aBanHolderCannotBanTheOwner`
- `anUnrankedBanIsDroppedWithoutOrphaningTheRestOfTheList` (pins the "ignore, don't reject"
  semantics of §5)

Two companions pin the behaviour the fix had to preserve, and passed throughout:

- `aBanHolderStillBansThoseItOutranks`
- `theOwnerBansAnyone`

Happy to port these to Armada's suite or restate them as spec examples if that is useful.
