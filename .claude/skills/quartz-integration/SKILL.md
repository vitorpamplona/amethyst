---
name: quartz-integration
description: Integration guide for using the Quartz Nostr KMP library in external projects. Use when: (1) adding Quartz as a Gradle dependency, (2) setting up NostrClient with WebSocket, (3) creating/signing/sending events, (4) building relay subscriptions with Filter, (5) handling keys with KeyPair/NostrSignerInternal, (6) using Bech32 encoding/decoding (NIP-19), (7) platform-specific setup (Android vs JVM/Desktop), (8) NIP-57 zaps, NIP-17 DMs, NIP-44 encryption in external projects, (9) running a relay on Quartz and serving/building its NIP-11 relay information document (application/nostr+json).
---

# Quartz Integration Guide

Reference for integrating `com.vitorpamplona.quartz:quartz` into external Nostr KMP projects.

**Published artifact**: `com.vitorpamplona.quartz:quartz:1.12.6` (Maven Central)
**Targets**: JVM 21+, Android (minSdk 21+), iOS (XCFramework `quartz-kmpKit`)
**License**: MIT

---

## 1. Gradle Setup

### Version Catalog (`libs.versions.toml`)

```toml
[versions]
quartz = "1.12.6"

[libraries]
quartz = { module = "com.vitorpamplona.quartz:quartz", version.ref = "quartz" }
```

