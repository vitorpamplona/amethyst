# Account migration to a new phone

_Status: in progress. Audit + the two mechanisms it calls for._

## The problem

`android:allowBackup="false"` (AndroidManifest.xml:99), so nothing Amethyst
writes reaches Google's cloud backup. The transition to a new phone is
therefore exactly: *whatever the account can rebuild from relays comes back,
everything else is gone.*

That split is invisible to the user. They enter their nsec, see their profile,
follows and feed return, and reasonably conclude the move worked — then
discover over the following days that their wallet is unpaired, every feed is
back to Global, and every notification is unread. The worst case is quieter
still: a Cashu keyset whose NUT-13 counter restarted at zero.

## What already survives, and how

Three mechanisms carry state today. Only the first two cross devices.

**1. Relays (the user's own events).** Profile (kind 0), follows (kind 3),
relay lists (10002/10050/10007/…), mute list, bookmark/hashtag/geohash/community
lists, NIP-60 wallet events, drafts in the private outbox. `AccountSettings`
keeps a local copy of each latest event (`backupContactList`,
`backupNIP65RelayList`, … — ~25 fields) purely as an offline cache; the relay
copy is authoritative and refetches on login. **Nothing to do here.**

**2. The NIP-78 settings blob.** One addressable kind-30078 event, d-tag
`AmethystSettings`, NIP-44 encrypted to self
(`AppSpecificState.saveNewAppSpecificData`). It already carries reactions, zap
amounts and default zap type, languages/translation, the security block,
video-player buttons, audio visualizer, pinned chatrooms, PoW settings, bottom
bar + drawer layout, and muted public chats. This is the mechanism that makes a
transition *automatic* — it needs no old phone, so it also covers the phone
that was lost, stolen or drowned.

**3. Device-local stores.** Everything below. Lost on transition.

## Inventory of device-local state

Per-account encrypted SharedPreferences (`amethyst_<npub>`), written by
`LocalPreferences.saveToEncryptedStorage`:

| State | Loss on a new phone | Fix |
| --- | --- | --- |
| `nwcWallets`, `clinkDebitWallets`, `defaultPaymentSourceId` | Cannot zap until every wallet is re-paired | Transfer file (secrets stay off relays) |
| ~35 per-feed `TopFilter` defaults (home, stories, discovery, articles, …) | Every feed resets to its stock filter | **Sync** |
| `enabledChatFeeds`, `enabledHomeFeedTypes` | Disabled feed types come back on | **Sync** |
| `defaultRelayAuthPolicy` + 4 trust toggles | Relay-auth posture resets to ALWAYS | **Sync** |
| `relayGroupViewMode`, `concordViewMode` | View modes reset | **Sync** |
| `hideDeleteRequestDialog`, `hideBlockAlertDialog`, `hideNIP17WarningDialog` | Dismissed warnings all return | **Sync** |
| `hasDonatedInVersion` | Donation nag returns for a supporter | **Sync** |
| `callsEnabled`, `splitNotificationsEnabled`, `showMessagesInNotifications` | Notification/call prefs reset | **Sync** |
| `dismissedPollNoteIds`, `dismissedChannelInvites`, `viewedPollResultNoteIds` | Dismissed things reappear | Transfer file (unbounded, churns) |
| `mutedPublicChats` | — | already synced |
| `lastReadPerRoute` | Every route shows unread | Transfer file (churns on every read) |
| `localRelayServers`, `defaultFileServer` | Local relay + media server reset | Transfer file |
| `torSettings` | Tor config resets | Transfer file |
| `pendingAttestations` | Pending OTS attestations dropped | Transfer file |
| `alwaysOnNotificationService` | Background service opt-in resets | Transfer file |
| `nostr_privkey` | — | Transfer file, always |
| `nip46BunkerSecret`, `nip46TransportKey`, `nip46SeenRequestIds` | Bunker-paired apps must re-pair | **Deliberately not transferred** — see below |

Other stores, outside that file:

| Store | Contents | Fix |
| --- | --- | --- |
| `cashu_prefs_<npub>.xml` (`CashuPreferences`) | NUT-13 counters per keyset | **Transfer file — highest severity** |
| UI shared settings (`SHARED_SETTINGS`) | Theme, language, autoplay | Transfer file (app-wide section) |

### App-wide stores, also carried

Named in `AccountTransferStores`, copied as raw bytes:

| Store | Contents |
| --- | --- |
| `shared_settings` | UI settings, Tor, OTS, Namecoin, Buzz workspaces/stars/attestations, relay-group deletions |
| `favorite_apps`, `browser_history` | Favorited apps, browser history |
| `napplet_permissions`, `napplet_storage`, `napplet_network`, `weburl_network` | Per-applet grants and sandboxed data |
| `relay_auth` | Per-relay NIP-42 AUTH decisions |
| `nip46_clients`, `nsp_*` | Connected-app signer pairings and permission grants |
| `amethyst_global_settings`, calendar reminder prefs, `chess_dismissed_games` | App-wide SharedPreferences files |
| `scheduled_posts.json` | Posts not yet published |

Two carry caveats worth knowing:

- **Scheduled posts** reference uploaded media by local path, so a row can land
  on the new phone pointing at a file that isn't there. Carried anyway: losing a
  queued post silently is worse than one that reports a missing attachment, and
  text-only posts are unaffected.
- **Connected-app pairings** travel, but the NIP-46 bunker identity does not
  (see below), so they only become live again once the user re-pairs.

### Marmot (MLS) — the archive travels, the crypto does not

Per account, under `accounts/<pubkeyHex>/`:

| Path | Contents | Travels? |
| --- | --- | --- |
| `mls_groups/<id>/messages` | decrypted inner event JSON | **Yes**, guarded |
| `mls_groups/<id>/state` | MLS ratchet state | No |
| `mls_groups/<id>/retained` | retained epoch secrets | No |
| `marmot_keypackages/state` | key package bundles (private keys) | No |

Cloning a ratchet onto a second device is exactly what MLS's forward secrecy
exists to prevent: two devices sharing one leaf can reuse a key at the same
epoch, and the ratchet's whole job is that old state is destroyed as it
advances. MLS handles multiple devices by adding each as its own member with its
own key package, never by copying. Key packages are consume-once and hold
private keys, so duplicating them is its own hazard. All of it is Keystore-
sealed anyway, so a byte copy would arrive undecryptable.

The consequence is the part worth stating plainly: the new phone rejoins as a
new member and **cannot decrypt anything sent before it joined**. Nor is the
history recoverable from the crypto state — `retained` is a short out-of-order
window for late-arriving messages (`tryDecryptWithRetainedEpoch`), not an
archive. So `messages` is the only thing that can carry a past conversation
across, which is why it travels even though the rest does not.

It is guarded rather than automatic because it cannot be authenticated.
MIP-03 inner events are unsigned rumors — `Account` replays them with
`justConsume(…, wasVerified = true)` precisely because they carry no Schnorr
signature, their authenticity having come from the MLS credential check at
decrypt time. That check cannot be redone from a file, so an archive is trusted
exactly as much as the bundle carrying it, and a hostile bundle could fabricate
a whole conversation attributed to anyone.

Rejoining is not something the transfer can do: an admin has to accept the new
device's key package. What the transfer does carry is `latestKeyPackageRelayList`
(kind 10051), so the new device knows where to publish.

### Deliberately dropped

| State | Why |
| --- | --- |
| Read markers (`last_read_route_per_route`) | Per-device reading history, unbounded, changes on nearly every interaction |
| Dismissed polls, viewed poll results | Same — history of what happened on the old phone |
| Pending OTS attestations | A request in flight belongs to the device that made it |
| `amethyst_secure_keys` | Keystore-sealed; a copy is undecryptable on the target |
| The account registry (`all_saved_accounts_info`, `currently_logged_in_account`) | Rebuilt by the importer as it adds each account |

### The one that costs money

`CashuPreferences` holds the NUT-13 deterministic-secret counter per keyset. The
wallet derives every blinded message from `(seed, keysetId, counter)`; the mint
permanently records which blinded messages it has signed and answers a repeat
with HTTP 400 "outputs already signed". The seed itself is in kind:17375 on
relays, so it *does* follow the user — which is precisely what makes this
dangerous. A new phone restores the seed, starts the counter at zero,
re-derives outputs the mint has already signed, and that keyset stops working.

Counters therefore travel in the transfer file and merge by **max**, never
overwriting a higher local value (`mergeCounters`).

### What deliberately does not travel

- **NIP-46 bunker identity.** Copying it leaves two phones answering as the same
  bunker with divergent request-id histories. The new phone re-pairs. The
  user-facing "act as a signer" toggle does travel.
- **The secret key**, unless the user explicitly opts in. A file exported as
  "my settings" must not silently turn out to carry the identity.

## The two mechanisms

**Sync (NIP-78), for portable non-secret settings.** Zero user action: log in on
the new phone and the settings arrive. Works with no access to the old phone.
Added as one nullable `app` section on `AccountSyncedSettingsInternal`, every
field nullable so an older client that rewrites the blob without them is read as
"absent, leave local alone" rather than "reset to default" — the discipline
`AccountChatPreferencesInternal.mutedPublicChats` already documents.

**Transfer file, for everything else.** One password-encrypted file
(`AccountTransferEnvelope`: scrypt + XChaCha20-Poly1305, the NIP-49
construction over an arbitrary payload). Carries per-account preference maps
*verbatim* rather than a hand-written field list, so settings added later are
covered without editing an export function — the omission this whole feature
exists to prevent. Exclusions are named in `AccountTransferKeys`.

Wallet secrets stay out of the sync path on purpose: NIP-44-to-self on a relay
means a future key compromise escalates from identity loss to wallet drain, and
the ciphertext sits in relay storage indefinitely.

## What shipped

**Sync path**
- `AccountAppPreferencesInternal` — the new, all-nullable `app` section on the
  blob.
- `PortableAccountSettings` + `mergePortableSettings` — the snapshot and the
  absent-vs-explicit merge, kept out of `AccountSettings` so they are testable
  (that class builds language preferences via `Resources.getSystem()` and cannot
  be constructed in a JVM test).
- `AccountSettings.portableSettings()` / `applyPortableSettings()` /
  `feedFilterFlows()` — one table of feed ids shared by the wire format, the
  preference file and the transfer bundle.
- `Account.publishPortableSettingsIfChanged()` — hangs off the existing
  debounced save rather than ~40 individual setters, and compares the serialized
  form so a save that touched nothing portable publishes nothing.

**Transfer path**
- `AccountTransferBundle` / `TransferValue` / `AccountTransferKeys` /
  `AccountTransferValues` in `commons` — versioned model, typed values, the
  exclusion list, and the preference-map conversion.
- `AccountTransferEnvelope` — scrypt + XChaCha20-Poly1305 over the whole
  payload, with the header authenticated so the scrypt cost can't be downgraded.
- `AccountTransferService` + `LocalPreferences.exportAccountPreferences` /
  `importAccount` + `CashuPreferences.exportCounters` / `importCounters`.
- `DeviceTransferScreen`, reachable from Settings.

**Also fixed:** `LocalPreferences.setDefaultAccount` gained an upgrade guard
mirroring its existing downgrade guard. Signing in for a pubkey that already has
settings on the device now keeps them and adopts the key, instead of saving
fresh defaults over them. Without it the ordinary transfer sequence — import the
settings, then log in with the key — erased everything that had just been
imported.

## Backup / device-transfer rules

While answering "does Android's phone transfer carry this?", two things in
`data_extraction_rules.xml` turned out to be wrong, and one assumption behind
them turned out to be false.

The assumption: the file described itself as defense-in-depth because
`allowBackup="false"` was set. The platform docs say otherwise — "On devices
from some device manufacturers, specifying `android:allowBackup="false"`
disables cloud-based backup and restore ... but doesn't disable device-to-device
transfers for the app." On those devices the `<device-transfer>` section is the
only thing standing between app data and a D2D copy.

The two bugs, both of which meant the per-account files were not actually
excluded:

- `<exclude domain="sharedpref" path="secret_keeper.xml"/>` matches only the
  global file. `EncryptedStorage.prefsFileName()` names the per-account files
  `secret_keeper_<npub>.xml`, and `path` "does not support wildcard or regular
  expression syntax" — so no fixed filename can cover them.
- The rule meant to catch the rest,
  `<exclude domain="sharedpref" path="." requireFlags="clientSideEncryption"/>`,
  uses an attribute valid only on `<include>` and "NOT available in the Android
  12+ `<data-extraction-rules>` format".

Both files now exclude every domain wholesale via `path="."`, which sidesteps
the wildcard limitation entirely. Nothing transfers, which matches the app's
intent: these files are encrypted with a hardware-bound Keystore master key, so
a copy on another phone is undecryptable and could only produce a device holding
files it cannot open.

## Known limits

- An account that is already loaded holds its settings in memory and would write
  them back over an import on its next save. The screen tells the user to
  restart; a cleaner fix is to reload the live account after importing.
- Cross-version sync drops what the older build has no enum for (a feed type,
  a view mode), the same way `hiddenDrawerItems` already does. Feed filters are
  merged per key, so those survive. Neither case loops: each side records what
  it actually holds, so the exchange converges rather than ping-ponging.
- The stores in "Not covered yet" above still don't travel.
- The Android preference I/O has no unit tests — there is no Robolectric in this
  repo. The conversion logic was extracted to `AccountTransferValues` in
  `commons` so at least the part that can silently corrupt an import (value
  types) is covered.
