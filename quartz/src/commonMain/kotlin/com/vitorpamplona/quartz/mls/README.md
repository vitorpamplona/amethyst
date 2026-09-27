# `mls/` — the RFC 9420 engine

A binding-agnostic MLS (RFC 9420) implementation: TLS presentation-language
codec, HPKE, X25519/Ed25519, the ratchet tree, the key schedule, the secret
tree, message framing, proposals, commits, Welcome, and the `app_data_dictionary`
component carrier from `draft-ietf-mls-extensions-10`.

**It knows nothing about Marmot, Nostr, or cordn, and must stay that way.** The
invariant, over the shipped code:

```bash
grep -rn 'import com.vitorpamplona.quartz.marmot' \
  --include='*.kt' quartz/src/{commonMain,jvmAndroid,appleMain,linuxMain}/kotlin/com/vitorpamplona/quartz/mls/
```

That must print nothing. Tests are deliberately outside it: a couple under
`mls/components/` decode real Marmot components as fixtures, because the point
of an interop test is to exercise the engine against payloads that actually
exist. A fixture is data; an import in the engine is a dependency.

It lived under `marmot/` until Stage 1 of
`quartz/plans/2026-09-17-cordn-interop.md`, and `:quic` was already importing
`crypto/X25519` from there for its TLS 1.3 handshake — a use that is neither
Marmot nor MLS, and the reason the extraction was overdue.

## Using it

```kotlin
val group = MlsGroup.create(identity)          // a plain RFC 9420 group
val group = MlsGroup.create(identity, policy = MarmotGroupPolicy)  // Marmot's profile
```

The default group requires nothing beyond RFC 9420: no `required_capabilities`,
no leaf capabilities (§7.2 forbids advertising DEFAULT types), no commit
exporter, and no limit on who may commit what.

## The `MlsGroupPolicy` seam

RFC 9420 says who may *send* a proposal and never who may *commit* one. Real
deployments need more — Marmot's MIP-03 names admin accounts and allows everyone
else only a self-Update or a SelfRemove — so the engine asks a policy wherever
the spec defers to the application:

| Hook | Called from | Marmot uses it for |
| ---- | ----------- | ------------------ |
| `authorizeCommit` | `commit()` and both inbound commit paths | MIP-03 proposal-set rules + admin depletion |
| `authorizeSelfRemove` | `proposeSelfRemove`, `buildSelfRemoveProposalMessage` | admin must self-demote first |
| `validateJoin` | `processWelcome` | `required_member_roles` on the agent-text-stream component |
| `defaultLeafCapabilities` | every leaf the engine builds | `0xF2EE` + `self_remove` |
| `defaultRequiredCapabilities` | `create()`, epoch 0 | the MIP-era interop set |
| `knownExtensionTypes` | GroupContextExtensions validation | `0xF2EE` |
| `commitExporter` | `CommitResult.preCommitExporterSecret` | `MLS-Exporter("marmot", "group-event", 32)` |

Three things to know before you add a binding:

1. **One argument selects a whole profile.** `policy` supplies the rules *and*
   the capability defaults *and* the exporter binding, so `capabilities` and
   `requiredCapabilities` default from it. Adopting a profile is one argument,
   not five.

2. **A policy is behaviour, not state.** It is deliberately absent from
   `MlsGroupState`, so whatever restores a group must pass the same policy it
   was created with. A restore that forgets it gets a group that silently skips
   the binding's rules. In Marmot only `MlsGroupManager` restores a group in
   order to commit with it, which is what keeps that narrow.

3. **A policy gets a `GroupView`, not the `MlsGroup`.** A policy holding the
   group could commit or rotate keys from inside the check meant to gate exactly
   that.

The default is permissive rather than closed. Closed would make the engine
unusable without a policy and would push callers into writing an
allow-everything one anyway; open puts each restriction in the binding that
documents it. The cost is item 2 above.

## Where the bindings live

- `quartz/…/marmot/groups/` — `MarmotGroupPolicy`, `MarmotCapabilities`,
  `MarmotGroupViews` (Marmot's reads of a GroupContext, as extension functions),
  plus `MlsGroupManager` and the two stores, all keyed on `nostrGroupId`.
- `quartz/…/marmot/mipXX…/` — the Nostr event layer.

## Tests

`quartz/src/commonTest/…/mls/` runs everywhere; `quartz/src/jvmAndroidTest/…/mls/`
holds the tests needing real secp256k1/JNI. `mls/interop/` replays the RFC 9420
test vectors. `group/MlsGroupPolicySeamTest` pins the seam itself with a
recording policy; `marmot/groups/MarmotPolicySeamTest` pins that Marmot's rules
travel with `MarmotGroupPolicy` and not with the engine.
