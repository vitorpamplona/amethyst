# cordn-vector-gen

Emits `quartz/src/commonTest/resources/cordn/coordinator-contracts.json` from
**`@cordn/core`** — the package cordn's own reference coordinator and client
both import. Consumed by `CoordinatorContractVectorTest` and
`CordnGroupRefVectorTest`.

## Why this exists

Our ts-mls fixtures (`resources/tsmls/`) cover the *crypto* layer: KeyPackages,
Welcomes, exporters, sealed payloads. They say nothing about the layer above —
the eleven coordinator tools, their argument names, which fields are optional,
and the `cordn1…` group ref. That layer had been verified only against our
reading of the spec, which is exactly the kind of agreement that holds right up
until someone runs a real coordinator.

So this generator goes the other way: it takes the payloads **we** put on the
wire and hands each one to **their** zod schema. A field we named wrong fails at
generation time. Each method also carries `rejects` — payloads their schema must
refuse — because a positive result only means something if the schema is strict
where we assume it is.

Group refs are round-tripped through their bech32 codec, and the vectors pin
both directions: we must decode what they encode, *and* encode byte-identically.
Only the second half catches TLV ordering, since the spec lets decoders accept
any order.

## Regenerating

```
cd quartz/tools/cordn-vector-gen
npm install
node generate.mjs > ../../src/commonTest/resources/cordn/coordinator-contracts.json
```

Output is deterministic — fixed actors, fixed timestamps, no randomness — so a
regeneration that changes the file means cordn changed something. Commit the
result with the reason.

## Licensing

`@cordn/core` and `@cordn/cli` are **MIT** and each ship a LICENSE file; this
generator is a dev-only dependency on the first and ships in nothing.

The rest of the `Cordn-msg/cordn` repository — `packages/coordinator`,
`packages/server`, `packages/test-utils`, and the `ghcr.io/cordn-msg/cordn`
image built from them — carries **no license file and no `license` field**, so
default copyright applies. That is why Tier B (a live coordinator round-trip)
is not wired up here and why these vectors are generated from the two licensed
packages instead. See `quartz/plans/2026-09-17-cordn-interop.md` §7.
