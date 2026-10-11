---
name: quartz
description: Building on Quartz, the Kotlin Multiplatform Nostr library (com.vitorpamplona.quartz): NostrClient + a WebsocketBuilder, fetchAll/fetchFirst/fetchAllPages/publishAndConfirm, typed event builders, NostrSigner, NIP-19/NIP-05/NIP-44. Use in projects that depend on Quartz.
---

# Quartz (Nostr for Kotlin Multiplatform)

Quartz is the protocol library behind the Amethyst Nostr client, published on Maven
Central as `com.vitorpamplona.quartz`. This skill is for **projects that depend on it**.
The full guides live in the repo:

- [Client guide](https://github.com/vitorpamplona/amethyst/blob/main/quartz/CLIENT.md) —
  transports, subscriptions, publishing, filters, connection lifecycle.
- [Relay guide](https://github.com/vitorpamplona/amethyst/blob/main/quartz/RELAY.md) —
  running a relay on Quartz: event stores, policies, NIP-11 limits and documents.
- [Client architecture](https://github.com/vitorpamplona/amethyst/blob/main/quartz/README.md) —
  how to structure a feed-style app on top of it.
- [One-shot relay operations](https://github.com/vitorpamplona/amethyst/blob/main/quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/client/accessories/README.md) —
  the catalog of `fetch*`, `count`, publish and negentropy helpers.

## Setup

```kotlin
// commonMain, or a JVM/Android module. Take the latest version from Maven Central:
// https://central.sonatype.com/search?q=com.vitorpamplona.quartz
implementation("com.vitorpamplona.quartz:quartz:<version>")
```

Targets: Android, JVM, iosArm64, iosSimulatorArm64, macosArm64, linuxX64 (per-target
artifacts such as `quartz-jvm` and `quartz-android` exist for non-KMP builds). Ktor is not
a dependency: on JVM and Android, Quartz talks to relays through OkHttp and parses events
with Jackson.

## Where things are

Packages are named after the NIP they implement: `nip01Core` (events, filters, relay
client, crypto, signers), `nip19Bech32`, `nip05DnsIdentifiers`, `nip17Dm`, `nip44Encryption`,
`nip57Zaps`, and so on, under `com.vitorpamplona.quartz`. To find a NIP, look for its
`nipNN…` package first; event classes expose `const val KIND` and a `build(...)` template.

## Connect and read

`NostrClient` is transport-agnostic: it needs a `WebsocketBuilder`. On JVM and Android,
use the bundled OkHttp one (add `com.squareup.okhttp3:okhttp` to construct the client);
on other targets, implement the three small interfaces shown in the client guide.

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import okhttp3.OkHttpClient

suspend fun read(pubkey: String) {
    val okHttp = OkHttpClient()
    val client = NostrClient(BasicOkHttpWebSocket.Builder { okHttp })

    val notes = client.fetchAll("wss://nos.lol", Filter(kinds = listOf(1), authors = listOf(pubkey), limit = 20))
    val profile = client.fetchFirst("wss://nos.lol", Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubkey)))
    println("${notes.size} notes; has profile: ${profile != null}")

    client.disconnect()
}
```

**Reach for the accessories before writing a REQ loop.** They are extension functions in
`nip01Core.relay.client.accessories` (live flows are in `…client.reqs`), so they don't show
in `NostrClient` completion — import them by name:

| Need | Use |
| --- | --- |
| All events for a filter | `fetchAll(relay, filter)` |
| First match | `fetchFirst(relay, filter)` |
| More than a relay's per-REQ cap | `fetchAllPages(relay, filters) { event -> }`; only `PagedFetchResult.End.DRAINED` proves there is nothing more |
| Verify/store as events arrive, per-relay results | `fetchAllWithHooks` |
| Many relays at once | `fetchAllPagesFromPool`, `fetchAllPagesFromPoolWithHooks` |
| Live subscription | `subscribeAsFlow(relay, filter)` (`…client.reqs`) |
| NIP-45 count | `count(relay, filter)`, `countMerged(relays, filter)` |
| NIP-77 sync | `negentropySync`, `negentropySyncOrFetch` |
| Publish and wait for `OK` | `publishAndConfirm(event, relays)`, `publishAndConfirmDetailed` |

Their waits are **idle windows** (`idleTimeoutMs`) that reset on real progress, never
wall-clock deadlines; wrap a call in `withTimeoutOrNull` for a hard bound. Relay URLs are
`NormalizedRelayUrl` (`"wss://…".normalizeRelayUrl()`); most helpers also accept a `String`.

## Keys, events and publishing

```kotlin
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub

suspend fun post(client: NostrClient) {
    val keys = KeyPair() // random; pass privKey = … to load an existing key
    val signer = NostrSignerInternal(keys)
    println("npub: ${keys.pubKey.toNpub()}")

    val event = signer.sign(TextNoteEvent.build("Hello from Quartz"))
    check(event.verify())

    val accepted = client.publishAndConfirm(event, setOf("wss://nos.lol".normalizeRelayUrl()))
    println("accepted by at least one relay: $accepted")
}
```

- Build events with the event class's `build(...)` template, then `signer.sign(template)`.
  Don't assemble tag arrays by hand when a typed builder exists.
- `NostrSigner` is the abstraction to code against (`sign`, `nip44Encrypt` /
  `nip44Decrypt`, `nip04Encrypt` / `nip04Decrypt`, all `suspend`).
  `NostrSignerInternal` holds a local key; remote (NIP-46) and Android external-app
  (NIP-55) signers implement the same class.
- `Event.verify()` checks the id and signature of events you receive from relays.

## Identifiers

- **Parse anything a user pastes** (npub, nprofile, note, nevent, naddr, `nostr:` URIs):
  `Nip19Parser.uriToRoute(text)` in `nip19Bech32`.
- **Resolve a user to a pubkey** from hex, npub, nprofile or a NIP-05 `name@domain`:
  `resolveUserHexOrNull(input, nip05Client)` in `nip05DnsIdentifiers`. Pass a
  `Nip05Client` to enable the NIP-05 lookup; without one only key formats resolve.
- **Encode**: `ByteArray.toNpub()` and the other `to*` helpers in `nip19Bech32`.

## Running a relay

Quartz also contains a relay server (event stores, policies, NIP-11, NIP-42, NIP-77) and a
standalone Ktor relay built on it (`geode`). Start from the relay guide linked above rather
than wiring `NostrServer` from scratch.
