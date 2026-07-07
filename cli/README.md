# Amy — Amethyst CLI

`amy` is the command-line face of [Amethyst](https://github.com/vitorpamplona/amethyst).
It speaks the same Nostr protocol as the Android and Desktop apps and shares
their codebase. From a terminal you can post notes, send NIP-17 DMs,
manage MLS group chats, switch identities, and pipe machine-readable JSON
into the rest of your toolbox.

`amy` is built for three audiences at once: humans at a terminal,
agents/LLMs driving an account through a deterministic JSON interface,
and interop test harnesses pinning Amethyst against the rest of the
Nostr-client ecosystem.

> **Looking for the architecture and the public-API contract?** See
> [DEVELOPMENT.md](./DEVELOPMENT.md). For what's coming, see
> [ROADMAP.md](./ROADMAP.md).

---

## Install

`amy` builds from this repository — no package manager yet.

```bash
# build the runnable distribution
./gradlew :cli:installDist

# the launch script
./cli/build/install/amy/bin/amy --help

# put it on your PATH if you want
ln -s "$PWD/cli/build/install/amy/bin/amy" ~/.local/bin/amy
```

Requires **JDK 21**. All state lives under `~/.amy/` — delete to reset.

---

## Quick start

```bash
# 1. Create an account named alice — keypair, default relays, kind:0 metadata,
#    everything Amethyst stamps on first run.
amy --account alice create --name "Alice"

# 2. With one account you can drop the flag from now on (auto-pick).
amy whoami

# 3. Post a short note.
amy notes post "hello from amy"

# 4. Send a NIP-17 DM.
amy dm send bob@example.com "hey"

# 5. Read your inbox.
amy dm list
```

That's the full loop. Add `--json` to any command if you want a single-line
JSON object instead of human-readable text — same data, machine shape.

---

## Examples

### 1. Post a note

```text
$ amy notes post "good morning nostr"

event_id:    a3c1f9c2…(64 hex)
kind:        1
accepted_by:
  - wss://relay.damus.io/
  - wss://nos.lol/
rejected_by: (none)
```

`amy notes feed` reads recent kind:1 notes from your follows; `--limit N`
caps the count, `--author npub1…` narrows to one user.

### 2. Send a direct message

```text
$ amy dm send npub1uu8m… "lunch friday?"

event_id:   18bd0a7e…
kind:       14
recipients:
  - pubkey:        e70fb804…
    relay_source:  kind_10050
    relays:
      - wss://nostr.wine/
```

`recipients[*].relay_source` tells you how amy resolved the recipient's
inbox — `kind_10050` is the strict NIP-17 inbox; `nip65_read` /
`bootstrap` only fire when you pass `--allow-fallback`.

### 3. Read a DM thread

```text
$ amy dm list --peer npub1uu8m… --limit 5

messages:
  - event_id:   a82f04e1…
    author:     71cf3ab2…
    type:       text
    created_at: 2026-04-25 13:42:11Z (8m ago)
    content:    sounds good
  - event_id:   18bd0a7e…
    author:     e70fb804…
    type:       text
    created_at: 2026-04-25 13:30:02Z (20m ago)
    content:    lunch friday?
```

`amy dm await --peer NPUB --match TEXT --timeout 60` blocks until a matching
DM arrives — useful in scripts.

### 4. View a profile

```text
$ amy profile show npub1th9z…

pubkey:         5dca27ae…
found:          yes
source:         cache
event_id:       a041df5a…
created_at:     2026-04-25 13:36:23Z (1h ago)
metadata:
  name:    Alice
  picture: https://example.test/a.png
  about:   demo identity
  nip05:   alice@example.test
queried_relays: (none)
```

`source: cache` means the local store served the lookup; pass `--refresh` to
force a relay round-trip. Profiles for `name@domain.tld` (NIP-05) are
resolved transparently.

### 5. Create a group, invite someone, send a message

```bash
# Mint a group and invite Bob.
GID=$(amy --json marmot group create --name "Lunch Plans" | jq -r .group_id)
amy marmot group add "$GID" npub1...bob

# Send an MLS-encrypted message.
amy marmot message send "$GID" "hello group"
```

On the other side:

```bash
# Bob waits for the invite to land, then sees the message.
amy --account bob marmot await group --name "Lunch Plans" --timeout 60
amy --account bob marmot message list "$GID"
```

### 6. Switch between accounts

```text
$ amy whoami
error: bad_args: multiple accounts in /home/me/.amy (alice, bob); pick one with --account <name> or `amy use <name>`

$ amy use bob
current: bob
root:    /home/me/.amy

$ amy whoami
name:     bob
npub:     npub1uu8m…
data_dir: /home/me/.amy/bob
```

`amy use --clear` removes the pin; `amy --account alice <cmd>` overrides
it for one command.

### 7. Add a relay

```text
$ amy relay add wss://nostr.wine

url:             wss://nostr.wine
added_to:
  - nip65
  - inbox
  - key_package
already_present: (none)

$ amy relay search add wss://relay.nostr.band    # search-only bucket
$ amy relay blocked set wss://bad.relay          # replace the blocked list
$ amy relay outbox add wss://nostr.wine          # NIP-65 write relay

$ amy relay publish-lists      # broadcast every updated relay list
```

---

## Commands

### Primitives (stateless — no account or network)

Army-knife verbs that operate purely on their arguments. They never touch
`~/.amy/`, so they run with zero state — handy for scripting and piping
(`amy decode … | jq`, `… | amy verify`).

| Command | What it does |
|---|---|
| `amy decode ENTITY` | Decode a NIP-19/21 entity (`npub`/`nsec`/`note`/`nevent`/`nprofile`/`naddr`/`nrelay`/`nembed`) to JSON. Accepts an optional `nostr:` prefix. |
| `amy encode npub HEX` / `nsec HEX` / `note ID` | Encode a single 32-byte hex value into the matching NIP-19 entity. |
| `amy encode nevent ID [--author HEX] [--kind N] [--relay URL[,URL…]]` | Encode an event pointer with optional author/kind/relay hints. |
| `amy encode nprofile HEX [--relay URL[,URL…]]` | Encode a profile pointer with optional relay hints. |
| `amy encode naddr --kind N --pubkey HEX --identifier D [--relay URL[,URL…]]` | Encode an addressable-event (`a` tag) pointer. |
| `amy verify [EVENT-JSON]` | Check an event's id hash and signature. Reads stdin when the argument is omitted or `-`. Reports `id_ok` + `signature_ok` separately. |
| `amy key generate` | Mint a fresh keypair (`nsec` + `npub` + hex). Does not persist — use `init`/`login` for that. |
| `amy key public NSEC\|HEX` | Derive the public key from a secret key. |
| `amy key encrypt NSEC\|HEX --password X` | NIP-49 encrypt a secret key to an `ncryptsec1…`. |
| `amy key decrypt NCRYPTSEC --password X` | NIP-49 decrypt back to nsec/hex/npub. |
| `amy key validate NPUB\|HEX` | Parse-check a public key. Prints `{valid, pubkey, npub}` or `{valid:false}` — never errors, so scripts branch on the field. |
| `amy filter [filter flags]` | Assemble and print a NIP-01 filter JSON from the same flags `fetch`/`subscribe` use — no query is sent. |
| `amy nip N` / `amy nip list` | Look up a NIP — the `nostr-protocol/nips` repo first, then a Nostr wiki/long-form fallback. `list` fetches the index. |
| `amy kind N` / `amy kind NAME` | Look up an event kind's label + defining NIP (number), or search labels by name. Backed by quartz's `KindNames` registry. |
| `amy namecoin resolve IDENT [--server HOST:PORT[:tcp][,…]] [--timeout SECS]` | Resolve a Namecoin identifier (`.bit`, `d/`, `id/`, `alice@example.bit`) to a Nostr pubkey + relays via the Namecoin blockchain. Stateless: talks directly to one or more ElectrumX servers over TLS (`:tcp` for plaintext), no account needed. Reuses the same NIP-05-Namecoin parser, server set, and pinned trust store as the Android and Desktop apps. |
| `amy namecoin servers` | Print the default ElectrumX server list (host, port, TLS flag). |
| `amy relay info URL` | Fetch and print a relay's NIP-11 information document. |

### Remote signing (NIP-46 bunker)

Two amy processes can talk: one **hosts** a bunker with its local key; the other **logs in** through it and signs remotely (events come out authored by the host's key).

| Command | What it does |
|---|---|
| `amy bunker [--relay URL[,URL…]] [--secret S] [--timeout SECS]` | Run a NIP-46 remote signer for the active local-key account. Prints a `bunker://…` URI, then services sign / nip04 / nip44 / get_public_key / ping requests until interrupted (or `--timeout`). |
| `amy login bunker://PUBKEY?relay=…&secret=…` | Log in through a bunker (signer advertises). Mints a local transport keypair; the account then acts as PUBKEY and every signing/encryption call is delegated to the remote signer. Percent-encoded relay params are decoded. |
| `amy bunker connect nostrconnect://…` | Client-initiated (NostrConnect) flow, signer side: ack a client's offer (echo its secret) and service its requests. |
| `amy login --nostrconnect [--relay URL[,URL…]] [--name N] [--timeout SECS]` | Client-initiated flow, client side: print a `nostrconnect://` offer, wait for a signer to connect, then persist a bunker account that acts as the signer's key. |

Interop-tested against the real [`nak`](https://github.com/fiatjaf/nak) binary:
- **bunker:// both directions** — `amy login bunker://` ⇄ `nak bunker`, and `nak event --sec bunker://` ⇄ `amy bunker`.
- **nostrconnect:// client** — `amy login --nostrconnect` ⇄ `nak bunker connect` (amy signs, event authored by nak's key).

Supports `connect` (secret-checked), `get_public_key`, `get_relays`, `sign_event`, `nip04_encrypt/decrypt`, `nip44_encrypt/decrypt`, `ping`. When a bunker answers with an `auth_url` challenge, amy prints the authorization URL to stderr and keeps waiting for the real response (open the URL in a browser to authorize).

Example (two terminals, shared `$HOME`):

```bash
# terminal 1 — host alice's key as a bunker
amy --account alice bunker --relay wss://relay.example --secret s3cret
#   → bunker://<alice-pubkey>?relay=wss://relay.example/&secret=s3cret

# terminal 2 — bob signs through it
amy --account bob login 'bunker://<alice-pubkey>?relay=wss://relay.example/&secret=s3cret'
amy --account bob event --kind 1 --content "signed remotely"   # authored by alice
```

### Raw events

| Command | What it does |
|---|---|
| `amy event --kind N [--content TEXT] [--tags JSON] [--created-at TS]` | Build + sign an arbitrary event with the active account. Prints the signed event. `--tags` is a JSON array-of-arrays, e.g. `'[["t","nostr"],["e","<id>"]]'`. |
| `amy event … --publish` / `--relay URL[,URL…]` | As above, then broadcast (to the outbox, or to the given relays). |
| `amy publish [EVENT-JSON] [--relay URL[,URL…]]` | Broadcast a pre-made signed event (verified first). Reads stdin when the argument is omitted or `-`. |

### Queries

Filter flags are shared by `fetch` and `subscribe`: `--kind K[,K]`, `--author U[,U]` (npub/nprofile/hex), `--id ID[,ID]` (note/nevent/naddr/hex), `--tag e=ID,p=PK,t=hashtag`, `--since TS`, `--until TS`, `--limit N`, `--search TEXT`, `--relay URL[,URL…]`. Relays default to your outbox, then the bootstrap set.

| Command | What it does |
|---|---|
| `amy fetch [filter flags] [--timeout SECS]` | One-shot query — collect until every relay sends EOSE (or `--timeout`, default 8s), dedupe, sort newest-first, print and exit. `--limit` defaults to 100. |
| `amy fetch CODE [--timeout SECS]` | Code mode — pass a single `nevent`/`naddr`/`nprofile`/`npub`/`note` or `name@domain`. Resolves relays the outbox way: the hints embedded in the code **plus** the author's NIP-65 write relays (draining their kind:10002 on a cache miss), exactly how the app opens a shared link. |
| `amy subscribe [filter flags] [--timeout SECS]` | Live stream — print each matching event as it arrives (NDJSON under `--json`). Runs until `--timeout` SECS or until interrupted. |
| `amy count [filter flags] [--timeout SECS]` | NIP-45 COUNT — per-relay match counts, no event download. |
| `amy outbox USER [--refresh] [--timeout SECS]` | Show USER's NIP-65 read/write relays (outbox model). Cache-first; `--refresh` forces a relay drain. |
| `amy sync --relay URL [filter flags] [--down] [--up]` | NIP-77 Negentropy reconcile between the local store and a relay. `--down` (default) pulls events we lack; `--up` pushes events the relay lacks; both for bidirectional. |

### Encryption

| Command | What it does |
|---|---|
| `amy encrypt --to USER [TEXT] [--nip04]` | NIP-44 (default) or NIP-04 encrypt with the active account's key. Reads stdin when TEXT is omitted or `-`. USER accepts npub/nprofile/hex/NIP-05. |
| `amy decrypt --from USER [CIPHERTEXT] [--nip04]` | Inverse of `encrypt`. |
| `amy gift wrap --to USER [EVENT-JSON] [--relay …]` | NIP-59: seal a signed inner event for USER and wrap it in a kind:1059 gift wrap. Prints the wrap; `--relay` also broadcasts it. |
| `amy gift unwrap [GIFTWRAP-JSON]` | Decrypt + unseal a kind:1059 wrap addressed to the active account; prints the inner event. |

### Git (NIP-34)

nak's `clone`/`push`/`pull` (git-packfile transport over relays/GRASP) are out of scope — these are the metadata + collaboration events.

| Command | What it does |
|---|---|
| `amy git announce --name N [--description D] [--clone URL[,URL]] [--web URL[,URL]] [--relay URL[,URL]] [--maintainer HEX[,HEX]] [--hashtag T[,T]] [--earliest-commit C] [--d ID]` | Publish a kind:30617 repository announcement. |
| `amy git list [USER]` | List a user's repo announcements (defaults to self). |
| `amy git show NADDR\|kind:pubkey:id` | Print one repo announcement (cache-first). |
| `amy git issue NADDR\|coords --subject S [BODY] [--hashtag T[,T]]` | Publish a kind:1621 issue against a repo. BODY from arg or stdin. |

### Podcasts (NIP-F4)

| Command | What it does |
|---|---|
| `amy podcast metadata --title T --image URL --description D [--website URL[,URL]]` | Publish kind:10154 show metadata (replaceable). |
| `amy podcast publish --title T --description D --audio URL[,URL] [--audio-type MIME] [--image URL] [--content MD]` | Publish a kind:54 episode. |
| `amy podcast list [USER] [--limit N]` | List a user's show metadata + episodes. |

### Blossom blobs (NIP-B7)

| Command | What it does |
|---|---|
| `amy blossom upload --server URL FILE [--mime-type M]` | Upload a file (BUD-01, authed). Prints the blob URL + sha256. |
| `amy blossom download URL [--out FILE]` | Download a blob (public). Accepts a full URL, or a `HASH` plus `--server URL`. |
| `amy blossom list --server URL [USER]` | List a user's blobs (BUD-04). USER defaults to the active account. |
| `amy blossom delete HASH --server URL` | Delete a blob you own (BUD-02). |
| `amy blossom check --server URL HASH[,HASH]` | HEAD-check the server has each blob; exit 1 if any is missing. |
| `amy blossom mirror --server URL SOURCE-URL` | Ask the server to mirror a blob from SOURCE-URL (BUD-04). |

### Cashu wallet (NIP-60 / NIP-61)

A NIP-60 ecash wallet + NIP-61 nutzaps, driven by the **same** shared
`commons` `CashuWalletOps` / `CashuWalletReader` the Android wallet runs — so
amy's on-relay events match the app's. NUT-13 counters persist in
`~/.amy/<account>/cashu.json`. `mint ping`/`info` are stateless (no account).

| Command | What it does |
|---|---|
| `amy cashu wallet create [--mint URL] [--mints a,b] [--privkey HEX] [--relay r1,r2]` | Publish a kind:17375 wallet + kind:10019 nutzap info. Advertises your outbox relays for nutzaps unless `--relay` overrides. |
| `amy cashu wallet show` | P2PK pubkey, mints, balance, per-mint balances, proof/history/pending counts. |
| `amy cashu wallet export-key` | Decrypt and print the wallet's P2PK private key. |
| `amy cashu wallet destroy` | Withdraw the nutzap advertisement and NIP-09 delete the wallet (leaves token events — the ecash still lives at the mint). |
| `amy cashu balance [--mint URL]` | Spendable balance from the local store (optionally one mint). |
| `amy cashu mint ping URL` / `info URL` | Stateless `/v1/info` probe (name/pubkey/version) / full DTO. |
| `amy cashu receive ln SATS [--mint URL]` | Request a mint quote; prints the bolt11 + kind:7374 quote. |
| `amy cashu receive complete QUOTE_ID` / `resume QUOTE_ID` | Poll the quote; once the invoice is settled, mint proofs (kind:7375 + kind:7376). |
| `amy cashu receive token TOKEN` | Redeem a `cashuB…` token into the wallet. |
| `amy cashu receive nutzap-sweep [--mint URL]` | Redeem inbound NIP-61 nutzaps locked to your wallet key. |
| `amy cashu send ln INVOICE [--mint URL]` | Melt proofs to pay a bolt11 (scrubs stale proofs first). |
| `amy cashu send token SATS [--mint URL] [--memo S]` | Export a `cashuB…` token of SATS. |
| `amy cashu send nutzap USER SATS [--zapped EVENT_ID] [--message S]` | Send a P2PK-locked nutzap to USER (resolves their kind:10019). |
| `amy cashu maintenance scrub [--mint URL]` | NUT-07 + NIP-09 prune of spent proofs. |
| `amy cashu maintenance restore MINT_URL` | NUT-09 restore unspent proofs from the wallet seed. |
| `amy cashu maintenance migrate-keysets [--mint URL]` | Consolidate proofs onto each mint's active keyset. |
| `amy cashu mint-rec show [--author NPUB]` / `add URL [--dtag X] [--review T]` / `remove EVENT_ID` | NIP-87 mint recommendations (kind:38000). |

### Relay management — admin (NIP-86)

Signs a NIP-98 request with the active account and POSTs it to the relay's
HTTP endpoint. Reuses quartz's `Nip86Client` and the shared `Nip86Retriever`
(the same path Amethyst's relay-management screen runs).

| Command | What it does |
|---|---|
| `amy admin RELAY supported-methods` | List the NIP-86 methods the relay implements. |
| `amy admin RELAY ban-pubkey HEX [--reason R]` / `unban-pubkey HEX` / `list-banned-pubkeys` | Pubkey ban list. |
| `amy admin RELAY allow-pubkey HEX [--reason R]` / `unallow-pubkey HEX` / `list-allowed-pubkeys` | Pubkey allow list. |
| `amy admin RELAY ban-event ID [--reason R]` / `allow-event ID` / `list-banned-events` / `list-needing-moderation` | Event moderation. |
| `amy admin RELAY allow-kind N` / `disallow-kind N` / `list-allowed-kinds` | Kind allow list. |
| `amy admin RELAY block-ip IP [--reason R]` / `unblock-ip IP` / `list-blocked-ips` | IP block list. |
| `amy admin RELAY change-name S` / `change-description S` / `change-icon URL` | Relay metadata. |

### Run a relay — serve

| Command | What it does |
|---|---|
| `amy serve [--host H] [--port N] [--path P] [--db FILE] [--admin NPUBS]` | Run a Nostr relay by embedding **geode** (the standalone Ktor relay on quartz's relay-server code). In-memory by default; `--db FILE` for SQLite. The active account is always an admin, so `amy admin ws://host:port …` works against it. Blocks until interrupted. |

### Identity

| Command | What it does |
|---|---|
| `amy --account NAME init [--nsec NSEC]` | Create or import a bare keypair. No relay traffic. |
| `amy --account NAME create [--name X]` | Full Amethyst-style bootstrap: keypair, default relays, kind:0, kind:3, the works. |
| `amy login KEY [--password X]` | Import an existing identity (`nsec`/`ncryptsec`/mnemonic/`npub`/`nprofile`/hex/NIP-05). |
| `amy whoami` | Print the active account's name + npub. |
| `amy use NAME` / `--clear` / no-arg | Pin / clear / inspect the active account. |
| `amy logoff [--yes] [--keep-events]` | Log off an account: delete its key + backend secret, the whole `~/.amy/<account>/` directory (run-state, aliases, cashu counters, Marmot state), the `current` pin if it points here, and the account's events (authored + `#p`-addressed) in the shared store. `--keep-events` leaves the shared cache alone. Destructive and irreversible — requires `--yes`; without it, prints a dry run and exits 2. |

### Social

| Command | What it does |
|---|---|
| `amy notes post TEXT [--relay URL]` | Publish a kind:1 short text note. |
| `amy notes feed [--author USER \| --following] [--limit N]` | Read recent kind:1 notes (yours, one user's, or your follow set). |
| `amy profile show [USER]` | Print kind:0 metadata. USER accepts npub/nprofile/hex/NIP-05; defaults to self. |
| `amy profile edit --name … --about … --picture URL …` | Patch and re-publish your kind:0. |

### Direct messages (NIP-17)

| Command | What it does |
|---|---|
| `amy dm send RECIPIENT TEXT [--allow-fallback]` | Gift-wrap a kind:14 to RECIPIENT. Strict kind:10050 routing by default. |
| `amy dm send-file RECIPIENT --file PATH --server URL` | Encrypt a local file, upload to a Blossom server, publish a kind:15 referencing it. |
| `amy dm send-file RECIPIENT URL --key HEX --nonce HEX` | Reference-mode: file already uploaded; just publish the kind:15. |
| `amy dm list [--peer NPUB] [--since TS] [--limit N]` | Drain and decrypt gift wraps. |
| `amy dm await --peer NPUB --match TEXT [--timeout SECS]` | Block until a matching DM arrives. |

### Groups (Marmot / MLS)

| Command | What it does |
|---|---|
| `amy marmot key-package publish` | Publish a fresh KeyPackage so others can invite you. |
| `amy marmot key-package check NPUB` | Look up someone else's KeyPackage on relays. |
| `amy marmot group create [--name X]` | New empty group with you as sole admin. |
| `amy marmot group list` | All groups you're a member of. |
| `amy marmot group show GID` | Members, admins, epoch, metadata. |
| `amy marmot group add GID NPUB [NPUB…]` | Fetch KeyPackages and invite. |
| `amy marmot group rename GID NAME` | Commit a metadata change. |
| `amy marmot group promote / demote / remove GID NPUB` | Admin verbs. |
| `amy marmot group leave GID` | Self-remove. |
| `amy marmot message send GID TEXT` | Publish a kind:9 inner event into the group. |
| `amy marmot message list GID [--limit N]` | Decrypted inner events, oldest first. |
| `amy marmot message react GID EVENT_ID EMOJI` | Publish a kind:7 reaction. |
| `amy marmot message delete GID EVENT_ID …` | Publish a kind:5 deletion. |

### CLINK Offers

| Command | What it does |
|---|---|
| `amy offer info NOFFER` | Decode a `noffer1…` pointer (pubkey, relays, price type/amount). Local, no network. |
| `amy offer request NOFFER [--amount SATS] [--timeout MS] [--payer-data K=V,…]` | kind:21001 round-trip: publish the request to the pointer's relays and print the returned BOLT11. `--amount` is required for spontaneous offers; fixed offers default to the pointer's price. `--payer-data` attaches payer fields (e.g. `email=a@b.c`) for offers that require them — Lightning.Pub answers "Invalid Offer" (code 1) when they are missing. |

### CLINK Debits

| Command | What it does |
|---|---|
| `amy debit info NDEBIT` | Decode an `ndebit1…` pointer (pubkey, relays, pointer id, session flag). Local, no network. |
| `amy debit pay NDEBIT BOLT11 [--amount SATS] [--timeout MS]` | kind:21002 round-trip: ask the pointed-to wallet to pay the invoice; print the preimage or the service's GFY error. |
| `amy debit budget NDEBIT --amount SATS [--frequency day\|week\|month] [--timeout MS]` | Authorize a spending budget; omit `--frequency` for a one-time budget. |

### Wait-for-condition (`await`)

Every `await` verb blocks until the condition holds, then prints the
matching event/state. All accept `--timeout SECS` (default 30); on
timeout the exit code is **124** so scripts can tell "didn't happen"
from "command crashed".

| Command | Blocks until… |
|---|---|
| `amy marmot await key-package NPUB` | NPUB has a KeyPackage discoverable on their advertised relays. |
| `amy marmot await group --name X` | You've been added to a group with that name. |
| `amy marmot await member GID NPUB` | NPUB is in GID's member set. |
| `amy marmot await admin GID NPUB` | NPUB is an admin of GID. |
| `amy marmot await message GID --match TEXT` | A message containing TEXT lands in GID. |
| `amy marmot await rename GID --name X` | GID's name matches X. |
| `amy marmot await epoch GID --min N` | GID's MLS epoch reaches N. |
| `amy dm await --peer NPUB --match TEXT` | A matching DM from NPUB arrives. |

### Relays

amy mirrors Amethyst's relay-settings screen with one **noun** per relay-list
kind, followed by `add`/`remove`/`set`/`clear` (a bare noun lists it) — the same
noun-first shape as `marmot group …` / `cashu mint …`:

| Noun | Kind | Notes |
|---|---|---|
| `outbox` / `inbox` / `nip65` | 10002 | NIP-65. `outbox` = write relays, `inbox` = read relays; `nip65` shows the combined read/write view. |
| `dm` | 10050 | NIP-17 DM inbox. |
| `key-package` | 10051 | MIP-00 MLS KeyPackage relays. |
| `search` | 10007 | NIP-50 search relays. |
| `private` | 10013 | NIP-37 private outbox (encrypted). |
| `blocked` `trusted` `proxy` `indexer` `broadcast` `feeds` | 10006 / 10089 / 10087 / 10086 / 10088 / 10012 | NIP-51 lists, stored NIP-44-encrypted exactly like the app. |

Local relays (a device-only preference, no Nostr event) and named relay sets
(kind 30002) are out of scope. Edits are local-first: they build, sign, and
store the new list event but do not broadcast — run `relay publish-lists`.

**NIP-65 markers.** `outbox`/`inbox` edit the one kind:10002 event and merge per
the spec: `outbox add R` on a read-only R promotes it to **both**; `outbox
remove R` on a both-R demotes it to **read** (keeps it in the inbox); dropping
the last facet removes R entirely.

| Command | What it does |
|---|---|
| `amy relay outbox add URL` / `inbox add URL` | Add URL as a write / read relay (merging to `both` if the other marker is already set). |
| `amy relay outbox remove URL` / `inbox remove URL` | Drop the write / read marker (demoting `both` to the other, or removing R). |
| `amy relay outbox set URL…` / `inbox set URL…` | Make exactly these the write / read relays. |
| `amy relay nip65 [list]` | Show the combined read/write/all view. `nip65 remove URL` drops R entirely; `nip65 clear` wipes kind:10002. |
| `amy relay <noun> add\|remove URL` | For the flat buckets (`dm`, `search`, `blocked`, …): append / drop URL. |
| `amy relay <noun> set URL…` / `clear` | Replace a bucket's whole list, or empty it. `set` needs ≥1 URL; use `clear` to empty. |
| `amy relay <noun>` | List that bucket. |
| `amy relay add URL` / `remove URL` | Fan-out to the transport lists (nip65 `both` + `dm` + `key-package`). |
| `amy relay list` | Print every configured relay bucket. |
| `amy relay publish-lists` | Broadcast every configured relay list to the union of your relays. |

### Local store maintenance

| Command | What it does |
|---|---|
| `amy store stat` | Event count, kind histogram, disk usage, oldest/newest timestamps. |
| `amy store sweep-expired` | Delete events past their NIP-40 expiration. |
| `amy store scrub` | Rebuild the index after external edits or a crash. |
| `amy store compact` | Drop dangling index entries (canonical event already gone). |

---

## Output: text by default, JSON on demand

By default amy writes a YAML-ish, colored, human-readable result to
stdout. Pass `--json` and stdout becomes a single-line JSON object —
same data, stable snake_case keys, ready for `jq`:

```bash
$ amy --json whoami
{"name":"alice","npub":"npub1th9z…","hex":"5dca27ae…","data_dir":"/home/me/.amy/alice"}

$ amy --json marmot group create --name "Lunch" | jq -r .group_id
ab12cd34…
```

Errors mirror the same rule. Default:

```text
$ amy marmot group show abc123
error: not_member: abc123        # exit 1
```

Under `--json` the error goes to stderr as `{"error":"not_member","detail":"abc123"}`.

Color auto-disables when stdout is a pipe; force it with `CLICOLOR_FORCE=1`,
turn it off entirely with `NO_COLOR=1`.

**Exit codes** — the real signal for scripts:

| Code | Meaning |
|---|---|
| 0 | success |
| 1 | runtime error (network, permission, NIP rejection, …) |
| 2 | bad arguments |
| 124 | `await` timed out |

---

## Multi-account workflows

`amy` is built to host more than one identity per machine. The layout
matches that:

```
~/.amy/
├── current                    # marker: which account `amy use NAME` pinned
├── shared/
│   └── events-store/          # one Nostr event store, shared by every account
├── alice/
│   ├── identity.json          # keypair (or reference to keychain entry)
│   ├── state.json             # sync cursors
│   ├── aliases.json           # local name → npub map
│   └── marmot/                # MLS state per group
└── bob/
    └── …
```

**Account selection** when you don't pass `--account`:

1. If `~/.amy/current` is set, use it.
2. Else if exactly one account exists, use it (silent auto-pick).
3. Else error and list the candidates so you can disambiguate.

`amy use NAME` writes `~/.amy/current`; `amy use --clear` removes it.
For one-off override, prepend `--account NAME` to any command.

`init` and `create` write a self-entry into `aliases.json` so you can
refer to your own account by name in future commands. The alias resolver
in recipient slots (`amy dm send alice "hi"`) is on the roadmap.

For the deeper layout (events-store internals, relay-routing rules, the
public-contract guarantees) see [DEVELOPMENT.md](./DEVELOPMENT.md).

---

## For agents and scripts

Three contracts keep amy machine-safe:

1. **One JSON object per success on stdout** under `--json`. Stable
   snake_case keys; keys never disappear silently.
2. **Errors as JSON on stderr** under `--json`: `{"error":"...","detail":"..."}`.
3. **Exit codes mean specific things** (table above) — `124` for
   `await` timeout in particular lets you distinguish "condition never
   happened" from "the command itself crashed".

### Recipes

```bash
# Capture a fresh group's id.
GID=$(amy --json marmot group create --name "ops" | jq -r .group_id)

# Add several members at once and report which KeyPackages were missing.
amy --json marmot group add "$GID" npub1aaa npub1bbb npub1ccc \
  | jq -r '.added[] | select(.status != "ok") | "missing: \(.pubkey)"'

# Wait up to 5 minutes for a particular message and capture its event id.
EVT=$(amy --json marmot await message "$GID" --match "deploy starting" --timeout 300 \
       | jq -r .event_id)

# Run a command per follow.
amy --json notes feed --following --limit 50 \
  | jq -r '.notes[].author' \
  | sort -u \
  | while read -r author; do
      amy --json profile show "$author" | jq -r '.metadata.name // "?"'
    done
```

### Test isolation

amy reads `$HOME` directly to find `~/.amy/`, so harnesses isolate the
exact same way `git`, `gpg`, `npm`, and `ssh` do — by overriding `$HOME`
for the subprocess:

```bash
HOME=$(mktemp -d) amy --account alice init
HOME=$(mktemp -d) amy --account alice marmot group create --name "scratch"
```

Inside the amy process there's no test mode — it just sees a fresh
`~/.amy/` and behaves like a brand-new install.

---

## Troubleshooting

- **`no account at ~/.amy`** — you haven't created one yet. Run
  `amy --account NAME init` (bare keypair) or `amy --account NAME create`
  (full Amethyst-style bootstrap).
- **`multiple accounts in ~/.amy (alice, bob)`** — pin one with
  `amy use NAME` or pass `--account NAME` per command.
- **`current pins 'X' but ~/.amy/X doesn't exist`** — the active-account
  marker is stale. Rewrite with `amy use OTHER` or `amy use --clear`.
- **`no_dm_relays`** — recipient hasn't published a kind:10050 inbox.
  Pass `--allow-fallback` to fall back to their kind:10002 read marker
  → bootstrap pool. Or wait for them to publish one.
- **`not_member`** — the group GID is unknown to this account. Run
  `amy marmot group list` to see what you're in, or `await group --name X`
  to wait for an invite.
- **A network verb hangs** — every network verb has a relay timeout.
  Inspect what amy is connecting to with `amy relay list`. Wrap any
  command in `timeout(1)` if you're scripting and want a hard ceiling.
- **Nothing seems to publish** — stderr carries `[cli] …` traces with
  per-relay `OK` / `REJECT`. Capture with `2> /tmp/amy.log` and grep.

---

## Where to go next

- **[DEVELOPMENT.md](./DEVELOPMENT.md)** — design principles,
  architecture, the public contract, the local event store, relay
  routing, full on-disk layout, how to extend amy without breaking it.
- **[ROADMAP.md](./ROADMAP.md)** — north-star goal and the parity matrix
  tracking what's left to extract from the Android app.
- **[`plans/`](./plans/)** — design docs for cross-cutting work
  (CLI distribution, file-backed event store, NIP-17 DMs, …).
- **[Nostr NIPs](https://github.com/nostr-protocol/nips)** — the
  protocol amy speaks.
