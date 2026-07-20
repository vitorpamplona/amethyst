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

event_id:     a3c1f9c2…(64 hex)
kind:         1
published_to:
  - wss://relay.damus.io/
  - wss://nos.lol/
rejected_by:  (none)
```

If **every** targeted relay refuses the event, amy reports
`error: rejected` and exits 1 — a total rejection never exits 0.

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
| `amy pow check EVENT-JSON\|-` | NIP-13 difficulty of a signed event: `actual_bits`, `committed_target`, `has_commitment`, and `effective_pow` (capped at the commitment so lucky low-target spam doesn't over-count), plus `valid` (id + signature). |
| `amy pow mine --target N [--pubkey HEX] [--timeout SECS] [--threads N] TEMPLATE-JSON\|-` | Mine an **unsigned** template to N leading zero bits and print it back with the nonce tag. Ids don't commit to signatures, so amy can mine on behalf of any pubkey (NIP-13 delegated PoW); defaults to the active account. Mines on all cores by default (`--threads` to override). Exit 124 on timeout. |
| `amy pow bench` | Benchmark this machine's hash rate — `hashes_per_second` is the all-cores rate `pow mine` uses by default, `hashes_per_second_single_core` the one-thread rate — and print expected mining time at 16/20/24/28 bits. |
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

The flows were verified against the real [`nak`](https://github.com/fiatjaf/nak)
binary during development — `amy login bunker://` ⇄ `nak bunker`, `nak event
--sec bunker://` ⇄ `amy bunker`, and `amy login --nostrconnect` ⇄ `nak bunker
connect` (amy signs, event authored by nak's key). A scripted nak harness
under `cli/tests/` is still pending, so treat these as dev-verified, not
CI-pinned.

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
| `amy fetch [filter flags] [--timeout SECS]` | One-shot query — collect until every relay sends EOSE or goes silent for `--timeout` (default 8s — an **idle window**, reset by every arriving event, so a slow-but-streaming relay is never cut off; a wall-clock ceiling of 10x the window still bounds a relay that trickles forever), dedupe, sort newest-first, print and exit. `--limit` defaults to 100. |
| `amy fetch CODE [--timeout SECS]` | Code mode — pass a single `nevent`/`naddr`/`nprofile`/`npub`/`note` or `name@domain`. Resolves relays the outbox way: the hints embedded in the code **plus** the author's NIP-65 write relays (draining their kind:10002 on a cache miss), exactly how the app opens a shared link. |
| `amy subscribe [filter flags] [--timeout SECS]` | Live stream — print each matching event as it arrives (NDJSON under `--json`). Runs until `--timeout` SECS or until interrupted. |
| `amy count [filter flags] [--timeout SECS]` | NIP-45 COUNT — per-relay match counts, no event download. |
| `amy outbox USER [--refresh] [--timeout SECS]` | Show USER's NIP-65 read/write relays (outbox model). Cache-first; `--refresh` forces a relay drain. |
| `amy sync --relay URL [filter flags] [--down] [--up]` | NIP-77 Negentropy reconcile between the local store and a relay. `--down` (default) pulls events we lack; `--up` pushes events the relay lacks; both for bidirectional. |

### Search (NIP-50)

Runs against your kind:10007 search-relay list, falling back to Amethyst's
default search relays.

| Command | What it does |
|---|---|
| `amy search user QUERY [--limit N] [--timeout SECS]` | Search kind:0 profiles. Default `--limit 50`. |
| `amy search note QUERY [--kind K[,K…]] [--limit N] [--timeout SECS]` | Search event content. Default kind:1 (e.g. `--kind 1,30023`); `--kinds` is accepted as an alias. |

### Encryption

| Command | What it does |
|---|---|
| `amy encrypt --to USER [TEXT] [--nip04]` | NIP-44 (default) or NIP-04 encrypt with the active account's key. Reads stdin when TEXT is omitted or `-`. USER accepts npub/nprofile/hex/NIP-05. |
| `amy decrypt --from USER [CIPHERTEXT] [--nip04]` | Inverse of `encrypt`. |
| `amy gift wrap --to USER [EVENT-JSON] [--relay …]` | NIP-59: seal a signed inner event for USER and wrap it in a kind:1059 gift wrap. Prints the wrap; `--relay` also broadcasts it. |
| `amy gift unwrap [GIFTWRAP-JSON]` | Decrypt + unseal a kind:1059 wrap addressed to the active account; prints the inner event. |

### Git (NIP-34)

`amy git` mirrors the pure-Nostr surface of [`ngit`](https://github.com/DanConwayDev/ngit-cli)
and `nak git`: repository announcements + state, patches, pull requests, issues,
threaded NIP-22 comments, and status updates. The git **packfile** transport
(`clone`/`fetch`/`push` of real git objects to clone/GRASP servers) is out of
scope — that needs a git plumbing layer, not an event builder — so `amy git`
publishes and reads the collaboration events, and you clone/push with `git`
itself (or `ngit`).

Every write verb accepts `[--relay URL[,URL]]` to override the target relays;
the default is the repo's advertised relays, else your outbox.

**Repository**

| Command | What it does |
|---|---|
| `amy git announce --name N [--description D] [--clone URL[,URL]] [--web URL[,URL]] [--relay URL[,URL]] [--maintainer HEX[,HEX]] [--hashtag T[,T]] [--earliest-commit C] [--personal-fork] [--d ID]` | Publish a kind:30617 repository announcement. |
| `amy git state REPO\|IDENTIFIER [--head BRANCH] [--branch name=commit[,…]] [--tag name=commit[,…]]` | Publish a kind:30618 repository state (branch/tag tips + HEAD). |
| `amy git list [USER]` | List a user's repo announcements (defaults to self). |
| `amy git show NADDR\|kind:pubkey:id` | Print one repo announcement (cache-first). |
| `amy git grasp list [USER]` | List a user's kind:10317 GRASP hosting-server list (defaults to self). |
| `amy git grasp set URL[,URL]` | Publish your GRASP hosting-server list (preference order — where PR tips get pushed). |

**Issues, patches & pull requests**

| Command | What it does |
|---|---|
| `amy git issue REPO --subject S [BODY] [--hashtag T[,T]]` | Publish a kind:1621 issue. BODY from arg or stdin. |
| `amy git patch REPO [--file PATH] [--root\|--root-revision] [--commit C] [--parent-commit P] [--in-reply-to ID]` | Publish a kind:1617 patch. Body is `git format-patch` output from `--file` or stdin. |
| `amy git pr REPO --commit TIP --clone URL[,URL] [--subject S] [--branch-name N] [--merge-base C] [--label L[,L]] [DESC]` | Publish a kind:1618 pull request (references a pushed branch tip by clone URL + commit). |
| `amy git pr-update PR --commit TIP --clone URL[,URL] [--merge-base C]` | Publish a kind:1619 update to a pull request's tip. |
| `amy git issues\|patches\|prs REPO [--open\|--applied\|--closed\|--draft\|--status a,b] [--limit N]` | List a repo's issues / patches / PRs with their derived status. |
| `amy git thread EVENT_ID` | Print one item plus its status timeline and comments. |

**Comments & status**

| Command | What it does |
|---|---|
| `amy git comment TARGET [BODY]` | Reply to an issue/patch/PR/repo with a NIP-22 kind:1111 comment. BODY from arg or stdin. |
| `amy git open TARGET [MSG]` | Publish a kind:1630 status (open / reopen / ready-for-review). |
| `amy git applied TARGET [MSG] [--merge-commit C] [--commit C[,C]] [--patch ID[,ID]]` | Publish a kind:1631 status (applied / merged / resolved). Aliases: `merged`, `resolved`. |
| `amy git close TARGET [MSG]` | Publish a kind:1632 status (closed). |
| `amy git draft TARGET [MSG]` | Publish a kind:1633 status (draft). |

### Podcasts (NIP-F4)

| Command | What it does |
|---|---|
| `amy podcast metadata --title T --image URL --description D [--website URL[,URL]]` | Publish kind:10154 show metadata (replaceable). |
| `amy podcast publish --title T --description D --audio URL[,URL] [--audio-type MIME] [--image URL] [--content MD]` | Publish a kind:54 episode. |
| `amy podcast list [USER] [--limit N]` | List a user's show metadata + episodes. |

### Podcasts (Podcasting 2.0 / podstr)

The podstr-compatible surface — Podcasting 2.0 tags carried in addressable
events. `--identifier` is accepted as an alias of `--d` everywhere here.

| Command | What it does |
|---|---|
| `amy podcast20 metadata --title T [--description D] [--author A] [--email E] [--image URL] [--language L] [--categories A,B] [--funding URL,URL] [--website URL] [--copyright C] [--type episodic\|serial] [--explicit] [--complete] [--locked] [--guid G] [--value-json JSON] [--relay URL[,URL…]]` | Publish kind:30078 show metadata (JSON body). `--value-json` is the value-for-value split block. |
| `amy podcast20 episode --title T --audio URL[,URL] [--d ID] [--audio-type MIME] [--description D] [--image URL] [--duration SECS] [--video URL] [--video-type MIME] [--episode N] [--season N] [--transcript URL] [--chapters URL] [--value-json JSON] [--topic A,B] [--content MARKDOWN] [--pubdate RFC2822] [--relay URL[,URL…]]` | Publish a kind:30054 episode. |
| `amy podcast20 trailer --title T --url URL [--d ID] [--type MIME] [--length BYTES] [--season N] [--pubdate RFC2822] [--relay URL[,URL…]]` | Publish a kind:30055 trailer. |
| `amy podcast20 list [USER] [--limit N] [--relay URL[,URL…]]` | List a creator's metadata + episodes + trailers. |

### Static websites & napplets (NIP-5A / NIP-5D)

Sites and napplets are manifests on Nostr with content on Blossom; every
fetch is verified against the manifest's sha256 pins. `--identifier` is an
alias of `--d`; `--d` selects a named site/napplet, else the root one.

| Command | What it does |
|---|---|
| `amy nsite fetch AUTHOR [--d ID] [--path P] [--server URL[,URL]] [--relay URL[,URL]] [--out FILE] [--max-inline-bytes N] [--timeout SECS]` | Resolve one path over Nostr + Blossom and verify it against the manifest's sha256 pin (kind:15128 root, or kind:35128 named with `--d`; `--path` defaults to `/`). |
| `amy nsite publish DIR --server URL[,URL] [--d ID] [--relay URL[,URL]] [--title T] [--description D] [--source URL] [--icon URL]` | Upload a directory to Blossom and broadcast its NIP-5A manifest, including the `x` aggregate hash so it is self-verifying. |
| `amy nsite serve AUTHOR [--d ID] [--port N] [--server URL[,URL]] [--relay URL[,URL]] [--timeout SECS]` | Fetch the manifest and serve it over a local HTTP server (sha256-verified per request) so you can open it in a browser. |
| `amy nsite list AUTHOR [--relay URL[,URL]] [--timeout SECS]` | Enumerate an author's sites: the root and every named one, latest per identifier. |
| `amy napplet fetch AUTHOR [--d ID] [--path P] …` | Like `nsite fetch`, plus NIP-5D verification: recompute + check the `x` aggregate hash and report the napplet's `requires` capabilities. `--snapshot EVENT-ID` pins a kind:5129 immutable snapshot by event id. |
| `amy napplet publish DIR --server URL[,URL] [--requires identity,relay,…] [--d ID] [--relay URL[,URL]] [--title T] [--description D] [--source URL] [--icon URL]` | Upload a napplet directory and broadcast its NIP-5D manifest (kind:15129 root / 35129 named) with the `x` aggregate hash and the `requires` capability tags the shell gates on. |
| `amy napplet serve AUTHOR [--d ID] [--port N] …` | Fetch + aggregate-verify the manifest and serve its static content over local HTTP. |
| `amy napplet list AUTHOR [--relay URL[,URL]] [--timeout SECS]` | Enumerate an author's napplets, latest per identifier. |

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
| `amy cashu receive complete QUOTE_ID` | Poll the quote; once the invoice is settled, mint proofs (kind:7375 + kind:7376). (`resume` is a deprecated alias.) |
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
| `amy status` | Read-only overview of everything under `~/.amy/`: every account, which one is current, each signer type (local keychain/ncryptsec/plaintext, NIP-46 bunker, or read-only) and whether it can sign, the local Marmot / Cashu / alias / sync-cursor footprint per account, and the shared event store's size. Built for the returning user. No keychain prompt, no network. |
| `amy logoff [--yes] [--keep-events]` | Log off an account: delete its key + backend secret, the whole `~/.amy/<account>/` directory (run-state, aliases, cashu counters, Marmot state), the `current` pin if it points here, and the account's events (authored + `#p`-addressed) in the shared store. `--keep-events` leaves the shared cache alone. Destructive and irreversible — requires `--yes`; without it, prints a dry run and exits 2. |

