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
2. **Only question lossy external rewrites.** `AccountSettings.acceptIntoBackup` guards
   every `update*` backup method. A newer version that was not signed here is diffed by
   `ReplaceableBackupDiff.diff` against the backup into a `BackupDiff`: the
   `BackupEventType` (profile, follow list, mute list, each relay list, wallet…) plus
   removed / added / changed `BackupEntry`s, each typed (`BackupEntryType`: person, relay,
   hashtag, word, thread, profile field, mint, trust provider…) so the UI can label them.
   It only counts as a loss when something was removed:
   - tags matched by name + value (a new relay hint or petname is an edit, not a loss;
     `alt`/`client`/`d`/`expiration` are ignored);
   - kind 0: any filled profile field that is now missing or blank;
   - kind 3: content ignored (deprecated relay map);
   - everything else: content that went from non-blank to blank (private items wiped).

   If nothing was dropped, the other app evidently built on the previous version, and the
   backup is updated silently as before.
3. **Freeze and ask.** On a loss, the backup keeps the saved version and a
   `ReplaceableBackupConflict` is published on `AccountSettings.backupConflicts`.
   `BackupConflictDialog` (shown from `LoggedInPage`) is specific to the event: it names it
   ("Your mute list changed in another app"), says what that event is for, and lists what
   was removed, added and changed, grouped by entry type (people by display name, relays
   with their read/write marker, profile fields old → new…). It offers:
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
