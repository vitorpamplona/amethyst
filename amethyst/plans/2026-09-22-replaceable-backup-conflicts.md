# Replaceable-event backup conflicts

## Problem

`AccountSettings` keeps an on-disk backup of ~26 of the account's replaceable events
(profile, follow list, mute list, relay lists, NIP-51 lists, wallet events, app settings).
Each `*State` collector copies whatever is newest in `LocalCache` into that backup. When
another client rewrites one of these events without reading the previous version (a fresh
follow list, a mute list without the private items, a profile missing fields), the newer
event wins on relays, lands in `LocalCache`, and the backup is overwritten with it: the
only surviving copy of the user's data is gone.

## Design

1. **Know what we signed.** `LocallySignedEvents` (commons, `model/backups/`) records the
   ids of replaceable/addressable events passing through `justConsumeMyOwnEvent`, the
   choke point for every local publish and for the backup restore at startup. It is a
   bounded LRU (500 ids): only the latest versions of each list matter.
2. **Events diff themselves.** Every backed-up event implements
   `DiffableEvent<D>` (quartz, `nip01Core/diff/`): `diffFrom(older)` returns its own diff
   class built from its own parsed objects, e.g. `ContactListDiff(follows:
   ListDiff<ContactTag>)`, `MuteListDiff(publicMutes: ListDiff<MuteTag>, privateItems)`,
   `AdvertisedRelayListDiff(relays: ListDiff<AdvertisedRelayInfo>)`, `NutzapInfoDiff(mints:
   ListDiff<NutzapMintTag>, relays, p2pkPubkey: ValueChange<HexKey>)`, `MetadataDiff` with
   one `ValueChange` per `UserMetadata` field plus NIP-39 claims and unmodeled JSON fields.
   The relay-tag lists (DM, key package, search, indexer, feeds, blocked, trusted, private
   outbox) share `RelayListDiff`; encrypted-only events (Cashu wallet, Concord list, NIP-78
   data) diff their content as a whole.

   Tags an event's parser doesn't read never reach its diff, so bookkeeping other clients
   rewrite on every save (`client`, NIP-13 `nonce`, NIP-40 `expiration`) is ignored by
   construction; `EventDiffTest.clientPowAndExpirationTagsAreIgnored` pins it.

   `nip01Core/diff/` only holds the containers: `EventDiff` (`removesData()`),
   `ListDiff<T>` (items matched by an identity key; a matched pair with different details
   is a change, not a removal plus an addition), `ValueChange<T>`, and `ContentChange` for
   NIP-44 private items, which can't be compared item by item without decrypting.
3. **Only question lossy external rewrites.** `BackupConflictGuard` (commons,
   `model/backups/`, tested in `BackupConflictGuardTest`) guards every `update*` backup
   method of `AccountSettings`, which owns one as `backupGuard`. A newer version that was not signed here goes through
   `ReplaceableBackupDiff.detectLoss` (commons), which asks the event for its diff and
   keeps it only when `removesData()`.

   If nothing was dropped, the other app evidently built on the previous version, and the
   backup is updated silently as before.

   Wiped versions (no tags, empty app data, a mints-less nutzap info) reach the guard too,
   so wiping everything is questioned like any other loss. An empty version only becomes
   the backup when this app signed it or the user kept it. The guard runs under a lock
   shared with the resolutions, and a version older than an open conflict's incoming can
   never replace or clear it (e.g. the retry of an accept that a newer change overtook).
