# Amy — Amethyst CLI

`amy` is the non-interactive command-line face of Amethyst. It speaks the
same Nostr protocol as the Android and Desktop apps, shares the same
`quartz` and `commons` code, and aims to eventually expose every feature
the GUI offers as a command you can script.

Amy exists for three audiences at once:

1. **Humans** using Amethyst from a terminal or remote shell.
2. **Agents / LLMs** driving a Nostr account through a deterministic,
   JSON-typed interface — no interactive prompts, no screen scraping.
3. **Interop test harnesses** that put Amethyst side-by-side with the
   other ~100 Nostr clients publishing and consuming the same events.
   Any flow that is tested in the Amethyst app should be reproducible
   through `amy` — that's the bar.

> Today Amy covers identity, relay config, account bootstrap, and
> Marmot / MLS group chat (MIP-00 / NIP-445). Everything else from the
> Android app is on the roadmap — see [ROADMAP.md](./ROADMAP.md).
>
> To extend Amy, see [DEVELOPMENT.md](./DEVELOPMENT.md).

---

## Output contract

What every caller — user, script, agent, CI — can rely on:

- **stdout is JSON. One line. One object.** Stable snake_case keys.
  Pipe it into `jq`, parse it from Python, hand it to an agent.
- **stderr is for humans.** Progress, warnings, per-relay ACK traces.
  Safe to discard.
- **Exit codes are the real signal.**
  - `0` — success
  - `1` — runtime error (JSON `{"error":"…","detail":"…"}` on stderr)
  - `2` — bad arguments
  - `124` — `await` timed out
- **No interactive prompts, ever.** Passwords, names, keys — all flags.
- **Data-dir is the whole world.** All state (identity, relays, MLS
  epochs, message archives, run cursors) lives under `--data-dir PATH`.
  Delete to reset; copy to move; `AMETHYST_CLI_DATA` env var overrides
  the default `./amy`.

The rationale behind each of these lives in
[DEVELOPMENT.md](./DEVELOPMENT.md). Breaking any of them is a breaking
change to Amy's public API.

---

## Local event store — the source of truth

Every Nostr event Amy observes is verified (NIP-01 id + signature
check) and persisted to a file-backed store at
`<data-dir>/events-store/`. That includes:

- events received from any relay subscription (`amy feed`, `amy dm
  list`, `amy keypackage publish`, group sync, …),
- events Amy generates and publishes itself,
- inner events unwrapped from NIP-59 gift wraps.

Malformed events are dropped before reaching command code. Persistence
is best-effort — if the store fails (full disk, permissions), the
relay subscription still works, but the event is not cached.

The store is the authoritative cache of everything Amy has seen:
profile metadata, relay lists (NIP-65 and NIP-02), gift wraps, group
events, follow lists, etc. Commands that need any of these should read
from the store first and only fall back to a relay fetch on miss.
Three convenience helpers exist on `Context`:

```kotlin
ctx.profileOf(pubKey)            // latest kind:0    (NIP-01)
ctx.relaysOf(pubKey)             // latest kind:10002 (NIP-65)
ctx.contactsOf(pubKey)           // latest kind:3    (NIP-02)
ctx.dmInboxOf(pubKey)            // latest kind:10050 (NIP-17 DM inbox)
ctx.keyPackageRelaysOf(pubKey)   // latest kind:10051 (MIP-00 KP relays)
ctx.cachedRelayListsOf(pubKey)   // RecipientRelayFetcher.Lists from cache
```

Commands that already read these cache-first:

- `amy profile show` — `--refresh` to bypass.
- `amy feed --following` — local kind:3 served via slot lookup; falls
  back to a relay drain on first run.
- `amy dm send` — recipient's kind:10050 / 10051 / 10002 served from
  cache before falling back to `RecipientRelayFetcher`.
- `amy marmot key-package check` and `amy marmot await key-package`
  — same recipient-relay lookup, cache-first.
- `amy marmot group add` — invitee relay lists served from cache.

