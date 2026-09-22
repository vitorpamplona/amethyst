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

   `nip01Core/diff/` only holds the containers: `EventDiff` (`removesData()`, `isEmpty()`),
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
   `BackupConflictDialog` (shown from `LoggedInPage`) is specific to the event: it names it
   ("Your mute list changed in another app"), says what that event is for, and lists what
   was removed, added and changed, grouped by entry type (people by display name, relays
   with their read/write marker, profile fields old → new…). The dialog maps each diff
   class to labelled groups from its typed objects (`when (diff)`), resolving pubkeys and
   channel ids to names from `LocalCache`. It offers:
   - **Restore saved version** — `Account.restoreBackupOver` re-signs the saved kind, tags
     and content (NIP-44 self-encrypted items stay valid) with
     `created_at = max(now, incoming + 1)` and publishes it through
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
