# Amethyst over Yggdrasil (IPv6 overlay) — compatibility assessment

Status: **analysis only** — no behavior changed. Characterization tests landed alongside
this doc pin the current behavior so a fix has a baseline to diff against.

## What Yggdrasil looks like to the app

Yggdrasil is an encrypted end-to-end mesh. Every node gets an IPv6 address derived from its
public key inside `0200::/7`, and hands out `0300::/8` subnets. Consequences that matter here:

- **No DNS.** A relay on the mesh is addressed as a bracketed IPv6 literal, always.
- **No certificates.** No CA issues for `0200::/7` literals, so relays run plain `ws://`.
  This is not a downgrade — the overlay already provides end-to-end encryption and
  authenticates the peer by its address.
- **On Android it is a `VpnService`**, so the app's default network becomes the VPN network.
- The address is a **stable node identifier**, so publishing it is equivalent to publishing
  a long-lived pseudonymous handle for the device.

## Verdict

A hand-typed `ws://[…]:port` relay works end to end: it normalizes, survives the RFC 3986
pass, and OkHttp parses and dials it. Nothing in the stack is IPv4-only, `TcpNoDelaySocketFactory`
is family-agnostic, and `network_security_config.xml` permits cleartext globally, so the
`ws://` requirement is already satisfied.

Everything around that happy path is where it degrades. Four gaps, in severity order.

### GAP 1 — one relay, two identities (correctness)

`RelayUrlNormalizer` folds hex case but does **not** canonicalize zero-compression or
leading zeros:

| input | `NormalizedRelayUrl` | OkHttp host |
|---|---|---|
| `ws://[201:d0e:9ba5:8bbc::1]:8080` | `ws://[201:d0e:9ba5:8bbc::1]:8080/` | `201:d0e:9ba5:8bbc::1` |
| `ws://[201:0d0e:9ba5:8bbc:0000:0000:0000:0001]:8080` | `ws://[201:0d0e:…:0001]:8080/` | `201:d0e:9ba5:8bbc::1` |

OkHttp collapses both to one host; the app does not. `NormalizedRelayUrl` is the key of the
connection pool (`PoolRequests`, `RelayPool`), every relay-list set, the NIP-11 cache and the
per-relay stat maps — so the same relay written two ways gets **two sockets, two REQ sets and
doubled traffic**, and appears twice in the relay UI. This affects all IPv6 literals, but it
only bites Yggdrasil users in practice, because on the mesh a literal is the *only* way to
name a relay. Fix: canonicalize the bracketed literal to RFC 5952 inside `fix()`.

### GAP 2 — schemeless entry defaults to `wss://` (dead end)

`RelayUrlNormalizer.isLocalHost()` recognizes `127.0.0.1`, `localhost`, `//umbrel:`,
`192.168.`, `.local:` / `.local/`. An Yggdrasil address matches none of them, so a schemeless
`[201:…]:8080` falls through to the clearnet default and becomes `wss://[201:…]:8080/` — a
URL whose TLS handshake can never succeed. The user must know to type `ws://` themselves.
Fix: teach `isLocalHost()` (or a sibling `isOverlayNetwork()`) the `0200::/7` prefix.

### GAP 3 — unbracketed literal is silently rejected (UX)

`yggdrasilctl getSelf` prints the address **unbracketed**, which is exactly what a user
copies into the "add a relay" box. `isBareHostAndPath()` rejects it (correctly — it is
ambiguous with a scheme), so `normalizeOrNull` returns null. But
`RelayUrlEditField.submitRelay()` has no else branch: the Add button just does nothing, with
no error. Fix: either auto-bracket a candidate that parses as an IPv6 address, or surface a
validation message instead of a silent no-op.

### GAP 4 — Tor routing sends mesh traffic into the SOCKS proxy (breaks the relay)

`TorRelayEvaluation` classifies relays as localhost / onion / dm / trusted / new. Yggdrasil
lands in **new**, so with Tor on and the default "new relays via Tor", the relay is dialed
through the Tor SOCKS proxy — which cannot route `0200::/7`. The connection can only fail.
Compare `ws://192.168.1.100:8080/`, which is correctly kept off Tor because `isLocalHost()`
knows the LAN prefix. Today the only workaround is turning "new relays via Tor" off, which
weakens the setting for every genuine clearnet relay. The same gap exists on desktop
(`DesktopHttpClient`) and for non-relay HTTP (`RoleBasedHttpClientBuilder`). Fixing GAP 2's
prefix check fixes this one too, since both read the same predicate.

## Propagation / privacy note (not a bug, a decision)

An Yggdrasil relay is not filtered out of NIP-65 publishing (`AdvertisedRelayInfoTag` only
rejects localhost) nor out of the outbox model
(`RelayListRecommendationProcessor.filterValidRelays`). So a mesh relay in your relay list is
**published to public relays and recommended to other users**. Two effects:

- Peers not on the mesh dial `[201:…]` and burn reconnect attempts on an unreachable host.
- Your Yggdrasil address — a stable, key-derived node identifier — becomes public.

Onion relays get special handling here (`hasOnionConnection` gates whether they are even
considered). An overlay-network classification would let Yggdrasil be treated the same way.

## Not covered

- **No live socket test.** The analysis container has no IPv6 stack at all
  (`AF_INET6` → `EAFNOSUPPORT`), so everything above is verified below the socket: URL
  normalization, OkHttp URL/host parsing, and the Tor routing decision. An on-device run
  against a real mesh relay is still needed to confirm the happy path end to end.
- **Android VPN interaction untested.** `ConnectivityFlow` uses
  `registerDefaultNetworkCallback`, so it follows the app's default network into the VPN.
  Whether `isMeteredOrMobileData()` reads correctly through Yggdrasil's `VpnService` depends
  on whether that app declares underlying networks; worth checking on device before assuming
  data-saving mode behaves.
- Media loading (Coil) and NIP-05 resolution against mesh hosts were not exercised.

## Tests

- `quartz/src/jvmAndroidTest/…/relay/YggdrasilCompatCharacterizationTest.kt` — GAPs 1–3 plus
  the working happy path.
- `commons/src/commonTest/…/tor/YggdrasilTorRoutingTest.kt` — GAP 4.