### `build.gradle.kts` (KMP project)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.quartz)
        }
    }
}
```

### Android-only project

```kotlin
dependencies {
    implementation("com.vitorpamplona.quartz:quartz:1.12.6")
}
```

### Transitive dependencies pulled in automatically

Quartz exposes these as `api` (you get them transitively):

| Dependency | Used for |
|-----------|----------|
| `fr.acinq.secp256k1:secp256k1-kmp-*` | Schnorr signing |
| `com.github.anthonynsimon:rfc3986-normalizer` | Relay URL normalization |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | Event JSON parsing |

For Android, add to `build.gradle.kts`:
```kotlin
android {
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
```

---

## 2. Key Concepts

### Core Types

```kotlin
typealias HexKey = String        // 64-char hex string (pubkey, event id, sig)
typealias Kind = Int             // Event kind number
typealias TagArray = Array<Array<String>>
```

### Event Anatomy

```kotlin
@Immutable
open class Event(
    val id: HexKey,        // SHA-256 of canonical JSON (64 hex chars)
    val pubKey: HexKey,    // Author public key (64 hex chars)
    val createdAt: Long,   // Unix timestamp (seconds)
    val kind: Kind,        // Event type
    val tags: TagArray,    // [["e","eventid"], ["p","pubkey"], ...]
    val content: String,
    val sig: HexKey,       // Schnorr signature (128 hex chars)
)
```

### Kind Classification

```kotlin
// Regular events — stored by relays forever
val isRegular = kind in 1..9999

// Replaceable events — relay keeps only latest per (pubkey, kind)
val isReplaceable = kind == 0 || kind == 3 || kind in 10000..19999

// Addressable events — relay keeps latest per (pubkey, kind, d-tag)
val isAddressable = kind in 30000..39999

// Ephemeral events — relays don't persist
val isEphemeral = kind in 20000..29999
```

---

## 3. Key Management

### Generate a new keypair

```kotlin
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair

// Generate fresh random keys
val keyPair = KeyPair()

// From existing private key bytes
val keyPair = KeyPair(privKey = myPrivKeyBytes)

// Read-only (public key only, cannot sign)
val keyPair = KeyPair(pubKey = myPubKeyBytes)

// Access
val pubKeyHex: String = keyPair.pubKey.toHexKey()
val privKeyHex: String? = keyPair.privKey?.toHexKey()
```

### Convert between formats

```kotlin
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser

// ByteArray → hex
val hex = byteArray.toHexKey()

// hex → ByteArray
val bytes = hex.hexToByteArray()

// Bech32 import (npub, nsec)
val parsed = Nip19Parser.uriToRoute("npub1abc...")
// or
val parsed = Nip19Parser.uriToRoute("nsec1abc...")
```

> Hex ↔ ByteArray is a first-class utility in Quartz — see **§3.1 Hex utilities** below.

---

### 3.1 Hex utilities (HexKey ↔ ByteArray)

Nostr keys, event ids and signatures travel as lower-case hex strings. Quartz
models this with the `HexKey` typealias (just a `String`) plus extension
functions — **do not** write your own byte loop or pull in a third-party codec.

**Packages:** `com.vitorpamplona.quartz.nip01Core.core` (the extensions) and
`com.vitorpamplona.quartz.utils` (the underlying `Hex` object).

```kotlin
import com.vitorpamplona.quartz.nip01Core.core.HexKey            // typealias = String
import com.vitorpamplona.quartz.nip01Core.core.toHexKey          // ByteArray → hex
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray    // hex → ByteArray
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.utils.Hex

// Encode / decode
val hex: HexKey = pubKeyBytes.toHexKey()      // lower-case, 2 chars per byte
val bytes: ByteArray = hex.hexToByteArray()   // throws on odd length

// Untrusted input → decode safely
val maybe: ByteArray? = userInput.hexToByteArrayOrNull()  // null if not valid hex

// Validate without decoding (no allocation)
Hex.isHex(userInput)        // even-length, all hex digits (any length)
Hex.isHex64(userInput)      // fast path for a 32-byte key/id (checks first 64 chars)
hex.isValid()               // 64 chars AND valid hex (pubkey / event-id shape)

// Compare a hex string to raw bytes without decoding
Hex.isEqual(incomingHexId, myIdBytes)
```

| Need | Call | Notes |
|------|------|-------|
| ByteArray → hex | `bytes.toHexKey()` | lower-case output |
| hex → ByteArray (strict) | `hex.hexToByteArray()` | throws on odd length |
| hex → ByteArray (safe) | `hex.hexToByteArrayOrNull()` | `null` on invalid hex |
| is this valid hex? | `Hex.isHex(s)` / `Hex.isHex64(s)` | `isHex64` ~30% faster for keys/ids |
| is this a pubkey/id shape? | `hex.isValid()` | 64 chars + valid hex |
| hex == bytes? | `Hex.isEqual(hex, bytes)` | no decode allocation |

Constants `PUBKEY_LENGTH` and `EVENT_ID_LENGTH` (both `64`) live in the same
`nip01Core.core` package.

---

### 3.2 Everyday utilities (time, random, hashing, bech32, base64)

These small helpers exist so you don't reinvent them — and several have a
footgun the built-in avoids. **Prefer them over stdlib/hand-rolled equivalents.**

**Time — `TimeUtils` (`com.vitorpamplona.quartz.utils`).** Everything is in Unix
**seconds** (what `created_at` and filter `since`/`until` use), *not* millis.

```kotlin
import com.vitorpamplona.quartz.utils.TimeUtils

val createdAt = TimeUtils.now()          // seconds — for created_at. NOT currentTimeMillis()/1000
val since = TimeUtils.oneDayAgo()        // relative filter bounds: oneHourAgo(), fiveMinutesAgo()…
val fresh = TimeUtils.withinTenMinutes(event.createdAt)  // NIP-42/NIP-98 freshness
// TimeUtils.nowMillis() is the only millisecond helper — non-protocol use only.
```

**Secure random — `RandomInstance` (`utils`).** Backed by `SecureRandom`; use it
for anything security-sensitive instead of `kotlin.random.Random`.

```kotlin
import com.vitorpamplona.quartz.utils.RandomInstance

val nonce = RandomInstance.bytes(32)     // nonces, salts, keys
val subId = RandomInstance.randomChars() // 16-char [a-zA-Z0-9] subscription id
```

**Hashing — `sha256(...)` + `EventHasher`.** `sha256` is the raw primitive; to
compute/verify an **event id** use `EventHasher`, which canonically serializes
`[0, pubkey, created_at, kind, tags, content]` before hashing (getting this wrong
is what makes relays reject an event). Typed builders already do this for you.

```kotlin
import com.vitorpamplona.quartz.utils.sha256.sha256
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher

val digest = sha256(bytes)               // raw 32-byte hash
val id = EventHasher.hashId(pubKey, createdAt, kind, tags, content)
val valid = EventHasher.hashIdCheck(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content)
```

**Bech32.** For `npub`/`nsec`/`note`/… prefer the NIP-19 layer (`ByteArray.toNpub()`,
`Nip19Parser.uriToRoute(...)` — see §10). Drop to the low-level
`Bech32` object (`nip19Bech32.bech32`) only for a custom prefix:

```kotlin
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes

val addr = Bech32.encodeBytes("npub", pubKeyBytes, Bech32.Encoding.Bech32)
val bytes = "npub1...".bechToBytes("npub")   // decode + assert the prefix
```

**Base64.** Quartz has no wrapper — use the Kotlin stdlib `kotlin.io.encoding.Base64`
directly, and match the variant the spec wants: NIP-44/NIP-04 payloads use
`Base64.Default` (standard, padded); url-safe contexts use `Base64.UrlSafe`
(configure padding via `.withPadding(...)`).

| Need | Call |
|------|------|
| Now (event `created_at`) | `TimeUtils.now()` (seconds) |
| Relative filter bound | `TimeUtils.oneDayAgo()` / `oneHourAgo()` / … |
| Secure random bytes | `RandomInstance.bytes(n)` |
| Subscription id | `RandomInstance.randomChars()` |
| Raw hash | `sha256(bytes)` |
| Event id / verify | `EventHasher.hashId(...)` / `hashIdCheck(...)` |
| Bech32 custom prefix | `Bech32.encodeBytes(hrp, bytes, enc)` / `s.bechToBytes(hrp)` |
| Base64 | `kotlin.io.encoding.Base64` (`.Default` / `.UrlSafe`) |

---

## 4. Signing Events

### `NostrSignerInternal` (local key, JVM + Android)

```kotlin
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

val keyPair = KeyPair()
val signer = NostrSignerInternal(keyPair)

// Sign any EventTemplate
val template = TextNoteEvent.build("Hello Nostr!")
val signedEvent: TextNoteEvent = signer.sign(template)
```

### `NostrSignerSync` (synchronous, for testing)

```kotlin
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync

val signerSync = NostrSignerSync(keyPair)
val event = signerSync.sign<TextNoteEvent>(
    createdAt = TimeUtils.now(),
    kind = 1,
    tags = emptyArray(),
    content = "Hello!"
)
```

### NostrSigner interface (for custom signers)

```kotlin
abstract class NostrSigner(val pubKey: HexKey) {
    abstract fun isWriteable(): Boolean
    abstract suspend fun <T : Event> sign(createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): T
    abstract suspend fun nip04Encrypt(plaintext: String, toPublicKey: HexKey): String
    abstract suspend fun nip04Decrypt(ciphertext: String, fromPublicKey: HexKey): String
    abstract suspend fun nip44Encrypt(plaintext: String, toPublicKey: HexKey): String
    abstract suspend fun nip44Decrypt(ciphertext: String, fromPublicKey: HexKey): String
    abstract suspend fun deriveKey(nonce: HexKey): HexKey
    abstract fun hasForegroundSupport(): Boolean
    // Convenience: auto-detects NIP-04 vs NIP-44 by ciphertext format
    suspend fun decrypt(encryptedContent: String, fromPublicKey: HexKey): String
}
```

---

## 5. Creating Events

### Using typed event builders (recommended)

```kotlin
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent

// Kind 1 — Text note
val template = TextNoteEvent.build("Hello Nostr!")
val event: TextNoteEvent = signer.sign(template)

// Kind 1 — Reply
val replyTemplate = TextNoteEvent.build(
    note = "Interesting thread!",
    replyingTo = originalEventHintBundle
)

// Kind 7 — Reaction
val reactionTemplate = ReactionEvent.build(
    content = "+",          // "+" = like, "-" = dislike, emoji = custom
    originalNote = targetEvent
)
```

### Using low-level `Event.build` DSL

```kotlin
import com.vitorpamplona.quartz.nip01Core.core.Event

val template = Event.build(
    kind = 1,
    content = "Hello world",
    createdAt = TimeUtils.now()
) {
    // TagArrayBuilder DSL
    add(arrayOf("p", mentionedPubKey))
    add(arrayOf("t", "nostr"))
    add(arrayOf("subject", "Greeting"))
}

val event: Event = signer.sign(template)
```

### TagArrayBuilder DSL methods

```kotlin
// In the DSL lambda:
add(arrayOf("tagname", "value"))          // append
addFirst(arrayOf("tagname", "value"))     // prepend
addUnique(arrayOf("d", "my-slug"))        // replace all tags with same name
addAll(listOf(arrayOf("t", "tag1"), ...)) // bulk add
remove("tagname")                          // remove all with this name
```

## 6. Relay Client Setup (JVM / Android)

The relay client requires an OkHttp WebSocket builder (available on JVM + Android).

### Minimal setup

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import okhttp3.OkHttpClient

// Build the WebSocket factory
val okHttpClient = OkHttpClient.Builder().build()
val wsBuilder = BasicOkHttpWebSocket.Builder { _ -> okHttpClient }

// Create client (manages its own CoroutineScope internally)
val nostrClient = NostrClient(websocketBuilder = wsBuilder)
nostrClient.connect()
```

### With custom OkHttpClient per relay

```kotlin
val wsBuilder = BasicOkHttpWebSocket.Builder { normalizedUrl ->
    if (normalizedUrl.url.contains(".onion")) {
        torEnabledOkHttpClient   // Tor proxy for .onion relays
    } else {
        regularOkHttpClient
    }
}
```

### With custom CoroutineScope

```kotlin
val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
val nostrClient = NostrClient(wsBuilder, scope = appScope)
```

---

## 7. Subscribing to Events

### Normalize relay URLs first

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

// Returns NormalizedRelayUrl (wrapper with validated wss:// URL)
val relayUrl = RelayUrlNormalizer.normalize("wss://relay.damus.io")
val relayUrlOrNull = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io")

// Handles common fixes: https:// → wss://, strips whitespace, etc.
```

### Build a Filter

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

// Fetch a user's notes
val filter = Filter(
    authors = listOf(pubKeyHex),
    kinds = listOf(1),
    limit = 50
)

// Since a timestamp
val filter = Filter(
    kinds = listOf(1, 6),
    since = System.currentTimeMillis() / 1000 - 3600  // last hour
)

// By event tags
val filter = Filter(
    kinds = listOf(7),
    tags = mapOf("e" to listOf(eventId))   // reactions to an event
)

// AND tag filter (NIP-91)
val filter = Filter(
    kinds = listOf(1),
    tagsAll = mapOf(
        "t" to listOf("nostr"),
        "p" to listOf(specificPubKey)
    )
)

// Full-text search (NIP-50)
val filter = Filter(
    kinds = listOf(1),
    search = "bitcoin lightning"
)
```

### Open a subscription

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage

val relay = RelayUrlNormalizer.normalize("wss://relay.damus.io")

val subId = "my-sub-${System.currentTimeMillis()}"
val filtersMap = mapOf(relay to listOf(filter))

nostrClient.openReqSubscription(
    subId = subId,
    filters = filtersMap,
    listener = object : IRequestListener {
        override fun onEvent(subId: String, event: Event, relay: IRelayClient) {
            println("Got event: ${event.id}")
        }
        override fun onEOSE(subId: String, relay: IRelayClient) {
            println("End of stored events from ${relay.url}")
        }
    }
)

// Close when done
nostrClient.close(subId)
```

### Global relay listener

```kotlin
nostrClient.subscribe(object : IRelayClientListener {
    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        when (msg) {
            is EventMessage -> handleEvent(msg.subscriptionId, msg.event)
            is EoseMessage  -> handleEose(msg.subscriptionId)
            else -> {}
        }
    }
    override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
        println("Connected to ${relay.url} in ${pingMillis}ms")
    }
    override fun onDisconnected(relay: IRelayClient) {
        println("Disconnected from ${relay.url}")
    }
    // other callbacks: onConnecting, onSent, onCannotConnect
})
```

---

## 8. Publishing Events

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

val relaySet = setOf(
    RelayUrlNormalizer.normalize("wss://relay.damus.io"),
    RelayUrlNormalizer.normalize("wss://nos.lol"),
)

// Sign the event
val template = TextNoteEvent.build("Hello Nostr!")
val event: TextNoteEvent = signer.sign(template)

// Send to relays (handles retry + reconnect automatically)
nostrClient.send(event, relaySet)
```

