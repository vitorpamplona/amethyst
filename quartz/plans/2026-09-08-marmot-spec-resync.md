# Marmot: resync against the adopted spec and current MDK

Status: Stages 0-7 landed. Convergence is on the inbound path, the superseded `CommitOrdering`
tiebreak is deleted, the app layer publishes current-profile KeyPackages and can create
current-profile groups, and publish-before-apply is enforced. The MDK interop harness has still
never been run against a live MDK build.

Sources checked on 2026-09-08:

- `marmot-protocol/marmot` @ `4a2bc65` ("Specify durability and restart contract", 2026-08-13)
- `marmot-protocol/mdk` @ `ef73de5` (2026-09-08), released tag `v0.9.19` (2026-09-07)
- `marmot-protocol/whitenoise-rs` @ `95fa0b8` (2026-08-05) — **archived/obsolete**

## 1. Executive summary

Our Marmot implementation targets the **MIP-era protocol** (MIP-00…MIP-05), which upstream
deprecated on **2026-07-02** when the "v2" spec was imported and marked adopted. Everything
we ship — `MarmotGroupData` (extension `0xF2EE`), the timestamp+event-id commit tiebreak,
the kind `10051` KeyPackage relay list, the `encoding` tag — is either superseded or now
explicitly forbidden.

The single hardest break: **current MDK rejects our groups outright.** MDK classifies a group
as `Legacy` or `Current` by looking at `RequiredCapabilities`:

- `Legacy` ⇔ requires MLS extension `0xf2f1` (`marmot.account-identity-proof.v1`)
- `Current` ⇔ requires app component `0x8009` (`marmot.member.account-identity-proof.v2`)

We require neither — our groups only require `0xF2EE`. MDK's
`protocol_profile_of_group_extensions` (`crates/cgka-engine/src/account_identity_proof.rs:509`)
returns an error for `(false, false)`:

> "group requires neither legacy proof extension 0xf2f1 nor current proof component 0x8009"

We never implemented the account identity proof at all (`grep -ri f2f1 --include=*.kt` finds
nothing). So we are not even a valid *legacy* Marmot client by the spec's own definition —
`foundation/account-identity-proof-v1.md` says "a member without a valid v1 proof is not a
valid v1 member."

This is not a patch-up job. Reaching the current profile means implementing the MLS
extensions draft (`app_data_dictionary`, `app_components`, `app_data_update`) inside our
from-scratch Kotlin MLS stack, plus a convergence engine and a group lifecycle state machine
we have no equivalent of.

## 2. Verdict on the Bitcoin++ Insider draft

The draft is a **technically accurate** description of the current spec. Every checkable
protocol claim in it holds up against `protocol-core/convergence.md` and MDK's source:

| Draft claim | Verified against |
| --- | --- |
| Five-commit rewind horizon | `max_rewind_commits = 5`; `V1_MAX_REWIND_COMMITS` in `crates/cgka-engine/src/convergence.rs:14` |
| 1s quiescence / 5s absolute pass deadline | `settlement_quiescence_ms = 1000`, `max_convergence_pass_ms = 5000`; `crates/cgka-engine/src/canonicalization.rs:23,25` |
| Witness quorum = 2 distinct senders on ≥1 epoch, boost capped at 1 commit | `witness_quorum_senders_per_epoch = 2`, `witness_quorum_epochs = 1`, `max_witness_override_depth = 1` |
| "3-commit branch with quorum ties a 4-commit branch, then wins the next comparison" | selection step 1 (`effective_commit_depth`) then step 2 (quorum beats no-quorum) |
| Tiebreak chain ends at tip priority → committer → digest of commit bytes | `convergence.md` "Branch selection" steps 4–6 |
| Parentage from MLS replay, never relay metadata | `convergence.md` "Candidate branches" |
| Publish-before-advancing, ack from ≥1 endpoint in recipient scope | `protocol-core/publish-lifecycle.md` "Publish obligation" |
| Earlier implementations broke same-epoch ties on outer Nostr timestamp + event id | exactly our `mip03GroupMessages/CommitOrdering.kt:37-40` |
| Conformance simulator drives the production-shaped Nostr peeler | `crates/cgka-conformance-simulator/` + `crates/transport-nostr-peeler/` both exist |

**One substantive error.** The draft says: *"If effective depth ties, the selector compares
quorum status, raw commit depth, accumulated witness score, …"*. The spec explicitly rules
out a raw-depth step:

> `raw_commit_depth` has no separate comparison step. It is already part of
> `effective_commit_depth`; if effective depth and witness-quorum status are both tied, a
> further raw-depth comparison is necessarily tied as well.

Harmless for the narrative, wrong if someone implements from the article.

**Claims we could not verify here** (no non-GitHub network): the White Noise release
versions, the `fips.network` research status, and the downstream apps (Tubestr, AgentNoise,
Burrow, Botburrow). The draft's own editorial hold flags most of these as TODO anyway.

**What the draft is missing for us** — and this is why it reads as vague: it describes the
convergence *policy* but not the surrounding breaking changes that actually dominate our
workload. It never mentions that `marmot_group_data` was dissolved into `app_data_dictionary`
components, that the account identity proof moved from extension `0xf2f1` to component
`0x8009`, that KeyPackage relay discovery moved from kind `10051` to NIP-65 kind `10002`, or
that group disbanding (`marmot.group.lifecycle.v1`) was added. Those are the real work.

## 3. Upstream changelog since our implementation was written

### 3a. The structural break (2026-07-02)

The MIP documents were deprecated wholesale. `mip-coverage.md` is now only a historical map.
The spec is reorganized into `foundation/`, `protocol-core/`, `app-components/`,
`transports/`, `features/`.

