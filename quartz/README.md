# Quartz Guide for Clients

Here's how to structure a new Twitter-like client.

## Architecture

Set up a Context class to wire Quartz components together. Usually there is only
one instance of this class.

```kotlin
object AppGraph {
    // application-wide scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // the local db
    val sqlite = EventStore(dbName = "demo-events.db")

    // the local cache that keeps only one copy of each event in memory
    val interned = InterningEventStore(sqlite)

    // the observable db, that you can produce flows that auto update
    val db = ObservableEventStore(interned)

    // the client to access relays
    val client = NostrClient(websocketBuilder = KtorWebSocket.Builder())

    // sends all events, regardless of the subscription, to the local db
    val collector = EventCollector(client) { event, _ ->
        runCatching {
            db.insert(event)
        }
    }

    // update this variable when a user logs in, starts with a guest
    var signer: NostrSigner = NostrSignerInternal(KeyPair())

    init {
        // Periodic NIP-40 sweep — drops expired events from SQLite and
        // emits StoreChange.DeleteExpired so live projections drop them
        // too. Without this the on-disk store grows monotonically.
        scope.launch {
            while (isActive) {
                delay(15.minutes)
                runCatching { db.deleteExpiredEvents() }
            }
        }
    }
}
```

Then use a view model to subscribe to relays and the local db at the same time,
like this:

```kotlin
class NotesFeed(
    private val db: ObservableEventStore,
    private val client: NostrClient,
) {
    private val subId = newSubId()
    private val filter = Filter(kinds = listOf(TextNoteEvent.KIND), limit = 100)
    private val relays =
        setOf(
            "wss://relay.damus.io".normalizeRelayUrl(),
            "wss://nos.lol".normalizeRelayUrl(),
            "wss://relay.nostr.band".normalizeRelayUrl(),
        )

    val notes: Flow<ProjectionState<TextNoteEvent>> =
        db
            .project<TextNoteEvent>(filter)
            .filterItems { it.value.isNewThread() }
            .onStart { client.subscribe(subId, relays.associateWith { listOf(filter) }) }
            .onCompletion { client.unsubscribe(subId) }
}

class FeedViewModel(
    private val db: ObservableEventStore,
    private val client: NostrClient,
) : ViewModel() {
    val notesFeed = NotesFeed(db, client)

    val feed = notesFeed
        .flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectionState.Loading)

    fun send(text: String, signer: NostrSigner) {
        viewModelScope.launch {
            val signed = signer.sign<TextNoteEvent>(TextNoteEvent.build(text))
            // Hits the bus → projection picks it up alongside any inbound relay copy.
            db.insert(signed)
            client.publish(signed, relays)
        }
    }
}
```

Notice that the `notes` flow is ready for the UI and automatically subscribes
and unsubscribes to any group of relays and filters the user wants. Similarly,
the `send` function updates both the local db and the relay.

`NostrClient` connects on-demand: the first `subscribe(...)` or `publish(...)` to a relay triggers the socket. There's no need to call `client.connect()` at startup — it's only useful for resuming after a prior `disconnect()`.

## Building a reactive feed UI

A feed screen reads from the view model's feed flow, which only updates when
new events arrive or are deleted due to kind 5 deletions, vanish requests or
expirations.

```kotlin
fun main() {
    application {
        val state = rememberWindowState(size = DpSize(560.dp, 720.dp))
        Window(onCloseRequest = ::exitApplication, state = state, title = "Nostr Kind 1 Demo") {
            MaterialTheme {
                val viewModel = remember {
                    FeedViewModel(AppGraph.db, AppGraph.client, AppGraph.signer)
                }

                val noteState by viewModel.feed.collectAsStateWithLifecycle()
                when (noteState) {
                    is ProjectionState.Loading -> LoadingFeed()
                    is ProjectionState.Loaded -> Feed(noteState.items)
                }
            }
        }
    }
}

@Composable
private fun LoadingFeed() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Feed(items: List<MutableStateFlow<TextNoteEvent>>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.value.id }) { handle ->
            NoteRow(handle)
            HorizontalDivider()
        }
    }
}

@Composable
private fun NoteRow(handle: MutableStateFlow<TextNoteEvent>) {
    val event by handle.collectAsStateWithLifecycle()
    Text(
        text = event.content,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}
```

Notice how each how also subscribe for changes. This is important to receive
updates from replaceable and addressable events.

# Appendix A

Quartz doesn't offer a Ktor websocket, but you can use this one as reference.

```kotlin
/**
 * Ktor-based [WebSocket] for talking to a Nostr relay.
 *
 * Quartz exposes [WebsocketBuilder] as the only seam between its relay-pool
 * and the underlying transport, so all this class has to do is open a Ktor
 * websocket session, forward incoming text frames to [out], and let Quartz
 * drive sends.
 */
class KtorWebSocket(
    private val url: NormalizedRelayUrl,
    private val httpClient: HttpClient,
    private val out: WebSocketListener,
) : WebSocket {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var session: DefaultWebSocketSession? = null
    private var readerJob: Job? = null

    override fun needsReconnect(): Boolean = session == null

    override fun connect() {
        readerJob =
            scope.launch {
                try {
                    val s = httpClient.webSocketSession(urlString = url.url)
                    session = s
                    out.onOpen(0, false)

                    for (frame in s.incoming) {
                        if (frame is Frame.Text) {
                            out.onMessage(frame.readText())
                        }
                    }

                    val reason = s.closeReason.await()
                    out.onClosed(
                        code =
                            reason?.code?.toInt() ?: CloseReason.Codes.NORMAL.code
                                .toInt(),
                        reason = reason?.message ?: "",
                    )
                } catch (t: Throwable) {
                    out.onFailure(t, null, null)
                } finally {
                    session = null
                }
            }
    }

    override fun disconnect() {
        val s = session
        session = null
        readerJob?.cancel()
        readerJob = null
        if (s != null) {
            runBlocking { s.close(CloseReason(CloseReason.Codes.NORMAL, "client disconnect")) }
        }
        scope.cancel()
    }

    override fun send(msg: String): Boolean {
        val s = session ?: return false
        scope.launch { s.send(msg) }
        return true
    }

    /**
     * The factory Quartz hands to [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient].
     * One [HttpClient] is shared by every relay in the pool.
     */
    class Builder(
        private val httpClient: HttpClient = defaultClient(),
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = KtorWebSocket(url, httpClient, out)

        companion object {
            fun defaultClient() =
                HttpClient(CIO) {
                    install(WebSockets)
                }
        }
    }
}
```

