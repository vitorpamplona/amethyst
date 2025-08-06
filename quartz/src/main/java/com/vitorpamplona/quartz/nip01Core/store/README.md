# Event Store Module

This module implements an **Event Store** with nostr-native queries.

The goal was not to make the fastest database, since there could be multiple optimizations made if
consistency can be sacrificed, but a database that will never crash and never go corrupt.

## Responsibilities

- **Storage & Retrieval**:
  Stores Nostr events and enables retrieval using Nostr filters

- **Replaceable Events**:
  - Old versions are removed when newer versions arrive.
  - Old versions are blocked if newer versions exist.

- **Ephemeral Events**
  - Ephemeral events never stored.

- **NIP-40 Expirations**
  - Manages expiration timestamps and prunes expired events.
  - Blocks expired events from being reinserted

- **NIP-09 Deletion Events**
  - Deletes by event id
  - Deletes by address until the `created_at`
  - Blocks deleted events from being re-inserted.

- **NIP-62 Right to Vanish**
  - Supports deleting an entire user until the `created_at` for enhanced privacy

- **NIP-50 Full Text Search**:
  - Implements content indexing and full text search supporting rich queries over event content.

- **Immutable Tables**
  Triggers ensure event immutability.

## Indexing Strategy

The store indexes events using five dedicated tables:
- `event_headers`: stores the canonical event fields.
- `event_tags`: indexes tag values for fast filtering on tag-based queries.
- `event_fts`: for the content of full text search
- `event_expirations`: to control when expired events must be deleted.
- `event_vanish`: to control up to when vanished accounts must be blocked.

SQL triggers ensure the **immutability of stored events**, preventing accidental or intentional
modifications.

## Querying

This module supports optimized query planning, producing efficient SQL for multi-filter evaluation
across fields and tags, including `limit` clauses per filter:

For instance, the following filter:
```kotlin
store.query(
  listOf(
    Filter(limit = 10),
    Filter(authors = listOf(hexkey), kinds = listOf(1, 1111), search = "keywords", limit = 100),
    Filter(kinds = listOf(20), search = "cats", limit = 30),
  )
)
```

Becomes

```sql
SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
INNER JOIN (
	SELECT event_headers.row_id AS row_id
	FROM event_headers
	ORDER BY created_at DESC,id ASC
	LIMIT 10

	UNION

	SELECT event_headers.row_id AS row_id
	FROM event_headers
	INNER JOIN event_fts ON event_fts.event_header_row_id = event_headers.row_id
	WHERE event_headers.kind IN (1, 1111)
      AND event_headers.pubkey = hexkey
      AND event_fts MATCH "keywords"
	ORDER BY created_at DESC, id ASC
	LIMIT 100

	UNION

	SELECT event_headers.row_id AS row_id
	FROM event_headers
	INNER JOIN event_fts ON event_fts.event_header_row_id = event_headers.row_id
	WHERE event_headers.kind = 20
	  AND event_fts MATCH "cats"
	ORDER BY created_at DESC,id ASC
	LIMIT 30
) AS filtered ON event_headers.row_id = filtered.row_id
ORDER BY created_at DESC,id
```

The union operations support complex filter lists while avoiding redundant data fetching and
duplicated outstreams.

## How to Use

The `EventStore` class provides a high-level interface for interacting with the event database.
It is initialized with a `SQLiteDatabase` instance, and it manages the underlying tables and query planning.

### Initialization

To initialize the `EventStore` in your Application class:

```kotlin
val eventStore = EventStore(context, "dbname.db", relayUrlIdentifier)
```

### Querying Events

To query events, use the `query` method with one or more `Filter` objects:

```kotlin
val filters = listOf(
    Filter(limit = 10),
    Filter(authors = listOf(hexkey))
)

val events = eventStore.query(filters)
```

or to receive events as the cursor sends:

```kotlin
val filters = listOf(
    Filter(limit = 10),
    Filter(authors = listOf(hexkey))
)

eventStore.query(filters) { event ->
    // do something
}
```

`count` and `delete` also accept one or more filters.

### Inserting Events

Insert a single event using the `insert` method:

```kotlin
eventStore.insert(event)
```

### Deleting Events

Events should be deleted by adding a DeletionRequest or a VanishRequest to the db, but to manually
delete an event by ID, use the `delete` method:

```kotlin
eventStore.delete(event.id)
```

### Full-Text Search

The store supports full-text search using the `search` parameter in filters:

```kotlin
val result = eventStore.query(Filter(search = "bitcoin", limit = 20))
```

This will match any event whose content contains "bitcoin", returning the most recent 20 results.

### Periodic cleanup of expired events.

The store exposes a `deleteExpiredEvents` to be used in a periodic clean up procedure. Users
should use a WorkManager or a coroutine to periodically call `store.deleteExpiredEvents()`. We
recommend a 15-minute window to remove recently expired events from the database.

Here's an example of a Worker that should be added to your application class.

```kotlin
class ExpirationWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        YourApplication.store.deleteExpiredEvents()
        return Result.success()
    }
}

fun schedulePeriodicWork(context: Context) {
    val periodicWorkRequest =
        PeriodicWorkRequestBuilder<ExpirationWorker>(15, TimeUnit.MINUTES).build()

    WorkManager.getInstance(context).enqueue(periodicWorkRequest)
}
```