---

## 9. Event Serialization

```kotlin
import com.vitorpamplona.quartz.nip01Core.core.Event

// Serialize to JSON string
val json: String = event.toJson()

// Parse from JSON string
val event: Event = Event.fromJson(json)

// Null-safe parse
val event: Event? = Event.fromJsonOrNull(json)

// Specific typed parse (returns base Event, cast if needed)
val textNote = Event.fromJson(json) as? TextNoteEvent
```

---

## 10. Bech32 Encoding / Decoding (NIP-19)

```kotlin
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

// Decode any bech32 entity (plain or nostr:-prefixed).
// uriToRoute() returns Nip19Parser.ParseReturn? — the parsed Entity is in .entity
when (val entity = Nip19Parser.uriToRoute(input)?.entity) {
    is NPub     -> println("pubkey: ${entity.hex}")
    is NNote    -> println("event id: ${entity.hex}")
    is NEvent   -> println("event: ${entity.hex}, relays: ${entity.relay}")
    is NProfile -> println("profile: ${entity.hex}")
    is NAddress -> println("address: ${entity.aTag()}")
    null        -> println("not a valid bech32 entity")
    else        -> {}
}

// Encode: ByteArray extensions from nip19Bech32/ByteArrayExt.kt
val npub = pubkeyBytes.toNpub()   // also toNsec(), toNote(), ...
// TLV entities with relay hints (relays: List<NormalizedRelayUrl>)
val nevent = NEvent.create(eventIdHex, authorHex, kind, relays)
```

