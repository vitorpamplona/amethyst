# Contributing to Amethyst with AI Assistance

This document is a companion to [`CONTRIBUTING.md`](CONTRIBUTING.md).
Everything in `CONTRIBUTING.md` applies to every contribution. This doc
adds gates specific to pull requests whose diff was substantially
authored by an AI coding assistant (Claude Code, Copilot, Cursor,
Codex, etc.). Where this doc and `CONTRIBUTING.md` differ, the
stricter rule wins.

If you are not using an AI assistant, you can stop reading here.

- [Research before code](#research-before-code)
- [Build and install both flavours](#build-and-install-both-flavours)
- [Protocol-introducing changes](#protocol-introducing-changes)
- [Performance and resource hygiene](#performance-and-resource-hygiene)
- [Automated tests for new logic](#automated-tests-for-new-logic)
- [Regression test plan](#regression-test-plan)
- [Code review pass before opening the PR](#code-review-pass-before-opening-the-pr)
- [Don't touch without an issue first](#dont-touch-without-an-issue-first)
- [Everything else](#everything-else)

---

## Research before code

AI agents are good at writing plausible-looking code for issues that
no longer make sense. Before you (or your assistant) write a line of
code, confirm the issue still wants to be implemented.

- **Issue still valid.** The issue is open, not superseded by a merged
  PR, not blocked by a NIP change, and not declared out of scope. Old
  bountied issues fail these checks routinely.
- **Post a research summary on the issue first.** A one-paragraph
  comment stating your read of the problem, the approach you intend
  to take, and the modules you expect to touch. Give maintainers a
  chance to flag it as stale before you invest in a diff.
- **No duplicate PR.** Search open and recently-closed PRs for the
  same feature. If a prior attempt exists, link to it and explain
  what you do differently.
- **Fits Amethyst's nature.** The feature must work in a decentralised
  client: no central server, no maintainer-controlled state, no
  required third-party account. If the proposal assumes any of these,
  the feature doesn't belong in Amethyst, regardless of the bounty.
- **NIPs still current.** If the issue references a specific NIP,
  check it hasn't been deprecated or superseded.

## Build and install both flavours

Any change that could differ between flavours — UI, services,
dependencies, `AndroidManifest.xml`, ProGuard rules — must build and
install on both Play and F-Droid:

```bash
./gradlew installPlayDebug
./gradlew installFdroidDebug
```

Paste the `BUILD SUCCESSFUL` tail of both into the PR description.

**Why both.** F-Droid drops Google-proprietary dependencies (Play
Services, Firebase, Cast SDK, etc.). Code that compiles only on Play
is rejected. The canonical recent example is the Chromecast feature:
the Google Cast SDK is a Play-only dependency, so F-Droid required a
separate stub implementation under `amethyst/src/fdroid/`. Agents
routinely add Play-only imports without realising the F-Droid build
breaks.

If a change is conclusively flavour-irrelevant (a pure `quartz/`
protocol fix, a docs change, a translation), one flavour is enough —
say which and why in the PR description.

### Test the release-minified build, not just `*-debug`

Reflection-touched code paths — `ViewModelProvider.Factory`,
`expect`/`actual` boundaries, serialization, custom Compose runtime
machinery — silently break under R8 / ProGuard while compiling fine
in `*-debug` builds. The cheapest way to catch this on this codebase
is the `benchmark` build type:

```bash
./gradlew :amethyst:installPlayBenchmark
```

That installs `com.vitorpamplona.amethyst.benchmark` ("Amy Benchmark"
on the home screen) — R8-minified, release-flavoured, `profileable =
true`, side-by-side with your normal Amethyst install via the
`.benchmark` applicationId suffix. Sign in to a test npub and exercise
the new flow there before opening the PR. A feature that crashes or
silently no-ops in the benchmark build but works in `*-debug` is a
missing keep rule — file it before reviewers find it.

## Protocol-introducing changes

If your PR publishes a new event kind, a new tag form, a new marker,
or a new interpretation of an existing NIP clause, the diff must also
include:

- **Cross-client compatibility section in the PR description.** State
  explicitly: which earlier Amethyst versions honour the new payload
  (forward/backward compat), whether other major clients (Damus,
  Primal, Iris, Coracle, etc.) implement the same NIP clause today,
  and what user-visible behaviour differs for users still on older
  builds or other clients. Don't paper over this with "syncs across
  devices" — say which devices, which clients, and which versions.
- **Wire-format verification.** Before opening the PR, fetch the
  published event from a relay and confirm its tags, content, and
  encryption envelope match what you intended. `nak req -i <event-id>
  wss://<relay>` is the standard tool. Paste the relevant tag line
  (redact private content) into the PR description. This catches bugs
  that don't show locally — wrong markers, missing relay hints,
  malformed private-tag JSON.
- **NIP citation.** If you claim "NIP-X allows this", quote the exact
  spec line in the PR. A reviewer should not have to re-derive the
  authority for your tag form.

## Performance and resource hygiene

Code that looks fine in review can wreck the app at runtime. The
following are common AI-agent footguns in this codebase. None of them
trip CI — they only surface in careful manual review or on a real
device. PRs that introduce any of them will be sent back.

### UI thread and recomposition

- **No main-thread blocking.** Network, JSON parsing, regex, crypto,
  file I/O, and DB queries belong on `Dispatchers.IO` or
  `Dispatchers.Default`. Never `runBlocking { ... }` from a Composable,
  click handler, or `LaunchedEffect`.
- **Hoist work out of `@Composable` bodies and `LazyColumn` item
  content.** Parsing, list filtering, building maps, allocating data
  classes all belong in `remember`, `derivedStateOf`, or the
  ViewModel, not in the render path. New lambdas allocated per render
  also defeat `@Stable` and cause unnecessary recomposition of
  children.
- **Use `collectAsStateWithLifecycle()`** for Flow → Compose, not
  `collectAsState()`, so collection pauses when the screen is
  off-screen.

### Coroutines and scoping

- **No `GlobalScope.launch` and no ad-hoc `CoroutineScope(Job())`.**
  Use `viewModelScope`, a lifecycle scope, or a passed-in
  `CoroutineScope`, so cancellation propagates on logout, navigation,
  or process death.
- **Don't put suspend work in `init {}`** of ViewModels. It runs on
  whatever thread constructed the VM and can't be cancelled. Use a
  `MutableStateFlow` + `viewModelScope.launch` pattern.

### Memory and caching

- **Don't build a parallel cache of Notes, Users, or Events.**
  Amethyst stores them once in `LocalCache` (backed by `LargeCache`),
  keyed by id or pubkey, mutable in place. A new
  `mutableMapOf<HexKey, Note>()` in your feature doubles the working
  set and gets stale.
- **Don't roll your own image cache.** Coil is wired up with
  size-aware loaders. Decoding a full-resolution image yourself will
  OOM mid-scroll.
- **Bound your collections.** Unbounded `mutableMapOf` or
  `mutableListOf` that accrue per-event entries are memory leaks. If
  you mean "the last N", use a size-bounded structure.

### Relay traffic and mobile network

- **Use the existing subscription layer.**
  `ComposeSubscriptionManager`, `Subscribable`, and the filter
  assemblers under `commons/.../relayClient/` are lifecycle-aware,
  deduped, and EOSE-closed. Don't open ad-hoc WebSockets and don't
  issue raw `REQ` filters from a Composable.
- **Don't re-fetch what's already in `LocalCache`.** Check the cache
  first; only subscribe for what's missing.
- **Respect data-saver and connectivity context.** Auto-fetching
  full-resolution video on cellular is a regression even if the code
  technically works.

### KMP source-set discipline

- **Android-only imports don't belong in `commons/commonMain` or
  `quartz/commonMain`.** Use `expect`/`actual` for platform-specific
  bits, or move the Android-specific code to `androidMain`.

### Logging

- **Use the Quartz lambda Log.**
  `com.vitorpamplona.quartz.utils.Log.d { "msg $x" }` — the lambda
  body only runs when the log level is enabled. Plain
  `Log.d("msg $x")` allocates the formatted string on every call,
  including in feed and scroll hot paths.
- **Strip diagnostic `Log.d` calls before commit.** Logs added
  during on-device debugging — even lambda-form ones — must be
  removed from the production diff. They survive R8 stripping only
  in debug builds, so committed `Log.d` doesn't directly hurt
  release performance, but it bloats the diff, scatters noise across
  logcat for the next developer, and silently grows over time. If a
  log line is genuinely load-bearing for future
  incident-response, promote it to `Log.i`/`w` with explicit
  justification in the commit message.

Relevant skills under `.claude/skills/`: `account-state`,
`relay-client`, `kotlin-coroutines`, `kotlin-multiplatform`,
`find-non-lambda-logs`.

## Automated tests for new logic

For any change beyond pure UI tweaks or docs, add automated tests.
"Tested manually" alone is not enough; it doesn't survive the next
refactor, and reviewers can't re-verify it.

Minimum bar:

- **New logic in `quartz/`** (event types, NIPs, parsing, crypto,
  Bech32) — must have unit tests in the matching
  `commonTest` / `androidTest` / `jvmTest` source set. Quartz is the
  protocol surface; everything new there gets coverage.
- **New logic in `commons/`** (ViewModels, filters, formatters,
  non-trivial state transitions) — unit tests for the paths a future
  refactor could break.
- **Bug fixes** — a regression test that fails before your fix and
  passes after. No exception. If the bug is hard to reproduce in a
  unit test, write the test that reproduces it first.
- **UI-only changes** in `amethyst/` or `desktopApp/` — automated UI
  tests are not required (per `CONTRIBUTING.md` § Tests). The manual
  on-device test plan and screenshots stay required.

If your change touches a domain covered by an interop suite (MLS /
Marmot, NIP-17 DMs, audio rooms, MoQ-lite, QUIC), run the relevant
suite locally and paste the result. CI does not run them. See
[`CONTRIBUTING.md` § *Interoperability tests*](CONTRIBUTING.md#interoperability-tests)
for the suite list and commands.

Commands:

```bash
./gradlew test                  # unit + KMP common tests, all modules
./gradlew :quartz:test          # one module
./gradlew connectedAndroidTest  # Android instrumented (needs device)
```

Tests pass before you open the PR. "CI will catch it" is not a
substitute — interop and instrumented suites don't run in CI.

## Regression test plan

The PR template has a **Test plan** section. For AI-authored PRs that
aren't pure docs or translations, that section must contain *two*
parts under these exact subheadings:

- `### Feature test plan` — what you did to confirm the new thing works.
- `### Regression test plan` — what you did to confirm the old things
  still work, and that you actually thought about which ones could
  break.

Don't add a new top-level section to the PR description — put both
subheadings inside the existing **Test plan** section.

Worked example of the required structure (real shape from a recent
feature PR; substitute your own touch points and verifications):

````markdown
## Test plan

### Feature test plan

- Long-press a reply note → quick-action sheet → "Mute thread" →
  thread disappears from Home immediately.
- Force-stop the app, relaunch → muted thread still hidden (relay
  round-trip verified).
- Settings → Security & Filters → Muted threads → tap "Unmute" →
  thread reappears in Home.

### Regression test plan

Touch points and verification:

- Existing user-mute — could regress through the shared
  `Account.isAcceptable` chokepoint — verified: muted a different
  user, posts hidden as before.
- Hidden words — same chokepoint — verified: added a word, posts
  filtered; removed it, posts back.
- Multi-account switch — could leak mute list across accounts —
  verified: switched between two npubs, each saw only its own mutes.
- R8-minified build — could fail on new ViewModel factory — verified
  on `installPlayBenchmark`.
- Both flavours — built and installed `installPlayDebug`; F-Droid
  build is flavour-irrelevant for this change (no Google-proprietary
  touch points), built only.
````

For the regression test plan, list:

1. **Touch points** — screens, flows, ViewModels, shared state, or
   modules your change reads from or modifies. List the ones a
   careful reader would expect to be affected, not the entire app.
2. **Failure mode** — for each touch point, what would actually go
   wrong if your change is buggy. "Feed wouldn't load." "Metadata
   stale across account switch." "OOM on scroll."
3. **Verification** — what you did to confirm it still works. Same
   `action → observed result` format as the feature test plan.

Worked example, for "add a new field to `Account`":

- Account creation — could crash on first launch — verified: created
  a fresh npub, app opened home feed.
- Account switching — could leak state across users — verified:
  switched twice between two npubs, feeds refreshed.
- Settings export/import — could corrupt restore — verified:
  exported, wiped data, re-imported, no errors.

Common touch-point categories worth scanning every PR for:

- Account / login / logout / multi-account switch.
- **Multi-device sync when the feature publishes per-account state to
  relays.** Sign in to the same npub on a second device and verify
  the new state propagates and is honoured there. State explicitly
  which Amethyst versions both sides need to be running for the sync
  to work (current build only, or older builds too).
- Both flavours (Play and F-Droid).
- Feed types: home, profile, hashtag, bookmarks, notifications,
  DMs, communities.
- Orientation changes.
- Cold start vs warm start.
- Background → foreground transitions.
- Release-minified build (`installPlayBenchmark`) for any code path
  that touches reflection — ViewModel factories, expect/actual,
  custom serialization.

If a touch point can't reasonably be verified (it would require a
relay matrix you don't have, or a device combination you can't
access), state so and explain why you accept the risk. A reviewer
can tell you to do it anyway, but silent omission is not an option.

## Code review pass before opening the PR

Before pushing, run a code-review pass with a *different* agent or
model than the one that wrote the code. AI agents are bad at finding
their own bugs; switching agent breaks the same-context blind spots
that produced the initial diff.

Options:

- Use a dedicated review skill if your harness has one — `/simplify`,
  `/kotlin-review`, `/security-review`, `/code-review`.
- Spawn a fresh agent from a different model (Sonnet → Opus, Opus →
  GPT-5, Claude → Codex) and have it review the diff.
- Run any static-analysis pass available (`./gradlew lint`).

After the review, **re-run the tests and the manual on-device test
plan**. Review feedback routinely surfaces bugs the tests didn't
catch; the fix introduces its own risk; verify the fix didn't
regress.

If the review flags issues, either address them or document in the
PR description why you accept the risk. Don't silently discard
review output.

### When on-device QA finds bugs in your own diff

On-device QA frequently surfaces defects after the initial
implementation reads as "done". Don't fold those fixes silently
into the feature commit and force-push — that erases the signal that
the defect existed and what it was. Instead:

- Land each fix as its own commit, or a final squashed
  `"Manual testing fixes"` commit at the tip of the branch.
- The commit message body names the root cause, not just the
  symptom. Answer the question "why was this missing from the initial
  diff?" — undocumented event-shape variant, collision with a
  recently-merged upstream change, Compose recomposition assumption,
  R8 keep-rule gap, lifecycle race.
- A reviewer reading your branch sees: feature → code-review cleanup
  → on-device manual-QA fixes. That's a healthy development arc, not
  a liability.

## Don't touch without an issue first

Open an issue and get explicit maintainer alignment before opening a
PR for:

- **Signer and KeyStore surface** — `NostrSigner` and its
  implementations (`NostrSignerInternal`, `NostrSignerRemote`,
  `NostrSignerExternal`), anything touching key storage or the
  signing flow.
- **Release pipeline** — workflow files under `.github/workflows/`,
  signing config, Gradle plugins, packaging.
- **NIP direction calls** — anything that changes how Amethyst
  interprets a NIP, or invents a new tag or kind, needs upstream NIP
  discussion (and likely a NIP PR) first.

These areas have a high cost when an unaligned PR lands — security
risk, release breakage, protocol fragmentation. Open the issue first
regardless of whether you call your change a bug fix, refactor, or
feature; describe what you observed and what you propose. A maintainer
will tell you to skip the issue gate if the change is genuinely
trivial.

## Everything else

For commit format, dev setup, interop tests, PR structure, translation
flow, and coding standards — see [`CONTRIBUTING.md`](CONTRIBUTING.md)
and [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md).