**MIP-01's monolithic `marmot_group_data` (`0xF2EE`) was split into app components** carried
in the MLS `app_data_dictionary` extension (`0x0006`, draft-ietf-mls-extensions-10), mutated
via the `app_data_update` proposal (`0x0008`):

| MIP-era field of `MarmotGroupData` | New owner |
| --- | --- |
| `name`, `description` | `0x8001` `marmot.group.profile.v1` |
| `admin_pubkeys` | `0x8003` `marmot.group.admin-policy.v1` |
| `nostr_group_id`, `relays` | `0x8004` `marmot.transport.nostr.routing.v1` |
| `image_*` | `0x8002` `marmot.group.blossom.image.v1` |
| `disappearing_message_secs` | `0x8005` `marmot.group.message-retention.v1` |

Plus components with no MIP-era equivalent: `0x8007` avatar-url, `0x8008`/`0x800b`
encrypted-media v1/v2, `0x8009` account-identity-proof v2, `0x800c` group-lifecycle,
`0x8006` agent-text-stream, `0x800a` multi-device-join (draft).

### 3b. Spec commits after adoption

- `2026-07-03` Align admin-policy, membership, and role-change invariants
- `2026-07-05` Tighten wire-boundary validation rules (tag cardinality, pre-peel validation)
- `2026-07-23` Resolve the spec issue sweep
- `2026-07-23` Specify push owner proof signing (kind `451`)
- `2026-07-28` **Specify terminal MLS group disbanding** (`marmot.group.lifecycle.v1`, `Disbanded` state)
- `2026-07-30` **Define convergence assurance contract** (`protocol-core/convergence.md` as it stands)
- `2026-08-13` **Specify durability and restart contract** (`protocol-core/durability.md`)

### 3c. MDK

MDK is a different codebase from the `mdk-core` we were byte-matching. It is now a
20+ crate workspace at `v0.9.19`, on a fork of OpenMLS `0.8.1`
(`erskingardner/openmls` @ `59e7d3b`) built with the `extensions-draft` feature.

**`whitenoise-rs` is archived** (obsolescence warning added 2026-08-05, pinned at
`mdk-core 0.8.0`). The `wn` / `wnd` binaries our interop harness drives now live in
`mdk/crates/cli`. Our `cli/tests/marmot/marmot-interop.sh:101` still clones the dead repo.

## 4. Gap analysis

Legend: **BLOCKER** = breaks interop today · **BREAK** = wire-visible divergence ·
**GAP** = required behavior we don't implement.

### 4.1 Identity — BLOCKER

We have no account identity proof of any kind. Both profiles require one.

- v1 (`0xf2f1`): BIP-340 Schnorr over a domain-separated preimage, in LeafNode extensions,
  required in `RequiredCapabilities`. Test vector in `foundation/account-identity-proof-v1.md`.
- v2 (`0x8009`): `MarmotAuthorizationProof` (104 bytes: `signer_pubkey[32] || created_at:u64
  || signature[64]`) in the LeafNode `app_data_dictionary`, signing a synthetic **kind 450**
  NIP-01 event. Test vector in `app-components/account-identity-proof-v2.md`.

Ours: nothing. `KeyPackageRotationManager.kt:528` advertises `0xF2EE, 0x000A` only.

### 4.2 Group state carrier — BLOCKER

`quartz/.../mip01Groups/MarmotGroupData.kt` (TLS struct under extension `0xF2EE`) has no
counterpart in the current profile. Needs: `AppDataDictionary` / `ComponentData` codecs, the
`app_components` (`0x0001`) and `safe_aad` (`0x0002`) upstream components, the
`AppDataUpdate` proposal (`0x0008`), and per-component encode/decode/validate/authorize for
`0x8001`–`0x800c`.

Our `Proposal.kt` has Add/Update/Remove/SelfRemove/GroupContextExtensions/Psk/ReInit/
ExternalInit — no `AppDataUpdate`, no `AppEphemeral`.

Blast radius of `MarmotGroupData`: 22 files across `quartz`, `commons`, `amethyst`, `cli`.

### 4.3 Convergence — GAP (the article's subject)

(As of the gap analysis; `CommitOrdering.kt` has since been deleted and replaced by
`MarmotConvergenceEngine` — see Stage 6.) We had none of it. `CommitOrdering.kt` implemented
the superseded rule (lowest `created_at`, then lowest Nostr event id) and was wired into
`MarmotInboundProcessor` as a per-`(group, epoch)` bucket. The spec forbids using transport
arrival order, transport timestamps, or outer event ids in branch selection at all.

Missing, all of `protocol-core/convergence.md`:

- bounded pass scheduler with `pass_base_epoch` snapshot, 1s quiescence / 5s hard deadline,
  frozen input batch, `Syncing`/`Resolving`/`Settled`/`Blocked` status;
- candidate-graph construction by MLS replay from retained states; deferred-commit handling
  with epoch-based expiry (`canonical_tip_epoch - commit_source_epoch > 5`);
- eligibility on `pass_base_epoch - fork_epoch <= 5`;
- app-payload witnesses counted by distinct Marmot account per branch epoch, capped;
- the six-step branch comparison;
- disposition assignment (`accepted`/`deferred`/`stale`/`invalidated`/`BeyondAnchor`) and the
  withdrawal of app payloads and state notifications from losing branches;
- the fair-scheduling preparation opportunity for a queued admin intent.

### 4.4 Group lifecycle state machine — GAP

