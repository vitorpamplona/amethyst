# Amy — Amethyst CLI

`amy` is the non-interactive command-line face of Amethyst. It speaks the
same Nostr protocol as the Android and Desktop apps, shares the same
`quartz` and `commons` code, and aims to eventually expose every feature
the GUI offers as a command you can script.

Amy exists to serve three audiences:

1. **Humans** who want to use Amethyst from a terminal or remote shell.
2. **Agents / LLMs** that need a deterministic, JSON-typed interface to
   drive a Nostr account.
3. **Interop test harnesses** that put Amethyst side-by-side with the
   other ~100 Nostr clients publishing and consuming the same events.
   Any flow that is tested in the Amethyst app should be reproducible
   through `amy` — that's the bar.

> Today Amy covers identity, relay config, account bootstrap, and
> Marmot / MLS group chat (MIP-00 / NIP-445). Everything else from the
> Android app is on the roadmap — see [DEVELOPMENT.md](./DEVELOPMENT.md).

---

## Design contract

- **Non-interactive.** One invocation, one job, exits cleanly. No REPL,
  no daemon, no hidden prompts. If a subcommand would need to block
  waiting for something on the network, it is an explicit `await` verb
  with a `--timeout`.
- **stdout is JSON. One line. One object.** Always. Pipe it into `jq`,
  parse it from Python, feed it to an agent — the shape is stable.
- **stderr is for humans.** Progress logs, warnings, `[cli] ingest …`
  traces all go to stderr and are safe to discard.
- **Exit codes are the real signal.**
  - `0` — success
  - `1` — runtime error (JSON `{"error":"…","detail":"…"}` on stderr)
  - `2` — bad arguments
  - `124` — `await` timed out
- **Data-dir is the whole world.** All persisted state (identity,
  relays, MLS epochs, message archives, run cursors) lives under
  `--data-dir PATH`. Delete it to reset; copy it to move; `AMETHYST_CLI_DATA`
  env var overrides the default `./amethyst-cli-data`.

Those rules are not incidental — they make `amy` cleanly consumable
from CI, from agents, and from other Nostr clients running the same
test matrix.

---

## Install

Until Amy ships as a signed native binary (see the "Distribution" section
in [DEVELOPMENT.md](./DEVELOPMENT.md)), run it from source:

```bash
# One-shot run — positional args go after `--args`, quoted as a single string
./gradlew :cli:run --quiet --args="whoami"

# Or build a runnable distribution and use the generated launch script
./gradlew :cli:installDist
./cli/build/install/amy/bin/amy whoami
```

The `installDist` tree under `cli/build/install/amy/` is self-contained
(just a JVM launcher + jars) and is what downstream packaging
(Homebrew formula, `.deb`, `.msi`, etc.) will wrap.

**Requirements:** JDK 21.

---

## Quick start

```bash
# 1. Create a data-dir with a full Amethyst-style account.
#    Generates a keypair, seeds default NIP-65 / inbox / key-package
#    relays, and publishes the nine bootstrap events (profile, contacts,
#    relay lists, etc).
amy --data-dir ./alice create --name "Alice"

# 2. Publish a fresh MLS KeyPackage so other users can invite you.
amy --data-dir ./alice marmot key-package publish

# 3. Create a group, invite someone, send a message.
amy --data-dir ./alice marmot group create --name "Test Group"
amy --data-dir ./alice marmot group add <GID> npub1...bob
amy --data-dir ./alice marmot message send <GID> "hello"

# 4. On the receiving side — poll until Bob sees the invite.
amy --data-dir ./bob marmot await group --name "Test Group" --timeout 60
amy --data-dir ./bob marmot message list <GID>
```

Every line above prints a single JSON object on success. Compose them
with `jq` to extract IDs:

```bash
GID=$(amy --data-dir ./alice marmot group create --name "Test" | jq -r .group_id)
```

---

## Full command reference

Run `amy --help` for the canonical list. As of today:

