# Building a Nostr Relay with Quartz

This guide walks you through building a fully functional Nostr relay using Quartz's `NostrServer`, the SQLite `EventStore`, and [Ktor](https://ktor.io/) as the WebSocket transport layer.

## Overview

Quartz provides a complete, transport-agnostic relay engine:

| Component | Class | Role |
|-----------|-------|------|
| **NostrServer** | `com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer` | Coordinates connections, sessions, and event routing |
| **EventStore** | `com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore` | SQLite-backed persistent event storage |
| **RelaySession** | `com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession` | Manages a single client connection and its subscriptions |
| **IRelayPolicy** | `com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy` | Pluggable access control and validation |

`NostrServer` doesn't know about HTTP or WebSockets. You provide a `send` callback per connection, and it gives you a `RelaySession` that accepts raw JSON strings. This makes it trivial to plug into Ktor (or any other transport).

Both `NostrServer` and `EventStore` implement `AutoCloseable`, so you can use Kotlin's `.use {}` for automatic resource cleanup.

## Quick Start

### 1. Add Dependencies

In your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-core:3.1.1")
    implementation("io.ktor:ktor-server-netty:3.1.1")
    implementation("io.ktor:ktor-server-websockets:3.1.1")

    // Quartz (use your local module or published artifact)
    implementation(project(":quartz"))
}
```

### 2. Create the Relay Server

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun main() {
    // 1. Create the event store (SQLite, persisted to disk)
    val store = EventStore(
        dbName = "relay-events.db",
    )

    // 2. Create the NostrServer with the default VerifyPolicy
    val server = NostrServer(store)

    // 3. Start Ktor with WebSocket support
    embeddedServer(Netty, port = 7777) {
        install(WebSockets)

        routing {
            webSocket("/") {
                // 4. Serve this WebSocket connection (auto-cleanup on disconnect)
                server.serve(
                    send = { json -> launch { send(Frame.Text(json)) } },
                ) { session ->
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            session.receive(frame.readText())
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}
```

That's it. You now have a NIP-01 compliant relay running on `ws://localhost:7777`.

The `serve` method creates a `RelaySession`, passes it to your block, and automatically closes the session when the block completes (normally or via exception). No `try`/`finally` needed.

> **Tip:** If you need direct control over the session lifecycle, use `connect()` with `.use {}`:
> ```kotlin
> server.connect { json -> launch { send(Frame.Text(json)) } }.use { session ->
>     for (frame in incoming) {
>         if (frame is Frame.Text) session.receive(frame.readText())
>     }
> }
> ```

## How It Works

### Connection Lifecycle

```
Client connects via WebSocket
        │
        ▼
server.serve(send) { session -> ... }
        │
        ▼
   RelaySession created
   (policy.onConnect called, e.g. AUTH challenge sent)
        │
        ▼
┌───────────────────────────────┐
│  For each incoming text frame │
│  session.receive(jsonString)  │──► Parses NIP-01 command
│                               │──► Validates via policy
│                               │──► Dispatches to handler
└───────────────────────────────┘
        │
        ▼
Client disconnects → session auto-closed
        │
        ▼
   All subscriptions cancelled
```

### Supported NIP-01 Commands

| Client Command | Handler | Description |
|----------------|---------|-------------|
| `["EVENT", <event>]` | `handleEvent` | Publishes an event. Policy validates, store persists, `OK` response sent. |
| `["REQ", <sub_id>, <filter>...]` | `handleReq` | Opens a subscription. Returns matching historical events, `EOSE`, then streams live matches. |
| `["CLOSE", <sub_id>]` | `handleClose` | Cancels a subscription. |
| `["COUNT", <query_id>, <filter>...]` | `handleCount` | Returns the count of matching events (NIP-45). |
| `["AUTH", <event>]` | `handleAuth` | Authenticates the client (NIP-42). |

### Live Subscriptions

When a client sends `REQ`, the relay:
1. Queries the `EventStore` for all matching historical events
2. Sends each as an `EVENT` message
3. Sends `EOSE` (End of Stored Events)
4. Keeps the subscription open - any new event inserted by *any* client that matches the filter is automatically pushed

This is handled by `LiveEventStore`, which wraps the store with a `SharedFlow` that broadcasts new events to all active subscriptions.

## Event Store

### SQLite (Persistent)

```kotlin
val store = EventStore(
    dbName = "relay-events.db",
)
```

The SQLite store uses `androidx.sqlite` with the bundled driver (no native SQLite dependency needed). It features:

- **WAL journal mode** for concurrent reads/writes
- **32 MB memory cache** for fast queries
- **Modular architecture** with pluggable processing modules:

| Module | NIP | Purpose |
|--------|-----|---------|
| `SeedModule` | NIP-01 | Core event storage and retrieval |
| `EventIndexesModule` | NIP-01 | Indexes for efficient filtering |
| `ReplaceableModule` | NIP-16 | Replaces older versions of replaceable events |
| `AddressableModule` | NIP-33 | Handles parameterized replaceable events |
| `EphemeralModule` | NIP-01 | Rejects ephemeral events from persistence |
| `DeletionRequestModule` | NIP-09 | Processes deletion requests |
| `ExpirationModule` | NIP-40 | Handles event expiration timestamps |
| `RightToVanishModule` | — | Cleans up ephemeral data |
| `FullTextSearchModule` | NIP-50 | Full-text search on event content |