`protocol-core/group-state.md` defines six states (`Stable`, `PendingPublish`, `Merging`,
`Recovering`, `Unrecoverable`, `Disbanded`) plus local gates (`Leaving`, `Disbanding`,
realized-removal). We have no explicit state machine; publish-before-apply exists only as an
ad-hoc `awaitCommitAck` in `MarmotWelcomeSender.kt`.

### 4.5 Durability / restart — GAP

`protocol-core/durability.md` + `foundation/conformance.md` §"Crash and restart scenarios"
define nine restart boundaries (prepared-not-published, ack-uncertain, confirmed-not-applied,
observer-atomic apply, frozen-batch abandonment, …). We have `MarmotManagerRestoreTest` and
nothing resembling this contract.

### 4.6 Nostr transport — BREAK

| Rule (`transports/nostr.md`) | Ours |
| --- | --- |
| KeyPackage relays come from **NIP-65 kind 10002 write-capable** entries; "there is no dedicated KeyPackage relay list" | we implement kind **10051** `KeyPackageRelayListEvent` and `MarmotSyncPolicy.Relays.keyPackageRelays()` |
| Sender **MUST NOT** add an `encoding` tag; receiver MUST NOT switch decoders on one | `KeyPackageEvent.build()` emits `encoding` (`tags/EncodingTag.kt`) |
| Kind 30443 tag set is `d`, `mls_protocol_version`, `i`, `mls_ciphersuite`, `mls_extensions`, `mls_proposals`, `app_components`; KeyPackage events do **not** repeat relays | we emit `relays` and no `app_components` |
| `app_components` MUST include `0x8009` | absent |
| `mls_extensions` should carry the profile's extensions (`0x0006` current / `0xf2f1` legacy) | we emit `0xf2ee`, `0x000a` |
| Last-resort is the empty-data `last_resort_key_package` **component `0x0004`** in the KeyPackage-level dictionary, *not* an extension type | we treat `0x000a` as a last-resort extension (`tags/MlsProposalsTag.kt:30`) |
| Dedup id is defined over recovered **MLS message bytes**, never the Nostr event id | `MarmotInboundProcessor` dedups on `processedEventIds` (Nostr ids) |
| Outer decryption tries the bounded retained-candidate key set (canonical epoch, retained epochs in horizon, staged local commit) | single-epoch decrypt |
| KeyPackage `Lifetime` MUST exist, be current, and span ≤ 7,261,200 s | `KEY_PACKAGE_LIFETIME_SECONDS` set but no upper-bound validation on inbound |
| Kind 445 may carry only `h` and NIP-40 `expiration`; commits/proposals MUST NOT carry `expiration` | builder is clean; the commit/proposal exclusion is unverified |

### 4.7 Group image — BREAK

`app-components/group-blossom-image-v1.md` now specifies:

- `image_key` is **the ChaCha20-Poly1305 key**, `image_upload_key` is **the Nostr secret key**
  — ours are HKDF *seeds* (`MarmotGroupData.kt:91,96`);
- AAD is `"marmot-group-image-v1" || 0x00 || canonical_media_type` — ours is empty
  (`MarmotGroupImageCipher.kt:50,58`);
- a `media_type<0..128>` field exists — we deliberately omitted it to avoid trailing bytes for
  old mdk-core.

### 4.8 App payloads — GAP

`foundation/application-messages.md` + `registries.md` assign inner kinds we don't handle:
`9` default chat, **`1009` message edit**, **`1210` group system event**, `1200` agent stream
start. `grep 1210\|1009` over our marmot tree: no hits. Receiver authentication (inner
`pubkey` == MLS-authenticated account) we do have (`MarmotInboundProcessor.kt:438`).

### 4.9 Encrypted media — BREAK

We implement the MIP-04 scheme (`mip04EncryptedMedia/`). Current is
`0x800b marmot.group.encrypted-media.v2` with a policy component
(`media_format = "encrypted-media-v2"`, `allowed_locator_kinds`, `default_blob_endpoints`);
`0x8008` v1 is frozen. Our `Mip04ParseResult.DeprecatedV1` path suggests we're on the v1
lineage.

### 4.10 Push notifications — GAP

