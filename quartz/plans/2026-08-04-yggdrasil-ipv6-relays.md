# Amethyst over Yggdrasil (IPv6 overlay)

Status: **fixed** — the four gaps found in the original assessment are closed. The last
section records what was deliberately left alone.

## What Yggdrasil looks like to the app

Yggdrasil is an encrypted end-to-end mesh. Every node gets an IPv6 address derived from its
public key inside `0200::/7` (nodes in `0200::/8`, subnets in `0300::/8`). Consequences:

- **No DNS.** A relay on the mesh is addressed as an IPv6 literal, always.
- **No certificates.** No CA issues for `0200::/7`, so relays run plain `ws://`. Not a
  downgrade — the overlay already encrypts end to end and authenticates the peer by an
  address derived from its public key.
- **On Android it is a `VpnService`**, so the app's default network becomes the VPN network.
- `0200::/7` is deprecated NSAP space, so nothing else routes there. An address in the range
  is reachable *only* through a running mesh interface — which is what makes it safe to key
  behavior off the prefix.

## What was wrong, and what fixed it

### 1. One relay, two identities

`RelayUrlNormalizer` folded hex case but not zero-compression, so
`[201:0d0e:9ba5:8bbc:0000:0000:0000:0001]` and `[201:d0e:9ba5:8bbc::1]` stayed two distinct
`NormalizedRelayUrl`s for one host — while OkHttp collapsed both to the same host when
dialing. Since that value keys the connection pool, the relay-list sets, the NIP-11 cache and
the per-relay stat maps, the app opened two sockets to one relay and counted it twice.

**Fix:** new `Ipv6` util (`quartz/utils/Ipv6.kt`) — pure-Kotlin parse, RFC 5952 canonical
format and range classification, no `java.net`, so it works on every KMP target.
`RelayUrlNormalizer.norm()` now canonicalizes the bracketed host. The canonical form is
byte-for-byte what OkHttp renders, so the key the app stores is the host it actually dials —
asserted differentially against OkHttp in `YggdrasilCompatCharacterizationTest`.

Affects every IPv6 relay, not just mesh ones; it only bit Yggdrasil users because on the mesh
a literal is the *only* way to name a relay. No migration needed: relay lists are rehydrated
from event tags through `normalizeOrNull`, so stored entries fold on load.

### 2. Schemeless entry defaulted to `wss://`

`isLocalHost()` knew `127.0.0.1` / `localhost` / `//umbrel:` / `192.168.` / `.local`, so a
mesh address fell through to the clearnet default and produced a `wss://` url whose TLS
handshake could never succeed.

**Fix:** new `RelayUrlNormalizer.isOverlayNetwork()` recognizes `0200::/7` and joins
`isOnion` / `isLocalHost` in choosing `ws://`. Clearnet IPv6 (`2001:db8::1`) still gets
`wss://`.

`isLocalHost()` separately grew the IPv6 twins of the literals it already knew — `::1`
(loopback), `fc00::/7` (unique local, the 192.168. analogue) and `fe80::/10` (link-local).
Those are the same question every caller is asking, so a relay on one now correctly skips TLS
and Tor and stays out of published relay lists.

### 3. Unbracketed literal silently rejected

`yggdrasilctl getSelf` prints the address unbracketed — exactly what gets pasted into "add a
relay". Normalization returned null (correctly: it is ambiguous with a scheme) and
`RelayUrlEditField.submitRelay` had no else branch, so the Add button did nothing at all.

**Fix, two halves:**
- `fix()` brackets a bare literal automatically, but only when the whole string parses as an
  IPv6 address — so `31990:hex:dtag` (addressable pointer), `abcd:1234` (host:port) and
  `relay.example.com:8080` still fall through untouched.
- The edit field now sets `isError` and shows `relay_url_not_valid` instead of no-opping.
  That fixes the dead button for *all* invalid input, not just IPv6.

### 4. Tor routing broke mesh relays

`TorRelayEvaluation` classified mesh relays as "new", so with Tor on and the default "new
relays via Tor" they were dialed through the SOCKS proxy — which cannot route `0200::/7`.
Guaranteed failure, not privacy.

**Fix:** `useTor()` returns false for `isOverlayNetwork()`, checked right after the localhost
branch. Both the Android and desktop relay paths delegate here (`TorRelayState`,
`DesktopHttpClient`), so one change covers both. `RoleBasedHttpClientBuilder` got the same
treatment for non-relay HTTP (images, previews, NIP-05, money ops). Clearnet IPv6 relays keep
following the Tor setting — asserted in `YggdrasilTorRoutingTest`.

## Audit round: bugs found in the host predicates

Auditing the change above turned up a family of bugs in `isLocalHost` / `isOnion` that predate
it. All shared one root cause — the predicates ran `contains` over the **whole url** instead of
parsing the authority — and all are now anchored, parsed and covered by
`RelayUrlAuthorityAnchoringTest`.

