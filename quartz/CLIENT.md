# Building a Nostr Client with Quartz

This guide walks you through building a Nostr client using Quartz's `NostrClient` and [Ktor](https://ktor.io/) as the WebSocket transport layer. On JVM and Android you can skip the transport and use the bundled OkHttp one: `NostrClient(BasicOkHttpWebSocket.Builder { okHttpClient })` (`nip01Core.relay.sockets.okhttp`).

Using a coding agent (Claude Code, Codex, Cursor, …)? Install the [Quartz agent skill](agent-skill/README.md) so it reaches for Quartz's helpers instead of re-implementing them.

## Overview

Quartz provides a complete, transport-agnostic client engine:

| Component | Class | Role |
|-----------|-------|------|
| **NostrClient** | `com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient` | Manages relay connections, subscriptions, and event publishing |
| **WebsocketBuilder** | `com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder` | Factory interface for pluggable WebSocket transports |
| **Filter** | `com.vitorpamplona.quartz.nip01Core.relay.filters.Filter` | NIP-01 subscription filters |
| **KeyPair** | `com.vitorpamplona.quartz.nip01Core.crypto.KeyPair` | Schnorr key pair for signing events |
| **NostrSignerInternal** | `com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal` | Signs events with a local private key |

`NostrClient` doesn't know about HTTP or WebSockets. You provide a `WebsocketBuilder` that creates transport connections, and the client handles relay pooling, subscription management, reconnection, and event delivery.

## Quick Start

### 1. Add Dependencies

In your `build.gradle.kts`:

```kotlin
dependencies {
    // Ktor WebSocket client
    implementation("io.ktor:ktor-client-core:3.1.1")
    implementation("io.ktor:ktor-client-cio:3.1.1")
    implementation("io.ktor:ktor-client-websockets:3.1.1")

    // Quartz from Maven Central (check the latest version there)
    implementation("com.vitorpamplona.quartz:quartz:1.19.0")
}
```

### 2. Implement the Ktor WebSocket Transport

Quartz uses two interfaces for WebSocket connectivity:

```kotlin
// WebSocket — a single connection to a relay
interface WebSocket {
    fun needsReconnect(): Boolean
    fun connect()
    fun disconnect()
    fun send(msg: String): Boolean
}

// WebsocketBuilder — factory that creates WebSocket instances
interface WebsocketBuilder {
    fun build(url: NormalizedRelayUrl, out: WebSocketListener): WebSocket
}

// WebSocketListener — callbacks from the transport layer
interface WebSocketListener {
    fun onOpen(pingMillis: Int, compression: Boolean)
    fun onMessage(text: String)          // must deliver messages in order
    fun onClosed(code: Int, reason: String)
    fun onFailure(t: Throwable, code: Int?, response: String?)
}
```

Here's a complete Ktor implementation:

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

class KtorWebSocket(
    private val url: NormalizedRelayUrl,
    private val client: HttpClient,
    private val out: WebSocketListener,
) : WebSocket {
    private var session: DefaultClientWebSocketSession? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun needsReconnect() = session == null

    override fun connect() {
        job = scope.launch {
            try {
                client.webSocket(url.url) {
                    session = this
                    out.onOpen(0, false)

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                out.onMessage(frame.readText())
                            }
                        }
                    } finally {
                        val reason = closeReason.await()
                        session = null
                        out.onClosed(
                            reason?.code?.toInt() ?: 1000,
                            reason?.message ?: "Connection closed",
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session = null
                out.onFailure(e, null, e.message)
            }
        }
    }

    override fun disconnect() {
        job?.cancel()
        job = null
        session = null
    }

    override fun send(msg: String): Boolean {
        val currentSession = session ?: return false
        return try {
            runBlocking { currentSession.send(Frame.Text(msg)) }
            true
        } catch (e: Exception) {
            false
        }
    }

    class Builder(
        private val client: HttpClient,
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = KtorWebSocket(url, client, out)
    }
}
```

### 3. Subscribe to Events (Flow API)

The simplest way to receive events — `subscribeAsFlow` returns a `Flow<List<Event>>` that accumulates events as they arrive:

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    // 1. Create the Ktor HTTP client with WebSocket support
    val httpClient = HttpClient(CIO) {
        install(WebSockets)
    }

    // 2. Create the NostrClient
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val client = NostrClient(KtorWebSocket.Builder(httpClient), scope)

    // 3. Subscribe to text notes (kind 1) from a relay
    val flow = client.subscribeAsFlow(
        relay = "wss://nos.lol",
        filter = Filter(kinds = listOf(1), limit = 20),
    )

    // 4. Collect events as they arrive
    val job = launch {
        flow.collect { events ->
            println("Got ${events.size} events so far")
            events.forEach { event ->
                println("  [${event.pubKey.take(8)}...] ${event.content.take(80)}")
            }
        }
    }

    // Let it run for 10 seconds
    delay(10_000)
    job.cancel()

    // 5. Clean up
    client.disconnect()
    scope.cancel()
    httpClient.close()
}
```