Kinds 446–449 exist for us. Missing: the kind `451` push **owner proof** (spec'd 2026-07-23)
and the token-record `relay_hint` publish-target rules.

### 4.11 Test/interop infrastructure — was a BLOCKER, addressed in Stage 0

- ~~`cli/tests/marmot/` clones the archived `whitenoise-rs`~~ — repointed at
  `marmot-protocol/mdk` (`-p wn-cli`).
- ~~`quartz/tools/mdk-vector-gen` pins plain openmls 0.8~~ — repointed at the
  `extensions-draft` fork, and `marmot-profile-gen` now emits current-profile fixtures.
- Still open: our MIP tests (`MarmotMipComplianceTest`, `MarmotMipBehaviorTest`) assert the
  deprecated rules — e.g. `MarmotMipBehaviorTest.kt:793` asserts
  `RequiredCapabilities == [0xF2EE]`. They pin us to the old profile and must be re-pointed,
  not deleted (the legacy bytes still matter for reading our own stored groups).

## 5. Interop verdict today

- **Against current MDK / White Noise: broken.** Our groups fail profile classification
  before any component check. Our KeyPackages carry no `0x8009` data and no `app_components`
  tag, and carry a forbidden `encoding` tag.
- **Against old `mdk-core` 0.8 (archived White Noise): probably still fine**, which is what
  our fixtures prove — and that's now a dead target.
- **Our own groups still work with our own clients.** Nothing here is urgent for
  Amethyst-to-Amethyst Marmot chat; it is urgent for cross-client chat.

## 6. Proposed staging

Each stage is independently shippable and independently testable.

**Stage 0 — re-establish a live reference. DONE.**

- `quartz/tools/mdk-vector-gen` now pins `erskingardner/openmls` at the exact rev MDK's root
  `Cargo.toml` names, with the `extensions-draft` feature. Verified: it builds and runs.
- New generator `marmot-profile-gen` emits
  `quartz/src/commonTest/resources/mls/marmot-current-profile.json` — a real current-profile
  group with `app_data_dictionary` state at every location, PublicMessage handshakes, the Add
  commit, the Welcome, and exporter KATs. It self-checks the account-identity-proof v2
  construction against the spec's published fixture at startup, so the emitted proofs are known
  to match byte-for-byte.
- The interop harness (`cli/tests/marmot/`) now clones `marmot-protocol/mdk` and builds
  `-p wn-cli` instead of the archived `whitenoise-rs`. Both source patches are gone: the
  mock-keyring patch is replaced by MDK's native `--secret-store file`, and the
  skip-unprocessable-retry patch targeted a path MDK does not have. The daemon socket is now
  pinned with `wnd --socket` rather than guessed from a derived default.

**Not yet run end-to-end.** The harness changes are derived from reading MDK's `DaemonArgs` and
`wn-cli` manifest, not from a passing run — building MDK's full workspace needs its pinned
toolchain and a local relay. A human run of `marmot-interop-headless.sh` is the acceptance test,
and is likely to surface at least the retry behaviour the old patch used to paper over.

**Stage 1 — MLS extensions draft in Quartz. DONE.**

- `marmot/mls/components/` — `ComponentData`, `AppDataDictionary` (extension `0x0006`) and the
  `ComponentsList` payload shared by `app_components` (`0x0001`) and `safe_aad` (`0x0002`).
  Building a dictionary sorts for you; *decoding* rejects out-of-order or duplicate entries
  rather than normalizing them, because a receiver that silently sorted would accept two
  encodings of one dictionary and then disagree with a peer about the signed bytes.
- `Proposal.AppDataUpdate` (`0x0008`), including its `update`/`remove` operations, wired
  through `MlsGroup` on both the committing and receiving paths, plus `proposeAppDataUpdate` /
  `proposeAppDataRemoval` / `appDataDictionary()`.
- Last resort is read as the KeyPackage-level `0x0004` component, not an MLS extension type.

Two application rules were taken from openmls rather than guessed, because both change the
resulting GroupContext bytes and therefore the epoch key schedule:

1. `AppDataUpdate` applies AFTER the rest of the proposal list, so a `GroupContextExtensions`
   proposal in the same commit is already reflected — regardless of list order.
2. The dictionary extension is added-or-replaced in place and never dropped, even when the
   last component is removed. An absent extension and an empty dictionary are different
   GroupContexts.

MLS deliberately leaves update-payload semantics to the application (openmls hands the
proposals back unresolved). Every Marmot component defines its update as a full replacement
state, so resolution here is the identity function; a future diff-shaped component would have
to resolve before this layer.

Verified: 12 tests parse the MDK-generated KeyPackage in `marmot-current-profile.json` all the
way down — MLSMessage → KeyPackage → LeafNode → dictionary → components — and re-encode the
dictionary byte-identically (it sits inside the signed LeafNode, so a one-byte difference
would invalidate the signature). 9 more cover the proposal wire format and its group-level
application, including two members converging on the same dictionary across the receive path.
Full `:quartz:jvmTest`: 4,507 tests, 0 failures.

Known gap, deliberately left for Stage 3: `enforceAuthorizedProposalSet` and
`enforceNoAdminDepletion` read MIP-01's `marmot_group_data` (`0xF2EE`). A current-profile group
has no such extension, so both gates return without enforcing anything — an `AppDataUpdate` in
such a group is currently unauthorized by us. The admin-policy component (`0x8003`) is what
closes it.

**Stage 2 — account identity proof v2 (`0x8009`). DONE.**

- `marmot/foundation/authorizationProofs/MarmotAuthorizationProof` — the 104-byte common
  envelope from `foundation/authorization-proofs.md`, with the `created_at` bounds (`1` to
  `2^53-1`, catching a uint64 that reads back negative as a Long) and event-id reconstruction.
  Deliberately no wall-clock comparison: a proof does not expire, because clock skew must not
  make two members disagree about the same Commit.
- `marmot/appComponents/accountIdentityProof/AccountIdentityProofV2` — the kind-450 signing
  template, production through `NostrSigner` (so NIP-46 / NIP-55 signers work), and validation
  returning a typed reason. `create()` re-verifies what the signer returned — pubkey, timestamp,
  kind, tags, content, id and signature — because an external signer is free to substitute.
- `marmot/appComponents/AppComponentIds` — the component registry from
  `foundation/registries.md`, needed by Stages 1 and 3 too.
- `MlsCiphersuite` gained the RFC 9420 §17.1 signature-scheme mapping, which the proof signs
  and validates explicitly.

Verified: 16 tests against the spec's published fixture (canonical event serialization, event
id, signature, 104-byte layout) plus every signed input's binding, and 6 tests validating the
proofs in `marmot-current-profile.json` — proofs produced by a *separate* implementation for
random keys, which a fixed vector cannot establish. Full `:quartz:jvmTest --tests "*marmot*"`:
395 tests, 0 failures.

Not yet wired: nothing reads or writes these components on a real leaf. The carrier is the
`app_data_dictionary`, which is Stage 1. Until then this is a correct, tested primitive with no
call sites — which is exactly what makes Stage 1 mechanical rather than exploratory.

**Stage 3 — split `MarmotGroupData` into components. DONE (codecs + authorization).**

