# amy CLI test harnesses

Shell-based end-to-end harnesses that drive the `amy` CLI binary — against an
embedded relay (`amy serve`, i.e. **geode**, the relay this repo ships), live
public servers, or no relay at all, depending on the suite. No suite depends on
an external relay binary or a Rust toolchain for its relay: every relay-backed
harness boots geode from the `amy` binary it already built, so the relay under
test is the same server code that runs in production. Eleven directories:

```
cli/tests/
├── lib.sh                 # shared logging, results, assertions
├── headless/              # shared bits used by every harness
│   └── helpers.sh                  # amy wrappers, assertions, embedded relay boot
├── blossom/               # Blossom blob lifecycle vs LIVE public servers
│   └── blossom-live.sh
├── cache/                 # local-store-as-cache semantics (profile show
│   └── cache-headless.sh  #   cache/refresh, store stat) vs embedded `amy serve`
├── clink/                 # CLINK pointer decode — local-only, no relay
│   └── clink-headless.sh
├── dm/                    # NIP-17 DM interop (amy ↔ amy) vs embedded `amy serve`
│   ├── dm-interop-headless.sh
│   ├── setup.sh                    # preflight + identities
│   └── tests-dm.sh
├── git/                   # NIP-34 git collaboration vs embedded `amy serve`
│   └── git-nip34-headless.sh
├── marmot/                # Marmot / MLS group-messaging interop
│   ├── marmot-interop.sh           # interactive — prompts Amethyst Android UI
│   ├── marmot-interop-headless.sh  # zero-prompt
│   ├── setup.sh                    # preflight + wn + identities
│   ├── tests-create.sh             # tests 01–05
│   ├── tests-manage.sh             # tests 06–08, 11
│   ├── tests-extras.sh             # tests 09, 10, 12, 13
│   ├── tests-media.sh              # tests 20-29 (avatar, edits, deletions,
│   │                               #   media v2, retention, disband)
├── nests/                 # Audio-rooms interop (Amethyst ↔ nostrnests.com)
│   ├── nests-interop.sh            # 47-test manual harness
│   └── README.md                   # operator brief + per-test matrix
├── pow/                   # NIP-13 primitives (bench/mine/check) — no relay
│   └── pow-headless.sh
├── relaygroup/            # NIP-29 round-trip vs embedded `amy serve` (geode)
│   └── relaygroup-headless.sh
└── sync/                  # NIP-77 deletion propagation vs `amy serve`
    └── sync-deletions-headless.sh
```

These shell suites are the *interop* layer. The *contract* net — `Args`
parsing, the exit-code contract (bad_args → 2, timeout → 124), and `--json`
shapes — is the JVM unit suite at `cli/src/test/kotlin/` (`ArgsTest`,
`ExitCodeContractTest`, `JsonContractTest`), which drives `runCli`
in-process with an isolated `~/.amy` via the `amy.home` seam. Run it with
`./gradlew :cli:test` — no relay, no Rust, milliseconds.

Suite notes:

