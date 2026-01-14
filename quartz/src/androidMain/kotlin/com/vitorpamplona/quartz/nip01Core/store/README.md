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

- **Addressable Events**:
    - Forces unique constraint by kind, pubkey, d-tag
    - Old versions are removed when newer versions arrive.
    - Old versions are blocked if newer versions exist.

- **Ephemeral Events**
  - Ephemeral events never stored.

- **NIP-40 Expirations**
  - Deletes expired events.
  - Blocks expired events from being reinserted

- **NIP-09 Deletion Events**
  - Deletes by event id
  - Deletes by address until the `created_at`
  - Blocks deleted events from being re-inserted.
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