---

## 11. Encryption

### NIP-44 (modern, recommended)

```kotlin
// Via signer (preferred)
val encrypted = signer.nip44Encrypt(
    plaintext = "Secret message",
    toPublicKey = recipientPubKeyHex
)
val decrypted = signer.nip44Decrypt(
    ciphertext = encrypted,
    fromPublicKey = senderPubKeyHex
)

// Auto-detect format (NIP-04 or NIP-44)
val plaintext = signer.decrypt(encryptedContent, fromPublicKeyHex)
```

### NIP-04 (legacy, avoid for new code)

```kotlin
val encrypted = signer.nip04Encrypt(plaintext, recipientPubKeyHex)
val decrypted = signer.nip04Decrypt(ciphertext, senderPubKeyHex)
```

---

## 12. Common NIP Event Builders

### NIP-02 — Follow list (kind 3)

```kotlin
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

val template = ContactListEvent.build(
    follows = listOf(
        ContactListEvent.Contact(pubKey = alicePubKey, relayUrl = "wss://relay.damus.io", petname = "alice"),
        ContactListEvent.Contact(pubKey = bobPubKey)
    )
)
```

### NIP-25 — Reaction (kind 7)

```kotlin
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent

val like = ReactionEvent.build("+", targetEvent)
val dislike = ReactionEvent.build("-", targetEvent)
val custom = ReactionEvent.build("🤙", targetEvent)
```

