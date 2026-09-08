# Marmot: resync against the adopted spec and current MDK

Status: Stage 0 done. Stages 1-7 open.

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

We have none of it. `CommitOrdering.kt` implements the superseded rule (lowest `created_at`,
then lowest Nostr event id) and is wired into `MarmotInboundProcessor.kt:523` as a
per-`(group, epoch)` bucket. The spec now forbids using transport arrival order, transport
timestamps, or outer event ids in branch selection at all.

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

**Stage 1 — MLS extensions draft in Quartz (large, foundational).**
`AppDataDictionary` / `ComponentData` TLS codecs; `app_components` (`0x0001`) and `safe_aad`
(`0x0002`); `AppDataUpdate` proposal (`0x0008`) through `MlsGroup` staging/validation;
last-resort as KeyPackage component `0x0004`. Everything else depends on this.

**Stage 2 — account identity proof v2 (`0x8009`).**
`MarmotAuthorizationProof` codec, kind-450 signing template, BIP-340 verify, LeafNode/
KeyPackage validation, capability advertisement. Ships with the spec's published test vector,
so it can be built and verified before Stage 1 lands. This is what makes us classifiable at
all.

**Stage 3 — split `MarmotGroupData` into components.**
`0x8001` profile, `0x8003` admin-policy, `0x8004` nostr-routing, `0x8002` blossom-image
(with the new key semantics + `media_type` + domain-separated AAD), `0x8005`
message-retention, `0x800c` lifecycle. Keep the `0xF2EE` decoder as a read-only legacy path
for groups already on disk.

**Stage 4 — Nostr transport corrections.**
Drop kind 10051 in favor of NIP-65 write relays; drop the `encoding` and `relays` tags from
30443; add `app_components`; MLS-bytes dedup id; bounded retained-candidate trial decryption;
KeyPackage lifetime bound.

**Stage 5 — lifecycle state machine + publish-before-apply.**
The six canonical states, the `Leaving` / `Disbanding` gates, and the publish-obligation
record (bytes + recipient scope + prior state + pending state) surviving restart.

**Stage 6 — convergence engine.**
Bounded passes, candidate graph, eligibility, witnesses, six-step selection, dispositions and
withdrawal. Delete `CommitOrdering`'s transport-metadata tiebreak at this point, not before.

**Stage 7 — durability/restart conformance, app payload kinds (1009/1210), encrypted-media
v2, push owner proof.**

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