### Social

| Command | What it does |
|---|---|
| `amy notes post TEXT [--relay URL] [--pow BITS [--pow-timeout SECS]]` | Publish a kind:1 short text note; `--pow` mines a NIP-13 proof of work into it first, using all cores (blocks while mining, exit 124 on timeout with nothing published; `--json` adds `pow`, `pow_target`, `pow_millis`). |
| `amy notes feed [--author USER \| --following] [--limit N]` | Read recent kind:1 notes (yours, one user's, or your follow set). |
| `amy profile show [USER]` | Print kind:0 metadata. USER accepts npub/nprofile/hex/NIP-05; defaults to self. |
| `amy profile edit --name … --about … --picture URL …` | Patch and re-publish your kind:0. |
| `amy follow USER` / `amy unfollow USER` | Add/remove USER from your kind:3 contact list (fetches the freshest list first). |
| `amy graperank [OBSERVER] [--offline] [--min-rank N]` | Crawl + score: compute GrapeRank web-of-trust scores (0..1) over the follow/mute/report graph, then persist the result. Exhaustively crawls each user's kind:10002 outbox for their latest kind:3/10000/1984 until every discovered user is checked (no user cap), dropping reports the author retracted via NIP-09. **Every score run persists its result locally**: the cards (rank cutoff `--min-rank`, default 2) are reconciled into the shared store as NIP-85 kind:30382 cards signed by a per-observer **service key** — each carries `rank`, `followers` (trusted-follower count, cutoff `--followers-threshold`, default 0.02) and `hops` (follow distance from the observer); changed cards re-signed, unchanged skipped (no event-id churn), dropped targets retracted (kind:5). `--offline` skips the crawl. |
| `amy graperank crawl [OBSERVER] [--max-hops N] [--no-preconnect]` | Pipeline stage 1 — network only: crawl the follow/mute/report graph (kind 3/10000/1984/10002) into the local store, no scoring. Idempotent and cumulative: run it a few times to load everything, then `score`. |
| `amy graperank followers [OBSERVER] [--relay URL[,URL…]]` | The **reverse** crawl: find every user who *follows* the observer by asking as many relays as possible for kind:3 lists that `#p`-tag them (paged past each relay's cap). The outbox model can't find followers — you don't know one exists until you've seen their list — so this casts a wide net over the whole relay universe (reachability-cache live set + every kind:10002/30166 relay in the store + index/aggregator relays), skipping proven-dead relays. Persists each follower's list, enriching the graph a later `score` builds. Idempotent and cumulative. |
| `amy graperank score [OBSERVER]` | Pipeline stage 2 — local only: score from the store and persist the cards (identical to bare `--offline`; same scoring flags). No network, so re-run with different `--rigor`/`--attenuation`/`--min-rank` without re-crawling. |
| `amy graperank publish [OBSERVER] [--relay URL[,URL…]]` | Pipeline stage 3 — transport only: make the operator relay(s) converge to the locally persisted card set — one NIP-77 up-only reconcile per relay over the service key's kind:30382 + kind:5 (nothing is re-scored or re-signed; a relay that can't reconcile gets the full set published instead). Also refreshes the observer's kind:10040 pointer when we hold their key. |
| `amy graperank rank USER [--provider PUBKEY] [--refresh]` | The consumer side: read the kind:30382 cards about USER — one rank per provider, newest card each. Local store first; `--refresh` (or a miss) drains the operator relays, the relays your kind:10040 declares, and the bootstrap set. |
| `amy graperank refresh [--down] [--up]` | Refresh every locally-known author's WoT record kinds (0/3/10002/1984) from their own outbox: one NIP-77 negentropy reconcile per write relay scoped to its authors, so the next `score` runs on current data without a full re-crawl. (`update` is the pre-rename alias.) |
| `amy graperank status` | Read-only local inventory, no network, no signing: WoT record counts in the store (the "do I need to crawl again?" answer), reachability-cache size + age, operator/service-key state, and the persisted card + retraction counts per observer. |
| `amy graperank operator [status \| relay <url>… \| keys]` | Manage the machine's operator keys (independent of any account, under `~/.amy/operator/`). `relay` sets where `publish` sends cards + retractions; `status` shows the master pubkey and relays; `keys` lists the observer → service-pubkey map (`providers` is the pre-rename alias). |
| `amy graperank register [PROVIDER] [--service KIND:TAG] [--relay URL]` | Declare a NIP-85 provider in your kind:10040 so clients can discover it (default: self as the `30382:rank` provider). |
| `amy graperank unregister PROVIDER [--service KIND:TAG] [--relay URL]` | The inverse of `register`: remove matching entries (public + private) from your kind:10040 and re-publish it. `--service`/`--relay` narrow the match; without them every entry for that provider key is dropped. |
| `amy graperank providers [USER]` | List a user's declared NIP-85 trusted providers (public + your own private entries). |
| `amy fof get USER` | Follows-of-follows social proof: how many accounts you follow also follow USER. Single-hop, cheap — **not** the computed web of trust (that's `graperank`). Read from the local store; run `fof sync` first to freshen it. |
| `amy fof list [--threshold N] [--limit N]` | Rank accounts by that social-proof score — who's most-followed inside your network (discovery). Defaults: `--threshold 1`, `--limit 50`. |
| `amy fof sync [--timeout SECS]` | Pull your follows' latest kind:3 from the index relays so the next `get`/`list` is current. (`amy wot …` remains as a deprecation alias for all three.) |

#### GrapeRank scores are persisted locally, then published (NIP-85)

Ranks are signed as kind:30382 cards, but **not** under your account key. A
machine holds one **operator master** seed (`~/.amy/operator/`, stored via the
same `--secret-backend` as accounts, independent of any account). From it a
distinct, deterministic **service key** is derived per observer:

```
serviceKey(observer) = sha256(masterPriv ‖ "graperank-provider:" ‖ observerHex)
```

Because kind:30382 is addressable (`pubkey + d-tag`), the stable per-observer key
means re-signing **replaces** a target's card instead of orphaning it — and
losing everything but the master seed still re-derives every key.

**Every score run persists its cards.** After scoring, Amy reconciles the result
into the local store: new or changed cards (rank ≥ `--min-rank`, default 2) are
signed; unchanged cards are skipped (no new event id); and any card whose target
dropped out of the graph or fell below the cutoff is **retracted** with a kind:5
(the store applies it; the tombstone is kept). Each card carries three public
tags: `rank` (`round(score*100)`), `followers` — the number of the target's
followers whose own score clears `--followers-threshold` (default 0.02, matching
Brainstorm's trusted-follower cutoff) — and `hops`, the shortest follow-graph
distance from the observer (1 = a direct follow). A change to *any* of the three
re-signs the card. The local store is the source of truth — `graperank rank USER`
reads it offline, and `graperank publish` mirrors it out:

```bash
amy graperank operator relay wss://relay.example.com   # where all cards live
amy graperank <observer>                               # crawl + score + persist cards locally
amy graperank publish <observer>                       # make the operator relay match the local set
```

`publish` never re-scores or re-signs: it runs one NIP-77 up-only reconcile per
relay over the service key's kind:30382 + kind:5, so the relay converges to the
local card set (deletions included, lost cards restored); a relay that can't
negentropy-reconcile gets the full set published event-by-event instead. When
the observer is your own account (we hold the key), `publish` also writes their
kind:10040 pointing `30382:rank → serviceKey @ operator relay` to their outbox,
so clients can find the cards. For a third-party observer, `graperank operator
keys` prints the `observer → service-pubkey` mapping to wire their
kind:10040 out-of-band.

### Direct messages (NIP-17)

| Command | What it does |
|---|---|
| `amy dm send RECIPIENT TEXT [--allow-fallback]` | Gift-wrap a kind:14 to RECIPIENT. Strict kind:10050 routing by default. |
| `amy dm send-file RECIPIENT --file PATH --server URL` | Encrypt a local file, upload to a Blossom server, publish a kind:15 referencing it. |
| `amy dm send-file RECIPIENT URL --key HEX --nonce HEX` | Reference-mode: file already uploaded; just publish the kind:15. |
| `amy dm list [USER] [--peer USER] [--since TS] [--limit N]` | Drain and decrypt gift wraps. Positional USER is an alternative to `--peer` (the flag wins). Default `--limit 50`. |
| `amy dm await [USER] --match TEXT [--peer USER] [--timeout SECS]` | Block until a matching DM arrives (positional USER or `--peer`; the flag wins). |

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
| `amy marmot message list GID [--limit N]` | Decrypted inner events, oldest first. Default `--limit 50`. |
| `amy marmot message react GID EVENT_ID EMOJI` | Publish a kind:7 reaction. |
| `amy marmot message delete GID EVENT_ID …` | Publish a kind:5 deletion. |

### Relay groups (NIP-29)

Relay-based groups (à la Armada / relay29). A group lives on one host relay,
addressed by `(relay, group id)`; every read and write is pinned there. Distinct
from Marmot/MLS above — these are the NIP-29 groups Amethyst's "Relay Groups"
screen speaks.

| Command | What it does |
|---|---|
| `amy relaygroup list` | Your joined groups, from your kind:10009 list (public + private). |
| `amy relaygroup browse RELAY` | Every group a relay hosts (its 39000-39003 directory). |
| `amy relaygroup info RELAY GID` | A group's metadata + admin/member roster. |
| `amy relaygroup create RELAY --name X [--about A] [--private] [--closed]` | Create a group (publishes 9007 + 9002); prints the new `group_id`. |
| `amy relaygroup join RELAY GID [--code CODE]` | Request to join (9021) and add it to your kind:10009 list. |
| `amy relaygroup leave RELAY GID` | Leave (9022) and drop it from your kind:10009 list. |
| `amy relaygroup message RELAY GID TEXT` | Post a kind:9 chat message into the group. |
| `amy relaygroup edit RELAY GID [--name X] [--about A] [--private\|--public] [--closed\|--open]` | Edit metadata (9002, admin only). Reads current visibility and changes only the axis you pass, so re-asserting one flag never resets the other. |
| `amy relaygroup invite RELAY GID --code CODE` | Mint an invite code (9009, moderator). |
| `amy relaygroup put-user RELAY GID PUBKEY [--role admin\|moderator]` | Add or promote a user (9000, moderator). |
| `amy relaygroup remove-user RELAY GID PUBKEY` | Kick a user (9001, moderator). |

### Concord Channels (encrypted communities)

Encrypted, serverless communities (the CORD specs). Community secrets
persist in `~/.amy/<account>/concord.json`; your joined-community list is
also carried on-relay as an encrypted kind:13302.

| Command | What it does |
|---|---|
| `amy concord create --name NAME [--about T] [--relay wss://a,wss://b]` | Create an encrypted Concord community. `--relay` is canonical; `--relays` is accepted as an alias. |
| `amy concord list` | List joined Concord communities. |
| `amy concord import` | Fetch + decrypt this account's kind:13302 community list (carries heldRoots, CORD-06). |
| `amy concord channels COMMUNITY` | List a community's channels. |
| `amy concord send COMMUNITY CHANNEL TEXT` | Post a message (CHANNEL = `general`\|name\|id). |
| `amy concord read COMMUNITY CHANNEL [--limit N] [--epoch N] [--root HEX]` | Read a channel's messages (default 50); `--epoch`/`--root` read a prior epoch's plane. |
| `amy concord invite COMMUNITY [--base URL]` | Mint + publish a shareable invite link. |
| `amy concord join URL` | Redeem an invite link and save the community. |
| `amy concord roles COMMUNITY` | List live roles + the current banlist (CORD-04). |
| `amy concord role COMMUNITY NAME POSITION PERM…` | Define a role (perms by name, e.g. `BAN KICK`). |
| `amy concord grant COMMUNITY USER ROLE-ID` | Grant a role to a member. |
| `amy concord ban COMMUNITY USER` / `unban COMMUNITY USER` | Ban / unban a member. |

### Geochat (Bitchat geohash channels)

Bitchat-interoperable public location chat: ephemeral kind:20000 events
tagged `["g", geohash]`, signed with a per-geohash **throwaway identity**
and routed to the relays geographically nearest the cell. Relays broadcast
ephemeral events live but don't store them, so `listen` holds an open
subscription for a window.

| Command | What it does |
|---|---|
| `amy geochat listen GEOHASH [--seconds N] [--limit N] [--relay URL[,URL…]] [--no-fetch]` | Hold a live subscription to the cell and report messages + present pubkeys seen in the window (default `--seconds 30`, `--limit 50`). |
| `amy geochat send GEOHASH MESSAGE [--nick NAME] [--teleport] [--pow BITS] [--pow-timeout SECS] [--seed HEX] [--relay URL[,URL…]] [--no-fetch]` | Sign with the per-geohash throwaway identity and publish to the cell's nearest relays. |
| `amy geochat keys GEOHASH [--seed HEX]` | Print the per-geohash derived pubkey. |

`--no-fetch` skips the geo-relay directory refresh.

### Which chat system?

Four group-chat surfaces coexist — pick by threat model and topology:

| Verb | Protocol | Encryption | Where it lives | Use when |
|---|---|---|---|---|
| `marmot` | Marmot / MLS | E2EE (MLS) with forward secrecy | Gift-wrapped events on ordinary relays | Private groups; strongest crypto; membership managed by commits. |
| `relaygroup` | NIP-29 | None (relay-enforced access) | One **host relay** per group, which moderates | Public/moderated communities à la Armada/relay29. |
| `concord` | Concord (CORD) | Encrypted, serverless | Ordinary relays; secrets in `concord.json` | Encrypted communities with channels + roles, no host relay to trust. |
| `geochat` | Bitchat geohash | None (public, throwaway identity) | Ephemeral kind:20000 on geo-nearest relays | Location-based public chat; Bitchat interop. |

### Zaps (NIP-57)

Builds the kind:9734 zap request and fetches a BOLT11 invoice from the
recipient's Lightning service. No auto-payment by default — paste the
invoice into a wallet — unless you pass `--with NDEBIT`, which settles each
fetched invoice in-place through a CLINK debit pointer (kind:21002); the
output then also reports `paid` + the preimage.

| Command | What it does |
|---|---|
| `amy zap user USER SATS [--comment X] [--anon\|--private] [--with NDEBIT] [--timeout SECS]` | Profile zap: build the zap request and fetch a BOLT11 from USER's LN service. |
| `amy zap event EVENT-ID SATS [--comment X] [--anon\|--private] [--with NDEBIT] [--timeout SECS]` | Same, attributed to a specific event (must be in the local store). Zap splits are honored — one invoice per recipient. |

### CLINK Offers

| Command | What it does |
|---|---|
| `amy offer info NOFFER` | Decode a `noffer1…` pointer (pubkey, relays, price type/amount). Local, no network. |
| `amy offer discover NIP05` | Resolve a profile's advertised offer from its NIP-05 `.well-known` (e.g. `bob@example.com`). |
| `amy offer request NOFFER [--amount SATS] [--timeout SECS] [--follow] [--payer-data K=V,…]` | kind:21001 round-trip: publish the request to the pointer's relays and print the returned BOLT11. `--amount` is required for spontaneous offers; fixed offers default to the pointer's price. `--follow` chases an "Expired or Moved" (code 3) reply to the `latest` pointer. `--payer-data` attaches payer fields (e.g. `email=a@b.c`) for offers that require them — Lightning.Pub answers "Invalid Offer" (code 1) when they are missing. |
| `amy offer pay NOFFER --with NDEBIT [--amount SATS] [--timeout SECS]` | Fetch the invoice and settle it end-to-end through a CLINK debit pointer (kind:21002). |

### CLINK Debits

| Command | What it does |
|---|---|
| `amy debit info NDEBIT` | Decode an `ndebit1…` pointer (pubkey, relays, pointer id, session flag). Local, no network. |
| `amy debit pay NDEBIT BOLT11 [--amount SATS] [--timeout SECS]` | kind:21002 round-trip: ask the pointed-to wallet to pay the invoice; print the preimage or the service's GFY error. |
| `amy debit budget NDEBIT --amount SATS [--frequency day\|week\|month] [--timeout SECS]` | Authorize a spending budget; omit `--frequency` for a one-time budget. |

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
| `amy relay probe [--timeout SECS] [--concurrency N]` | The relay census: mass-connect every relay the local store knows (all stored kind:10002 relays + the reachability cache) in parallel waves and record live/dead + measured `rtt-open` into the NIP-66 reachability cache (kind:30166). Reachability-aware commands (`graperank crawl`/`refresh`) read it to skip dead relays and pre-connect live ones. (`amy graperank probe` remains as an alias.) |

### Local store maintenance

The shared store lives under `~/.amy/shared/` — a SQLite `events.db` by
default, or the `events-store/` file tree when `AMY_STORE=fs` (see
[DEVELOPMENT.md](./DEVELOPMENT.md)).

| Command | What it does |
|---|---|
| `amy store stat` | Event count + disk usage (kind histogram/mtime on the fs backend). |
| `amy store sweep-expired` | Delete events past their NIP-40 expiration. |
| `amy store scrub` | fs: rebuild `idx/` from canonical events; sqlite: no-op. |
| `amy store compact` | fs: drop dangling index entries; sqlite: `VACUUM`. |
| `amy store reindex-fts` | Rebuild the NIP-50 search index (after a searchable-kinds change). |

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

**Exit codes** — the real signal for scripts. The error **code string picks
the exit code**: `bad_args` → 2, `timeout` → 124, every other code → 1.

| Code | Meaning |
|---|---|
| 0 | success |
| 1 | runtime error (network, permission, `rejected`, `not_member`, …) |
| 2 | bad arguments — **any** `bad_args`, including unknown flags and malformed values |
| 124 | timed out — `await` verbs, `pow mine`, offer/debit round-trips |

Notable error codes (the full canonical list is in
[DEVELOPMENT.md](./DEVELOPMENT.md)):

- **`rejected`** (exit 1) — a publish was refused by **every** targeted
  relay **and at least one relay actually answered `OK false`**; the payload
  carries `event_id` + `rejected_by`. When every failure is transport-level
  (unreachable, dropped, silent past the window) the code is `timeout`
  (exit 124) instead — retry a flaky network, don't give up on a rejection
  no relay voiced. Partial acceptance still exits 0 and reports
  `published_to` / `rejected_by`. `rejected_by`
  is a list of `{relay, reason}` objects — the reason is the relay's own
  NIP-01 OK message (`blocked: …`, `rate-limited: …`), a connect error, or
  `no response within timeout`, so "why didn't it post?" answers itself:

  ```text
  $ amy notes post "hi"
  …
  rejected_by:
    - relay:  wss://nostr.wine/
      reason: blocked: not on the allowlist
  ```
- **`bad_args`** (exit 2) — also raised for **unknown flags** (`--limt 5`
  fails instead of silently no-oping) and malformed numeric / relay-URL /
  `--author` / `--id` values.
- **`timeout`** (exit 124) — every timeout error, not just `await`.

**Argument-parsing conveniences:**

- `amy <cmd> --help` prints that command group's usage (and an unknown
  sub-verb echoes the expected verb list). An unknown top-level verb prints
  a one-screen verb list; `amy --help` remains the full reference.
- A literal `--` ends flag parsing — everything after it is positional even
  if it starts with `--` (`amy notes post -- "--good morning"`).

---

## Multi-account workflows

`amy` is built to host more than one identity per machine. The layout
matches that:

```
~/.amy/
├── current                    # marker: which account `amy use NAME` pinned
├── operator/                  # machine-level GrapeRank operator keys (no account)
├── shared/
│   ├── events.db              # the shared event store — SQLite, the default
│   └── events-store/          # …or this file tree instead, when AMY_STORE=fs
├── alice/
│   ├── identity.json          # keypair (or reference to keychain entry)
│   ├── state.json             # sync cursors
│   ├── aliases.json           # local name → npub map
│   ├── cashu.json             # NIP-60 NUT-13 counters
│   ├── concord.json           # Concord community secrets
│   └── marmot/                # MLS state per group
└── bob/
    └── …
```

**Account selection** when you don't pass `--account`:

1. If `~/.amy/current` is set, use it.
2. Else if exactly one account exists, use it (silent auto-pick).
3. Else — for a **read-only** verb, run **anonymously**; for a **signing**
   verb, error and list the candidates so you can disambiguate.

**No account? Reads still work.** Verbs that only query relays or the shared
event store — `fetch`, `subscribe`, `count`, `publish` (broadcasts a
pre-signed event), `outbox`, `search`, `sync`, `store …`, the read halves of
`profile`/`notes`/`git`/`podcast`/`podcast20`, `nsite`/`napplet` fetch/serve/
list, `blossom download`/`check`, `offer`/`debit info`, and every stateless
primitive — run against an empty `~/.amy/` with a throwaway key. They read
fine; they just can't authenticate. Only verbs that **sign or encrypt with
your key** (post, edit, follow, dm, marmot, zap, relay-list edits, blossom
upload/list/delete, cashu, …) require an account — and say so.

`amy use NAME` writes `~/.amy/current`; `amy use --clear` removes it.
For one-off override, prepend `--account NAME` to any command.

`init` and `create` write a self-entry into `aliases.json` so you can
refer to your own account by name in future commands. Aliases **resolve in
every user slot**: anywhere a command takes a USER (`amy dm send bob "hi"`,
`amy follow bob`, `amy profile show bob`, …) the input is checked against
`aliases.json` first, then parsed as npub/nprofile/hex/NIP-05.

For the deeper layout (events-store internals, relay-routing rules, the
public-contract guarantees) see [DEVELOPMENT.md](./DEVELOPMENT.md).

---

## For agents and scripts

Three contracts keep amy machine-safe:

1. **One JSON object per success on stdout** under `--json`. Stable
   snake_case keys; keys never disappear silently.
2. **Errors as JSON on stderr** under `--json`: `{"error":"...","detail":"..."}`.
3. **Exit codes mean specific things** (table above) — `124` for a
   timeout in particular lets you distinguish "condition never
   happened" from "the command itself crashed", and `rejected` (exit 1)
   means no targeted relay accepted a publish.

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

- **`no account configured` / `multiple accounts in ~/.amy (alice, bob)`** —
  only **signing** verbs raise these; reads run anonymously instead (see
  "No account? Reads still work" above). Create one with
  `amy --account NAME init` (bare keypair) or `amy --account NAME create`
  (full Amethyst-style bootstrap), or pin/select one with `amy use NAME` /
  `--account NAME`.
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

- **[RECIPES.md](./RECIPES.md)** — task-shaped walkthroughs: run a
  relay, bunker, marmot, cashu, nsite, graperank.
- **[DEVELOPMENT.md](./DEVELOPMENT.md)** — design principles,
  architecture, the public contract, the local event store, relay
  routing, full on-disk layout, how to extend amy without breaking it.
- **[ROADMAP.md](./ROADMAP.md)** — north-star goal and the parity matrix
  tracking what's left to extract from the Android app.
- **[`plans/`](./plans/)** — design docs for cross-cutting work
  (CLI distribution, file-backed event store, NIP-17 DMs, …).
- **[Nostr NIPs](https://github.com/nostr-protocol/nips)** — the
  protocol amy speaks.
