# `openagents/`

**OpenAgents** — the agent-protocol family OpenAgents publishes over Nostr
(NIP-CAP capabilities, NIP-EXT extension packages, and the shared contracts
they rest on). These are OpenAgents' own draft kind assignments, not upstream
NIPs, which is why they live here as a family rather than as `nipXX`
packages — the same reason `contextvm/` and `marmot/` do. Sub-packages are
named after the OpenAgents NIP mnemonic, the way `nip85TrustedAssertions` is
after its number.

Quartz models the **public discovery records** only: enough to store, render,
link and search them. Nothing here admits, installs or invokes anything — the
specs are explicit that a record describes and grants nothing, and admission
is a host decision this client does not make.

## Implemented specification revision

Built from [`OpenAgentsInc/openagents`](https://github.com/OpenAgentsInc/openagents)
`nips/openagents/` (`NIP-CAP.md`, `NIP-EXT.md`, `contracts.md`), read on
2026-10-16, and checked against what the `openagents` CLI actually publishes on
`wss://relay.openagents.com` (the test fixtures are those signed events).

| Kind | Record | Class |
| ---- | ------ | ----- |
| 30180 | NIP-CAP capability discovery head | `nipCapCapabilities/discovery/CapabilityDiscoveryEvent` |
| 30184 | NIP-EXT package listing / current-release pointer | `nipExtExtensions/listing/ExtensionListingEvent` |
| 3184 | NIP-EXT immutable release declaration | `nipExtExtensions/release/ExtensionReleaseEvent` |

Not modelled yet: CAP `30181` (operator preferences, often self-encrypted),
EXT `3185` (revocation), `3186` (namespace migration) and `30185` (revocation
checkpoint). A client that shows a release as installable needs `3185`/`30185`
first.

## Shape

Every body is UTF-8 JSON with `v: 1`, `requires` and optional `meta`.
`core/` holds the shared contracts' value types (`EventRef`, `ArtifactRef`,
`DefinitionRef`, package and qualified ids) and the `t`-tag protocol markers
(`oa:ext:<type>:v1`, `oa:cap:v1`, `oa:profile:…`, `oa:transport:…`). Readers are
lenient — unknown keys are ignored and a malformed body parses to `null` — and
each event class offers `isConsistent()` for the body/tag/signer agreement the
specs require before a record counts. Writers emit exactly the field set and
order the CLI writes, omitting absent optional fields instead of writing `null`
(the contracts tell omission and `null` apart); a hidden listing's
`"release": null` is the one explicit `null`.

## Search and hints

All three kinds are `SearchableEvent`s (see the `event-store-semantics`
skill's `searchable-kinds.md`) over the human-chosen text only — titles,
descriptions, summaries, slugs, operation ids — never keys, digests, schemas or
payment terms. Their `t` markers are protocol, not topics, so
`SearchFieldExtractor` keeps them out of the hashtag role.

Hints: a capability's `nostr-cj` workers are `PubKeyHint`s on the relays the
capability names for them. A listing links the release it points at, and a
release links its package's listing address, so the relay either arrived from
stands in as the other's hint (the CLI publishes both to the same relay).
