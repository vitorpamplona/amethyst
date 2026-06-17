# streams: parallel Claude sessions, one app at a time

Run several Claude Code sessions in parallel — one git worktree per branch, each
in its own tmux window — and deploy any of them to the emulator to test.

The app installs as a **single** debug package
(`com.vitorpamplona.amethyst.debug`, "Amy Debug"), so testing is serial:
`streams deploy` from a worktree builds and installs that branch, replacing
whatever was on the device before. One version at a time.

## Daily workflow

```bash
# Spin up 3 streams (worktrees under .worktrees/, each a tmux window running claude)
tools/streams/streams up feature-a feature-b claude/experiment
tools/streams/streams attach            # jump into the tmux session

# Inside any worktree, build + install + launch that branch on the emulator:
tools/streams/deploy.sh                 # --fdroid for the F-Droid flavor; --no-launch to skip starting it

# See your streams
tools/streams/streams ls

# Tear one down (removes worktree + tmux window)
tools/streams/streams down feature-a
```

`streams deploy [dir]` does the same as running `deploy.sh` inside `dir` (default:
the current directory).

## Requirements

- `tmux` (for `up`/`attach`/`down`; `deploy.sh` needs only adb).
- `adb` on `PATH` or a standard SDK location (`ANDROID_HOME`/`ANDROID_SDK_ROOT`).
- A running emulator/device. Target a specific one with
  `ANDROID_SERIAL=emulator-5554`. This does **not** work in cloud/web sessions,
  which have no emulator.

## Notes

- Worktrees default to `.worktrees/` (gitignored). Override with `STREAMS_DIR`.
- The tmux session is named `amethyst`. Override with `STREAMS_TMUX`.
