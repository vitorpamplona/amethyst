# Quartz Guide for Clients

Here's how to structure a new Twitter-like client.

## Architecture

Set up a Context class to wire Quartz components together. Usually there is only
one instance of this class.

```kotlin
object AppGraph {
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
                val notes by viewModel.feed.collectAsState()
                FeedScreen(state = notes, onSend = viewModel::send)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: ProjectionState<TextNoteEvent>,
    onSend: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nostr Kind 1 Demo") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Composer(onSend = onSend)
            HorizontalDivider()
            when (state) {
                is ProjectionState.Loading -> LoadingFeed()
                is ProjectionState.Loaded -> Feed(state.items)
            }
        }
    }
}

@Composable
private fun Composer(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("What's on your mind?") },
            maxLines = 4,
        )
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
        ) { Text("Post") }
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
    // The list reference is stable while membership is unchanged, so
    // LazyColumn only invalidates structure on insert / removal.
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.value.id }) { handle ->
            NoteRow(handle)
            HorizontalDivider()
        }
    }
}

@Composable
private fun NoteRow(handle: MutableStateFlow<TextNoteEvent>) {
    // Only collectors of THIS handle re-render when the event mutates
    // in place (addressable supersession, etc.).
    val note by handle.collectAsState()
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = note.pubKey.take(12) + "…",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = formatTime(note.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            text = note.content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
```

Notice how each how also subscribe for changes. This is important to receive
updates from replaceable and addressable events.
