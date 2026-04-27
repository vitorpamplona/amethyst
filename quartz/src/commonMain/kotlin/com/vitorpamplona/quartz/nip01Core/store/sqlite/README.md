# Event Store Module

This module implements an **Event Store** with nostr-native queries.

The goal was not to make the fastest database, since there could be multiple optimizations made if
consistency can be sacrificed, but a database that will never crash and never go corrupt.

## Features

- **Storage & Retrieval**:
  Stores Nostr events and retrieves using Nostr filters

- **Replaceable Events**:
  - Forces unique constraint by kind, pubkey
  - Old versions are removed when newer versions arrive.
  - Old versions are blocked if newer versions exist.
  - Same `created_at`: NIP-01 lexical-id tiebreaker (lowest id wins).

- **Addressable Events**:
    - Forces unique constraint by kind, pubkey, d-tag
    - Old versions are removed when newer versions arrive.
    - Old versions are blocked if newer versions exist.
    - Same `created_at`: NIP-01 lexical-id tiebreaker (lowest id wins).

- **Ephemeral Events**
  - Ephemeral events never stored.

- **NIP-40 Expirations**
  - Deletes expired events.
  - Blocks expired events from being reinserted

- **NIP-09 Deletion Events**
  - Deletes by event id
  - Deletes by address up to and including the deletion's `created_at` (newer versions are kept).
  - Blocks deleted events from being re-inserted.
  - Only the original author's deletions take effect; cross-author kind-5s are stored but inert.
  - GiftWraps are deleted by p-tag

- **NIP-62 Right to Vanish**
  - Deletes all user events until the `created_at`
  - Blocks vanished events from being re-inserted.
  - GiftWraps are deleted by p-tag

- **NIP-45 Counts**:
  - Counts records matching Nostr filters

- **NIP-50 Full Text Search**:
  - Custom content/tag indexing
  - Rich queries over event content and tags
  - Indexes updated on replaceables, deletions, vanish and expirations.

- **NIP-91: AND operator for tags**:
  - Allows queries matching two or more tags at the same time

- **Immutable Tables**
  - Tables cannot be updated, only inserted and deleted.

## Indexing Strategy

The store indexes events using five dedicated tables:
- `event_headers`: stores the canonical event fields.
- `event_tags`: indexes tag values as a hash for fast filtering on tag-based queries.
- `event_fts`: for the content of full text search
- `event_expirations`: to control when expired events must be deleted.
- `event_vanish`: to control up to when vanished accounts must be blocked.

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

## Concurrency

`androidx.sqlite.SQLiteConnection` is not thread-safe — same contract as
`sqlite3*` in the C API. To support concurrent inserts and reads from
multiple coroutines, `SQLiteEventStore` owns a Room-style
[`SQLiteConnectionPool`](SQLiteConnectionPool.kt):

- **One writer connection**, guarded by a coroutine `Mutex`. SQLite only
  allows one writer at the file level anyway, so serialising here costs
  nothing — it just queues callers cooperatively instead of crashing
  them on `BEGIN IMMEDIATE`.
- **N reader connections** (default 4), handed out from a `Channel` that
  doubles as a semaphore. Under WAL (`PRAGMA journal_mode = WAL`)
  readers run in parallel with the writer and with each other.

For in-memory databases (`dbName == null`) the pool degrades to a
single shared connection — every fresh `:memory:` connection would
otherwise be a *separate* DB. Writes still serialise correctly; reads
just take the same writer mutex.

The whole public API on `EventStore` / `SQLiteEventStore` is therefore
`suspend`. Callers must be in a coroutine; on Android, schedule
maintenance work as a `CoroutineWorker`.

`Mutex` is non-reentrant: do not call `eventStore.query(...)` from
inside a `transaction { ... }` body. The transaction body itself
already holds the writer connection — query against the
`SQLiteConnection` handed to your block instead.

## How to Use

The `EventStore` class provides a high-level interface for interacting with the event database.
It owns the underlying [`SQLiteConnectionPool`](SQLiteConnectionPool.kt) and the query planner.

### Initialization

To initialize the `EventStore` in your Application class:

```kotlin
val eventStore = EventStore("dbname.db", relayUrlIdentifier)
```

### Querying Events

To query events, use the `query` method with one or more `Filter` objects (in a coroutine):

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

For batch inserts, prefer a single `transaction` — one `BEGIN`/`COMMIT`
per batch is roughly an order of magnitude faster on WAL than one per
event:

```kotlin
eventStore.transaction {
    insert(event1)
    insert(event2)
    insert(event3)
}
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

Here's an example of a Worker that should be added to your application class. Use
`CoroutineWorker` (not `Worker`) — `deleteExpiredEvents()` is a `suspend` function.

```kotlin
class ExpirationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
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