### 4. Subscribe to Events (Callback API)

For more control, use the manual subscription API with `SubscriptionListener`:

```kotlin
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.*

fun main() = runBlocking {
    val httpClient = HttpClient(CIO) { install(WebSockets) }
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val client = NostrClient(KtorWebSocket.Builder(httpClient), scope)

    // Define a listener for subscription events
    val listener = object : SubscriptionListener {
        override fun onEvent(
            event: Event,
            isLive: Boolean,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            val label = if (isLive) "LIVE" else "STORED"
            println("[$label] ${event.pubKey.take(8)}...: ${event.content.take(80)}")
        }

        override fun onEose(relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
            println("--- End of stored events from $relay ---")
        }
    }

    // Build filters per relay
    val filters = mapOf(
        RelayUrlNormalizer.normalize("wss://nos.lol") to listOf(
            Filter(kinds = listOf(1), limit = 50),
        ),
    )

    // Subscribe
    client.subscribe("my-feed", filters, listener)

    delay(15_000)

    // Unsubscribe and clean up
    client.unsubscribe("my-feed")
    client.disconnect()
    scope.cancel()
    httpClient.close()
}
```

### 5. Publish Events

Create a key pair, sign an event, and publish it to relays:

```kotlin
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.*

fun main() = runBlocking {
    val httpClient = HttpClient(CIO) { install(WebSockets) }
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val client = NostrClient(KtorWebSocket.Builder(httpClient), scope)

    // 1. Create a key pair (generates random keys)
    val signer = NostrSignerInternal(KeyPair())
    println("Public key: ${signer.pubKey}")

    // 2. Build and sign a text note (kind 1)
    val event = signer.sign(TextNoteEvent.build("Hello from Quartz!"))
    println("Event ID: ${event.id}")

    // 3. Publish and wait for relay confirmation
    val accepted = client.publishAndConfirm(
        event = event,
        relayList = setOf(
            "wss://nos.lol".normalizeRelayUrl(),
            "wss://relay.damus.io".normalizeRelayUrl(),
        ),
    )

    println("Published: $accepted")

    // 4. Clean up
    client.disconnect()
    scope.cancel()
    httpClient.close()
}
```

For detailed per-relay results, use `publishAndConfirmDetailed`:

```kotlin
val results = client.publishAndConfirmDetailed(event, relayList)
results.forEach { (relay, success) ->
    println("  $relay: ${if (success) "accepted" else "rejected"}")
}
```

#### Rate limits

A relay may refuse an `EVENT` or a `REQ` as `rate-limited:` and say how long to back off, in
the optional trailing `params` of NIP-01's `OK` / `CLOSED` (milliseconds, e.g.
`["OK", id, false, "rate-limited: slow down", "1000"]`). `NostrClient` honors it: each relay
connection (`BasicRelayClient`) has a `RateLimitGate` that holds every command of the refused
type (`REQ`, `EVENT`, or `COUNT`) until the back-off passes, whoever sends it — new
subscriptions and publishes, a subscription's update after its `EOSE`, the replay after a
reconnect. The client hands the refused REQ or EVENT straight back, and the gate queues it
first. A held REQ goes out with its subscription's filters as they are when it is released, so
changing a subscription during a back-off sends one REQ, not a stale one and then the new one. Then the held commands go out one at a time: each one the relay answers without
throttling (`OK` true, `EOSE`, a `COUNT` result) releases the next at once, and one it refuses
again waits out its new back-off (plus a tenth). That drains a sliding window, which
states its whole period and then takes many, in one round trip per command, and a token bucket
at its refill rate. A `rate-limited:` refusal without a back-off is left parked until the next
reconnect or filter change. The pool keeps each relay's gate by URL, so a back-off outlives the
relay leaving the pool and being dialed again. A `RelayPool` built without a `scope` (or a
`BasicRelayClient` without a `RateLimitGate`) honors no back-off; `NostrClient` always passes
its own.

