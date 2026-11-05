# Git dashboard: what is left after the audit

_Status: queued. Tested 2026-11-05. Quartz's share (history, blame and search downloads) is
`quartz/plans/2026-11-05-git-fetch-costs.md` in the quartz repo._

The in-app git features (review, PRs, edits, triage, settings, private repositories, checkout
helpers) shipped with an audit pass. Five items stayed open, and testing them found a sixth. Each
section says what was tested, what was found and the fix.

## 1. Signing flows have never run end to end

**Tested:** the pieces have unit and git-fixture tests: pushing to `refs/nostr/<id>`, commits made
from edits, the pusher against real `git http-backend`, and event building. Nothing has run the
whole thing: sign → push → re-sign → publish → read back in the app.

Those flows are opening a PR, updating one, proposing an edit, resolving and reopening a thread, a
review verdict, labels, a rename, and settings (announcement plus 30618 HEAD). Private repositories
add NIP-98 auth to all of them.

**Why not yet:** a GRASP target needs its relay and its git endpoint on the same host
(`GraspServer.pairedRelay`). Locally, `amy serve` (geode) and `git http-backend` run on different
ports, so the app treats the repository as non-GRASP and skips the push path. Testing against
relay.ngit.dev would publish to public relays.

**Plan:**

1. **A dev GRASP server in geode.** An option (`amy serve --git-root DIR`) that serves smart HTTP
   for `/<npub>/<repo>.git` on the relay's own port.
   - Requests go to `git http-backend` as a CGI subprocess. The git binary is run, not linked, so
     there is no license impact on shipped artifacts. It is test and dev tooling only, never
     bundled.
   - It enforces GRASP's rules:
     - anyone may push `refs/nostr/<event-id>`;
     - a branch push must match the newest 30618 of a maintainer;
     - a repository whose announcement says `private` wants a valid kind-27235 header on every
       request.
   - Quartz already has the relay-side pieces it needs: event store, `HTTPAuthorizationEvent`
     verification.
2. **`amy` verbs on the same code as the app.**
   - `amy git pr open` / `pr-update` / `edit` call `GitProposals` instead of only publishing the
     event. That is CLI parity, and it keeps the rule that amy is thin assembly.
   - Today `GitPrCommands` publishes without pushing, which is also wrong for GRASP.
3. **`cli/tests/git/git-grasp-e2e.sh`**, against that server only:
   1. announce a repository;
   2. open a PR from a branch, and check `refs/nostr/<id>` is on the server and the event names it;
   3. update the PR (1619);
   4. propose an edit (a commit made from file text);
   5. make one server refuse, to exercise the re-sign path;
   6. change the default branch;
   7. repeat steps 1 to 6 for a private repository, with auth required and nothing reaching any
      relay but the repository's.
4. **The desktop app against the same server.**
   - Run it with a throwaway key (`--login-file`) and `AMETHYST_LOCAL_RELAYS`.
   - Click through New pull request, Update branch, Edit and Propose, Resolve and Reopen, the
     review verdict, labels, rename and settings, and look at each result.
   - Inline comment threads get their first real rendering here (see 5).
5. **A unit test** for `GitProposals.publishWithCommits` with a fake pusher: all servers accept,
   one refuses, and every server refuses on each round.

## 2. Editing large files is slow

**Tested:** an offscreen render on the desktop JVM, timing one edit plus its frame in a field
holding the whole file:

| File | `OutlinedTextField(String)` | `BasicTextField(TextFieldState)` |
|---|---|---|
| 20 KB | 19 ms | |
| 51 KB | 42 ms | |
| 105 KB | 72 ms | |
| 256 KB (today's limit) | 149 ms | |
| 430 KB | 274 ms | 265 ms |

The cost is laying out the whole text on every keystroke, not copying a `String`, so
`TextFieldState` alone does not fix it. Phones will be several times slower than this.

**Plan:**

1. **Stopgap:** cap in-app editing at 64 KB. That is about 40 ms on desktop. Larger files show why
   and offer Check out locally instead.
2. **Real fix: a windowed editor.**
   - The file is shown as a `LazyColumn` of lines; one block (the lines around the cursor, or a
     tapped range) is a `TextFieldState`.
   - Committing a block splices it back into the file's line list.
   - Cost then tracks the block, not the file, and the 256 KB limit can return.
   - Line endings and the final newline are kept as read. The current editor already keeps the
     final newline; CRLF is unchecked.
3. **Test:** the same render timing as a jvmTest, asserting the edit cost is flat across file
   sizes, plus a round-trip test that an untouched file comes back byte for byte (CRLF, no
   trailing newline, tabs).

## 3. Diff row colours stop where the line's text ends (new)

**Found** while rendering the inline-comment test offscreen: in the unified view, an added or
removed line's background ends at its text instead of spanning the card. The desktop screenshots
show the same thing from before the audit. Each row sizes to its own content inside the
horizontally scrolled block.

**Plan:**
- Each scrolled run is at least the card's width (`BoxWithConstraints` outside the scroll,
  `widthIn(min = maxWidth)` plus `IntrinsicSize.Max` on the run), and each row fills it.
- **Test:** extend `GitDiffViewAnnotationTest` to assert that an added row's background width
  equals the card width when the line is short, and the widest line's width when it is long.

## 4. A private repository's event can leak when its announcement is not loaded

**Tested in the code:** `BroadcastRelayPlanner.privateGitRelays` returns null when none of the
repositories an event names is in the cache, so the event takes the normal outbox route.

This happens when the user replies, reacts or comments on a private repository's item that
arrived before its announcement, for example from a notification or a link.

**Plan:**
1. In the planner, an event that names a `30617:` coordinate whose announcement the cache lacks is
   treated as possibly private. It goes only to that tag's relay hint and the relays the item was
   seen on, never the outbox.
2. Composers of git events (comment, status, label, review) also load the announcement first,
   with `fetchFirst` on those relays, and send normally once it is known to be public.
3. **Test:** `BroadcastRelayPlannerTest` gets an issue under a repository that is not cached.
   Its comment goes to the hint relays only; once a public announcement arrives, the next one goes
   to the outbox.

## 5. Inline comment threads: layout verified, real thread not yet

**Tested:** `GitDiffViewAnnotationTest` (committed) renders a unified diff offscreen with a long
code line and a long comment under it.
- The comment is laid out at the card's 400 px and wraps.
- The code line scrolls on its own.
- Only the commented line draws anything.

That covers the layout fix from the audit. Real threads are `NoteCompose` cards with
Resolve/Reopen, and no public PR has inline comments yet. They are rendered in step 1.4.

## 6. Blame and search wait times in the app

These follow the quartz plan. On the app's side:
- Once quartz is released with it, bump the version. A release needs the maintainer's go-ahead.
- Show what blame is doing ("reading history", "reading N versions") instead of a bare bar, and
  let leaving the file cancel it. `produceState` already cancels; the quartz fetch must honour it
  mid-pack.

## Order

1. **Items 3 and 4:** small, contained, and they close a privacy gap.
2. **The editor stopgap (2.1).**
3. **The dev GRASP server and `amy` verbs (1.1, 1.2):** the largest piece, and the one that makes
   every later change in this area testable without public relays.
4. **The end-to-end script and the desktop pass (1.3, 1.4)**, fixing what they find.
5. **The windowed editor (2.2)**, and the quartz fetch work as its release lands (6).
