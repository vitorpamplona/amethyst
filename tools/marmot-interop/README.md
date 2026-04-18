# Marmot Interop Test Harness

Interactive harness that validates Amethyst's Marmot/MLS implementation against
**whitenoise-rs** (https://github.com/marmot-protocol/whitenoise-rs), the
reference Rust implementation that powers the White Noise Flutter app.

The harness drives two `wn` daemons (Identities **B** and **C**) from the
command line and prompts the human operator to perform the Amethyst-side steps
in the mobile UI as **Identity A**. Every test records a pass/fail/skip result
into a tab-separated log, and the summary is printed at the end of the run.

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

## Prerequisites

On the machine that runs the harness:

- **Rust 1.90+** — install via https://rustup.rs
- **git**, **curl**, **jq** — package manager
- **~5 GB disk** for the first-run build of `wn` + `wnd`
- Public internet access (for the default relay set and fetching crates)

On the Android side:

- Amethyst installed on an **emulator** or a **physical device**
- The device must reach the same relays the harness uses (see below)

## Quick start

```bash
cd tools/marmot-interop
./marmot-interop.sh
```

The script will, in order:

1. Verify `jq`, `git`, `cargo` etc. are present.
2. Clone `whitenoise-rs` into `state/whitenoise-rs/` and build `wn`/`wnd`
   (release, `--features cli`). First build takes ~5 minutes; subsequent runs
   reuse the binaries.
3. Launch two `wnd` daemons (one for Identity B, one for Identity C).
4. Create Nostr identities for B and C, persist their npubs in `state/run.env`.
5. Ask you to paste **your Amethyst account npub** (Identity A). This is
   cached for subsequent runs.
6. Add the default public relays to both daemons and run a sanity check
   (publish a KP from B, fetch it from C).
7. Print an **Amethyst setup checklist** — add the same relays to Amethyst,
   publish a KP, verify you are logged in with A.
8. Run all 13 tests sequentially. Each test either:
   - runs `wn` commands fully automatically and asserts on JSON output, **or**
   - prints a "DO THIS IN AMETHYST" prompt and waits for you to press `<Enter>`,
     then verifies the Amethyst action via `wn`.
9. Stop the daemons and print a results table.

## Command-line flags

```
--local-relays    Use ws://localhost:8080 instead of the default public relays.
                  Required if the public relays reject kinds 444/445/30443.
                  Run 'just docker-up' inside whitenoise-rs first.
--transponder     Run Test 14 (push notifications via the transponder service).
--no-build        Fail instead of rebuilding wn/wnd. Useful when iterating.
-h, --help        Show help.
```

Environment overrides:

```
WN_REPO=/some/path/whitenoise-rs   # use an existing checkout
```

## Default relays

```
wss://relay.damus.io
wss://nos.lol
wss://relay.primal.net
```

These are known to accept kind 1059 (gift wraps) and kind 30000+ (addressable
events). If the **sanity check fails** — meaning C cannot read the KeyPackage
that B just published — the harness warns you and continues. In that case
re-run with `--local-relays` after starting the Docker stack:

```bash
cd state/whitenoise-rs
just docker-up
cd ../..
./marmot-interop.sh --local-relays
```

For Amethyst with `--local-relays`:

- **Android emulator:** add `ws://10.0.2.2:8080` to Settings → Relays and
  Settings → Key Package Relays.
- **Physical device on same Wi-Fi:** add `ws://<laptop-LAN-ip>:8080`.

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
- `state/` — runtime directory, gitignored. Contains `whitenoise-rs/` source
  checkout, per-daemon data/log dirs, the session `run.env`, logs, and
  results TSVs.