`publishAndCollectResults` waits for that retry when it fits inside the timeout; otherwise
the relay's refusal is reported with `PublishResult.retryAfterMs` set. `fetchAllPages` keeps
waiting on the same page while the pool re-sends it. A `SubscriptionListener` reads the raw
value by overriding the `onClosed` overload that takes `params` (forward the three-argument
one to it too), and `RetryAfter.parse(message, params)` turns it into milliseconds.

On the relay side, `RateLimitPolicy` refuses with exactly the wait a client's token bucket
needs: build one `RateLimiter` per command type once, and a `RateLimitPolicy(events, reqs)`
per connection around them. It counts per `RequestContext.clientAddress` (pass it to
`RelayServerBase.connect(sink, clientAddress)`), or per connection when there is none. A
custom policy can state a back-off itself with `PolicyResult.Rejected.rateLimited(message, retryAfterMs)`.

### 6. One-shot Reads (fetch, page, count)

Most code doesn't need a live subscription: it needs "the events matching this
filter" and then to move on. Quartz ships these as **suspending extension functions
on `INostrClient`** in `com.vitorpamplona.quartz.nip01Core.relay.client.accessories`.
Because they are extensions, they don't appear in `NostrClient`'s method completion —
import them by name:

```kotlin
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl

suspend fun readExamples(client: NostrClient, pubkey: String) {
    // Every event matching the filter, deduped by id, until EOSE.
    val notes = client.fetchAll("wss://nos.lol", Filter(kinds = listOf(1), authors = listOf(pubkey), limit = 50))

    // The first match only; null when nothing arrives.
    val profile = client.fetchFirst("wss://nos.lol", Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubkey)))

    // More than the relay's per-REQ cap (often ~500): walks a created_at cursor page by page.
    val result =
        client.fetchAllPages("wss://nos.lol", listOf(Filter(kinds = listOf(1), authors = listOf(pubkey)))) { event ->
            println(event.id)
        }
    if (result.end != PagedFetchResult.End.DRAINED) println("Stopped early: ${result.end} ${result.message}")

    // NIP-45 COUNT; null when the relay doesn't support it.
    val followers = client.count("wss://nos.lol".normalizeRelayUrl(), Filter(kinds = listOf(3), tags = mapOf("p" to listOf(pubkey))))
    println("${notes.size} notes, profile: ${profile != null}, followers: ${followers?.count}")
}
```

Things worth knowing:

- Every wait is an **idle window** (`idleTimeoutMs`), reset by real progress, never a
  wall-clock deadline: a relay streaming a large backlog is never cut off mid-delivery.
  Wrap a call in `withTimeoutOrNull` when you need a hard bound.
- Only `PagedFetchResult.End.DRAINED` proves the relay has nothing more; the other
  endings (`IDLE`, `CLOSED`, `AUTH_REQUIRED`, …) say why the walk stopped, and
  `message` carries the relay's own words.
- A relay that requires NIP-42 AUTH is waited on while a `RelayAuthenticator` attached
  to the client answers the challenge, instead of being reported as empty.
- There are more: `fetchAllWithHooks` (verify/store each event as it arrives, with
  per-relay results), multi-relay paging (`fetchAllPagesFromPool`), `countMerged`, and
  NIP-77 negentropy sync (`negentropySync`, `negentropySyncOrFetch`). The full catalog is
  the [accessories README](src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/client/accessories/README.md).

## How It Works

### Architecture

