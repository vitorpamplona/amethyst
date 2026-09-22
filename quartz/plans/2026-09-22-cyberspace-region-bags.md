# Cyberspace §7 — opening a region bag

_Written 2026-09-22, after DECK-0003 (SNO) shipped its reader and left `kind 33330`
closed. This is the plan for opening it._

Source: [`CYBERSPACE_V2.md`](https://github.com/arkin0x/cyberspace/blob/master/CYBERSPACE_V2.md)
§2 (coordinates), §4 (Cantor trees), §7 (location-based encryption and discovery),
§8.6 (the bag event). Reference implementations: `arkin0x/cyberspace-cli` (Python) and
`arkin0x/cyberspace-cli-js` (TypeScript, published as `cyberspace-core`), plus
`arkin0x/ONOSENDAI` as the only client that does this today.

Decisions taken up front: **all three layers ship** (protocol, CLI, UI), and **every
sweep is a tap** — no background CPU is ever spent on a stranger's puzzle, however
cheap it looks.

---

## 1. What this reverses

`amethyst/plans/2026-09-21-deck-0003-sno.md` deferred bags as decision D2, on a
reason that turned out to be wrong twice over.

The first version said opening a bag needs "§7.4 discovery scanning", and leaned on
Amethyst having no position in cyberspace to scan from. §7.7 says the opposite in as
many words:

> The seeker's own position never enters this cost, because §7.1 makes looking and
> walking equivalent: a region key can be computed for any coordinate without
> traveling there.

Scanning your own neighbourhood is the opportunistic half, and §7.7 is blunt about
what it is worth alone: "a bag is found only by intentionally deep scanning and/or
wandering. With no additional information, any given bag is equally likely to be at
any point in the full 2^256 coordinate space: an impossibly hardened secret."
ONOSENDAI's own code agrees — `destinationKeys.ts` opens with "Things are hidden in
cubes, so that key almost never fits a lock."

What finds a bag is a **hint**: the hider publishes an aligned box, and a seeker
sweeps the candidate regions inside it. A client with no avatar, no position and no
movement chain can do that.

The second wrong reason was that it would cost too much. It does not, at the heights
anyone actually hides at. §3 measures it.

## 2. What a bag is, end to end

1. A hider picks a coordinate and a height `h`, and derives the region key: three
   per-axis Cantor roots of the aligned subtrees at `h` (§4.5, §4.6), combined by
   nested Cantor pairing into `region_n` (§4.7), then
   `key = sha256(region_n)` and `lookup_id = sha256(key)` (§7.2).
2. They encrypt everything they have hidden in that region — a JSON array of nostr
   events — with AES-256-GCM under that key, and publish it as a `kind 33330`
   addressable by `d = lookup_id` (§7.6, §8.6).
3. They MAY attach a `hint` tag naming an aligned box the region lies in, one height
   per axis (§7.7).
4. A seeker sweeps the box: for each candidate region derive the key, hash to the
   `lookup_id`, and ask a relay for a bag with that `d`. Found, decrypt, verify the
   items, render by inner kind.

The two-layer hash is the whole trick: `lookup_id` is safe to publish because it is
a hash *of* the key, so seeing it buys nothing without the region preimage.

## 3. What it costs, measured

A region key is three `O(2^h)` folds of BigInts that double in width at every level,
plus one combine. Measured on this JVM against the same leaf-by-leaf fold
`cyberspace-core` uses:

| bag height `h` | one axis root | one combine + 2 SHA-256 |
|---:|---:|---:|
| 5 | 0.03 ms | 0.033 ms |
| 8 | 0.18 ms | 0.48 ms |
| 12 | 9.2 ms | 32 ms |
| 16 | 555 ms | 1,873 ms |

Per key (three axes plus a combine) that is 1.2 ms at h8 and 2.1 s at h16, against
the spec's own figures in §7.7 of 1.3 ms and 816 ms on one desktop core — within 2
to 3x, which is what a JVM `BigInteger` (Karatsuba and Toom-Cook, no FFT) should
give against C.

**A sweep decomposes per axis.** The axes are independent all the way to the combine
(§4.7), so a box of gap `G` costs `3 · 2^(G/3)` tree builds and `2^G` combines, not
`2^G` full keys. The combine term dominates above h8:

| | gap 0 | gap 6 | gap 12 | gap 18 |
|---|---|---|---|---|
| **h5** | instant | 2 ms | 0.14 s | 9 s |
| **h8** | 2 ms | 31 ms | 2 s | 2 min |
| **h12** | 60 ms | 2 s | 2 min | 2.3 h |
| **h16** | 3.5 s | 2 min | 2 h | — |

This agrees with §7.7's own table (gap 12 at h≤8 is "seconds", gap 24 is "hours"),
and it says the affordable corner is most of the corner anyone uses. Android will be
slower — measure it before quoting a number in the UI.

**The growth ratio is ~2.2x per height, not 2x**, in the spec's figures and in the
measurement, because a pairing doubles the leaf count *and* the operand width. A
`GROWTH_RATIO_FLOOR` of that shape is what ONOSENDAI's own calibration fits.

## 4. Layer 1 — quartz, protocol only

New package `quartz/.../cyberspace/`, beside the `CyberspaceScale` and
`CyberspaceCoordinate` the SNO work already put there.

| Piece | What | Pinned by |
| --- | --- | --- |
| `CyberspaceCoordinate` | §2.2 in full: interleave and de-interleave X/Y/Z/plane. Fixed 256 bits, so a `ByteArray(32)`; no bignum, no allocation per axis | §9.8's six vectors round-tripped, §7.7's three hint vectors |
| `CantorTree` | §4.5/§4.6: `cantorPair`, `alignedBase`, `subtreeRoot(base, height)`, with the reference's `maxComputeHeight` refusal | `cyberspace-cli`'s own roots, through the CLI harness |
| `RegionKey` | §7.2's two hashes over `int_to_bytes_be_min` | the same |
| `CyberspaceHint` | §7.7 tag parse with every MUST: arity, lowercase hex, aligned base, `H` in `[0,85]`, canonical integers, `H >= h`, sector-tag agreement — and a malformed hint is **absent**, never a rejection of the bag | §7.7's three golden vectors |
| `CyberspaceBagEvent` | kind 33330: `d`/`h`/`encrypted`/`hint`/sector tags, AES-256-GCM open, §7.6's plaintext rules | round trip against the reference CLI's `encrypt` |
| `RegionSweep` | the axis-decomposed enumeration, as a cold sequence a caller pulls with its own budget | a unit test that a gap-0 hint is one candidate and equals the direct derivation |

Two things need an `expect`/`actual`: arbitrary-precision integers (`java.math.BigInteger`
on `jvmAndroid`) and AES-256-GCM (`javax.crypto`). iOS gets neither for now and the
module declares it.

**The rules that are easy to get wrong**, all normative, all worth a test each:

- A failed decryption "MUST NOT be treated as an error in the bag" (§7.6) — it means
  only that this reader lacks the key.
- Items MAY be unsigned. A signed item must have the canonical id and a verifying
  signature or it is dropped, **and only it**: "one corrupt or forged item says
  nothing about the others". An unsigned item's `pubkey` is a claim and MUST NOT be
  shown as verified authorship.
- Placement is attributable to the bag's author; authorship of an item only to the
  item's own pubkey, and only when signed.
- An item's `C` tag must lie inside the bag's region, and a reader MAY drop one that
  does not.
- A reader that does not understand an item's kind skips it and renders the rest.

## 5. Layer 2 — `amy`

Thin assembly over Layer 1, which is where interop is proven. The SNO conformance
harness already runs 11 of 11 against the reference implementations; these extend it.

```
amy cyberspace coord <hex>                 # x, y, z, plane
amy cyberspace region <hex> <height>       # region_n, key, lookup_id
amy cyberspace hint <bag.json>             # box, candidates, estimated cost
amy cyberspace sweep <bag.json> [--budget] # the lookup_ids, or the key when found
amy cyberspace open <bag.json> --key <hex> # decrypt, verify items, dump
```

`region` and `open` are directly diffable against `cyberspace-cli`; `hint` against
`hint-reference.py`'s golden vectors. No UI decision touches any of it.

## 6. Layer 3 — Amethyst

Only for a bag someone put in front of you: quoted in a note, or in a thread. Nothing
sweeps the network on its own, and nothing sweeps on arrival.

A `kind 33330` in a feed renders a card that has already parsed its hint and priced
it, and says so: *"Hidden in a 4,096-region box — about 2 minutes of searching."*
A button starts it. While it runs, progress and a cancel. Found, the items render
through the cards that already exist: `3330` through `SnoObjectCard`, `1` as a note,
anything else skipped.

**Every sweep is a tap, including a gap-0 hint** that the spec itself calls "a
destination the seeker can compute or walk to directly, not a search" and which costs
60 ms. The consistency is the point: a reader never learns that some bags open
themselves, so a hostile bag cannot hide inside that expectation.

**Price before you spend.** The budget comes from the `hint` tag and the `h` tag
before a single tree is built, and it is a hard cap, because a bag can carry a gap-30
hint precisely to burn a day of a reader's battery. A sweep that would exceed the cap
is not offered as a button at all — it is reported as out of reach.

## 7. Not in scope

- §7.4 position scanning. We have nowhere to stand, and §7.7 says it is not how
  things are found.
- §4–§6 movement chains, §8.3 spawn, DECK-0001 hyperspace. That is a client *for*
  cyberspace; this is a Nostr client reading an event.
- Publishing, hiding, or holding a region (§7.8). Read-only, like the rest of SNO.
- §9.7's GPS mapping: 96-digit decimal arithmetic with a hand-rolled deterministic
  trig series, carried so every client agrees where a place on Earth is. Amethyst has
  no Earth to agree about.

## 8. Order

1. **Coordinates.** `CyberspaceCoordinate` in full, §9.8 and §7.7 vectors. No bignum
   and no new dependency: an 85-bit axis splits at 64 into a 21-bit high half and an
   unsigned low Long, which is enough for the bit layout, the plane, the sector shift
   of §10 and `alignedBase` — and `alignedBase` is exactly what a sweep enumerates,
   so this is the foundation the rest stands on. **Done**, 8 tests, every vector the
   spec publishes.

   It does *not* improve the shard card, which was the claim when this was written.
   The thought was to upgrade the `C` tag line from a plane to a place, but the only
   human-readable thing a decode adds is the sector triple, and at 55 bits an axis
   that is a fifty-character string for a 12 cm cube — worse than the abbreviated
   coordinate already there, which at least copies. Two shards in one sector would be
   worth saying; there are 21 reachable `3330`s and one author, so there is nothing
   to compare. Revisit when there is.
2. **Cantor + region key**, with the `expect`/`actual` bignum and the height refusal.
   `amy cyberspace region`, diffed against `cyberspace-cli`.
3. **Hints.** Parse, validate, price. `amy cyberspace hint`. The three golden vectors.
4. **The bag.** AES-256-GCM, plaintext shapes, item verification. `amy cyberspace
   open`, round-tripped against the reference CLI's `encrypt`.
5. **The sweep**, as a budgeted cold sequence. `amy cyberspace sweep`.
6. **The card**, last, once every number it quotes is measured on a device.

Steps 1 to 5 have no product risk and every one of them is diffable against a
reference implementation. Step 6 is the only judgement call, and it is small.