| Verb | Summary |
|---|---|
| `init [--nsec NSEC]` | Create or import a bare identity. Does not publish anything. |
| `create [--name NAME]` | Provision a full account + publish the nine Amethyst bootstrap events. |
| `login KEY [--password X] [--private]` | Import `nsec` / `ncryptsec` / BIP-39 mnemonic / `npub` / `nprofile` / hex / NIP-05. Read-only when no secret material is supplied. |
| `whoami` | Print the identity stored in `--data-dir`. |
| `relay add URL [--type T]` | `T = nip65 \| inbox \| key_package \| all`. |
| `relay list` | Dump configured relays by bucket. |
| `relay publish-lists` | Publish kind:10002 (NIP-65) + kind:10050 (DM inbox). |
| `marmot key-package publish` | Publish a fresh MLS KeyPackage (kind:30443). |
| `marmot key-package check NPUB` | Fetch someone else's KeyPackage from their advertised relays. |
| `marmot group create [--name NAME]` | New empty group with you as sole admin. |
| `marmot group list` | All groups you're currently a member of. |
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
| `marmot await key-package NPUB` | Block until a KeyPackage is seen on relays. |
| `marmot await group --name NAME` | Block until we're added to a group with that name. |
| `marmot await member GID NPUB` | Block until NPUB is in GID's member set. |
| `marmot await admin GID NPUB` | Block until NPUB is an admin of GID. |
| `marmot await message GID --match TEXT` | Block until a message containing `TEXT` lands. |
| `marmot await rename GID --name NAME` | Block until GID's name matches. |
| `marmot await epoch GID --min N` | Block until GID's MLS epoch is ≥ N. |

All `await` verbs accept `--timeout SECS` (default 30). Timeout exits 124
so scripts can tell the difference between "condition never happened"
and "the command itself crashed".

### Global flags

- `--data-dir PATH` — defaults to `./amethyst-cli-data` or
  `$AMETHYST_CLI_DATA`. Always an absolute path after resolution.
- `--help` / `-h` — usage summary.

---

## Data-dir layout

```
<data-dir>/
├── identity.json         # nsec/npub/hex — the account
├── relays.json           # nip65 / inbox / key_package buckets
├── state.json            # sync cursors (giftWrapSince, groupSince)
├── keypackages.bundle    # MLS KeyPackage bundles (NostrSignerInternal)
└── groups/
    ├── <gid>.mls         # MLS group state per group
    └── <gid>.log         # decrypted inner events (one JSON per line)
```

All files are plain JSON or framed binary — human-inspectable, easy to
diff across two accounts in a test run.

---

## Use from agents

Amy is intentionally easy for an LLM to drive:

1. Every stdout is one JSON object — `jq`-ready, schema-stable.
2. Errors are JSON too, so "did it work?" is a machine question.
3. No interactive prompts — even password input takes `--password`.
4. `await` verbs mean an agent can say "send this, then wait for the
   other account to see it" without implementing its own polling.

Recommended agent loop:

```text
plan → amy <cmd> --data-dir A → parse JSON → amy <cmd> --data-dir B …
```

When Amy prints `{"error":"bad_args",…}` or exits 2, the agent should
re-read `--help` rather than retry blindly.

---

## Use from interop tests

The test goal that drives Amy's roadmap: every canonical Amethyst
user-flow should be expressible as a short shell script that can also
be aimed at any other Nostr client with a similar harness.

Template:

```bash
set -euo pipefail
TMP=$(mktemp -d)
A=$TMP/alice; B=$TMP/bob

amy --data-dir "$A" create --name Alice
amy --data-dir "$B" create --name Bob

# ... the scenario under test ...

amy --data-dir "$B" marmot await message "$GID" --match "hello" --timeout 60
```

If an Amethyst scenario cannot be scripted through `amy` yet, that is a
gap to close — see the roadmap in [DEVELOPMENT.md](./DEVELOPMENT.md).

---

## Troubleshooting

- **`no identity`** — run `init`, `create`, or `login` first, or pass a
  different `--data-dir`.
- **`not_member`** — the group GID is unknown to this data-dir. Run
  `marmot group list` to confirm, or `marmot await group --name …` to
  wait for an invite to arrive.
- **Hang on a network verb** — Amy connects to the relays in
  `relays.json`; verify with `amy relay list`. Every network-bound
  operation has a timeout — use `--timeout` for `await`, or wrap the
  whole command in `timeout(1)` if you're scripting.
- **Nothing seems to publish** — inspect stderr; each publish prints
  per-relay `OK` / `REJECT` via the `[cli] ingest …` traces.
