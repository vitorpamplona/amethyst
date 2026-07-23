# BOLT12 zap proof verification — interop test vectors (follow-up)

Status: **blocked on upstream.** The NIP-XX BOLT12-zap layer (`quartz/…/nipXXBolt12Zaps/`)
verifies fully-disclosed payer proofs and reports compressed ones as
`Bolt12ProofResult.Unsupported` (surfaced as `cryptoVerified = false`). Two pieces
of work are gated on the BOLT12 payer-proof spec ([lightning/bolts#1346]) merging
with published test vectors.

## Why it's gated

Today the crypto path (`Bolt12ProofVerifier` + `Bolt12Merkle`) is validated only by
**self-consistent round-trips** (our own encoder ↔ our own verifier, see
`Bolt12ProofFixture` + `Bolt12MerkleTest` + `Bolt12ZapValidatorTest`). That proves
internal correctness, not agreement with CLN/LDK. Several constants are our best
reading of the still-draft spec and MUST be reconciled against real vectors before
we trust wallet-produced proofs:

- TLV type numbers (`Bolt12PayerProof` companion): 240/241, 1001–1005, 22, 80–91,
  160–176.
- Signature digest tags (`Bolt12ProofVerifier`): `"lightning" + messagename + fieldname`
  — `INVOICE_MESSAGE`/`PROOF_MESSAGE`/`SIGNATURE_FIELD`. The proof-signature field
  name especially is a guess.
- Merkle leaf/branch tag strings + odd-node promotion (`Bolt12Merkle`) — believed to
  match LDK, not checked byte-for-byte.
- 33-byte compressed `point` → BIP-340 x-only handling / even-y convention for
  `invoice_node_id` and `invreq_payer_id`.

## Work item 1 — vector-driven interop test

When `bolt12/payer-proof-test.json` exists in #1346:

1. Vendor the vectors into `quartz/src/commonTest/resources/` (or inline the hex).
2. Add `Bolt12PayerProofVectorTest`: for each `valid` proof assert
   `Bolt12ProofVerifier.verify(...) is Valid`; for each `invalid` proof assert the
   specific rejection reason.
3. Fix any constant above that the vectors disprove. If a fix is needed, the
   round-trip tests will still pass (they move with our encoder) — the vector test
   is the real gate.

## Work item 2 — compressed-proof merkle reconstruction

Real wallet proofs omit non-required invoice TLVs (blinded paths, etc.), which still
contributed to the invoice signature's merkle root — so `Bolt12ProofVerifier.verify`
currently returns `Unsupported` for them. Implement the reconstruction in
`Bolt12Merkle`, rebuilding the invoice root from:

- disclosed invoice TLVs → compute their `LnLeaf` hashes locally;
- `proof_leaf_hashes` (1004) → the `LnNonce` leaves for disclosed fields (can't be
  computed locally — the nonce tag embeds the possibly-omitted first TLV);
- `proof_omitted_tlvs` (1002) → markers for where omitted fields sit in
  TLV-ascending order;
- `proof_missing_hashes` (1003) → sibling subtree hashes for omitted branches,
  consumed post-order DFS smallest-to-largest.

Then verify the invoice signature against the reconstructed root and drop the
`isCompressed()` short-circuit. Gate acceptance behind Work item 1's vectors — a
reconstruction that only round-trips against our own encoder proves nothing about
real-wallet interop.

## Not gated on this

Runtime validation is fully offline (no network) and everything else in the feature
— events, accounting, display, the fully-disclosed crypto path — is done. This
document only covers making compressed real-wallet proofs count as
`cryptoVerified = true`.

[lightning/bolts#1346]: https://github.com/lightning/bolts/pull/1346
