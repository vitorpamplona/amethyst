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
3. **Only question lossy external rewrites.** `AccountSettings.acceptIntoBackup` guards
   every `update*` backup method. A newer version that was not signed here goes through
   `ReplaceableBackupDiff.detectLoss` (commons), which asks the event for its diff and
   keeps it only when `removesData()`.

   If nothing was dropped, the other app evidently built on the previous version, and the
   backup is updated silently as before.
4. **Freeze and ask.** On a loss, the backup keeps the saved version and a
   `ReplaceableBackupConflict` is published on `AccountSettings.backupConflicts`.
   `BackupConflictDialog` (shown from `AppNavigation`, so it can navigate) is a short
   summary specific to the event: it names it ("Your mute list changed in another app"),
   says what that event is for, and counts what was removed, added and changed per entry
   type. **Review changes** opens `Route.BackupConflictReview(slot)`
   (`BackupConflictReviewScreen`): a `LazyColumn` of every entry, so lists with hundreds of
   items scroll lazily. Entries are typed (`ReviewItem`, built per diff class in
   `BackupConflictPresentation.kt`) and rendered with the app's loaders, which subscribe to
   relays and recompose when data arrives: people (`LoadUser` + `UsernameDisplay`, tap →
   profile), muted threads (`LoadNote` + `NoteCompose`), communities and feeds
   (`LoadAddressableNote` + `NoteCompose`), public chats (`observeChannel`, tap → chat),
   ephemeral rooms (tap → room) and relays (tap → relay info). The dialog hides while the
   review screen is open (`AccountSettings.reviewingBackupConflict`).

   The list is laid out per event (`BackupConflictEventViews.kt`, `eventDiffItems`): a
   profile compares each changed field saved vs new side by side, with picture and banner
   shown as images and removed fields highlighted; a NIP-65 list is a relay table with the
   saved and new read/write access and a count of unchanged relays; follow and mute lists
   get a count header (on this device vs new version) above the linked people. Other
   events use the generic removed / added / changed sections. Both offer:
   - **Restore saved version** — `Account.restoreBackupOver` re-signs the saved kind, tags
     and content (NIP-44 self-encrypted items stay valid) with
     `created_at = max(now, incoming + 1)`, dropping the old `client` tag (so the signer
     adds the current one), the `nonce` (its PoW was for the old id) and an already-past
     `expiration` (`BackupRestore.tagsToResign`), and publishes it through
     `EventBroadcaster.sendRestoredVersion`: where a normal save of that kind goes, and for
     the user's own relay lists (NIP-65, DM, key package) also to every relay the restored
     list names, since the lossy version may have dropped them from the outbox set.
   - **Use new version** — `Account.acceptExternalVersion` whitelists that id and re-runs
     the original update, so the backup moves forward.
   - **Decide later** — snoozes the slot in `AccountSettings.snoozedBackupConflicts` while
     the account is loaded (survives rotation) and until another app changes it again; the
     backup stays frozen.

   Both resolutions first *claim* the conflict: they act only if it is still the open
   conflict of its slot, so a stale dialog, a double tap, or a conflict dropped because the
   wallet/nutzap info was deleted does nothing. While a restore is signing, its incoming
   version can't re-raise the conflict; a failed restore reopens it. The same incoming
   version re-emitted by the cache is ignored without re-diffing.

   Public items removed while the private section went from empty to filled are treated as
   made private, not lost (`ContentChange.publicRemovalsAreLoss`).

   While a conflict is open, edits made in Amethyst are compared against the frozen
   saved version too (they are built on top of the external one), so they cannot
   silently clear the conflict. They move the conflict's `incoming` forward but keep the
   external version as its `cause` (the dialog shows that date) and keep it snoozed.

Conflicts are not persisted: the frozen backup is reloaded on start, the newer relay
version replaces it in `LocalCache`, and the same conflict is detected again.

## Known limits / follow-ups

- Private (encrypted) list items are only compared as "all gone"; a rewrite that drops
  *some* private items isn't detected. Decrypting both sides would fix it but needs the
  signer (an Amber round-trip), so it's left out of the passive path.
- The ids of locally signed events are in memory only. If the app dies inside the 1s
  settings-save debounce right after a local removal, the next launch may ask once about
  our own event. Harmless (either answer keeps the data the user chose).
- Edits from the same user on another Amethyst install count as "external" when they
  remove items. That's intended: the device can't tell them apart from a careless app.
- Desktop marks its own events too but has no backup store or dialog yet.
