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
- [Performance and resource hygiene](#performance-and-resource-hygiene)
- [Regression test plan](#regression-test-plan)
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

Relevant skills under `.claude/skills/`: `account-state`,
`relay-client`, `kotlin-coroutines`, `kotlin-multiplatform`,
`find-non-lambda-logs`.

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
- Both flavours (Play and F-Droid).
- Feed types: home, profile, hashtag, bookmarks, notifications,
  DMs, communities.
- Orientation changes.
- Cold start vs warm start.
- Background → foreground transitions.

If a touch point can't reasonably be verified (it would require a
relay matrix you don't have, or a device combination you can't
access), state so and explain why you accept the risk. A reviewer
can tell you to do it anyway, but silent omission is not an option.

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
