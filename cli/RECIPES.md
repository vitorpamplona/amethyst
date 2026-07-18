# Amy Recipes

Task-shaped walkthroughs for amy's bigger subsystems. The [README](./README.md)
teaches the verbs; this file teaches the *jobs* — each recipe is a complete,
copy-pasteable path from nothing to a working result, with the gotchas called
out. Commands assume an account already exists (`amy --account me create`)
unless the recipe says otherwise.

Conventions used throughout:

- `$(amy --json … | jq -r .key)` captures a result field — every amy command
  emits one JSON object on stdout under `--json`.
- Exit code `124` always means "the awaited condition never happened", never
  "amy crashed" — build retry loops on it.
- Two-terminal recipes share one machine and one `~/.amy/` on purpose; use
  `--account` to play both sides.

---

## 1. Run your own relay — and administer it

**Goal:** a local Nostr relay with persistence, your account as admin, and
moderation driven from the command line. Amy embeds **geode** (the standalone
Ktor relay built on quartz's relay-server code), so this needs no other
software.

```bash
# Terminal 1 — run the relay with a persistent SQLite store.
amy serve --port 7447 --db ~/relay.db
#   listening: ws://127.0.0.1:7447/
#   admin_pubkeys: [<your pubkey>]      ← the active account is always an admin
```

```bash
# Terminal 2 — make it one of your write relays and publish something to it.
amy relay outbox add ws://127.0.0.1:7447
amy relay publish-lists
amy notes post "first note on my own relay"

# Read it back straight from your relay.
amy fetch --kind 1 --relay ws://127.0.0.1:7447 --limit 10
```

Moderation is NIP-86 over NIP-98-signed HTTP — the same path Amethyst's
relay-management screen uses. Because your account is an admin, `amy admin`
against your own relay Just Works:

```bash
amy admin ws://127.0.0.1:7447 supported-methods
amy admin ws://127.0.0.1:7447 ban-pubkey <hex> --reason "spam"
amy admin ws://127.0.0.1:7447 list-banned-pubkeys
amy admin ws://127.0.0.1:7447 change-name "vitor's relay"
```

**Backups for free:** the relay speaks NIP-77, so a full copy of its contents
is one Negentropy reconcile into your local store — and `--up` pushes your
store's events back after a wipe:

```bash
amy sync --relay ws://127.0.0.1:7447 --down          # relay → ~/.amy store
amy sync --relay ws://127.0.0.1:7447 --up            # store → fresh relay
```

**Gotchas:** in-memory is the default — without `--db` everything is gone on
Ctrl-C. `--admin npub1…,npub2…` grants co-admins. The listen address defaults
to loopback; `--host 0.0.0.0` exposes it.

---

## 2. Remote-sign with a bunker (NIP-46)

**Goal:** keep a key on one machine ("the signer") and use it from another
("the client") — events come out authored by the signer's key. Both directions
of the protocol are covered; pick the one that matches who initiates.

**Direction A — signer advertises (`bunker://`):**

```bash
# Signer machine: host alice's key. Prints a bunker:// URI and blocks.
amy --account alice bunker --relay wss://relay.nsec.app --secret s3cret
#   → bunker://<alice-pubkey>?relay=wss://relay.nsec.app/&secret=s3cret

# Client machine: log in through it (quote the URI — & is shell-special).
amy --account remote login 'bunker://<alice-pubkey>?relay=wss://relay.nsec.app/&secret=s3cret'

# Every signing verb now round-trips to the signer:
amy --account remote notes post "signed over NIP-46"   # authored by alice
```

**Direction B — client advertises (`nostrconnect://`):**

```bash
# Client machine: print an offer and wait for a signer to connect.
amy --account remote login --nostrconnect --relay wss://relay.nsec.app --timeout 120
#   → nostrconnect://<client-pubkey>?relay=…&secret=…   (hand this to the signer)

# Signer machine: ack the offer and start serving requests.
amy --account alice bunker connect 'nostrconnect://…'
```

**Locking the bunker down:** by default the bunker approves everything. Two
gates exist — `--perms sign_event:1,nip44_encrypt,get_public_key` allows only
the listed operations (everything else rejected), and `--interactive` prompts
y/N on the signer's terminal for anything not pre-allowed (needs a TTY; it is
the one deliberate exception to amy's no-prompts rule).

**Gotchas:** if the signer answers with an `auth_url` challenge, amy prints
the URL to stderr and keeps waiting — open it in a browser to authorize. The
bunker services `sign_event`, `nip04_*`, `nip44_*`, `get_public_key`,
`get_relays`, `ping`. Interop with `nak bunker` works in both directions.

---

## 3. Private group chat (Marmot / MLS)

**Goal:** an end-to-end-encrypted group between two accounts, surviving
completely disjoint relay configurations (amy routes per the Marmot spec:
KeyPackages via kind:10051, welcomes via the recipient's kind:10050, group
events via the group's own relay list).

```bash
# Both sides, once: publish a KeyPackage so you're invitable.
amy --account alice marmot key-package publish
amy --account bob   marmot key-package publish

# Alice: create the group and invite Bob (npub, hex, NIP-05, or a local alias).
GID=$(amy --account alice --json marmot group create --name "Ops" | jq -r .group_id)
amy --account alice marmot group add "$GID" bob@example.com
amy --account alice marmot message send "$GID" "welcome"
```

```bash
# Bob: wait for the invite to land, then read and reply.
amy --account bob marmot await group --name "Ops" --timeout 60   # exit 124 = no invite yet
amy --account bob marmot group list
amy --account bob marmot message list "$GID"
amy --account bob marmot message send "$GID" "here"
```

The `await` family is what makes this scriptable — every step of a scenario
has a blocking verb: `await key-package NPUB` (before `group add`),
`await member GID NPUB`, `await message GID --match TEXT`, `await epoch GID
--min N`. All take `--timeout SECS` and exit 124 when the condition never
happens, so a harness can tell "not yet" from "broken".

**Gotchas:** `marmot group add` fails if the invitee has no discoverable
KeyPackage — `marmot await key-package` first. Admin verbs
(`promote`/`demote`/`remove`/`rename`) commit MLS proposals; expect the epoch
to advance. `marmot reset --yes` wipes all local MLS state — a recovery
hammer, not an undo.

---

## 4. An ecash wallet in your terminal (NIP-60 / NIP-61)

**Goal:** a Cashu wallet whose on-relay events are byte-identical to the
Android app's (same shared `commons` wallet code), funded over Lightning,
spending to tokens and nutzaps.

```bash
# 1. Create the wallet (kind:17375) + nutzap advertisement (kind:10019).
amy cashu wallet create --mint https://mint.minibits.cash/Bitcoin

# 2. Fund it: request a Lightning invoice from the mint, pay it from any
#    LN wallet, then complete the quote to mint the proofs.
amy --json cashu receive ln 1000 | tee /tmp/quote.json | jq -r .bolt11
#   … pay the printed bolt11 …
amy cashu receive complete "$(jq -r .quote_id /tmp/quote.json)"

amy cashu balance                      # spendable sats, per-mint breakdown in wallet show
```

Spending, three ways:

```bash
amy cashu send token 210 --memo "coffee"       # → cashuB… token to paste anywhere
amy cashu receive token cashuB…                # ← redeem one somebody sent you
amy cashu send ln lnbc…                        # melt proofs to pay an invoice
amy cashu send nutzap alice@example.com 21 --message "gm"   # P2PK-locked zap
amy cashu receive nutzap-sweep                 # claim nutzaps sent to you
```

Periodic hygiene — mints rotate keysets and proofs go stale:

```bash
amy cashu maintenance scrub                    # prune spent proofs (NUT-07 + NIP-09)
amy cashu maintenance migrate-keysets          # consolidate onto active keysets
amy cashu maintenance restore https://mint…    # rebuild proofs from the wallet seed
```

**Gotchas:** `receive complete` polls the quote — if the invoice isn't paid
yet it tells you; re-run it (or `resume`, its deprecated alias) after paying.
The wallet's P2PK key is printable with `cashu wallet export-key` — that key
IS the money; treat the output accordingly. `wallet destroy` retracts the
events but the ecash still lives at the mint — spend or restore first.

---

## 5. Publish a website on Nostr (NIP-5A nsite)

**Goal:** host a static site where the *manifest* lives on relays and the
*files* live on Blossom servers, so any client can fetch and verify it — no
web server of yours involved.

```bash
# Publish a directory: uploads every file to Blossom, then broadcasts the
# kind:15128 manifest (with the aggregate hash, so the site self-verifies).
amy nsite publish ./public --server https://blossom.primal.net \
    --title "my site" --description "built with amy"

# A named secondary site lives under a d-tag next to your root site:
amy nsite publish ./blog --server https://blossom.primal.net --d blog
```

Consume it — from any machine, no account needed (reads run anonymously):

```bash
amy nsite list npub1yourself…                        # root + every named site
amy nsite fetch npub1yourself… --path /index.html    # one file, sha256-verified
amy nsite serve npub1yourself… --port 8080           # browse it: http://localhost:8080
```

**Gotchas:** every response `nsite fetch`/`serve` returns is verified against
the manifest's sha256 pin — a tampering Blossom server produces an error, not
wrong bytes. Re-publishing the same directory replaces the manifest
(kind:15128/35128 are replaceable); old blobs on the Blossom server can be
cleaned with `amy blossom list/delete`. Napplets (NIP-5D sandboxed apps) use
the same shape — `amy napplet publish/fetch/serve` — plus capability
verification.

---

## 6. Stand up a GrapeRank web-of-trust provider (NIP-85)

**Goal:** crawl the follow/mute/report graph around an observer, score it,
and serve signed kind:30382 trust cards that clients can discover. This is
the operator-shaped subsystem — the pipeline is explicit stages so each can
be re-run independently.

```bash
# 0. One-time machine setup: where cards get published, independent of accounts.
amy graperank operator relay wss://cards.example.com

# 1. Crawl the graph into the local store (idempotent — repeat to deepen).
amy graperank crawl                        # network: kind 3/10000/1984/10002
amy graperank followers                    # reverse crawl: who follows YOU
amy relay probe                            # relay census → skip dead relays next time

# 2. Score locally and persist cards (no network — tune and re-run freely).
amy graperank score                        # or: --rigor 0.5 --min-rank 5 …

# 3. Converge the operator relay to the local card set (NIP-77 up-sync),
#    and point your kind:10040 at it so clients can discover the cards.
amy graperank publish
amy graperank register
```

Consume it (any account, including someone else's provider):

```bash
amy graperank rank npub1someone…           # their cards, one rank per provider
amy graperank providers npub1client…       # which providers a user trusts
amy fof get npub1someone…                  # the cheap single-hop cousin
```

Keep it fresh with cron: `graperank refresh` re-syncs every known author's
records from their own outbox (one Negentropy reconcile per write relay), so
the next `score` runs on current data without a full re-crawl. Check
`graperank status` first — it answers "do I need to crawl again?" offline.

**Gotchas:** cards are signed by a per-observer **service key** derived from
the operator master seed (`~/.amy/operator/`) — losing everything but that
seed still re-derives every key, so back it up. `publish` never re-scores or
re-signs; it only makes the relay match the local store. For a third-party
observer you hold no key for, `graperank operator keys` prints the
observer→service-key map to wire their kind:10040 out-of-band.

---

## 7. Scripting patterns that hold everything together

The glue the recipes above rely on, in one place:

```bash
# Isolate a scenario completely — amy reads $HOME like git/gpg/npm do.
export HOME=$(mktemp -d)
amy --account a create; amy --account b create

# Branch on exit codes: 0 ok · 1 runtime · 2 bad args · 124 await-timeout.
if amy --account b dm await --peer a --match "ping" --timeout 30; then
    amy --account b dm send a "pong"          # 'a' is an alias — aliases.json
elif [ $? -eq 124 ]; then
    echo "no ping within 30s (relay down? wrong account?)"
fi

# Total-publish-failure is now an error, so set -e pipelines stop honestly:
set -euo pipefail
amy --json notes post "x" | jq -r .event_id   # exits non-zero if EVERY relay rejects

# Machine-read anything; stderr never pollutes stdout.
amy --json profile show fiatjaf@fiatjaf.com 2>/dev/null | jq .metadata.name
```

Discoverability from the binary itself: `amy <cmd> --help` prints any group's
full usage; an unknown verb answers with the verbs that do exist instead of a
400-line dump.