### In-Memory (Testing)

Pass `null` as the database name for a purely in-memory store (no disk I/O):

```kotlin
val store = EventStore(null)
```

### Indexing Strategy

Control which indexes are created via `IndexingStrategy`:

```kotlin
val store = EventStore(
    dbName = "relay-events.db",
    indexStrategy = DefaultIndexingStrategy(
        // Enable if you receive many filter-by-time-only queries
        indexEventsByCreatedAtAlone = false,
        // Enable if you receive many tag-only queries without kind
        indexTagsByCreatedAtAlone = false,
        // Enable for queries combining tags + kind + author
        indexTagsWithKindAndPubkey = false,
        // Enable for spec-compliant ordering when created_at collides
        useAndIndexIdOnOrderBy = false,
    ),
)
```

By default, all single-letter tags with values are indexed (e.g., `#e`, `#p`, `#t`). Override `shouldIndex(kind, tag)` for custom behavior.

> **Tip:** More indexes = faster queries but larger database. Only enable what your relay's query patterns actually need.

## Policies

Policies control what clients can do. They validate commands and can rewrite filters before execution.

### Built-in Policies

#### `VerifyPolicy` (default)

Verifies event signatures and IDs. Rejects malformed events. Allows all `REQ` and `COUNT` commands.

```kotlin
val server = NostrServer(store) // Uses VerifyPolicy by default
```

#### `EmptyPolicy`

Accepts everything without any validation. Useful for testing or trusted environments.

```kotlin
val server = NostrServer(store, policyBuilder = { EmptyPolicy })
```

#### `FullAuthPolicy`

Requires NIP-42 authentication before accepting any `EVENT`, `REQ`, or `COUNT` commands. Sends an `AUTH` challenge on connect.

```kotlin
val server = NostrServer(
    store = store,
    policyBuilder = {
        FullAuthPolicy(relay = "wss://myrelay.example.com/".normalizeRelayUrl()!!)
    },
)
```

Validates:
- Challenge string matches
- Relay URL matches
- Timestamp is within 10 minutes
- Event signature is valid

### Composing Policies

Chain multiple policies using the `+` operator or `PolicyStack`. All must approve; the first rejection wins. Policies run in order and can rewrite commands for downstream policies.

```kotlin
// Using the + operator
val server = NostrServer(
    store = store,
    policyBuilder = {
        VerifyPolicy + FullAuthPolicy(relay = "wss://myrelay.example.com/".normalizeRelayUrl()!!)
    },
)

// Or using PolicyStack for three or more policies
val server = NostrServer(
    store = store,
    policyBuilder = {
        PolicyStack(
            VerifyPolicy,
            FullAuthPolicy(relay = "wss://myrelay.example.com/".normalizeRelayUrl()!!),
            MyCustomRateLimitPolicy(),
        )
    },
)
```

### Writing a Custom Policy

Implement `IRelayPolicy`:

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult

class KindWhitelistPolicy(
    private val allowedKinds: Set<Int>,
) : IRelayPolicy {

    override fun onConnect(send: (Message) -> Unit) { }

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> =
        if (cmd.event.kind in allowedKinds) {
            PolicyResult.Accepted(cmd)
        } else {
            PolicyResult.Rejected("blocked: kind ${cmd.event.kind} not allowed")
        }

    override fun accept(cmd: ReqCmd) = PolicyResult.Accepted(cmd)
    override fun accept(cmd: CountCmd) = PolicyResult.Accepted(cmd)
    override fun accept(cmd: AuthCmd) = PolicyResult.Accepted(cmd)
}
```

Use it:

```kotlin
val server = NostrServer(
    store = store,
    policyBuilder = {
        VerifyPolicy + KindWhitelistPolicy(allowedKinds = setOf(0, 1, 3, 7, 30023))
    },
)
```

**Policy interface methods:**

| Method | When Called | Return |
|--------|------------|--------|
| `onConnect(send)` | New client connects | Send welcome messages (e.g., AUTH challenge) |
| `accept(EventCmd)` | Client publishes an event | `Accepted` or `Rejected("reason")` |
| `accept(ReqCmd)` | Client opens a subscription | `Accepted` (may rewrite filters) or `Rejected` |
| `accept(CountCmd)` | Client requests a count | `Accepted` or `Rejected` |
| `accept(AuthCmd)` | Client authenticates | `Accepted` or `Rejected` |
| `canSendToSession(event)` | Live event about to be forwarded | `true` to deliver, `false` to suppress |

## Full Example: Production-Ready Relay

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch

fun main() {
    val store = EventStore(
        dbName = "relay-events.db",
        relay = "wss://myrelay.example.com/".normalizeRelayUrl(),
        indexStrategy = DefaultIndexingStrategy(
            indexEventsByCreatedAtAlone = true,
        ),
    )

    // NostrServer implements AutoCloseable — .use {} ensures clean shutdown
    NostrServer(
        store = store,
        policyBuilder = { VerifyPolicy },
    ).use { server ->
        embeddedServer(Netty, port = 7777) {
            install(WebSockets) {
                pingPeriodMillis = 30_000
                timeoutMillis = 60_000
                maxFrameSize = Long.MAX_VALUE
            }

            routing {
                webSocket("/") {
                    server.serve(
                        send = { json -> launch { send(Frame.Text(json)) } },
                    ) { session ->
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                session.receive(frame.readText())
                            }
                        }
                    }
                }
            }
        }.start(wait = true)
    }
}
```

