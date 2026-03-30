# Building a Nostr Relay with Quartz

Quartz provides a transport-agnostic relay engine. You provide a `send` callback per connection, and it gives you a `RelaySession` that accepts raw JSON strings. Plug it into Ktor or any WebSocket transport.

Both `NostrServer` and `EventStore` implement `AutoCloseable`.

## Quick Start

### 1. Add Dependencies

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-core:3.1.1")
    implementation("io.ktor:ktor-server-netty:3.1.1")
    implementation("io.ktor:ktor-server-websockets:3.1.1")
    implementation(project(":quartz"))
}
```

### 2. Create the Relay Server

```kotlin
fun main() {
    val store = EventStore(dbName = "relay-events.db")
    val server = NostrServer(store)

    embeddedServer(Netty, port = 7777) {
        install(WebSockets)

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
```

That's it. You now have a NIP-01 compliant relay running on `ws://localhost:7777`.

## Event Store

### SQLite (Persistent)

```kotlin
val store = EventStore(dbName = "relay-events.db")
```

Uses `androidx.sqlite` with WAL journal mode and a 32 MB memory cache.

### In-Memory (Testing)

```kotlin
val store = EventStore(null)
```

### Indexing Strategy

Control which indexes are created:

```kotlin
val store = EventStore(
    dbName = "relay-events.db",
    indexStrategy = DefaultIndexingStrategy(
        indexEventsByCreatedAtAlone = false,
        indexTagsByCreatedAtAlone = false,
        indexTagsWithKindAndPubkey = false,
        useAndIndexIdOnOrderBy = false,
    ),
)
```

By default, all single-letter tags with values are indexed. Override `shouldIndex(kind, tag)` for custom behavior. More indexes = faster queries but larger database.

## Policies

Policies control what clients can do. They validate commands and can rewrite filters.

### Built-in Policies

**`VerifyPolicy`** (default) — Verifies event signatures and IDs. Rejects malformed events.

```kotlin
val server = NostrServer(store) // Uses VerifyPolicy by default
```

**`EmptyPolicy`** — Accepts everything. Useful for testing.

```kotlin
val server = NostrServer(store, policyBuilder = { EmptyPolicy })
```

**`FullAuthPolicy`** — Requires NIP-42 authentication before accepting any command.

```kotlin
val server = NostrServer(
    store = store,
    policyBuilder = {
        FullAuthPolicy(relay = "wss://myrelay.example.com/".normalizeRelayUrl()!!)
    },
)
```

### Composing Policies

Chain policies with `+` or `PolicyStack`. All must approve; first rejection wins.

```kotlin
val server = NostrServer(
    store = store,
    policyBuilder = {
        VerifyPolicy + FullAuthPolicy(relay = "wss://myrelay.example.com/".normalizeRelayUrl()!!)
    },
)
```

### Writing a Custom Policy

Implement `IRelayPolicy`:

```kotlin
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

## Testing

```kotlin
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
            val messages = mutableListOf<String>()
            val session = server.connect { messages.add(it) }

            session.receive("""["EVENT",{"id":"${"0".repeat(64)}","pubkey":"${"a".repeat(64)}","created_at":1000,"kind":1,"tags":[],"content":"hello","sig":"${"b".repeat(128)}"}]""")
            assertTrue(messages.any { it.contains("OK") })

            session.receive("""["REQ","sub1",{"kinds":[1]}]""")
            assertTrue(messages.any { it.contains("EVENT") })
            assertTrue(messages.any { it.contains("EOSE") })
        }
    }
}
```

## Key Source Files

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