All six component codecs in `marmot/appComponents/`, plus `MarmotGroupState` — the read view
over a GroupContext dictionary that replaces `MarmotGroupData`:

| Component | Notes |
| --- | --- |
| `0x8001` profile | byte equality, no Unicode normalization; limits are in bytes |
| `0x8003` admin-policy | sorted/unique/non-empty 32-byte account keys; unsigned byte order |
| `0x8004` nostr-routing | raw 32-byte id + sorted relay list; relay-URL profile checked, never rewritten |
| `0x8005` message-retention | fixed uint64, no length prefix; `0` = disabled (MIP-01 rejected `0`) |
| `0x800c` lifecycle | one byte |
| `0x8002` blossom-image | five var-byte fields incl. the new `media_type`, plus the domain-separated AAD |

**The Stage 1 authorization gap is closed.** `enforceAuthorizedProposalSet` and
`enforceNoAdminDepletion` now resolve admins through `currentAdminIdentities()`, which reads
`0x8003` when present and falls back to `0xF2EE`. Depletion also resolves an admin-policy
change carried by an `AppDataUpdate`, not just by a `GroupContextExtensions` proposal.

One design decision worth recording: the admin lookup decodes **only** `0x8003`, never the
whole component set. Authorization must not depend on components it does not read — an
initial version that went through `MarmotGroupState` made a malformed profile component
freeze the group, which its own tests caught.

The `0xF2EE` decoder stays as the legacy read path; nothing that reads it was removed.

Verified: 35 new tests — the MDK-generated GroupContext dictionary decoded component by
component and re-encoded byte-identically, per-component validation rules the happy-path
fixture cannot reach, and authorization driven through real groups (a non-admin cannot rewrite
group state; an admin can; adminship transfers; a phantom admin with no member leaf is
rejected; removing the admin policy is rejected). Full `:quartz:jvmTest`: 4,542 tests, 0
failures. `:commons` and `:cli` still compile.

**Image crypto — DONE.** `GroupBlossomImageCrypto` implements the current-profile scheme:
`image_key` IS the AEAD key, `image_upload_key` IS the Blossom-auth secret, and the AAD is
`"marmot-group-image-v1" || 0x00 || canonical_media_type`. `MarmotMediaType` implements the
frozen canonicalization (ASCII case folding only, one alias). The MIP-01 scheme stays in
`MarmotGroupImageEncryption` for groups already on disk, and nothing falls back between them —
the component id is the version.

**Stage 4 — Nostr transport corrections. MOSTLY DONE.**

- `KeyPackageEvent.buildCurrentProfile()` emits the current tag set: `mls_extensions` =
  `0x0006`, `mls_proposals` adds `0x0008`, an `app_components` tag that must name `0x8009`,
  and NO `encoding` or `relays` tags. `KeyPackageUtils.isValid` is profile-aware, told apart by
  the presence of `app_components` rather than a version tag; the MIP-era shape stays valid.
- KeyPackage discovery moved to the NIP-65 write set. `publishRelaysFor` no longer *prefers*
  a kind:10051 list — publishing only where a removed list points would make us invisible to a
  conformant peer, which looks in the NIP-65 set and nowhere else. The 10051 list is unioned
  in, never substituted, so unmigrated peers still find us.
- Dedup is now `SHA-256(mls_message_bytes)`, never the Nostr event id. The old scheme
  collapsed nothing: each transport copy of one MLS message carries a fresh ephemeral pubkey
  and therefore a different event id, and a hostile republisher can mint unlimited ids for one
  message. `OutboundGroupEvent` carries the id so self-echo suppression works by MLS identity.
- KeyPackage `Lifetime` is validated: present, current, and spanning at most 7,261,200 s.
- The embedded account identity proof is validated on inbound current-profile KeyPackages —
  the `app_components` tag is only an advertisement, so the decoded LeafNode decides.