4. **Freeze and ask.** On a loss, the backup keeps the saved version and a
   `ReplaceableBackupConflict` is published on `BackupConflictGuard.conflicts`.
   Home shows a card per open conflict at the top of the feed (`BackupConflictCards`,
   next to the key-backup nudge): "Your mute list changed in another app" with a
   "12 removed · 3 added" summary. Cards can't be dismissed; they stay until the user
   decides. The most recent conflict gets a lead card with an event-specific headline
   ("3 relays were unblocked", "Your follow list shrank" with its bar); the others collapse
   into slim pills. At most two pills show; more open conflicts sit behind an "N more lists
   changed" toggle that expands into a capped, scrollable area, since the cards float over
   the feed instead of scrolling with it. List headlines lead with what was lost and add
   what was added to the same list, worded per list ("12 follows were dropped, and 40 were
   added", "You'd leave 2 groups, and join 1", "3 relays were unblocked, and 1 newly
   blocked"). Tapping one opens `Route.BackupConflictReview(slot)`
   (`BackupConflictReviewScreen`): a `LazyColumn` of every entry, so lists with hundreds of
   items scroll lazily. Entries are typed (`ReviewItem`, built per diff class in
   `BackupConflictPresentation.kt`) and rendered with the app's loaders, which subscribe to
   relays and recompose when data arrives: people (`LoadUser` + `UsernameDisplay`, tap →
   profile), muted threads (`LoadNote` + `NoteCompose`), communities and feeds
   (`LoadAddressableNote` + `NoteCompose`), public chats (`observeChannel`, tap → chat),
   ephemeral rooms (tap → room) and relays (tap → relay info). Leaving the screen without
   deciding keeps the card and the frozen backup.

   The list is laid out per event (`BackupConflictEventViews.kt`, `eventDiffItems`), each
   leading with one picture of the change (`BackupConflictVisuals.kt`; red lost, green
   gained, amber changed, all from the theme): the follow list shows saved → new counts, a
   kept/dropped/new split bar, Dropped/New/Edited tabs and a searchable grid of faces; the
   mute list a shield with the unmuted count, per-kind tiles, a face pile, struck-through
   words and the private-items card; the profile both versions side by side as mini
   cards, then inline field diffs with images as thumbnails; NIP-65 reach numbers ("people
   find your posts on 2 of 3 relays") over outbox and inbox lanes; nutzap info a key swap
   drawn as color fingerprints with a warning, then mints; groups a tile per group, faded
   when you'd leave it. The other lists (`BackupConflictListViews.kt`) get their own too:
   each relay list framed by what it is for (DM, key-package, search, indexer, relay-feed,
   private-outbox and trusted relays as "N of M" reach over per-relay rows; blocked relays
   as an "N unblocked" warning with allowed / newly blocked / still blocked), public
   chats, communities, favorite feeds and ephemeral rooms as tiles loaded from relays and
   faded when you'd leave them, hashtags and places as a pill cloud, trust providers as a
   per-service before/after table, payment targets and BOLT12 offers as cards, and the
   encrypted-only Cashu wallet and Concord list as one explained panel. Only NIP-78 app
   settings still uses the generic removed / added / changed sections.
   Buttons are worded per event ("Keep 120" / "Restore 523", "Re-mute 37", "Rejoin 3").
   Keeping the new version is the default (the filled button, on the right); restoring
   always reverts another app's change, so it is an outlined red button on every screen.
   The screen offers:
   - **Restore saved version** — `Account.restoreBackupOver` re-signs the saved kind, tags
     and content (NIP-44 self-encrypted items stay valid) with
     `created_at = max(now, incoming + 1)` (capped at 15 minutes ahead; a far-future
     external version is answered at "now"), dropping the old `client` tag (so the signer
     adds the current one), the `nonce` (its PoW was for the old id) and an already-past
     `expiration` (`BackupRestore.tagsToResign`), and publishes it through
     `EventBroadcaster.sendRestoredVersion`: where a normal save of that kind goes, and for
     the user's own relay lists (NIP-65, DM, key package) and nutzap info also to every
     relay the restored list names, since the lossy version may have dropped them from the
     outbox set; profiles, like their normal saves, go everywhere.
   - **Use new version** — `Account.acceptExternalVersion` whitelists that id and re-runs
     the original update, so the backup moves forward.

   Both resolutions first *claim* the conflict: they act only if it is still the open
   conflict of its slot, so a stale screen, a double tap, or a conflict dropped because the
   wallet/nutzap info was deleted does nothing. While a restore is signing, its incoming
   version can't re-raise the conflict; a failed restore reopens it. The same incoming
   version re-emitted by the cache is ignored without re-diffing.

   Public items removed while the private section went from empty to filled are treated as
   made private, not lost (`ContentChange.publicRemovalsAreLoss`).

   While a conflict is open, edits made in Amethyst are compared against the frozen
   saved version too (they are built on top of the external one), so they cannot
   silently clear the conflict. They move the conflict's `incoming` forward but keep the
   external version as its `cause` (the screen shows that date).

Open conflicts are persisted with the backups (`BackupConflictStorage`, as the saved,
incoming and cause events) and re-seeded on load through `BackupConflictGuard.restore`. The
diff is recomputed, so a version the other app has since fixed stops being a conflict. A
restored conflict has no retry closure until its version arrives again; keeping it rebuilds
the update from the event's type (`AccountSettings.reapplyBackup`), except NIP-78, which
lands on the next delivery.

## Known limits / follow-ups

- Private (encrypted) list items are only compared as "all gone"; a rewrite that drops
  *some* private items isn't detected. Decrypting both sides would fix it but needs the
  signer (an Amber round-trip), so it's left out of the passive path.
- The ids of locally signed events are in memory only. If the app dies inside the 1s
  settings-save debounce right after a local removal, the next launch may ask once about
  our own event. Harmless (either answer keeps the data the user chose).
- Edits from the same user on another Amethyst install count as "external" when they
  remove items. That's intended: the device can't tell them apart from a careless app.
- Desktop marks its own events too but has no backup store or conflict UI yet.
