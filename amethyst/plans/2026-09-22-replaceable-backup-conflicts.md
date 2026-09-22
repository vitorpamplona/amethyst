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
2. **Events diff themselves.** `Event.diffFrom(older)` (quartz, `nip01Core/diff/`)
   compares two versions of the same event and returns an `EventDiff`: removed / added /
   changed `DiffEntry`s plus a `ContentChange` for content not already expressed as
   entries (the NIP-44 private items of lists). `DiffEntry` is a sealed vocabulary
   (`Person`, `Relay` with read/write, `Hashtag`, `Word`, `Geohash`, `EventRef`,
   `AddressRef`, `ProfileField`, `RelayGroup`, `ChatRoom`, `TrustProvider`, `Mint`,
   `NutzapKey`, `PaymentTarget`, `Bolt12Offer`, `OtherTag`), each with a stable identity
   `key`: same key in both versions but unequal entries is a change (new relay marker,
   petname, edited bio), not a removal plus an addition.

   Each event owns its mapping through two hooks: `diffEntry(tag)` (default handles
   `p`/`t`/`word`/`g`/`r`/`relay`/`e`/`a`, skips `alt`/`client`/`d`/`expiration`) and
   `diffContent(older)`. Overrides: `MetadataEvent` (JSON fields → `ProfileField`, no
   content change), `ContactListEvent` (ignores the deprecated relay-map content),
   `SimpleGroupListEvent`, `EphemeralChatListEvent`, `TrustProviderListEvent`,
   `NutzapInfoEvent`, `PaymentTargetsEvent`, `Bolt12OfferListEvent`.
3. **Only question lossy external rewrites.** `AccountSettings.acceptIntoBackup` guards
   every `update*` backup method. A newer version that was not signed here goes through
   `ReplaceableBackupDiff.detectLoss` (commons), which keeps the `EventDiff` only when
   `removesData()`: an entry was removed or the private content was cleared.

   If nothing was dropped, the other app evidently built on the previous version, and the
   backup is updated silently as before.
4. **Freeze and ask.** On a loss, the backup keeps the saved version and a
   `ReplaceableBackupConflict` is published on `AccountSettings.backupConflicts`.
   `BackupConflictDialog` (shown from `LoggedInPage`) is specific to the event: it names it
   ("Your mute list changed in another app"), says what that event is for, and lists what
   was removed, added and changed, grouped by entry type (people by display name, relays
   with their read/write marker, profile fields old → new…). Rendering is an exhaustive
   `when` over `DiffEntry`, so a new entry type can't be forgotten by the UI; the group
   label also depends on the event (an `e` tag is a muted thread in a mute list and a
   joined chat in a public chat list). It offers:
   - **Restore saved version** — `Account.restoreBackupOver` re-signs the saved kind, tags
     and content (NIP-44 self-encrypted items stay valid) with
     `created_at = max(now, incoming + 1)` and publishes it.
   - **Use new version** — `Account.acceptExternalVersion` whitelists that id and re-runs
     the original update, so the backup moves forward.
   - **Decide later** — hides it for this session only; the backup stays frozen.

   While a conflict is open, edits made in Amethyst are compared against the frozen
   saved version too (they are built on top of the external one), so they cannot
   silently clear the conflict.

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