**Still open:** bounded retained-candidate trial decryption for kind:445. The rule ("try the
canonical epoch, retained epochs inside the rollback horizon, and any staged-but-unmerged local
commit — and no more") is defined in terms of the retained-state set that convergence owns, so
it lands with Stage 6 rather than ahead of it.

**Stage 5 — lifecycle state machine. CORE DONE.**

`GroupLifecycleState` (the six canonical states with their legal-transition table),
`ConvergenceStatus` (the four derived statuses with the legal-combination table), and
`LocalOutboundGate` for `Leaving` / `Disbanding` / realized-removal.

Two table entries are load-bearing and tested as such: there is NO `Merging -> Recovering`
edge — a competing branch seen mid-merge is retained, the merge finishes to `Stable`, and the
bounded pass then triggers `Stable -> Recovering`, because diverting mid-merge would leave a
half-applied epoch — and `Disbanded` has no outgoing edge at all.

**Still open:** the publish-obligation record (bytes + recipient scope + prior state + pending
state) surviving restart, and wiring these states into `MlsGroup`/`MarmotManager` so they
actually gate anything. Today they are a correct model with no enforcement behind them.

**Stage 6 — convergence engine. SELECTION DONE.**

`ConvergencePolicy` (the v1 constants, with the `max_witness_override_depth <=
max_rewind_commits` bound enforced in the constructor), `CandidateBranch` (fork/tip epochs,
raw depth, tip priority/committer/digest, per-epoch witnesses), `BranchSelector` (the six-step
comparison), and the `ConvergenceDisposition` / `ConvergenceCategory` vocabularies.

Things worth knowing about the implementation:

- Byte ordering is UNSIGNED. Account keys and digests are uniformly distributed, so a signed
  comparison would invert about half of all final ties — and two clients would then disagree
  that often.
- `raw_commit_depth` has no comparison step of its own. It is already inside
  `effective_commit_depth`. The widely circulated write-up of this algorithm lists it as a
  step; the spec explicitly does not, and there is a test that fails if it is added.
- Witnesses count DISTINCT sender accounts per branch epoch, capped at the quorum size, and
  epochs at or before `fork_epoch` do not count at all.

`ConvergencePass` implements the bounded window: `pass_base_epoch` snapshot, the quiescence
and absolute deadlines, the frozen batch, and the forced `Stable -> Recovering` transitions for
a fork or an admitted disband candidate. Driven by an injected monotonic clock so the timing
rules are tested deterministically rather than flakily.

Two asymmetries there are deliberate and tested: only SELECTION-RELEVANT input restarts
quiescence (ordinary chat must not hold a pass open, because outbound work is gated on
settling), and neither a fork nor a disband restarts anything — a pass that becomes a recovery
is the same pass, and restarting would let a trickle of forks hold it open forever. Deferred
commit expiry tracks the LIVE canonical tip while branch eligibility uses the FROZEN
`pass_base_epoch`; using one epoch for both would let an open pass move its own horizon.

`CandidateGraphBuilder` builds the branches the selector compares, over a
`CandidateStateEngine<S>` so the graph algebra is testable without MLS and the MLS adapter
(`MlsCandidateStateEngine`) is testable on real forks.

The things that make it non-obvious, all of them tested:

- **Parentage is derived, never declared.** A commit carries no parent pointer, and it must not
  be believed if it did. The builder finds a parent by asking which retained state the commit's
  membership tag authenticates against. `MlsCandidateGraphTest` builds a genuine same-epoch fork
  (two Alices restored from one snapshot, each adding a different member) and both commits land
  on the same retained parent.
- **A state id is `SHA-256` over the serialized GroupContext, not the epoch number.** Two states
  can share an epoch number and be different states — that is what a fork IS — and the
  GroupContext covers the tree and transcript hashes, so it separates them.
- **It is a fixed point, not a sweep.** Replaying a commit produces a state that may be the
  parent of a commit nothing could place a moment earlier, so it keeps sweeping the unplaced set
  until a pass produces no new edge. A two-commit chain offered child-first still rebuilds as one
  branch of depth 2.
- **"I cannot place this" is not "I caught you misbehaving."** An unauthorized commit whose
  parent IS known is terminal `authorization_failed`; a commit nothing authenticates is
  `deferred`, and only `stale` once the LIVE canonical tip has passed the rollback horizon. A
  tampered commit therefore comes out `deferred`, never `authorization_failed`.
- **An invalid resulting state produces no edge at all**, so convergence cannot select it.
  Validation is "decode the component set" — every decoder is strict, so malformed or unsorted
  bytes throw rather than yielding a lenient value.
- **An unattributable tip is dropped, not zero-filled.** Comparison step 5 breaks ties on the tip
  committer's account pubkey; substituting `ByteArray(32)` would hand such a tip the lowest
  possible key and win it a tie-break it never earned. A group whose leaves are not account
  pubkeys simply produces no selectable branch.
- Every trial replay restores a FRESH group from the retained snapshot, because a candidate
  parent gets tried by several competing commits and "advance it then roll it back" works until
  an exception escapes halfway through.

`MlsGroup` grew three non-mutating helpers for this — `resolveCommitProposals`,
`isCommitAuthorized`, `isSelfOnlyCommit` — reusing the same `enforceAuthorizedProposalSet` /
`enforceNoAdminDepletion` gates the local commit path runs, so an inbound commit and one we
authored are held to one rule rather than two that drift.

`MarmotConvergenceEngine` puts all of it on the inbound path and `mip03GroupMessages/
CommitOrdering.kt` is **deleted** — the superseded rule (lowest outer `created_at`, then lowest
Nostr event id) no longer exists anywhere in the tree.

The wiring decision worth recording is that convergence does NOT hold every commit for the
quiescence window. A literal reading of the bounded pass would tax the overwhelmingly common
single-commit case with a second of latency for nothing. It does not have to, because MLS is
its own fork detector: once a commit is applied, a competitor authored against the same parent
stops authenticating against the new tip but still authenticates against the RETAINED parent.
So linear commits apply eagerly, the state each was applied to is retained, and a commit that
authenticates a retained state rather than the tip IS the fork — only then does a pass open.

That is not an optimization that changes the answer, and the reason is the base choice at
resolution time. The base is the newest retained state a divergent commit authenticates
against, NOT the current tip; the canonical commits applied at or after it are replayed back
into the graph, so the incumbent is rebuilt as a branch and scored by the same six-step rule as
its challengers instead of winning by being already applied. Eager application only decides
which branch is provisionally displayed while a pass runs.
`MarmotConvergenceWiringTest.twoObserversConvergeRegardlessOfArrivalOrder` builds a real
same-epoch fork, feeds two observers the same two commits in opposite orders, and asserts they
end on the same GroupContext with exactly one of them having rewound.

Supporting pieces: `MlsGroupManager.snapshot` (state without touching storage) and
`installState` (the rewind primitive — it pushes the outgoing epoch's secrets into the
retention window first, so traffic already sent on the abandoned branch still decrypts).
`MarmotManager` records locally-authored commits too, because our own commit is half of any
fork we are party to; without it a peer's competitor would look like an unplaceable orphan and
be deferred rather than compared.

**Still open:** app-payload witnesses are plumbed (`recordWitness`) but nothing feeds them yet
— that needs the bounded retained-candidate trial decryption still outstanding from Stage 4.
A group that goes quiet mid-pass has no inbound traffic to tick it, so the app layer must drive
`settleDueConvergence()` from a timer while `openConvergencePasses()` is non-empty; that timer
is not wired in `commons`/`amethyst` yet.

**Stage 7 — app payloads, encrypted media v2, push owner proof. DONE.**

The app-payload work turned up a live conformance bug rather than a gap: we were sending inner
events WITH a Nostr signature, and a conformant decoder rejects a payload carrying a `sig`
member at all. Every message we sent was refusable by any spec-following peer. `MarmotAppEvent`
is the canonical unsigned shape; the id is unchanged by the switch (NIP-01 never hashed the
signature), so existing history still lines up, and the Android pipeline already treated inner
events as unsigned rumors, so the empty `sig` is re-added at the inbound boundary instead of
travelling on the wire.

Duplicate-key detection needed its own scanner. Every JSON library here resolves duplicates
before the caller sees them, and "last one wins" versus "first one wins" are both defensible —
which is the problem, because identical bytes would then yield different ids on two clients.

Kinds `1009` (edits) and `1210` (system rows) are implemented, the latter synthesized from
canonical state rather than received, which is what makes it unforgeable by one member.
Encrypted-media v2 (`0x800b`) and the kind-`451` push owner proof are implemented and verified
against the fixtures the spec publishes — the 1210 example's event id and the push removal
vector's `owner_sig` both reproduce exactly, which is what separates correct from
self-consistent.

**Stage 7 leftover:** durability/restart conformance beyond the publish obligation (which IS
durable) — specifically re-emission and reconstruction of application effects after restart.

### Settled: Quartz keeps its own MLS

Decided 2026-09-08. We do not bind `marmot-c` / `marmot-uniffi`; the pure-Kotlin stack stays,
and full MDK interoperability is the target.

Consequences to plan around, since they are now ours to carry:

- Stage 1 means implementing draft-ietf-mls-extensions-10's `app_data_dictionary` (`0x0006`),
  `app_components` (`0x0001`), `safe_aad` (`0x0002`) and the `app_data_update` proposal
  (`0x0008`) in `quartz/.../marmot/mls/`, against a draft upstream tracks through a fork of
  OpenMLS rather than a released crate.
- The OpenMLS rev pinned in `mdk-vector-gen/Cargo.toml` is a version we now track deliberately.
  When MDK bumps it, regenerate the vectors in the same change and diff them — a silent bump is
  how we would drift again.
- Byte-level conformance is the only thing that keeps us honest, so every stage below lands with
  vectors from `marmot-profile-gen`, not just unit tests written against our own reading.

## Producing current-profile groups

`CurrentProfileGroupFactory` builds current-profile leaves, KeyPackages and groups. The order
it enforces is not stylistic: a leaf must carry an identity proof over its OWN signature key,
and only an account signer — possibly a remote bunker — can produce that proof, so the keypair
is generated first, authorized, and only then built into a leaf. A proof cannot be added
afterwards by code that only sees a finished leaf. `MlsGroup.create` / `createKeyPackage` gained
leaf-extension, capability and required-capability parameters to make that possible.

Writing the producer side immediately found two bugs the reader-side tests could not:

1. `buildLeafNode` accepted leaf extensions and then wrote `extensions = emptyList()`. Every
   leaf we built would have silently dropped its identity proof.
2. Fresh KeyPackages carried `Lifetime(0, Long.MAX_VALUE)`. That fails the bound Stage 4 had
   just started enforcing, so every KeyPackage we published would have been rejected by any
   conformant peer — including, once Stage 4 landed, by us. Now `now - 1h` to `+84 days`.

## Interop status (2026-09-09)

The MDK 0.9.20 harness runs end to end and is **green: 17 of 17**, twice in a
row from a clean state. It started this pass at **1 passed / 12 failed**. The
defects it found are below — every one of them a place where two
implementations have to agree on something one implementation alone never
disagrees with, which is why none of them was visible to any same-implementation
test we have.

### Defects the harness found in our own code

1. **A Commit rebuilt our own leaf from defaults.** The UpdatePath leaf replaces
   OUR leaf — same member, new key material — so its capabilities and extensions
   must carry over. `buildLeafNode` was called with neither, so the very first
   invite we ever sent dropped the `account-identity-proof` (a LEAF extension no
   proposal can restore) and stopped advertising the extension and proposal the
   group's own `required_capabilities` demanded. MDK reported
   `PublicGroupError(LeafNodeValidation(UnsupportedExtensions))` and dropped the
   Welcome minted by that same commit; the invitee simply never saw an invite,
   with nothing logged on either side.

2. **We held private keys for our leaf only, not our direct path.** RFC 9420
   §7.6 lets a committer address us at any node in the copath resolution whose
   key we hold, and a merged subtree resolves to its PARENT — so from three
   members on, the ciphertext meant for us stops naming our leaf. Worked for
   two members, failed for three, which is exactly why it survived every
   two-party test. `MlsGroupState` v3 persists them.

3. **Three readers only understood the legacy `0xF2EE` extension**, so they did
   nothing at all on a current-profile group: the Welcome's `nostr_group_id`
   (without which a joiner cannot even subscribe), the admin gate on
   GroupContextExtensions changes (which skipped rather than failed closed), and
   disappearing-message expiration. Group metadata reads and writes now go
   through `MarmotManager.groupView` / `setGroupProfile` / `setGroupAdmins` /
   `setGroupImage`, which dispatch on the profile the group actually uses.

4. **A non-confirmed publish discarded its obligation**, letting a REPLACEMENT
   commit be prepared for the same epoch. "No OK arrived" is not "no peer took
   it": a timeout or a dropped connection leaves it unknown, and minting a
   second commit for an epoch a peer may already hold is the fork the gate
   exists to prevent. `PublishOutcome.UNKNOWN` keeps the record and holds the
   group.

5. **We treated a last-resort KeyPackage as single-use.** We publish every
   KeyPackage marked last resort and then dropped its private keys the moment
   one Welcome consumed it. OpenMLS deletes a consumed bundle only
   `if !key_package.last_resort()`; MDK marks all of its own last resort, caches
   the peer KeyPackage it resolved, and invites from that cached copy every time
   after. So the first invite addressed to us worked and every one after it died
   on "No matching KeyPackageBundle". Two things had to be fixed together: the
   last-resort check had to read the current profile's carrier (a
   `last_resort_key_package` component inside the KeyPackage-level
   `app_data_dictionary`, not the MIP-era `0x000A` extension type), and the
   Welcome lookup had to trust the MLS KeyPackageRefs over the Nostr `e` tag,
   which MDK stamps from its stale cached copy.

