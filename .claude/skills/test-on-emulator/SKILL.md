---
name: test-on-emulator
description: Build, install, and launch THIS worktree's debug build on the running Android emulator as its own per-branch "slot", so several parallel Claude sessions can each deploy to one shared emulator without overwriting each other. Use when the user (or you, on their behalf) wants to see the current branch's changes running on the emulator — "deploy this", "put it on the emulator", "let me test this build", "install the current branch". Each branch installs as com.vitorpamplona.amethyst.debug.<slot> with its own launcher name + icon tint. NOT for unit tests (use ./gradlew test) and NOT for the desktop app (use /desktop-run).
---

# Deploy this stream to the shared emulator

This repo supports running 5–6 parallel Claude sessions, one git worktree each,
all tested on a **single emulator**. The trick: every branch installs under its
own applicationId "slot", so installs coexist instead of clobbering. This skill
deploys *the branch you're currently in*.

## The one command

From anywhere inside the worktree:

```bash
tools/parallel-streams/deploy-slot.sh
```

That builds `:amethyst:installPlayDebug`, installs it as this branch's slot, and
launches it on the connected emulator. Report the `applicationId` it prints back
to the user so they know which launcher icon is theirs (e.g. `Amy my branch`).

Pass `--no-launch` to install without starting the app. Forward extra Gradle
flags after `--`, e.g. `deploy-slot.sh -- --offline`.

## How the slot is decided (don't re-implement this)

The slot is derived **in Gradle** by `resolveSlot()` in
`amethyst/build.gradle.kts`, in this order:

1. `-Pslot=<x>` Gradle property
2. `AMETHYST_SLOT` env var
3. the current **git branch** (last path segment, sanitized)

Base branches (`main`/`master`/`develop`), detached HEAD, and `none`/`off`/`base`
resolve to **no slot** — the historical plain `.debug` build. Never hardcode the
package name; ask Gradle: `./gradlew -q :amethyst:printDebugAppId`.

## When it fails

- **"no device/emulator connected"** — the emulator isn't running. Cloud / web
  sessions cannot run an Android emulator at all; this only works on a local
  machine (or one with adb pointed at a remote device via `ANDROID_SERIAL`).
  Tell the user rather than retrying.
- **Targeting a specific emulator** — set `ANDROID_SERIAL=emulator-5554`; both
  adb and Gradle honor it.

## Related

- `tools/parallel-streams/streams` — manage the whole fleet (up/ls/down/installed).
- `tools/parallel-streams/README.md` — the full workflow.
- `/desktop-run` — for the desktop app instead of the Android emulator.
