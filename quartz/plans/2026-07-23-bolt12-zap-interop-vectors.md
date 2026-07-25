# BOLT12 zap proof verification — interop test vectors (follow-up)

Status: **done** (both work items shipped), with one standing upstream caveat.
`Bolt12ProofVerifier` now reconstructs and fully verifies **compressed** BOLT12
payer proofs — the selective-disclosure case real wallets emit — so they count as
`cryptoVerified = true` (subject to the usual offer-binding rule). Verified
byte-for-byte against the draft's own conformance vectors.

## What shipped

- **Vector-driven interop test.** `bolt12/payer-proof-test.json` from
  [lightning/bolts#1346] is vendored into `quartz/src/commonTest/resources/bolt12/`
  and driven by `Bolt12PayerProofVectorTest`: all 5 `valid_vectors` verify, all 23
  `invalid_vectors` are rejected, and the writer (`Bolt12ProofBuilder`) reproduces
  each valid vector's `proof_omitted_tlvs` / `proof_missing_hashes` /
  `proof_leaf_hashes` exactly. The vectors disproved two of our earlier guesses,
  now fixed:
  - the **nonce leaf** hashes the record's *type* bytes, not the full encoded TLV
    (`Bolt12Merkle.nonceLeafHash`);
  - the **proof signature** field name is `proof_signature`, not `signature`
    (`Bolt12ProofVerifier.PROOF_SIG_FIELD`).

- **Compressed-proof merkle reconstruction.** `Bolt12Merkle.reconstructRoot`
  rebuilds the invoice root from the disclosed `LnLeaf` hashes + `proof_leaf_hashes`
  (nonce leaves) + `proof_omitted_tlvs` markers + `proof_missing_hashes` (consumed
  post-order DFS smallest-to-largest). `invreq_metadata` (type 0) is always the
  implied first omitted leaf. The `isCompressed()` short-circuit is gone; every
  proof now goes through reconstruction.

## Standing caveat (upstream)

#1346 is still an unmerged draft. The TLV type numbers, signature digest tag
strings, leaf/branch tags, and the invoice/proof field ranges track the current
PR head (`vincenzopalazzo/bolts@1be97b2`) and MUST be re-checked if the spec
changes before it merges. The vector test is the tripwire: refresh the resource
from the merged BOLT and it will flag any drift.

## Not covered here

Offer↔recipient-identity binding is still out of scope — a verified proof only
proves payment to the *embedded* offer, not that the offer belongs to the
p-tagged recipient (see `Bolt12ZapValidator.isInvoiceBoundToOffer`).

[lightning/bolts#1346]: https://github.com/lightning/bolts/pull/1346