### NIP-57 — Zap request (kind 9734)

```kotlin
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

val template = LnZapRequestEvent.build(
    message = "Great post!",
    relays = listOf("wss://relay.damus.io"),
    target = targetEvent,
    zapType = LnZapRequestEvent.ZapType.PUBLIC
)
val zapRequest: LnZapRequestEvent = signer.sign(template)
```

### NIP-59 — Gift wrap / sealed DM (kind 1059 + 14)

```kotlin
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory

// Creates sealed rumor + gift wrap pair
val (dmEvent, giftWrap) = NIP17Factory.create(
    msg = "Private message",
    fromSigner = senderSigner,
    toUsers = listOf(recipientPubKey),
    relayList = listOf("wss://relay.damus.io")
)
```

### NIP-23 — Long-form article (kind 30023)

```kotlin
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent

val template = LongTextNoteEvent.build(
    body = markdownContent,
    title = "My Article",
    image = "https://example.com/cover.jpg",
    summary = "A brief summary",
    slug = "my-article"   // d-tag
)
```

---

## 13. Platform-Specific Notes

### JVM / Desktop

```kotlin
// jvmMain dependencies needed in consuming project:
// secp256k1-kmp-jni-jvm and lazysodium-java are transitive from quartz
// But you need JNA on the classpath for libsodium:
implementation("net.java.dev.jna:jna:5.18.1")
```

