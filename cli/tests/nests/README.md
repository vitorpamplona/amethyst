# Nests audio-room interop harness

Manual interop test script for Amethyst's audio-rooms feature against
[nostrnests.com](https://nostrnests.com) (the reference web client
[`NestsUI-v2`](https://github.com/nostrnests)). Verifies the full
NIP-53 / kind:30312 + moq-lite Lite-03 stack end-to-end, prompting the
operator at every step.

This harness is **fully manual** — `amy` does not currently ship
`amy nests <verb>` subcommands, so every action runs through the
production UIs on each side. The script's job is to walk the operator
through every feature + edge case and record pass / fail / skip per
test, the same way `marmot/marmot-interop.sh` does for MLS.

## Quick start

```bash
cd cli/tests/nests
./nests-interop.sh
```

The script prompts for two npubs (Amethyst account A, web account W)
and an optional third (X, used by tests 11, 28, 32, 45, 47), then
walks through 47 tests covering every interop surface.

To run a subset:

```bash
./nests-interop.sh --only 03 --only 10           # just audio-path round-trips
./nests-interop.sh --skip 36 --skip 38           # skip the 11- and 6-minute waits
./nests-interop.sh --keep-state                  # reuse cached npubs from a prior run
```

## Prerequisites

### Amethyst (account A)

1. Build & install **this branch** (`claude/test-nests-amethyst-interop-WETiY`)
   on a real Android device (emulator works for most tests but not
   tests 14 / 16 / 18 / 33 / 34 — mic capture, foreground service, PIP).
2. Sign in with account A.
3. **Settings → Relays** — make sure the relay set overlaps the web
   client's. Default `damus.io / nos.lol / primal.net` works for
   nostrnests.com out of the box.
4. **Settings → Audio-room servers** — confirm
   `nostrnests.com → https://moq.nostrnests.com` is present (default).
5. Grant **microphone** + **post notifications** permissions on first
   prompt (the foreground-service notification needs the latter on
   Android 13+).

### nostrnests.com (account W)

1. Open <https://nostrnests.com/> in a Chromium-based browser
   (Chrome, Edge, Brave) — Firefox does not yet ship WebTransport
   stable.
2. Install a Nostr signer extension — [Alby](https://getalby.com),
   [nos2x](https://github.com/fiatjaf/nos2x), or similar.
3. Sign in with account W via the extension popup.
4. Allow microphone access when the browser prompts.
5. Confirm the relay list overlaps Amethyst's.

### Optional third identity (X)

Used by:

| Test | Why X is needed |
|---|---|
| 04, 11 | Verifies the listener counter dedupes correctly with 2 listeners |
| 28 | Late-join chat-history backfill |
| 32 | 3-speaker concurrent broadcast |
| 45 | Same-pubkey-on-two-devices dedupe (X is the second device) |
| 47 | Concurrent host-edit race |

Provide X via either nostrnests.com in a second browser profile, or a
second Amethyst install on a different device. If you skip X the
dependent tests are recorded as `SKIP` and the rest run normally.

## What is covered

| # | Test | Surface |
|---|---|---|
| 01 | Amethyst hosts → web discovers | kind:30312 publish + relay scan |
| 02 | Web joins as listener | NIP-98 → JWT mint → moq-lite Lite-03 ALPN |
| 03 | Amethyst → web audio | Opus encode → moq uni-stream → Web Audio decode |
| 04 | Listener counter aggregation | kind:10312 dedupe by pubkey |
| 05 | Edit room metadata | kind:30312 re-publish round-trip |
| 06 | Web leaves | presence with `["leaving"]`, then heartbeat decay |
| 07 | Amethyst host closes | kind:30312 with `["status","ended"]` |
| 08–11 | Reverse direction (web hosts) | symmetric with 01–04 |
| 12 | Listener cannot publish | role-gating in moq-auth + client UI |
| 13–14 | Mute round-trips | kind:10312 with `["muted","1"]` |
| 15–19 | Hand-raise + promote / demote | kind:10312 `["hand","1"]` + kind:30312 role re-publish |
| 20–22 | Reactions (kind 7) | NIP-25 + NIP-30 custom emoji |
| 23 | Reaction overlay auto-clear | 30-second visibility window |
| 24–28 | In-room chat (kind 1311) | including image upload + history backfill |
| 29–30 | Kick (kind 4312) | ephemeral admin command + auto-disconnect |
| 31 | Web closes room | reverse of test 07 |
| 32 | Multi-speaker | 3 concurrent moq-lite publishers |
| 33 | Background audio | `AudioRoomForegroundService` |
| 34 | PIP screen | `NestPipScreen` |
| 35 | Network drop → reconnect | `ReconnectingNestsListener` + backoff |
| 36 | Long session (11 min) | JWT re-mint past 10-min `exp` |
| 37 | Force-close | ghost-listener decay via heartbeat-timeout |
| 38 | Scheduled rooms | `["status","planned"]` + `["starts","<unix>"]` |
| 39 | Themed room | EGG-10 graceful fallback |
| 40 | Share via naddr | NAddress deep link round-trip |
| 41 | Custom moq server | kind:10112 `NestsServersEvent` |
| 42 | Empty room (host alone) | publisher path with 0 listeners |
| 43 | Long title / summary | UI truncation + relay size limits |
| 44 | Leave stage (onstage=0) | role tag preserved across stage exit |
| 45 | Same-pubkey two-device dedupe | counter sees one logical user |
| 46 | Per-participant actions | profile / follow / mute / zap from a room |
| 47 | Concurrent host-edit race | last-write-wins on relay |

Tests 36 and 38 contain `sleep` calls (11 min and 6 min). Skip them
with `--skip 36 --skip 38` if you're iterating.

## How human interaction works

Three prompt styles. The color always tells you which side to act on:

```
---- DO THIS IN AMETHYST (Android) ----    # yellow
1. Tap …
[Press Enter when done]
```

```
---- DO THIS ON nostrnests.com (web) ----  # magenta
1. Click …
[Press Enter when done]
```

```
---- DO THIS ON THE 3RD IDENTITY (X) ----  # cyan
1. …
```

After each test the script asks a verification question:

```
? Did Amethyst's Audience now show W?
   [p]ass / [f]ail / [s]kip:
```

Pick `p`, `f`, or `s`. The result is logged + tallied in the final
summary. Fails record `FAIL <test-id>` to the log so you can grep
later. Skipped tests carry a note explaining why (filtered, no third
identity, operator declined long-wait, etc.).

## Output

- `state/logs/run-<timestamp>.log` — every prompt, every confirm
  outcome, every value the operator pasted.
- `state/results-<timestamp>.tsv` — `test_id <TAB> pass|fail|skip <TAB> note`.
- `state/run.env` — cached npubs (and any other state captured via
  `prompt_text`); re-used with `--keep-state`.
- Final colored summary table on stdout.

## Cleanup

State is kept on disk so you can `--keep-state` to resume across runs.
To start completely fresh:

```bash
rm -rf cli/tests/nests/state/
```

Rooms created during a run are real public rooms on
nostrnests.com. They're closed automatically by the script (see
test 07, 31, etc.) but if you `Ctrl-C` mid-run, leftover rooms will
auto-close after ~8 hours of inactivity per the nests server's
default policy.

## Known limitations

- **Audio quality** is operator-judged ("did you hear it cleanly?").
  No automatic SNR / latency measurement.
- **Tests 33 / 34** require a real Android device — emulators don't
  expose the foreground-service notification or PIP gestures the
  same way as a phone.
- **Test 36** (11-min token refresh) and **test 38** (6-min schedule
  wait) are slow. Use `--skip 36 --skip 38` during iteration.
- **Test 45** needs a second Android device or emulator running A.
- **Test 41** needs a non-default moq-rs deployment to be meaningful.
- The script does **not** verify which moq endpoint a connection
  actually used — that visibility lives in
  `nestsClient/src/jvmTest/interop/` (the Docker-driven JVM tests
  gated by `-DnestsInterop=true`). Pair this script with that suite
  for a complete picture.

## Pointers

- `nestsClient/specs/README.md` — EGG conformance specs
- `nestsClient/plans/2026-04-26-audio-rooms-completion.md` — feature status
- `nestsClient/plans/2026-04-26-nostrnests-integration-audit.md` — gap list
- `nestsClient/plans/2026-04-26-moq-lite-gap.md` — wire-spec details
- `cli/tests/marmot/marmot-interop.sh` — the inspiration / pattern this script follows
- Amethyst nests UI: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/nests/`
- nests transport / audio: `nestsClient/src/commonMain/`