These predicates decide whether a relay is exempt from Tor, and relay urls arrive from other
people (NIP-65 lists, relay hints, `r` tags), so they are attacker-controlled input.

| # | Bug | Effect |
|---|---|---|
| 1 | A path could impersonate the host: `wss://evil.example.com/127.0.0.1` answered `isLocalHost() == true` | Any relay list could hand the app a url that silently dropped its own Tor routing |
| 2 | `.onion:8080` never matched the `.onion/` test | An onion relay on an explicit port was not treated as onion — never forced onto Tor, hostname sent to the clearnet DNS resolver |
| 3 | `.onion.` / `localhost.` (RFC 1034 fully-qualified form) matched nothing | Same leak as #2, via a different spelling |
| 4 | Host tests were case-sensitive, but `fix()` runs *before* the RFC 3986 pass folds case | `LOCALHOST:8080` and `ABC.ONION:8080` were given a `wss://` scheme neither host can serve |
| 5 | Private IPv4 was substring-matched | `10.0.0.5`, `172.16.3.4`, `127.1.2.3` were not local (LAN relay got `wss://` and Tor), while `192.168.evil.com` — a registrable domain — was |
| 6 | `contains("localhost")` matched `notlocalhost.example.com` | Same Tor exemption as #1, via a registrable domain |
| 7 | A `://` inside a path was read as a scheme separator | `relay.com/x://127.0.0.1` read its path as the authority |

Two bugs were introduced by this branch and caught in the same pass: the IPv6 host lookup had
the #1 flaw (`wss://evil.example.com/[fd00::1]` read as localhost), and IPv6 canonicalization
could rewrite a **path** (`/x[0:0:0:0:0:0:0:1]y` → `/x[::1]y`), corrupting the url.

Fixes: a shared `hostStart` / `hostEnd` / `hostEndWithoutPort` trio bounds every test to the
authority, strips `:port` and trailing dots, and validates the scheme; private ranges are
parsed via the new `Ipv4` util and `Ipv6` rather than substring-matched; comparisons are
case-insensitive per RFC 4343; `NormalizedRelayUrl.isOnion()` now delegates instead of keeping
a second, weaker copy of the test.

Performance: no regression, likely a small win. The old form ran six full-string `contains`
scans; the new one bounds its work to the authority and rejects a DNS host from an IP parse on
a single character. `Ipv6.isLiteral` gained a two-colon gate so the schemeless `host:port` case
answers without allocating the parser's 16-byte buffer.

`Ipv6` itself is pinned by `Ipv6DifferentialTest`: 4000 random addresses round-trip against
`java.net.InetAddress` in both directions, and the canonical form is asserted equal to
OkHttp's host for the same address — so the relay identity the app stores provably matches the
host it dials.

## Deliberately not changed

**Mesh relays are still published and recommended.** `AdvertisedRelayInfoTag` (NIP-65) and
`RelayListRecommendationProcessor.filterValidRelays` only exclude localhost, so a mesh relay
in your relay list is still published to public relays and offered to other users via the
outbox model. Two consequences worth a maintainer's decision:

- Peers not on the mesh dial `[201:…]` and burn reconnect attempts on an unreachable host.
- Your Yggdrasil address — a stable, key-derived node identifier — becomes public.

Onion relays already have precedent for both readings: they *are* published, but
`filterValidRelays` gates them behind `hasOnionConnection`. The equivalent for overlay relays
would be a `hasMeshConnection` gate. That is a product call about whether mesh relays are
meant to be discoverable, so it is flagged rather than decided here.

## Not verified here

- **No live socket test.** The analysis container has no IPv6 stack at all (`AF_INET6` →
  `EAFNOSUPPORT`), so everything is verified below the socket: normalization, OkHttp URL/host
  agreement, and the Tor routing decision. An on-device run against a real mesh relay is
  still needed to confirm the happy path end to end.
- **Android VPN interaction untested.** `ConnectivityFlow` uses
  `registerDefaultNetworkCallback`, so it follows the app into the VPN network. Whether
  `isMeteredOrMobileData()` reads correctly through Yggdrasil's `VpnService` depends on
  whether that app declares underlying networks — worth checking on device before assuming
  data-saving mode behaves.
- Media loading (Coil) and NIP-05 resolution against mesh hosts were not exercised.

## Tests

- `quartz/…/utils/Ipv6Test.kt` — parser, RFC 5952 formatting, range classification.
- `quartz/…/relay/YggdrasilCompatCharacterizationTest.kt` — normalization end to end, plus
  the differential assertions that our identity matches the host OkHttp dials.
- `commons/…/tor/YggdrasilTorRoutingTest.kt` — overlay relays never Torified, clearnet IPv6
  still follows the setting.
