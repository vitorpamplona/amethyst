# Buzz protocol (`com.vitorpamplona.quartz.buzz`)

Quartz-native models for the [`block/buzz`](https://github.com/block/buzz) protocol —
a self-hosted "workspace where humans and agents build together, on a relay you own."

Buzz runs **NIP-01 on the wire** and uses the `kind` integer as its only dispatch
switch. Architecturally it is a member of the **NIP-29 relay-group family**, *not* an
encrypted client-side community like `concord`: the relay is the single source of
truth, it enforces membership server-side, it keeps content **plaintext** (so it can
run full-text search, audit, and workflows), and it signs overlay/state events. Buzz
literally reuses NIP-29 primitives — the `h` channel tag, kind `9` stream messages,
the `9000`/`9001`/`9002` admin events, and the `39000`/`39002` relay-signed metadata —
and layers an agent + workspace vocabulary on top.

Because of that lineage, this package models only the **Buzz-custom extensions**. The
standard/NIP-29 kinds are reused from their existing Quartz packages
(`nip29RelayGroups`, `nip34Git`, `nip51Lists`, `nip42RelayAuth`, `nip43RelayMembers`,
…). `kinds/BuzzKinds.kt` is the full registry and notes, per kind, where the standard
ones already live.

## Source of truth

The prose specs under Buzz's `docs/nips/*.md` are **drafts and lag the code**. Every
model here is confirmed against the authoritative Rust — `crates/buzz-core` (the
`kind.rs` registry + per-kind modules), `crates/buzz-sdk` (event builders,
`nip_oa.rs`), and the `buzz-conformance` vectors — not the markdown. Where Buzz ships
a known-answer test vector (e.g. NIP-OA), it is pinned into the Quartz tests as a
cross-implementation compliance check.

## Layout

Mirrors the `concord`/`experimental` convention (a top-level product package with
per-feature sub-packages, each holding the `Event`/value types, a `tags/` folder, and
`TagArrayBuilderExt` DSL verbs):

| Package | Buzz NIP | Kinds | Status |
|---|---|---|---|
| `kinds` | — | (registry) | ✅ |
| `oaOwnerAttestation` | NIP-OA | `auth` tag (no kind) | ✅ |
| `apPersonas` | NIP-AP | 30175 | ⬜ planned |
| `aeEngrams` | NIP-AE | 30174 | ⬜ planned |
| `amTurnMetrics` | NIP-AM | 44200 | ⬜ planned |
| `aoObserver` | NIP-AO | 24200 | ⬜ planned |
| agent identity | — | 10100 / 30176 / 30177 | ⬜ planned |
| workspace overlays | NIP-PL/RS/ER/DV/WP/IA/CW | 30350 / 30300 / 30622 / 9033 / 903x / 3900x | ⬜ planned |
| messaging + collab | — | 4000x / 4101x / 4300x / 4500x / 30620+4600x | ⬜ planned |

## Owner Attestation (NIP-OA) — implemented

The primitive that makes "agents as first-class members" work without enrolling every
agent key. An owner signs a standalone commitment authorizing an agent pubkey to
publish under conditions; the relay grants virtual membership while the owner is a
member. See `oaOwnerAttestation/OwnerAttestation.kt`.

```
commitment = "nostr:agent-auth:" + agentPubKey + ":" + conditions
message    = SHA-256(commitment)
sig        = BIP-340 Schnorr(message, ownerPrivKey)      // over the 32-byte digest
tag        = ["auth", ownerPubKey, conditions, sig]
```

The signed message is not an event, so only a signer holding the raw owner private key
can produce it (a NIP-46 bunker / NIP-55 external signer cannot) — hence `sign()` takes
a `KeyPair`/private key, not a `NostrSigner`.
