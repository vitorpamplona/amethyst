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

$ amy relay publish-lists      # broadcast updated kind:10002/10050/10051
```

---

## Commands

### Identity

| Command | What it does |
|---|---|
| `amy --account NAME init [--nsec NSEC]` | Create or import a bare keypair. No relay traffic. |
| `amy --account NAME create [--name X]` | Full Amethyst-style bootstrap: keypair, default relays, kind:0, kind:3, the works. |
| `amy login KEY [--password X]` | Import an existing identity (`nsec`/`ncryptsec`/mnemonic/`npub`/`nprofile`/hex/NIP-05). |
| `amy whoami` | Print the active account's name + npub. |
| `amy use NAME` / `--clear` / no-arg | Pin / clear / inspect the active account. |

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

| Command | What it does |
|---|---|
| `amy relay add URL [--type T]` | Add URL to a bucket: `nip65`, `inbox`, `key_package`, or `all`. |
| `amy relay list` | Print the configured relays per bucket. |
| `amy relay publish-lists` | Broadcast your kind:10002 / 10050 / 10051. |

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
