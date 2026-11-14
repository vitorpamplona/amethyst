# Claude Code sessions in the desktop app

Status: second version (2026-11-13): redesigned screens and the NIP-34 workflow. Desktop only.

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
- Tool calls read as what they do, not as JSON (`ToolViews`): an edit or a write is a unified diff
  (`LineDiff`, placed at its line in the file) drawn by the git diff view; a command is
  `$ command` and its output; paths are relative to the session's folder. Edits keep their own
  rows; reads, searches and commands fold into one line per run.
- Tool calls that need the user show an approve/deny card with the change itself (the diff of an
  edit, measured against the file as it is; the command); Enter gives the default answer, Esc
  denies, and "don't ask again" sends the CLI's own suggestion back.
- A session another Claude Code holds is read-only here; "Fork into a new session" resumes it with
  `--fork-session`, so the copy gets a session of its own on its first message.
- A chat that waits on an approval or ends its turn while the window is in the background raises
  an OS notification (`ClaudeCodeService.attention`, `NotifKind.AGENT`, on the desktop
  notifications switch), and the drawer counts the chats waiting (`LocalDrawerBadges`).
- The footer shows how full the context is (the last reply's `usage` against the result's
  `contextWindow`), not the cost. Ctrl/Cmd+K jumps between sessions.
- The Changes screen shows what isn't committed (new files included), the whole branch, or one
  commit; a line can be asked about, and the question goes to the chat with the line quoted.
- The session reader keeps the results of calls made together in one turn: the CLI hangs an
  earlier call's result off its call, beside the chain the walk follows.
- A new session can start in a fresh git worktree (`<repo>-worktrees/claude-<time>` on a
  `claude/<time>` branch), so the running app's checkout is untouched.
- In an Amethyst checkout, the chat's play button runs `./gradlew :desktopApp:run` there, output
  in `build/claude-code-run.log`.

## Working through nostr (ngit / gitworkshop)

A session is tied to a NIP-34 repository and, once published, to its proposal, so the work goes
the way an ngit contributor's does and shows up on gitworkshop like any other proposal.

1. **Start from nostr.** "Work on this with Claude" on a repository (top bar), an issue or a
   proposal (thread root, `WorkWithClaudeButton`) opens `Route.ClaudeCodeStart`. It finds the
   user's checkout (remembered in `claude-code-checkouts.json` in the app data folder, or found by
   a `nostr://` remote or clone URL among the folders sessions ran in) or clones it, fetches the
   maintainers' main into `refs/remotes/nostr/<main>`, and makes a worktree on `pr/feat/<slug>`
   (or on the proposal's tip, fetched from `refs/nostr/*`, to continue a proposal). The issue or
   proposal becomes the first message.
2. **Links live in git.** `branch.<name>.nostr-repo`, `nostr-issue`, `nostr-proposal` and
   `amethyst-base` in the branch's config (`GitWorkspace`), so the next session on the branch, or
   any checkout of it, knows what it is.
3. **The agent can't publish.** A session on a nostr repository starts with
   `--disallowedTools` for `git push`, `ngit send`, `ngit pr merge`, `ngit sync` and
   `amy git pr/pr-update` (`ClaudeChat.NO_PUBLISHING`): CONTRIBUTING-WITH-AI.md.
4. **Changes** (`Route.ClaudeCodeChanges`): commits ahead of the base and the diff, uncommitted
   work included, through the git dashboard's `GitDiffView`.
5. **Propose** (`Route.ClaudeCodePropose`): title and description (from the commits, or drafted
   by a tool-less one-off `claude -p`), the repository's build gate
   (`PREPUSH_KMP_METADATA=1 .git-hooks/pre-push`, when it has one; cancellable, its whole output in
   `<git dir>/amethyst/claude-code-gate.log` so it never shows as a change), and two confirmations
   (read the diff, tried it by hand). Publishing signs with the account: `GitProposals.
   openPullRequestFromLocal` / `updatePullRequestFromLocal` push the objects read from the
   checkout (`LocalGitObjects`: `git rev-list --objects` + `git cat-file --batch`) to the GRASP
   servers under `refs/nostr/<event id>`, then publish the kind 1618, or a 1619 for a branch that
   already is a proposal. A repository without a GRASP server can't take local commits that way:
   the branch is first pushed to one of the checkout's remotes, with the user's own git login, and
   the proposal points at that clone URL.
6. **Review loop.** While a linked proposal's chat is open its thread loads; reviewers' comments
   show as a banner whose "Address it" sends them to Claude (`ReviewPrompt`: an inline comment
   quotes its lines, ±2, from the commit it names or the merge base for a removed line). The ids
   sent are kept in the branch's config (`nostr-reviewed`). A merged (1631) proposal offers to
   remove the worktree; one with uncommitted files is kept unless the user removes it anyway.
7. **Names.** A session started from an issue or proposal is named after its subject
   (`rename_session` once the CLI reports the session), not after the long first message.

Tested end to end on this machine with `cli/tests/git/git-grasp-e2e.sh --keep` and
`run-headless.sh --local-only`: started from a proposal, cloned, Claude committed, the 1619 named
the commit, merge base and GRASP server, and a comment from another account reached the session.
Then: the gate run, cancelled, read in full and passed; an inline comment sent with its lines; a
new version published, merged in the app, the worktree removed; "Draft with Claude"; "Run this
checkout" on a stand-in checkout; and a proposal to a repository with no GRASP server, its branch
pushed to a plain remote first.

## Settings assistant

A Claude Code chat whose only tools are Amethyst's settings (Settings › "Settings assistant", or
"Ask the assistant" when a settings search finds nothing; desktop only).

- `commons/settings/registry`: `SettingsRegistry` describes each setting once (id, plain
  description for the model, type and options, app or account, its screen, careful or not) with a
  reader and a writer that goes through the same flows and setters the screens use.
  `AmethystSettings` lists them. Keys, the signer, wallets, payment targets and relay lists are left
  out on purpose.
- The tools run inside the app: the CLI's stream-json mode carries MCP over the session's own pipe
  (`sdkMcpServers` in `initialize`, `mcp_message` control requests answered with `mcp_response`), the
  way the Agent SDK runs in-process tools. `claudeCodeClient/mcp` holds `SdkMcpServer` and the
  JSON-RPC dispatcher; `SettingsToolServer` offers `search_settings`, `get_setting`,
  `open_settings_screen` (allowed without asking) and `change_setting` (approved one at a time; the
  approval card shows the setting's before and after, and offers no "always allow").
- The session starts with no built-in tools (`--tools ""`), none of the user's MCP servers
  (`--strict-mcp-config`) and a system prompt for the role, in a folder of its own.
- Verified with a live CLI (`LiveClaudeCodeTest`) and in the desktop app: "everything is too small
  to read" → Text size NORMAL → LARGE, approved with Enter, applied at once.

## Not done

- Images in prompts, slash commands, model switching in the UI, forking from a given message.
- Claude.ai cloud sessions (no documented API to list them).
- Subagent messages are folded into their tool call, not shown.
- Publishing through the user's own `ngit` instead of the app's signer.