- **`clink/clink-headless.sh`** is local-only (no relay): asserts that
  `amy offer info` / `amy debit info` decode the canonical interop vectors
  (the same fixtures quartz's `ClinkInteropTest` uses) to the right fields,
  plus the argument-error paths. The round-trip verbs (`offer request`,
  `debit pay/budget`) need a live CLINK service and aren't covered here.
- **`pow/pow-headless.sh`** is also relay-free: `pow bench` sanity,
  `pow mine` hitting its target (and exiting 124 on an impossible one),
  and mined-nonce round-trips through `pow check`.
- **`cache/cache-headless.sh`** proves the local store is the source of
  truth for reads: `profile show` served from cache vs `--refresh`, and
  `store stat` reporting the right histogram, vs the embedded `amy serve` relay.
- **`relaygroup/relaygroup-headless.sh`** runs NIP-29 create/message/join/
  list/browse against an embedded relay (`amy serve`, which boots geode) —
  no external relay binary. geode doesn't sign 39000-39003, so browse/info
  emptiness is a relay capability, not a client bug.
- **`sync/sync-deletions-headless.sh`** proves NIP-77 deletion propagation
  both directions (plus the `--no-sync-deletions` opt-out) against
  `amy serve`, with one `$HOME` per account so stores don't share.
- **`git/git-nip34-headless.sh`** drives the full NIP-34 collaboration surface
  against `amy serve`: `git init` bootstrapping a repo from the harness's own
  git checkout (announce + state derived via `git`), announce (30617) + state
  (30618) + GRASP list (10317),
  issue (1621), patch (1617), pull request (1618) + update (1619), NIP-22
  comment (1111), NIP-32 label (1985), and status events (1630-1633). It also
  publishes a real `git format-patch` and `git apply`s it back into a scratch
  working tree, and asserts the `issues`/`patches`/`prs`/`thread` reads derive
  the right status (a closed issue reads `closed`, an applied PR reads
  `applied`) and that `--open`/`--closed` filter correctly. Pass `--live` to additionally exercise
  the git smart-HTTP reads (`git browse`/`cat`/`log`) against a real public
  repo (`$LIVE_REPO`, default octocat/Hello-World) — skipped by default since
  it needs a reachable git host.

The Marmot harnesses come in two flavours, same scenarios:

- **`marmot/marmot-interop.sh`** — interactive. Drives B/C via `wn` and
  **prompts the human** to perform each Amethyst-side step in the mobile UI
  (Identity A). Use this for final UI verification.
- **`marmot/marmot-interop-headless.sh`** — zero prompts. Drives A via the
  `amy` CLI (`./gradlew :cli:installDist`) and B/C via `wn`. Runs every
  scenario end-to-end and exits with a pass/fail summary. Use this for CI
  and for iterating on the Nostr/Marmot plumbing without needing to touch a
  phone.

  Most features are covered in BOTH directions — founding (02/03), adding
  (04/05), removal (06/14), leaving (11/15), keypackage rotation (13/16),
  agent streams (18/19), avatar URL (20/21), encrypted media v2 (24/25),
  deletion (23/27), retention (26/28). Three gaps are the reference CLI's,
  not ours, and cannot be closed from here:

  - **edits wn→amy.** `wn messages` has no `edit` verb, and MDK reserves
    kind 1009 so `messages send-event` refuses to forge one. MDK's runtime
    has `edit_message` and its uniffi surface exposes it; only the CLI
    does not.
  - **setting retention from wn.** `wn groups` has no retention verb and
    `groups create` has no flag for it, so test 28 has amy own the setting
    and wn own the sending — which is the half that was untested anyway,
    since inbound messages are where the epoch-pinning rule lives.
  - **disband wn→amy.** Same shape: `disband_group` exists on MDK's runtime
    and uniffi surface (the apps call it) but has no `wn groups` verb, so
    test 29 runs one way only.

  **The daemon is not a way around this**, which is worth stating because it
  is the obvious next idea. `wnd`'s socket protocol
  (`crates/cli/src/daemon/protocol.rs`) carries `Ping`, `Status`, `Shutdown`,
  four `*Subscribe` variants, and `Execute { cli: Box<Cli> }` — and that last
  one takes the same clap command tree `wn` parses. The daemon is a persistent
  host for the CLI's verbs, not a richer RPC, so a verb missing from `Cli` is
  unreachable through the socket too. Closing these three needs either a verb
  upstream in MDK or a driver linked against `marmot-uniffi`/`marmot-c`; both
  are out of scope for a harness that deliberately builds MDK unpatched.

A third, slimmer harness covers the NIP-17 DM surface:

- **`dm/dm-interop-headless.sh`** — two `amy` processes (Identity A and
  Identity D) exchange NIP-17 DMs through the embedded `amy serve` relay.
  No MDK, no Rust — only `amy`.

A harness covers Blossom blob storage (BUD-01/02/04/09) against **live**
public servers rather than a loopback relay:

- **`blossom/blossom-live.sh`** — drives the full `amy blossom` lifecycle
  (upload → HEAD check → download-and-verify-hash → list → cross-server
  mirror → delete) against a real Blossom server. Server-side write
  rejections (whitelists, rate limits, payment) record as SKIP, not FAIL —
  only a broken client contract (bad descriptor, hash mismatch) fails.
  Defaults to `https://files.sovbit.host`; pass `--mirror-server URL` to
  exercise BUD-04. Example:
  `blossom/blossom-live.sh --server https://files.sovbit.host --mirror-server https://blossom.primal.net`.

A fourth harness covers audio rooms (NIP-53 + moq-lite):

- **`nests/nests-interop.sh`** — fully manual interop between Amethyst
  Android and the [nostrnests.com](https://nostrnests.com) reference
  web client. 47 tests spanning host/listener flows, audio round-trip,
  hand-raise + role promotion, reactions, in-room chat (kind 1311),
  kick (kind 4312), close-room, schedule, network-drop reconnect,
  10-min JWT refresh, custom moq servers (kind 10112), and PIP /
  background audio. See `nests/README.md` for the full matrix and
  prereqs.

Both Marmot harnesses validate Amethyst against **MDK**
(https://github.com/marmot-protocol/mdk), the reference Rust implementation of
the Marmot protocol, via its `wn` / `wnd` binaries (the `wn-cli` package).
Every test records a pass/fail/skip result into a tab-separated log, and the
summary is printed at the end of the run.

> These harnesses previously targeted `marmot-protocol/whitenoise-rs`, which was
> archived on 2026-08-05 pinned to `mdk-core 0.8.0`. Testing against it meant
> testing against a frozen MIP-era client. The reference moved into `mdk`, and
> so did we — see `quartz/plans/2026-09-08-marmot-spec-resync.md` for what that
> change exposed.

## What gets tested

| # | Test | Needs 3rd identity |
|---|---|---|
| 01 | KeyPackage publish & discovery (MIP-00) | – |
| 02 | Amethyst creates group, invites wn | – |
| 03 | wn creates group, invites Amethyst | – |
| 04 | 3-member group, add-after-create | yes |
| 05 | wn adds Amethyst to existing group | yes |
| 06 | Member removal + forward secrecy | yes |
| 07 | Group metadata rename round-trip (MIP-01) | – |
| 08 | Admin promote / demote | yes |
| 09 | Reply / react / unreact (inner event kinds 9, 7) | – |
| 10 | Concurrent commits race | – |
| 11 | Leave group | – |
| 12 | Offline catch-up / replay | – |
| 13 | KeyPackage rotation | – |
| 14 | Push notifications (MIP-05) | opt-in via `--transponder` |

### DM (amy ↔ amy, NIP-17) — `dm/dm-interop-headless.sh`

| # | Test |
|---|---|
| dm-01 | Text round-trip A↔D (kind:14) |
| dm-02 | `dm list` returns prior exchange with `type:text` discriminator |
| dm-03 | Strict kind:10050 refuses sends to an inboxless recipient |
| dm-04 | `--allow-fallback` opts into the NIP-65 read / bootstrap chain |
| dm-05 | File message reference mode round-trip (kind:15 with manual key/nonce) |
| dm-06 | `dm list --since` filters out older messages (window-slide past the newest event returns 0) |

**Relay binding note:** the DM harness binds the loopback relay to
`127.0.0.2` (not `127.0.0.1`) because Quartz's `RelayTag.parse` rejects
localhost URLs via `isLocalHost()` — so `ws://127.0.0.1` in a kind:10050
event is silently stripped during recipient-relay resolution, which
would make strict-mode DM sends spuriously fail. `127.0.0.2` is still
pure loopback and isn't matched by that filter. Override with
`--host 127.0.0.5` etc. if `127.0.0.2` is taken.

**Note:** dm-05 validates the kind:15 wire format via reference mode
(caller supplies the URL + AES-GCM key/nonce). The upload-mode variant
(`dm send-file --file PATH --server URL`) needs a local Blossom server
and isn't scripted here — the upload classes are unit-tested on desktop
at `desktopApp/src/jvmTest/kotlin/.../service/upload/`.

## Prerequisites

On the machine that runs the harness:

- **Rust 1.90+** — install via https://rustup.rs (for MDK's `wn`/`wnd` only;
  the relay is `amy serve`, no Rust needed for it)
- **git**, **curl**, **jq** — package manager
- **~5 GB disk** for the first-run build of `wn` + `wnd`
- Internet access for fetching crates on the first build. Test traffic
  stays on the machine unless you pass `--public-relays`.

On the Android side:

- Amethyst installed on an **emulator** or a **physical device**
- The device must reach the harness's embedded relay over the network
  (see below), or the public relays when running with `--public-relays`

## Quick start

```bash
cd tools/marmot-interop
./marmot-interop.sh
```

The script will, in order:

1. Verify `jq`, `git`, `cargo` etc. are present.
2. Clone `mdk` into `state/mdk/` and build `wn`/`wnd`
   (`cargo build --release -p wn-cli`). First build takes ~5 minutes;
   subsequent runs reuse the binaries. MDK pins its own Rust toolchain in
   `rust-toolchain.toml`, so rustup may fetch a toolchain on the first run.
3. Launch two `wnd` daemons (one for Identity B, one for Identity C).
4. Create Nostr identities for B and C, persist their npubs in `state/run.env`.
5. Ask you to paste **your Amethyst account npub** (Identity A). This is
   cached for subsequent runs.
6. Boot the embedded relay (`amy serve`, i.e. geode, on `0.0.0.0:8080`),
   add it to both daemons and run a sanity check (publish a KP from B,
   fetch it from C). With `--public-relays` the default public set is used
   instead and the relay is not started.
7. Print an **Amethyst setup checklist** — add the same relay to Amethyst,
   publish a KP, verify you are logged in with A.
8. Run all 13 tests sequentially. Each test either:
   - runs `wn` commands fully automatically and asserts on JSON output, **or**
   - prints a "DO THIS IN AMETHYST" prompt and waits for you to press `<Enter>`,
     then verifies the Amethyst action via `wn`.
9. Stop the daemons and print a results table.

## Command-line flags

```
--public-relays   Use the public relay set below instead of the embedded relay.
                  The only mode whose test traffic leaves the machine; the
                  public relays may reject kinds 444/445/30443.
--port N          Port for the embedded relay (default 8080).
--transponder     Run Test 14 (push notifications via the transponder service).
--no-build        Fail instead of rebuilding wn/wnd. Useful when iterating.
-h, --help        Show help.
```

Environment overrides:

```
WN_REPO=/some/path/mdk             # use an existing checkout
```

## Relays

By default the harness owns the only relay: `amy serve` (geode) bound to
`0.0.0.0:8080`. The `wn` daemons reach it on loopback; Amethyst reaches it
over the network:

- **Android emulator:** add `ws://10.0.2.2:8080` to Settings → Relays,
  Settings → Key Package Relays and Settings → DM Inbox Relays.
- **Physical device on same Wi-Fi:** add `ws://<laptop-LAN-ip>:8080`.

With `--public-relays` the daemons are bootstrapped on

```
wss://relay.damus.io
wss://nos.lol
wss://relay.primal.net
wss://nostr.bitcoiner.social
wss://nostr.mom
```

instead and Amethyst is left on its own relay set, so the run surfaces
real-world discovery failures (A's inbox behind NIP-42, whitelists, kinds the
public relays drop). If the **sanity check fails** in that mode — meaning C
cannot read the KeyPackage that B just published — the harness warns you and
continues; re-run without `--public-relays` to rule the relays out.

## How human interaction works

When the script needs you to do something in Amethyst, it prints a yellow
block like this:

```
---- DO THIS IN AMETHYST ----
In Amethyst:
  1. Tap + -> Create Group
  2. Name: Interop-02
  3. Add member: npub1abc...
  4. Tap Create / Send Invite
-----------------------------
[Press Enter to continue]
```

After you press Enter the script resumes. For UI-only verifications (e.g.
"does Amethyst show reaction 🌮?"), the script asks:

```
? Does Amethyst show the 🌮 reaction?  [p]ass / [f]ail / [s]kip:
```

Pick `p`, `f`, or `s`.

## Output

- **`state/logs/run-<timestamp>.log`** — every step, assertion, and human
  prompt, with the exact `wn` stdout that was parsed.
- **`state/results-<timestamp>.tsv`** — tab-separated `test_id \t status \t
  note` lines. Easy to grep.
- **`state/run.env`** — persistent key/value state (npubs, group ids) so you
  can kill and resume the harness mid-run without losing context.
- **Final summary table on stdout** — colored per-test status and totals.

## State cleanup

The harness leaves `state/` on disk so daemons and identities survive across
runs. To start completely fresh:

```bash
# stops daemons if still running; removes identities, groups, logs
./marmot-interop.sh --no-build   # Ctrl-C when it waits for input, then:
rm -rf state/
```

Published KeyPackages on public relays will remain until they expire naturally
or are deleted via `wn keys delete-all --confirm`. Use a throwaway identity
for B/C if this matters to you.

## Known gaps

- **Test 10 (concurrent commits)** is inherently human-timing sensitive. It's
  a best-effort race; expect occasional flakes.
- **Test 14 (push)** only exercises the harness side — full end-to-end
  verification requires a running `transponder` instance and platform
  registrations that are out of scope here.
- The harness assumes Amethyst exposes UI affordances for add-member,
  remove-member, rename, promote/demote, and leave. If any of those is missing
  from the current build, the corresponding test will fail with a clear note
  rather than crash.

## Files

- `marmot-interop.sh` — main entry point; orchestrates preflight, daemons,
  identities, relays, and runs the 13 tests in sequence.
- `lib.sh` — helpers (logging, prompts, polling, jq wrappers, result table).
- `state/` — runtime directory, gitignored. Contains the `mdk/` source
  checkout, per-daemon data/log dirs, the session `run.env`, logs, and
  results TSVs.