## Architecture Diagram

```
┌─────────────────────────────────────────────────┐
│                   Ktor Server                    │
│                                                  │
│   WebSocket("/")                                 │
│       │                                          │
│       ▼                                          │
│   ┌────────────────────────────────────────┐     │
│   │            NostrServer                 │     │
│   │                                        │     │
│   │   serve(send) { session -> ... }       │     │
│   │                      │                 │     │
│   │                      ├─ IRelayPolicy   │     │
│   │                      │  (validate)     │     │
│   │                      │                 │     │
│   │                      ├─ LiveEventStore │     │
│   │                      │  (query + live) │     │
│   │                      │                 │     │
│   │                      └─ Subscriptions  │     │
│   │                         (per client)   │     │
│   └────────────────────────────────────────┘     │
│                      │                           │
│                      ▼                           │
│   ┌────────────────────────────────────────┐     │
│   │       EventStore (SQLite)              │     │
│   │                                        │     │
│   │   insert / query / count / delete      │     │
│   │                                        │     │
│   │   Modules:                             │     │
│   │     Seed ─ Replaceable ─ Addressable   │     │
│   │     Deletion ─ Expiration ─ FTS        │     │
│   └────────────────────────────────────────┘     │
└─────────────────────────────────────────────────┘
```

## Testing

Use the in-memory store and `UnconfinedTestDispatcher` for deterministic tests:

```kotlin
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MyRelayTest {

    @Test
    fun clientCanPublishAndSubscribe() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        NostrServer(
            store = EventStore(null),
            policyBuilder = { EmptyPolicy },
            parentContext = dispatcher,
        ).use { server ->
            // Collect messages sent to this client
            val messages = mutableListOf<String>()
            val session = server.connect { messages.add(it) }

            // Publish an event
            session.receive("""["EVENT",{"id":"${"0".repeat(64)}","pubkey":"${"a".repeat(64)}","created_at":1000,"kind":1,"tags":[],"content":"hello","sig":"${"b".repeat(128)}"}]""")

            // Verify OK response
            assertTrue(messages.any { it.contains("OK") })

            // Subscribe to kind 1
            session.receive("""["REQ","sub1",{"kinds":[1]}]""")

            // Verify we get the event back + EOSE
            assertTrue(messages.any { it.contains("EVENT") })
            assertTrue(messages.any { it.contains("EOSE") })
        }
    }
}
```

## NIP Support

The relay engine and SQLite store support the following NIPs out of the box:

| NIP | Feature | Component |
|-----|---------|-----------|
| NIP-01 | Basic protocol (EVENT, REQ, CLOSE, EOSE, OK, NOTICE) | `NostrServer`, `RelaySession` |
| NIP-09 | Event deletion | `DeletionRequestModule` |
| NIP-16 | Replaceable events | `ReplaceableModule` |
| NIP-33 | Parameterized replaceable events | `AddressableModule` |
| NIP-40 | Expiration timestamp | `ExpirationModule` |
| NIP-42 | Authentication | `FullAuthPolicy` |
| NIP-45 | Event counts | `RelaySession.handleCount` |
| NIP-50 | Search | `FullTextSearchModule` |

## Key Source Files

All relay infrastructure lives in the `quartz` module:

```
quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/
├── relay/server/
│   ├── NostrServer.kt          # Main entry point
│   ├── RelaySession.kt         # Per-connection handler
│   ├── LiveEventStore.kt       # Reactive event streaming
│   ├── IRelayPolicy.kt         # Policy interface + PolicyResult
│   └── policies/
│       ├── EmptyPolicy.kt      # Accept everything
│       ├── VerifyPolicy.kt     # Signature verification (default)
│       ├── FullAuthPolicy.kt   # NIP-42 auth required
│       └── PolicyStack.kt      # Chain multiple policies
├── store/
│   ├── IEventStore.kt          # Storage interface
│   └── sqlite/
│       ├── EventStore.kt       # Public SQLite store wrapper
│       ├── SQLiteEventStore.kt # Full implementation
│       └── IndexingStrategy.kt # Index configuration
└── relay/filters/
    ├── Filter.kt               # NIP-01 subscription filters
    └── FilterMatcher.kt        # Event-to-filter matching
```