```
┌──────────────────────────────────────────────┐
│               Your Application               │
│                                              │
│   subscribeAsFlow() / subscribe() / publish()│
└─────────────────────┬────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────┐
│              NostrClient                     │
│                                              │
│   PoolRequests    (active subscriptions)      │
│   PoolCounts      (count queries)            │
│   PoolEventOutbox (pending publishes)        │
└─────────────────────┬────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────┐
│              RelayPool                       │
│                                              │
│   Manages BasicRelayClient per relay URL     │
│   Auto-connects/disconnects as needed        │
│   Tracks connected/available relay state     │
└─────────────────────┬────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────┐
│          BasicRelayClient (per relay)        │
│                                              │
│   Connection lifecycle + reconnection        │
│   NIP-01 message parsing                     │
│   Exponential backoff on failures            │
└─────────────────────┬────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────┐
│     WebsocketBuilder → WebSocket             │
│                                              │
│   KtorWebSocket (this guide)                 │
│   BasicOkHttpWebSocket (built-in for JVM)    │
└──────────────────────────────────────────────┘
```

### Connection Lifecycle

1. You call `subscribe()` or `publish()` with relay URLs
2. `NostrClient` tells `RelayPool` which relays are needed
3. `RelayPool` creates a `BasicRelayClient` for each new relay
4. `BasicRelayClient` uses `WebsocketBuilder` to create and open a `WebSocket`
5. On connection, all pending subscriptions and events are synced automatically
6. On disconnection, exponential backoff retries the connection
7. On reconnection, filters are re-sent and events re-delivered

`NostrClient` starts active by default and connects on demand — there's no need to call `client.connect()` at startup. That method only matters for resuming after a prior `disconnect()` (e.g., a user-toggled offline mode). Conversely, `client.disconnect()` is what you want when the app is going to background or being shut down: it tears down every socket and stops the auto-reconnect loop.

### Subscription Flow

```
client.subscribe("my-feed", filters, listener)
        │
        ▼
NostrClient stores filters in PoolRequests
        │
        ▼
RelayPool connects to needed relays
        │
        ▼
BasicRelayClient sends ["REQ", "my-feed", <filter>...]
        │
        ▼
Relay responds with:
  ["EVENT", "my-feed", <event>]  →  listener.onEvent(event, isLive=false)
  ["EVENT", "my-feed", <event>]  →  listener.onEvent(event, isLive=false)
  ["EOSE", "my-feed"]           →  listener.onEose(relay)
  ["EVENT", "my-feed", <event>]  →  listener.onEvent(event, isLive=true)   ← new events
```

### NIP-01 Commands

| Direction | Command | Description |
|-----------|---------|-------------|
| Client → Relay | `["REQ", subId, filter...]` | Open a subscription |
| Client → Relay | `["CLOSE", subId]` | Close a subscription |
| Client → Relay | `["EVENT", event]` | Publish an event |
| Relay → Client | `["EVENT", subId, event]` | Deliver a matching event |
| Relay → Client | `["EOSE", subId]` | End of stored events |
| Relay → Client | `["OK", eventId, success, message]` | Publish acknowledgement |
| Relay → Client | `["NOTICE", message]` | Relay notice |

## Filters

Filters define what events a subscription matches. All fields are optional — omitted fields match everything.

```kotlin
// Latest 20 text notes
Filter(kinds = listOf(1), limit = 20)

// Metadata for specific authors
Filter(
    kinds = listOf(0),
    authors = listOf(
        "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245",
    ),
)

// Events since a timestamp
Filter(kinds = listOf(1), since = 1700000000)

// Events tagged with a specific pubkey
Filter(
    kinds = listOf(1),
    tags = mapOf("p" to listOf("32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245")),
)

// Full-text search (NIP-50, relay must support it)
Filter(kinds = listOf(1), search = "bitcoin", limit = 10)
```

### Filter Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `ids` | `List<HexKey>?` | Match specific event IDs (64-char hex) |
| `authors` | `List<HexKey>?` | Match specific author pubkeys (64-char hex) |
| `kinds` | `List<Int>?` | Match event kinds (0=metadata, 1=text note, 3=contacts, etc.) |
| `tags` | `Map<String, List<String>>?` | Match events with any of the listed tag values |
| `since` | `Long?` | Events with `created_at` >= this Unix timestamp |
| `until` | `Long?` | Events with `created_at` <= this Unix timestamp |
| `limit` | `Int?` | Maximum number of events to return |
| `search` | `String?` | Full-text search (NIP-50) |

