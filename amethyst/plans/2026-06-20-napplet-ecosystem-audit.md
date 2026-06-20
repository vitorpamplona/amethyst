# Napplet implementation audit vs the upstream SDK / demo runtimes

**Date:** 2026-06-20
**Sources:** `github.com/napplet/naps` (NAP specs), `github.com/napplet/web`
(`@napplet/shim` SDK), `github.com/kehto/web` + `kehto.github.io/web/playground`
(reference runtime). Audited against our branch `claude/awesome-pasteur-xwiwad`.

## TL;DR

Our shell is a **correct and security-hardened NIP-5A/5D renderer + capability
broker** — process isolation, verified-blob serving, default-deny CSP,
capability-aware + signer-aware consent, foreground-only, permissions UI. Those
are *shell-quality* properties the demo runtimes don't even specify, and we're
ahead there.

**But it is not wire-compatible with the napplet ecosystem.** Real napplets are
built against `@napplet/shim`, which exposes a **namespaced** `window.napplet.*`
and a **`{type:"domain.action", id}`** postMessage envelope. We inject a **flat**
`window.napplet.*` and use a **`{id, payload:{op}}`** envelope. A napplet from the
kehto playground calling `window.napplet.relay.publish(...)` or
`window.napplet.shell.supports("relay")` hits `undefined` on our shell. So today:
**0 real ecosystem napplets run as-is.**

## Reference: the upstream surface

`window.napplet` namespaces (from `@napplet/web`):

| Namespace | Methods (upstream) |
|---|---|
| `shell` | `supports(domain)` — **required of every runtime** |
| `identity` | `getPublicKey()`, `onChanged(handler)` |
| `keys` | signing / NIP-04 / NIP-44 (separate domain from `identity`) |
| `relay` | `subscribe(filters)` (live), `publish(event)`, `query(filters)`, `publishEncrypted` |
| `storage` | `get(key)`, `set(key, value)`, `remove(key)` |
| `inc` | `emit(type, payload)`, `on(type, handler)` (inter-napplet) |
| `resource` | `bytes(url)`, `bytesAsObjectURL(url)` — fetch https/blossom/nostr/data |
| `value` | shell-mediated value transfer + zaps (depends on `relay`) |
| `upload` | shell-mediated file/blob upload (Blossom; depends on `relay`) |
| `intent` | invoke a napplet by archetype |
| `theme`, `notify`, `media`, `config`, `outbox`, `ifc`, `cvm` | further domains |

**Envelope:** `{ type: "<domain>.<action>", id, ...payload }` →
result `{ type: "<domain>.<action>.result", id, ok, ... }`.
**Sandbox:** `iframe sandbox="allow-scripts"` only (no `allow-same-origin`) — **we
match this exactly.**

## Coverage scorecard (our broker vs upstream domains)

| Domain | Status | Notes |
|---|---|---|
| `shell` | ✗ **missing** | `supports()` is foundational; without it well-behaved napplets bail early |
| `identity` | ◐ | `getPublicKey` ✓; `onChanged` ✗ (no push) |
| `keys` (sign/nip04/nip44) | ◐ | implemented, but lumped under our `IDENTITY` capability, not a `keys` domain |
| `relay` | ◐ | `publish` ✓, `query` ✓ (now live); `subscribe` (live) ✗, `publishEncrypted` ✗ |
| `storage` | ✓ | get/set/remove — shape matches (wire differs) |
| `value` | ◐ | we do `payInvoice` via NWC; upstream `value` is zaps/value-transfer (depends on relay) — different method shape |
| `resource` | ✗ | no `bytes(url)`; we only serve manifest subresources |
| `upload` | ✗ | no Blossom upload |
| `inc` | ✗ | deferred (see inter-applet plan) |
| `intent` | ✗ | deferred |
| `theme` `notify` `media` `config` `outbox` `ifc` `cvm` | ✗ | not modeled |

Our extra `NET` domain has **no upstream equivalent** — upstream fetching is
`resource`. Our `fromNapDomain` recognizes `identity/relay/value/storage/net`;
everything else (`shell`, `resource`, `upload`, `inc`, `intent`, `keys`, …) maps
to *unknown → denied*. So a manifest `requires: ["relay","shell","resource"]`
would have two of three flagged unknown — and `shell` is mandatory.

## The two interop blockers

1. **Envelope.** Real napplets send `{type:"relay.publish", id, event}` and await
   `{type:"relay.publish.result", id, ok}`. We expect `{id, payload:{op:"publish"}}`
   and reply `{id, response:{type:"published"}}`. Incompatible.
2. **API surface.** Upstream is namespaced (`relay.publish`, `identity.getPublicKey`,
   `keys.signEvent`, `shell.supports`, `resource.bytes`). Ours is flat
   (`getPublicKey`, `signEvent`, `publish`, `queryEvents`, `payInvoice`) + a
   namespaced `storage`. Only `storage` lines up.

Both live in our **edge layer** (the injected JS shim + `NappletProtocolJson` +
`NappletIpc`) and the capability enum — *not* in the security core (broker,
ledger, process model), which is dialect-agnostic. So aligning is an edge rewrite,
not an architecture change.

## What we have that the demos don't

- Separate-OS-process sandbox (`:napplet`), not just an iframe.
- Verified-blob serving (manifest-authoritative, per-blob sha256) with default-deny
  CSP (`connect-src 'none'`) and Tor-routed fetch.
- Capability **declaration enforcement** (manifest `requires` gate), **per-use**
  payment consent, **signer-aware** identity deferral, **foreground-only** execution.
- A persisted permission ledger + a permissions-management UI.

These are real-shell concerns the reference runtimes leave to the implementer; we
should keep them.

## Recommended path to ecosystem compatibility (priority order)

1. **Adopt the upstream envelope** `{type:"domain.action", id}` / `…​.result` in the
   shim + `NappletProtocolJson` + `NappletIpc`. (Unblocks everything.)
2. **Re-shape the injected shim** to the namespaced `window.napplet.*` and add
   **`shell.supports(domain)`** (cheap; derive from the granted capability set).
3. **Split capabilities** to match domains: `keys` (sign/nip04/nip44) distinct from
   `identity` (getPublicKey/onChanged); rename `NET`→`resource`; add `SHELL`,
   `UPLOAD`, `VALUE` semantics (zap-by-target, not raw invoice).
4. **Add the push channel**: `relay.subscribe` (live events), `identity.onChanged`,
   later `inc.on` — the host already holds a `JavaScriptReplyProxy` we can push
   unsolicited `…event` messages through.
5. **`resource.bytes`** (consented fetch of https/blossom/nostr/data) and
   **`upload`** (Blossom), both brokered + consent-gated.
6. Lower priority / app-specific: `theme`, `notify`, `media`, `config`, `outbox`,
   `intent`, `inc`, `ifc`, `cvm`.

## Verdict

- **As a secure NIP-5A/5D renderer + broker:** ~85% — the hard, security-critical
  parts are done and tested; gaps are on-device verification and breadth of ops.
- **As an ecosystem-compatible napplet host (runs real napplets):** ~25% — blocked
  by the envelope + namespaced-API mismatch and missing `shell`/`resource`. Until
  #1–#2 land, existing napplets won't run regardless of how solid the core is.
