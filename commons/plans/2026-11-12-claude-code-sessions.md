# Claude Code sessions in the desktop app

Status: first version (2026-11-12). Desktop only.

## Goal

Develop from inside Amethyst, Amethyst itself included: see every Claude Code session on the
computer, continue one or start one, approve its tool calls, and try the result.

## Shape

| Piece | Where | Notes |
|---|---|---|
| CLI client: process, stream-json protocol, permission prompts, session files | `claudeCodeClient/` (new JVM module) | No Amethyst or Nostr deps, so it can move to its own repository. Unofficial: drives the user's `claude`, bundles nothing from Anthropic. |
| Chats that outlive their screen, the session list, git worktrees, running the app from a checkout | `commons/src/jvmMain/.../claudecode/` | `ClaudeCodeService.shared` holds the processes; `ClaudeChat` per session; ViewModels thin over it. |
| Screens | `commonsUI/src/jvmMain/.../screen/loggedIn/claudecode/` | Registered by `desktopOnlyDestinations()`, which only the desktop app calls; the drawer row (`NavBarItem.CLAUDE_CODE`) hides wherever the route has no screen. |
| Strings | `commonsUI` composeResources, `claude_code_*` | |

Why not the official Agent SDK: it is TypeScript/Python and proprietary ("All rights reserved",
Commercial Terms), so it can't ship in the MIT app. The CLI's stdin/stdout protocol is small; the
client was written from the CLI's flags, the SDK's public type definitions and recorded output, not
from the SDK's code. Someone should still read Anthropic's terms before this is published as its
own library.

Why desktop only: an Android app can't run the CLI. Reaching a computer's sessions from a phone
would go over Nostr (the Buzz agent channel, `cli/plans/2026-07-25-buzz-agent-support-channel.md`).

## Behaviour

- The list merges the CLI's saved sessions with the chats open in the app, newest first, with a
  folder filter and search, and marks sessions another Claude Code has open
  (`~/.claude/sessions/<pid>.json`).
- A saved session shows its history from its file; its process starts on the first message, with
  `--resume`.
- Tool calls that need the user show an approve/deny card; "don't ask again" sends the CLI's own
  suggestion back.
- A new session can start in a fresh git worktree (`<repo>-worktrees/claude-<time>` on a
  `claude/<time>` branch), so the running app's checkout is untouched.
- In an Amethyst checkout, the chat's play button runs `./gradlew :desktopApp:run` there, output
  in `build/claude-code-run.log`.

## Not done

- Rendering replies as markdown (shown as plain selectable text).
- Images in prompts, slash commands, model switching in the UI, forking from a message.
- Claude.ai cloud sessions (no documented API to list them).
- Subagent messages are folded into their tool call, not shown.