6. **Every rotation downgraded us off the current profile.** MIP-00 replaces a
   KeyPackage as soon as a Welcome consumes it, so rotation runs right after the
   first group we are ever invited to — and it minted the replacement through
   the legacy generator. From that moment the only KeyPackage on relays for us
   had no account identity proof, MDK refused it outright, and the account was
   silently uninvitable one join after setup. Rotation and first publication now
   share one mint path.

7. **The harness was parsing a wire format `wn` no longer speaks.** MDK 0.9.x
   returns `{"ok":true,"result":{"invites":[…]}}`; iterating `.result` walked
   that object's three VALUES, so every poll matched nothing and reported "never
   received invite" for welcomes that had arrived and been accepted.

8. **A hang-up ended the publish wait.** A relay that dropped the socket
   between our EVENT frame and its OK gave no verdict — the event may be stored,
   it may not — and we recorded that as the relay's answer and stopped waiting.
   The pool's own outbox would have re-sent on reconnect; nobody was listening
   by then. `publishAndCollectResults` now keeps a transport failure provisional
   for one retry, dials past the backoff, and takes the OK when it arrives; it
   stays inside the caller's existing timeout, so nothing waits longer than
   before. This was one lost message per few harness runs, and on mobile it is
   every publish that races a network change.

### Harness defects (not ours)

