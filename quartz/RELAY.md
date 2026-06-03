# Building a Nostr Relay with Quartz

Quartz provides a transport-agnostic relay engine. You provide a `send` callback per connection, and it gives you a `RelaySession` that accepts raw JSON strings. Plug it into Ktor or any WebSocket transport.

There are two engines on the same `RelaySession` core: `NostrServer` for storage-backed relays (an `IEventStore` with a live tail after EOSE) and `ReqResponderServer` for non-storage relays (search, redirector, computed data — see [Non-Storage Relays](#non-storage-relays-search-redirector-computed)).

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

## Non-Storage Relays (search, redirector, computed)

A relay's job is to *answer REQs*. When the answer doesn't come from a stored
set — a NIP-50 search that forwards to an HTTP backend, a relay that emits
computed/projected data — implement `ReqResponder` and serve it with
`ReqResponderServer`. You supply a `Flow<Event>`; the engine owns the wire
protocol (challenge/auth, command parsing, policy, `EVENT`/`EOSE`/`CLOSED`
framing, subscription lifecycle), so there's no hand-written read loop.

```kotlin
class SearchResponder(private val backend: SearchApi) : ReqResponder {
    override fun respond(filters: List<Filter>): Flow<Event> = flow {
        filters.forEach { f ->
            f.search?.let { raw ->
                val q = SearchQuery.parse(raw)
                backend.search(q.terms, domain = q.domain, language = q.language)
                    .forEach { emit(it) }
            }
        }
    }
    // COUNT defaults to counting respond(); override for a cheaper backend count.
}

fun main() {
    val server = ReqResponderServer(
        responder = SearchResponder(searchApi),
        policyBuilder = { FullAuthPolicy(relay) }, // optional NIP-42 gating
    )

    embeddedServer(Netty, port = 7777) {
        install(WebSockets)
        routing {
            webSocket("/") {
                server.serve(send = { json -> launch { send(Frame.Text(json)) } }) { session ->
                    for (frame in incoming) {
                        if (frame is Frame.Text) session.receive(frame.readText())
                    }
                }
            }
        }
    }.start(wait = true)
}
```

**EOSE = flow completion.** The engine sends `EOSE` when `respond(...)`
completes, which is the natural shape for finite queries. Relays that need an
open-ended live tail after EOSE should use the storage path (`NostrServer` +
`IEventStore`) instead. EVENT publishes are rejected (`OK false` —
`blocked: this relay does not accept events`) and negentropy is disabled, since
there's no stored set. A failure thrown from the flow ends the subscription
with `CLOSED` `error: <message>`.

`ReqResponderServer` and `NostrServer` are the two concrete dispatch engines;
both build on `RelaySession` and the shared `SessionBackend` seam (`LiveEventStore`
is the storage-backed `SessionBackend`; `ReqResponderBackend` adapts a
`ReqResponder`). Implement `SessionBackend` directly only if you need custom
control over the EVENT/negentropy paths as well as REQ/COUNT.

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

`FullAuthPolicy` already implements the full NIP-42 challenge/verify handshake — you should not re-implement it. To bridge auth to an external system (e.g. exchange the verified event for a backend JWT), override the `suspend` `onAuthenticated` hook. It runs after the NIP-42 checks pass but before the success `OK` is sent, so it can do network/disk I/O; throwing from it turns the AUTH into a failing `OK false`:

```kotlin
class JwtAuthPolicy(
    relay: NormalizedRelayUrl,
    private val backend: AuthBackend,
) : FullAuthPolicy(relay) {
    override suspend fun onAuthenticated(pubKey: HexKey, event: RelayAuthEvent) {
        // Suspends; a thrown exception rejects the login with OK false.
        backend.exchangeForSession(pubKey, event)
    }
}
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

## NIP-50 Search Queries

`Filter.search` is the raw NIP-50 string. `SearchQuery` parses it into the
free-text terms plus the typed `key:value` extensions (`domain:`, `language:`,
`sentiment:`, `nsfw:`, `include:spam`), so a search relay or redirector doesn't
have to re-parse the string. Unknown extensions are preserved and readable via
`extension(key)`; `toSearchString()` re-assembles a canonical query.

```kotlin
val q = SearchQuery.parse(filter.search) // "best apps domain:example.com nsfw:false"
q.terms        // "best apps"
q.domain       // "example.com"
q.nsfwIncluded // false  (NIP-50 default is true when the token is absent)
```

A search/redirector relay is just a custom policy (or, for computed results, a
custom `IEventStore` whose `query` answers the REQ) that reads the parsed query:

```kotlin
override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> {
    cmd.filters.forEach { f ->
        val q = SearchQuery.parse(f.search)
        if (q.domain != null && q.domain !in allowedDomains) {
            return PolicyResult.Rejected(
                MachineReadablePrefix.RESTRICTED.format("domain not searchable"),
            )
        }
    }
    return PolicyResult.Accepted(cmd)
}
```

## Wire Helpers

`Command` and `Message` carry symmetric JSON helpers so you don't have to reach
for the mapper directly:

```kotlin
val cmd = Command.fromJson(text)   // ["REQ", "sub", {...}] -> ReqCmd
val json = EoseMessage("sub").toJson()
val msg = Message.fromJson(json)
```

Build standardized OK/CLOSED reasons with `MachineReadablePrefix` instead of
hand-writing the NIP-01 prefixes (`auth-required:`, `restricted:`, `error:`, …):

```kotlin
session.send(OkMessage.rejected(event.id, MachineReadablePrefix.AUTH_REQUIRED, "log in first"))
session.send(ClosedMessage.of(subId, MachineReadablePrefix.RESTRICTED, "not allowed yet"))
MachineReadablePrefix.parse("rate-limited: slow down") // -> RATE_LIMITED
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
│   ├── NostrServer.kt          # Storage-backed engine (IEventStore)
│   ├── ReqResponderServer.kt   # Non-storage engine (search/redirector/computed)
│   ├── ReqResponder.kt         # Flow<Event> REQ-responder SPI
│   ├── SessionBackend.kt       # Data-plane seam (LiveEventStore / ReqResponderBackend)
│   ├── RelaySession.kt         # Per-connection handler
│   ├── LiveEventStore.kt       # Reactive event streaming (storage SessionBackend)
│   ├── IRelayPolicy.kt         # Policy interface + PolicyResult + onAuthenticated
│   └── policies/
│       ├── EmptyPolicy.kt      # Accept everything
│       ├── VerifyPolicy.kt     # Signature verification (default)
│       ├── FullAuthPolicy.kt   # NIP-42 auth required (override onAuthenticated to bridge)
│       └── PolicyStack.kt      # Chain multiple policies
├── relay/commands/
│   ├── toRelay/Command.kt      # Command.fromJson / toJson
│   └── toClient/
│       ├── Message.kt          # Message.fromJson / toJson
│       └── MachineReadablePrefix.kt # Typed OK/CLOSED reason prefixes
├── store/
│   ├── IEventStore.kt          # Storage interface
│   └── sqlite/
│       ├── EventStore.kt       # Public SQLite store wrapper
│       ├── SQLiteEventStore.kt # Full implementation
│       └── IndexingStrategy.kt # Index configuration
├── relay/filters/
│   ├── Filter.kt               # NIP-01 subscription filters
│   └── FilterMatcher.kt        # Event-to-filter matching
└── ../nip50Search/
    └── SearchQuery.kt          # NIP-50 search-string parser
```