## Key Management

```kotlin
// Generate a new random key pair
val keyPair = KeyPair()

// Import an existing private key (32 bytes)
val keyPair = KeyPair(privKey = hexToByteArray("your-64-char-hex-private-key"))

// Read-only (public key only, cannot sign)
val keyPair = KeyPair(pubKey = hexToByteArray("your-64-char-hex-public-key"))

// Create a signer for signing events
val signer = NostrSignerInternal(keyPair)
```

## Event Kinds

Common event kinds you'll work with:

| Kind | NIP | Class | Description |
|------|-----|-------|-------------|
| 0 | NIP-01 | `MetadataEvent` | User metadata (name, picture, about) |
| 1 | NIP-01 | `TextNoteEvent` | Text note (short post) |
| 3 | NIP-02 | `ContactListEvent` | Contact list / follow list |
| 4 | NIP-04 | `EncryptedDmEvent` | Encrypted direct message |
| 7 | NIP-25 | `ReactionEvent` | Reaction (like, emoji) |
| 30023 | NIP-23 | `LongFormContentEvent` | Long-form content (articles) |

## Multi-Relay Subscriptions

Subscribe to different filters on different relays:

```kotlin
val filters = mapOf(
    RelayUrlNormalizer.normalize("wss://nos.lol") to listOf(
        Filter(kinds = listOf(1), limit = 50),
    ),
    RelayUrlNormalizer.normalize("wss://relay.damus.io") to listOf(
        Filter(kinds = listOf(0, 3), authors = listOf(myPubKey)),
    ),
)

client.subscribe("multi-relay-feed", filters, listener)
```

## Using OkHttp Instead of Ktor

Quartz ships with a built-in OkHttp WebSocket implementation for JVM/Android:

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import okhttp3.OkHttpClient

val httpClient = OkHttpClient.Builder()
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

val socketBuilder = BasicOkHttpWebSocket.Builder { url -> httpClient }
val client = NostrClient(socketBuilder, scope)
```

## Full Example: Simple Feed Reader

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take

fun main() = runBlocking {
    val httpClient = HttpClient(CIO) { install(WebSockets) }
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val client = NostrClient(KtorWebSocket.Builder(httpClient), scope)

    println("Connecting to relay...")

    client.subscribeAsFlow(
        relay = "wss://nos.lol",
        filter = Filter(kinds = listOf(1), limit = 10),
    ).take(1).collect { events ->
        println("\n=== Latest ${events.size} text notes ===\n")
        events.forEach { event ->
            println("Author:  ${event.pubKey.take(16)}...")
            println("Content: ${event.content.take(120)}")
            println("---")
        }
    }

    client.disconnect()
    scope.cancel()
    httpClient.close()
}
```

## Key Source Files

All client infrastructure lives in the `quartz` module:

```
quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/
├── relay/client/
│   ├── NostrClient.kt               # Main client (manages relay pool + subscriptions)
│   ├── INostrClient.kt              # Client interface
│   ├── reqs/
│   │   ├── SubscriptionListener.kt   # Callback interface for subscriptions
│   │   └── NostrClientSubscribeAsFlowExt.kt  # Flow-based subscription API
│   └── accessories/
│       └── NostrClientPublishExt.kt  # publishAndConfirm / publishAndConfirmDetailed
├── relay/sockets/
│   ├── WebSocket.kt                  # Transport interface
│   ├── WebSocketListener.kt         # Transport callback interface
│   └── WebsocketBuilder.kt          # Transport factory interface
├── relay/filters/
│   └── Filter.kt                    # NIP-01 subscription filters
├── crypto/
│   ├── KeyPair.kt                   # Schnorr key pair
│   └── Nip01Crypto.kt              # Low-level crypto operations
└── signers/
    ├── NostrSigner.kt               # Abstract signer
    └── NostrSignerInternal.kt       # Signs with local private key
```