### Android

```kotlin
// androidMain dependencies (transitive from quartz):
// secp256k1-kmp-jni-android, lazysodium-android, jna (aar)
// No extra setup needed beyond the maven dependency.

// For NIP-55 (Android external signer apps):
import com.vitorpamplona.quartz.nip55AndroidSigner.ExternalSignerLauncher
```

### iOS

The library produces an XCFramework named `quartz-kmpKit`.

```bash
# Build XCFramework
./gradlew :quartz:assembleQuartz-kmpKitReleaseXCFramework
# Output: quartz/build/XCFrameworks/release/quartz-kmpKit.xcframework
```

In Xcode: drag & drop the `.xcframework` into your project, then use from Swift via Kotlin/Native interop.

---

## 14. Event Store (SQLite, all platforms)

SQLite-backed storage in `commonMain` (JVM, Android, iOS — uses the bundled
androidx.sqlite driver) with full NIP support (NIP-09, NIP-40, NIP-45, NIP-50,
NIP-62). All operations are `suspend`:

```kotlin
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore

val store = EventStore()  // default DB file "events.db"

// Insert
store.insert(event)

// Query
val events = store.query<Event>(
    Filter(authors = listOf(pubKey), kinds = listOf(1), limit = 50)
)

// Count (NIP-45)
val count = store.count(Filter(kinds = listOf(1)))

// Full-text search (NIP-50)
val results = store.query<Event>(Filter(search = "bitcoin"))
```

---

## 15. NIP-11 Relay Information Document

If you're standing up a relay on Quartz's relay-server code, serve your NIP-11
document with the **type-safe builder** — don't hand-write the JSON string.

**Package:** `com.vitorpamplona.quartz.nip11RelayInfo`

```kotlin
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip11RelayInfo.relayInformation

val info =
    relayInformation {
        name = "sot"
        description = "NIP-50 profile search ranked by Nostr web-of-trust"
        software = "https://github.com/vitorpamplona/sot"
        version = "0.1"
        supports(1, 11, 42, 50)   // ints → spec-compliant [1,11,42,50] in the JSON
    }

val json = info.toJson()          // null/empty fields are omitted
```

Serve it at the relay root, branching on the `Accept` header (Ktor example):

```kotlin
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import io.ktor.http.ContentType

get("/") {
    val accept = call.request.headers[HttpHeaders.Accept].orEmpty()
    if (accept.contains(Nip11RelayInformation.CONTENT_TYPE)) {      // "application/nostr+json"
        call.respondText(json, ContentType.parse(Nip11RelayInformation.CONTENT_TYPE))
    } else {
        call.respondText("Open a WebSocket (NIP-01) or send Accept: ${Nip11RelayInformation.CONTENT_TYPE}")
    }
}
```

### Nested objects, lists, and enforced limits

```kotlin
val info =
    relayInformation {
        name = "Paid Relay"
        supports(1, 11, 42)
        supportsExtensions("nip50-search")   // supported_nip_extensions
        countries("US", "CA")                // relay_countries; also languages(...), tags(...)
        nip50Features("profile_search")      // the `nip50` field

        // limitation { } — camelCase maps to NIP-11 snake_case fields
        limitation {
            maxSubscriptions = 20
            maxFilters = 10
            authRequired = true
        }

        // fees { } — each helper is repeatable
        fees {
            admission(amount = 1000, unit = "msats")
            publication(amount = 100, unit = "msats", kinds = listOf(1, 30023))
        }

        // retention(...) — call once per policy entry
        retention(kinds = listOf(0, 3), count = 1)
    }
```

**Keep advertised limits in sync with enforced ones.** If you build a
`RelayLimits` for the server's policy chain, hand the *same* object to the
builder so what you publish can never drift from what you enforce:

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits

val limits = RelayLimits(maxSubscriptions = 20, maxFilters = 10, maxLimit = 500, authRequired = true)

