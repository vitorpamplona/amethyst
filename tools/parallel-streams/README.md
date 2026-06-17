# Parallel streams: many sessions, one emulator

Run several Claude Code sessions in parallel — one git worktree per branch — and
test them all on a **single Android emulator**. The mechanism: every debug build
installs under its own *slot* applicationId, so the installs coexist instead of
overwriting one another.

```
com.vitorpamplona.amethyst.debug                 # main/master (no slot, unchanged)
com.vitorpamplona.amethyst.debug.my_feature      # branch "my-feature"
com.vitorpamplona.amethyst.debug.claude_xyz      # branch "claude/xyz"
```

Each slot also gets its own **launcher name** (`Amy my feature`) and a
deterministic **icon tint**, so they're easy to tell apart on the home screen.

## The four pieces

1. **Slot wiring** — `amethyst/build.gradle.kts` (`resolveSlot`, `slotColor`).
   The debug build type derives a slot and sets `applicationIdSuffix`,
   `versionNameSuffix`, the `app_name` string, and the `ic_launcher_background`
   color from it. This is the single source of truth.
2. **`deploy-slot.sh`** — build + install + launch the current branch's slot.
3. **`streams`** — manage the fleet of worktrees + tmux windows.
4. **Android Studio run config** — *Install current slot (emulator)* runs
   `deploy-slot.sh` for the open project. Plus the `test-on-emulator` skill so
   Claude can deploy on request.

## How the slot is chosen

`resolveSlot()` in `amethyst/build.gradle.kts`, in order:

1. `-Pslot=<x>` Gradle property
2. `AMETHYST_SLOT` environment variable
3. the current **git branch** (last path segment, lowercased, non-alphanumerics
   → `_`, capped at 16 chars)

`main` / `master` / `develop`, a detached HEAD, and the literals
`none` / `off` / `base` all resolve to **no slot** — i.e. the historical plain
`.debug` build with the white icon and `Amy Debug` name. So nothing about the
default `installDebug` on `main` changes.

```bash
./gradlew -q :amethyst:printDebugAppId          # what would this branch install as?
./gradlew :amethyst:installPlayDebug -Pslot=qa  # force an explicit slot
AMETHYST_SLOT=qa ./gradlew :amethyst:installPlayDebug
```

## Daily workflow

```bash
# Spin up 3 streams (worktrees under .worktrees/, each a tmux window running claude)
tools/parallel-streams/streams up feature-a feature-b claude/experiment
tools/parallel-streams/streams attach           # jump into the tmux session

# Inside any worktree, deploy that branch to the shared emulator:
tools/parallel-streams/deploy-slot.sh           # (or click the AS run config, or ask Claude)

# See what's going on
tools/parallel-streams/streams ls               # worktrees + branches
tools/parallel-streams/streams installed        # slots currently on the emulator

# Tear a stream down (removes worktree + tmux window)
tools/parallel-streams/streams down feature-a
```

Target a specific emulator with `ANDROID_SERIAL=emulator-5554` — both `adb` and
Gradle honor it.

## Requirements

- `adb` on `PATH` or a standard SDK location (`ANDROID_HOME` /
  `ANDROID_SDK_ROOT`).
- `tmux` (for the `streams` fleet commands only; `deploy-slot.sh` needs only adb).
- A running emulator/device. This does **not** work in cloud/web sessions, which
  have no emulator.

## Notes

- Worktrees default to `.worktrees/` (gitignored). Override with `STREAMS_DIR`.
- The tmux session is named `amethyst`. Override with `STREAMS_TMUX`.
- Slots only affect the **debug** build type — release/benchmark are untouched.