- **Runs inherited each other's state.** wnd wipes B's and C's data dirs on
  start, but A's amy home and the relay's SQLite file survived, and the
  leftovers are not inert — a KeyPackage from an earlier run is still on the
  relay to be invited with, and old kind:445 events still arrive undecryptable.
  Tests 03 and 08 failed on a dirty tree and passed on a clean one. Every run
  now starts from empty stores; `--reuse-state` opts out and `--tests "…"` runs
  a subset.
- **Test 16 asked `wn keys publish` to rotate.** That verb is the idempotent
  retry of the durable stable-slot replacement, so with nothing pending it
  republishes the same event id and there is no rotation to observe.
  `wn keys rotate` is the one that mints.
- **Test 09 polled the wrong surface.** `wn messages list` reads the raw
  app-event log, where a reaction is its own kind:7 entry with an `e` tag naming
  the anchor; `reactions.by_emoji` is the materialized timeline's aggregate,
  which that command does not project. The reaction had been arriving and being
  stored correctly the whole time.

### What is NOT done

- **Agent-text-stream publishes records but has nowhere to send them.** We
  decode the `0x8006` policy, derive per-stream record keys, open records and
  fold the transcript, we advertise the `0xF2D1` receive capability, and
  `AgentTextStreamPublisher` now seals records under the sequence discipline the
  spec demands: values reserved durably ahead of use (in windows, so the hot
  path is not a write per record), never restarted or replayed across a crash,
  and a publisher that cannot prove which value is next refuses to publish at
  all rather than colliding a ChaCha20-Poly1305 (key, nonce) pair. Only an
  in-memory `AgentTextStreamSequenceStore` exists; a platform-backed one lands
  with the transport that needs it.

  We still do NOT advertise `send` (`0xF2D2`) or `fanout` (`0xF2D4`). Not for
  want of a transport any more — see below — but because nothing in the app yet
  originates a stream, and a role we do not serve is worse for the group than a
  role we do not claim. A group whose policy requires `send` is refused at join
  rather than joined into a state every peer would reject us from.

- **The QUIC transport binding is implemented and verified against MDK's
  broker.** `transports/quic.md` is a RAW QUIC binding — its own ALPNs
  (`marmot.quic_broker.v1` / `marmot.quic_stream.v1`), frames written straight
  onto QUIC streams — so it does not go through `nestsClient`'s
  `WebTransportSession`, which begins above HTTP/3 Extended CONNECT. It sits
  directly on `:quic`, which already had everything under that line: the
  connection, TLS 1.3, ALPN negotiation, stream multiplexing, the UDP socket.

  The pure protocol layer (control envelope, `uint32` frame codec with both
  caps, `quic://` candidate parsing, stream-id pinning) is in `quartz`; the
  connection layer is the new `:marmotQuic` module, which mirrors how
  `:nestsClient` sits on `:quic`. `MarmotQuicBrokerInteropTest` drives our
  publisher and subscriber through MDK's own `marmot-quic-broker` and checks
  that the records come back, open under the group-derived key, and fold to the
  publisher's transcript hash — plus that the broker keeps rooms apart. Opt in
  with `-DmarmotQuicBroker=host:port`; it skips visibly without one.

  What is left is the application wiring: nothing yet mints a kind-1200 start,
  picks a broker candidate, or renders a live preview. The direct path
  (`marmot.quic_stream.v1`) is also unimplemented — v1 has no start-payload
  candidate format for it, so it is only usable with an out-of-band endpoint.