val info =
    relayInformation {
        name = "My Relay"
        supports(1, 11, 42, 45)
        limitation(limits)        // == limits.toNip11Limitation()
    }
```

To load an operator-supplied doc from disk or a string instead of building it,
use `Nip11RelayInformation.fromJson(json)`.

> `geode` (Quartz's standalone relay) builds its default document exactly this
> way — see `geode/.../RelayInfo.kt`.

---

## 16. Quick Reference

| Task | API | Package |
|------|-----|---------|
| Generate keys | `KeyPair()` | `nip01Core.crypto` |
| Create signer | `NostrSignerInternal(keyPair)` | `nip01Core.signers` |
| Build event | `TextNoteEvent.build(...)` or `Event.build(kind, content) { tags }` | `nip10Notes`, `nip01Core.core` |
| Sign event | `signer.sign(template)` | `nip01Core.signers` |
| Serialize | `event.toJson()` | `nip01Core.core` |
| Parse | `Event.fromJson(json)` | `nip01Core.core` |
| ByteArray → hex | `bytes.toHexKey()` | `nip01Core.core` |
| hex → ByteArray | `hex.hexToByteArray()` / `hex.hexToByteArrayOrNull()` | `nip01Core.core` |
| Validate hex | `Hex.isHex(s)` / `Hex.isHex64(s)` / `hex.isValid()` | `utils`, `nip01Core.core` |
| Now (seconds) | `TimeUtils.now()` | `utils` |
| Relative time | `TimeUtils.oneDayAgo()` / `oneHourAgo()` | `utils` |
| Secure random | `RandomInstance.bytes(n)` / `randomChars()` | `utils` |
| Hash / event id | `sha256(bytes)` / `EventHasher.hashId(...)` | `utils.sha256`, `nip01Core.crypto` |
| Normalize relay URL | `RelayUrlNormalizer.normalize("wss://...")` | `nip01Core.relay.normalizer` |
| Setup relay client | `NostrClient(BasicOkHttpWebSocket.Builder { okhttp })` | `nip01Core.relay.client` |
| Subscribe | `client.openReqSubscription(subId, mapOf(relay to filters), listener)` | `nip01Core.relay.client` |
| Publish | `client.send(event, setOf(relayUrl))` | `nip01Core.relay.client` |
| NIP-44 encrypt | `signer.nip44Encrypt(text, recipientPubKey)` | `nip01Core.signers` |
| Bech32 decode | `Nip19Parser.uriToRoute("npub1...")` | `nip19Bech32` |
| Bech32 encode | `Nip19Bech32.createNPub(pubKeyHex)` | `nip19Bech32` |
| Build NIP-11 doc | `relayInformation { name = ...; supports(1, 11) }` | `nip11RelayInfo` |
| Serialize NIP-11 doc | `info.toJson()` (media type `Nip11RelayInformation.CONTENT_TYPE`) | `nip11RelayInfo` |

## Common Event Kinds

| Kind | Event Type | NIP | Quartz class |
|------|-----------|-----|-------------|
| 0 | User metadata | 01 | `MetadataEvent` |
| 1 | Text note | 10 | `TextNoteEvent` |
| 3 | Follow list | 02 | `ContactListEvent` |
| 4 | Legacy DM | 04 | `PrivateDmEvent` |
| 5 | Deletion | 09 | `DeletionEvent` |
| 6 | Repost | 18 | `RepostEvent` |
| 7 | Reaction | 25 | `ReactionEvent` |
| 14 | Chat message (sealed) | 17 | `NIP17GroupMessage` |
| 1059 | Gift wrap | 59 | `GiftWrapEvent` |
| 9734 | Zap request | 57 | `LnZapRequestEvent` |
| 9735 | Zap receipt | 57 | `LnZapEvent` |
| 10002 | Relay list | 65 | `AdvertisedRelayListEvent` |
| 30023 | Long-form content | 23 | `LongTextNoteEvent` |

## Related Skills

- **nostr-expert** — Internal Quartz patterns for Amethyst development
- **kotlin-multiplatform** — KMP source sets, expect/actual patterns
- **kotlin-coroutines** — Flow patterns for relay event streams