The store implements every feature of the Quartz SQLite store —
NIP-01 replaceable / addressable uniqueness, NIP-09 deletion
tombstones, NIP-40 expiration, NIP-50 search, NIP-62 right-to-vanish,
NIP-91 multi-tag AND. See `cli/plans/2026-04-24-file-event-store-*.md`
for the design and `quartz/.../store/fs/FsEventStore.kt` for the
implementation. The on-disk layout is plain JSON files under shard
directories, intentionally inspectable with `ls`, `cat`, `jq`,
`grep`, `find`, `rsync`, and `git`.

To manage the store directly:

```sh
# raw inspection
find $AMY_HOME/events-store/events -name '*.json' | head
jq . $AMY_HOME/events-store/replaceable/0/<pubkey>.json

# delete a specific event (tombstone NOT installed — see below)
rm $AMY_HOME/events-store/events/<aa>/<bb>/<id>.json
```

Deleting an event file is treated as a deliberate "I never saw this"
by Amy. The store tolerates external edits: dangling index entries are
skipped at query time and can be cleaned up with `compact()` /
`scrub()` from the API.

---

## Install

Until Amy ships as a signed native binary (see
[cli/plans/2026-04-21-cli-distribution.md](./plans/2026-04-21-cli-distribution.md)),
run it from source:

```bash
# One-shot run — positional args go after `--args`, quoted as one string
./gradlew :cli:run --quiet --args="whoami"

# Or build a runnable distribution and use the generated launch script
./gradlew :cli:installDist
./cli/build/install/amy/bin/amy whoami
```

The `installDist` tree under `cli/build/install/amy/` is self-contained
(JVM launcher + jars) and is what downstream packaging will wrap.

**Requirements:** JDK 21.

---

## Quick start

```bash
# 1. Create a data-dir with a full Amethyst-style account.
#    Generates a keypair, seeds default NIP-65 / inbox / key-package
#    relays, and publishes the nine bootstrap events.
amy --data-dir ./alice create --name "Alice"

# 2. Publish a fresh MLS KeyPackage so others can invite you.
amy --data-dir ./alice marmot key-package publish

# 3. Create a group, invite someone, send a message.
amy --data-dir ./alice marmot group create --name "Test Group"
amy --data-dir ./alice marmot group add <GID> npub1...bob
amy --data-dir ./alice marmot message send <GID> "hello"

# 4. On the receiving side — poll until Bob sees the invite.
amy --data-dir ./bob marmot await group --name "Test Group" --timeout 60
amy --data-dir ./bob marmot message list <GID>
```

Compose with `jq` to chain commands:

```bash
GID=$(amy --data-dir ./alice marmot group create --name "Test" | jq -r .group_id)
```

