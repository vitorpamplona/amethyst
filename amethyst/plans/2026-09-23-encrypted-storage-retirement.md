# Retiring EncryptedStorage

Status: **blocked** — the legacy files cannot be deleted yet, and the reader
can never be.

## The constraint

Every migration in the preference layer is *lazy*: it reads the legacy store
when it runs, not when the app is installed. Nine come through
`EncryptedStorage` — the seven `CopyOnceMigration`s in `LocalPreferences` plus
the key, secret and roster stores. Only the Cashu counters and calendar
reminders read a plain (non-encrypted) source and would survive its removal.

So deleting `EncryptedStorage` does not merely affect installs that have not
upgraded yet. It strands anyone who **skips** the release introducing the new
stores: a pre-migration build upgrading straight to a post-deletion build runs
its migration against a reader that no longer exists. Keys, accounts, wallets
and settings stay encrypted on disk with nothing able to read them, and the app
opens as a fresh install. Auto-update off, the F-Droid cadence and restoring
from a backup all skip releases.

**The reader is permanent.** What a later release can retire is the legacy
*write*, which stops new data landing there while old data stays readable.
`androidx.security.crypto` has to stay for as long as the reader does. That is
an unmaintained-library risk, not an active vulnerability, and a much smaller
cost than stranding users.

## Outstanding before any legacy file is deleted

Deletion is only safe for an account whose every key has a new home. These do
not yet, and are still read from the legacy files:

| key | scope | if deleted today |
|---|---|---|
| `NOSTR_PUBKEY` | per-account | **fatal** — `loadAccountConfigFromEncryptedStorage` returns null without it, so the account disappears even though its private key migrated |
| `LOGIN_WITH_EXTERNAL_SIGNER` | per-account | external-signer accounts stop resolving their signer |
| `SIGNER_PACKAGE_NAME` | per-account | as above |
| `HAS_BACKED_UP_KEYS` | per-account | the key-backup nag returns for everyone |
| `LOCAL_RELAY_SERVERS` | per-account | silently lost |
| `OPEN_BACKUP_CONFLICTS` | per-account | silently lost |
| `SHARED_SETTINGS` | global | UI settings reset |

## Deliberately not migrated

Three keys are accepted losses rather than outstanding work — the cost of
losing them is one-off and small, and carrying them is not worth the code:

| key | what is lost |
|---|---|
| `PENDING_ATTESTATIONS` | queued OTS attestations are not published |
| `NOTIF_GLOBAL_TO_CURATED_MIGRATED` | the one-shot notification filter migration runs once more |
| `LAST_READ_PER_ROUTE` | every feed reads as unread once |

`NotificationPrefsStore` was given `hasRunGlobalToCuratedMigration`,
`markGlobalToCuratedMigrated`, `lastReadPerRoute` and `saveLastReadPerRoute`
for the last two of these. Nothing ever called them, and now nothing will;
they have been removed rather than left looking like a feature.

`USE_PROXY` and `PROXY_PORT` need nothing either: they are only ever `remove`d,
being cleaned up rather than read.

## Order of work

1. Migrate the seven keys above, on the same dual-store terms as the rest.
2. Add a per-account completeness check — every key present in the new stores —
   and only then delete that account's legacy file, after reading back what was
   written.
3. Retire the legacy writes once (2) holds for every account on a device.
4. Keep the reader, and the dependency, indefinitely.

## Verification this needs and has not had

None of the AndroidKeyStore paths have executed: this environment has no device
or emulator, and `commons` has no Robolectric. What is tested is the decision
logic against fakes. On a real device, before any deletion ships: upgrade an
install holding accounts and confirm they all list; open one and sign;
force-stop and relaunch; add and remove an account; pair a NIP-46 signer; pay
from a wallet.
