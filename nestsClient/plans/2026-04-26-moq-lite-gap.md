# Plan: bridge the moq-lite protocol gap

**Status:** discovered, not yet decided.
**Context:** discovered while writing the nostrnests interop test suite (phases 1–4).

## Discovery

`:nestsClient` implements **IETF draft-ietf-moq-transport-17** (`TrackNamespace`
tuples, `CLIENT_SETUP` / `SERVER_SETUP`, `OBJECT_DATAGRAM` with `track_alias`,
two-message ANNOUNCE / SUBSCRIBE shape, etc.).

The actual nostrnests stack is built on **moq-lite** — kixelated's own MoQ
flavour, wire-incompatible with IETF MoQ-transport. Reference points:

- JS client `NestsUI-v2/package.json` depends on `@moq/lite`, `@moq/publish`,
  `@moq/watch`. None are IETF MoQ-transport.
- Rust relay `kixelated/moq-rs` is built on `rs/moq-lite/` types throughout
  (see `rs/moq-lite/src/path.rs`, `rs/moq-lite/src/model/origin.rs`).
- ANNOUNCE wire on moq-lite: `(active: bool, suffix: string)` — single
  string, no tuple. Source: `@moq/lite/lite/announce.js:14-19`.
- SUBSCRIBE wire on moq-lite:
  `(id: u62, broadcast: string, track: string, priority: u8, …)` — two
  independent strings. Source: `@moq/lite/lite/subscribe.js:87-91`.
- Path model: a plain string, `/`-joined and trim-normalised; prefix-strip
  is delimiter-aware (`"foo"` does NOT match `"foobar"`).

Concrete shapes the JS reference sends per nests room:

| Wire field             | JS reference value                       |
| ---------------------- | ---------------------------------------- |
| WT URL path            | `/nests/30312:<host>:<roomId>`           |
| `?jwt=` query          | JWT (`claims.root` = the same path)      |
| `claims.put` (publish) | `[<myPubkey>]`                           |
| `claims.get`           | `[""]`                                   |
| ANNOUNCE.suffix        | `<myPubkey>` (single string)             |
| SUBSCRIBE.broadcast    | `<speakerPubkey>` (single string)        |
| SUBSCRIBE.track        | `"catalog.json"` then `"audio/data"`     |

## Why this matters

The wire-shape fixes landed in this PR (path = `/<namespace>`, JWT in
`?jwt=` query) make the WebTransport CONNECT itself succeed against
moq-rs. But the FIRST MoQ control message we send afterwards
(IETF `ClientSetup`) is unintelligible to moq-rs's moq-lite framing, so
all post-CONNECT integration tests (round-trip, multi-peer, fan-out,
subscribe-before-announce) cannot pass against real nests in their
current form.

## Options

### A. Add moq-lite codec alongside the IETF one

- New `MoqLiteSession` parallel to `MoqSession`.
- New `NestsListener` / `NestsSpeaker` impls switched at construction
  time, or behind a `MoqDialect` enum on `NestsRoomConfig`.
- ~1–2 weeks of work; two parallel protocols to maintain.
- Keeps the IETF unit-test suite intact for any future IETF target.
- **Pro:** real interop with nests; production speaker/listener works.
- **Con:** dual codepaths.

### B. Drop IETF MoQ-transport, replace with moq-lite

- Rewrites `MoqSession`, `MoqCodec`, `MoqMessage`, all unit tests.
- Smaller surface long-term.
- **Pro:** one truth; less code.
- **Con:** discards finished IETF work; risks rework if the audio-rooms
  NIP later pivots to IETF MoQ.

### C. Hold integration round-trip / multi-peer tests, ship now

- This PR: phase-1 auth ping, phase-3 round-trip code, phase-4 wire fix,
  phase-4 negative auth + endpoints, phase-4 multi-peer code.
- All test code that reaches into MoQ framing is `-DnestsInterop=true`
  gated, so the default test run stays green.
- Land a TODO in this doc; pick A or B as a separate planned phase.

### D. Pivot the round-trip target to an IETF MoQ-transport server

- e.g. quic-go-moq, aioquic-moq, moq-go.
- Validates our IETF MoQ codec but does NOT validate nests interop —
  different goalpost, same effort to wire up a Docker harness.

## Recommendation

**C now, A next phase.** Land the wire fixes + HTTP-only tests + the
multi-peer test code (gated) so we have the test scaffolding in place
when moq-lite framing lands. Treat moq-lite as an explicit phase-5
work item with its own design doc.

## Open questions

- Does moq-rs accept IETF MoQ-transport behind any flag? (Quick check
  needed; agent's read of `rs/moq-lite/` suggests no.)
- Is the audio-rooms NIP draft IETF-MoQ-transport-binding, or
  moq-lite-binding? If IETF, nostrnests is non-conforming and may
  switch; if moq-lite, the NIP itself is moq-lite-bound and we should
  pursue option A.
- ALPN check: WT itself uses `h3`; moq-lite framing rides on top of
  WT bidi streams + datagrams, same as IETF MoQ-transport. The
  `/anon` vs `/<namespace>` URL path mismatch is unrelated.

## When picking up

- Read `NestsUI-v2/src/transport/moq-transport.ts` and the `@moq/lite`
  / `@moq/publish` / `@moq/watch` packages cached at
  `~/.cache/amethyst-nests-interop/nests/NestsUI-v2/node_modules/`.
- Read `kixelated/moq-rs/rs/moq-lite/src/` (announce.rs, subscribe.rs,
  path.rs, model/origin.rs) for the relay's view.
- The nests-side `claims.put = [pubkey]` rule is in
  `moq-auth/src/index.ts:160-166`.