For an interop-test script template, see
[DEVELOPMENT.md § Testing](./DEVELOPMENT.md#testing). The runnable
harnesses live under [`cli/tests/`](./tests/README.md) —
`cli/tests/marmot/` for MLS group messaging vs whitenoise-rs,
`cli/tests/dm/` for NIP-17 DMs between two `amy` clients.

---

## Command reference

Run `amy --help` for the canonical list. As of today:

| Verb | Summary |
|---|---|
| `init [--nsec NSEC]` | Create or import a bare identity. Does not publish anything. |
| `create [--name NAME]` | Provision a full account + publish the nine Amethyst bootstrap events. |
| `login KEY [--password X] [--private]` | Import `nsec` / `ncryptsec` / BIP-39 mnemonic / `npub` / `nprofile` / hex / NIP-05. Read-only when no secret material is supplied. |
| `whoami` | Print the identity stored in `--data-dir`. |
| `profile show [PUBKEY] [--refresh] [--timeout SECS]` | Print kind:0 metadata. Default reads from the local store (cache-first); `--refresh` forces a relay drain. PUBKEY accepts `npub` / `nprofile` / hex / `name@domain.tld`; omit for self. |
| `profile edit --name X [--display-name X] …` | Build + publish a new kind:0 starting from the current cached metadata (or fetched if missing). |
| `relay add URL [--type T]` | `T = nip65 \| inbox \| key_package \| all`. |
| `relay list` | Dump configured relays by bucket. |
| `relay publish-lists` | Publish kind:10002 (NIP-65) + kind:10050 (DM inbox) + kind:10051 (KeyPackage relay list). |
| `marmot key-package publish` | Publish a fresh MLS KeyPackage (kind:30443) to the configured `key_package` bucket (fallback: NIP-65 outbox). |
| `marmot key-package check NPUB` | Look up NPUB's kind:10051 / kind:10002 on bootstrap relays, then fetch their KeyPackage from those relays. |
| `marmot group create [--name NAME]` | New empty group with you as sole admin. |
| `marmot group list` | All groups you're a member of. |
| `marmot group show GID` | Full group state (members, admins, epoch, metadata). |
| `marmot group members GID` | Members only. |
| `marmot group admins GID` | Admins only. |
| `marmot group add GID NPUB [NPUB…]` | Fetch KeyPackages for the npubs and commit an add. |
| `marmot group rename GID NAME` | Commit a metadata change. |
| `marmot group promote GID NPUB` | Make an existing member an admin. |
| `marmot group demote GID NPUB` | Revoke admin. |
| `marmot group remove GID NPUB` | Remove a member. |
| `marmot group leave GID` | Self-remove. |
| `marmot message send GID TEXT` | Publish a kind:9 inner event into the group. |
| `marmot message list GID [--limit N]` | Decrypted inner events, oldest first. |
| `marmot await key-package NPUB` | Block until a KeyPackage is seen on NPUB's advertised relays (kind:10051 / kind:10002). |
| `marmot await group --name NAME` | Block until we're added to a group with that name. |
| `marmot await member GID NPUB` | Block until NPUB is in GID's member set. |
| `marmot await admin GID NPUB` | Block until NPUB is an admin of GID. |
| `marmot await message GID --match TEXT` | Block until a message containing `TEXT` lands. |
| `marmot await rename GID --name NAME` | Block until GID's name matches. |
| `marmot await epoch GID --min N` | Block until GID's MLS epoch is ≥ N. |
| `dm send RECIPIENT TEXT [--allow-fallback]` | Send a NIP-17 gift-wrapped text DM (kind:14 inside kind:1059). Default delivers only to the recipient's kind:10050 (per NIP-17); pass `--allow-fallback` to fall back to kind:10002 read marker → bootstrap pool. |
| `dm send-file RECIPIENT --file PATH --server URL [--mime-type M] [--allow-fallback]` | Encrypt the local file with a fresh AES-GCM cipher, upload the ciphertext to the Blossom server, then publish a kind:15 NIP-17 file message referencing the returned URL. The auto-detected hash, size, dimensions, and blurhash from the upload are folded into the event. The response also surfaces the encryption key + nonce so the same blob can be re-shared without re-uploading. |
| `dm send-file RECIPIENT URL --key HEX --nonce HEX [--mime-type M] [--hash H] [--original-hash H] [--size N] [--dim WxH] [--blurhash S] [--allow-fallback]` | Reference-mode variant: the file is already uploaded; `--key`/`--nonce` carry the AES-GCM material that recipients use to decrypt the bytes at `URL`. Useful when the upload happened elsewhere or to re-publish a previously-uploaded blob. |
| `dm list [--peer NPUB] [--since TS] [--limit N] [--timeout SECS]` | Drain and decrypt gift wraps on our inbox relays. Returns kind:14 (text) and kind:15 (file) messages with a `type` discriminator. With neither `--peer` nor `--since` the gift-wrap cursor in `state.json` is advanced to the newest message seen. |
| `dm await --peer NPUB --match TEXT [--timeout SECS]` | Block until a DM from NPUB containing TEXT arrives (matches text content for kind:14, URL for kind:15). Timeout exits 124. |

All `await` verbs accept `--timeout SECS` (default 30). Timeout exits 124
so scripts can distinguish "condition never happened" from "the command
itself crashed".

### Global flags

- `--data-dir PATH` — defaults to `./amy` or
  `$AMETHYST_CLI_DATA`. Always an absolute path after resolution.
- `--help` / `-h` — usage summary.

---

## Relay routing

Amy follows the Marmot protocol's per-event routing rules so two users
with completely disjoint relay configurations can still marmot each
other. No event ever ships blindly to "our configured relays" — Amy
looks up the right relay set per event per recipient.

| Event | Publish to | Fetch from |
|---|---|---|
| kind:30443 (our own KeyPackage) | `key_package` bucket → NIP-65 outbox → any configured | — |
| kind:30443 (someone else's KeyPackage) | — | Their kind:10051 → their kind:10002 write → our bootstrap pool |
| kind:10051 / 10050 / 10002 (our own lists) | All configured relays (broadcast) | — |
| kind:10051 / 10050 / 10002 (someone else's) | — | Our bootstrap pool = configured relays ∪ Amethyst defaults |
| kind:1059 Welcome gift wrap (kind:444 inside) | Recipient's kind:10050 → their kind:10002 read → `DefaultDMRelayList` → our outbox | — |
| kind:1059 gift wraps addressed to us | — | Our kind:10050 |
| kind:445 Group Event (Commit / Proposal / chat) | Group's MIP-01 `relays` field | Same |

**Bootstrap pool**: when Amy needs to discover a user it's never talked
to, it queries `configured relays ∪ Amethyst's default NIP-65 set ∪
Amethyst's default DM-inbox set`. These defaults come from
`commons.defaults.AmethystDefaults` and match what the Android/Desktop
UI publishes to on first run, so any fresh Amethyst account is
reachable via the bootstrap pool even before Amy has seen any of their
events.

---

## Data-dir layout

```
<data-dir>/
├── identity.json             # nsec/npub/hex — the account
├── state.json                # sync cursors (giftWrapSince, groupSince)
├── events-store/             # FsEventStore — every observed Nostr event
│   ├── events/<aa>/<bb>/…    # canonical kind:0 / 3 / 10002 / 10050 / 10051 / 1 / 5 / 1059 / …
│   ├── replaceable/<k>/…     # one slot per (kind, pubkey) for kind:0/3/10000-19999
│   ├── addressable/…         # one slot per (kind, pubkey, d-tag) for kind:30000-39999
│   ├── idx/                  # hardlink indexes (kind / author / owner / tag / fts / expires_at)
│   └── tombstones/           # NIP-09 / NIP-62 enforcement
└── marmot/
    ├── keypackages.bundle    # MLS KeyPackage bundles (NostrSignerInternal)
    └── groups/
        ├── <gid>.mls         # MLS group state per group
        └── <gid>.log         # decrypted inner events (one JSON per line)
```

All files are plain JSON or framed binary — human-inspectable, easy to
diff across two data-dirs in a test run.

The local relay configuration (kind:10002 / 10050 / 10051) is **not** a
separate file — it lives in `events-store/` as signed events.
`amy relay add` builds + signs + ingests a new relay-list event;
`amy relay list` reads URLs straight out of the latest event for each
kind; `amy relay publish-lists` broadcasts those events to upstream
relays. There is no `relays.json`.

---

## Troubleshooting

- **`no identity`** — run `init`, `create`, or `login` first, or pass a
  different `--data-dir`.
- **`not_member`** — the group GID is unknown to this data-dir. Run
  `marmot group list` to confirm, or `marmot await group --name …` to
  wait for an invite to arrive.
- **Hang on a network verb** — Amy connects to the relays advertised
  in your local kind:10002 / 10050 / 10051 events; inspect with
  `amy relay list`. Every network-bound operation has a timeout — use
  `--timeout` for `await`, or wrap the whole command in `timeout(1)`
  if you're scripting.
- **Nothing seems to publish** — inspect stderr; each publish prints
  per-relay `OK` / `REJECT` via the `[cli] …` traces.
