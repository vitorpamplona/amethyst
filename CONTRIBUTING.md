# Contributing to Amethyst

Thanks for your interest in improving Amethyst. This document captures the
expectations, conventions, and review rules for code, documentation, and
translation contributions across all modules in this repository (`amethyst/`,
`desktopApp/`, `quartz/`, `commons/`, `cli/`, `quic/`, `nestsClient/`).

By contributing, you agree to license your work under the MIT license. Any
work contributed where you are not the original author must contain its
license header with the original author(s) and source.

- [Ways to contribute](#ways-to-contribute)
- [Human and AI contributions](#human-and-ai-contributions)
- [New contributor: proof of testing](#new-contributor-proof-of-testing)
- [Reporting bugs and requesting features](#reporting-bugs-and-requesting-features)
- [Security issues](#security-issues)
- [Development setup](#development-setup)
- [Project layout](#project-layout)
- [Where code belongs (sharing philosophy)](#where-code-belongs-sharing-philosophy)
- [Workflow](#workflow)
- [Coding standards](#coding-standards)
- [Tests](#tests)
- [Interoperability tests](#interoperability-tests)
- [Commits](#commits)
- [Pull requests](#pull-requests)
- [Translations](#translations)
- [Releases](#releases)

---

## Ways to contribute

- Fix bugs or implement features (see issues and the bounty notes in the
  issue templates).
- Improve documentation in `README.md`, `BUILDING.md`, `SECURITY.md`, the
  per-module `plans/` folders, and the `.claude/` skill docs.
- Add or improve translations on
  [Crowdin](https://crowdin.com/project/amethyst-social).
- Report bugs, suggest features, or open issues at
  [github.com/vitorpamplona/amethyst/issues](https://github.com/vitorpamplona/amethyst/issues)
  or the mirror at
  [gitworkshop.dev/repo/amethyst](https://gitworkshop.dev/repo/amethyst).
- Send patches over Nostr using
  [GitStr](https://github.com/fiatjaf/gitstr) — see the address at the bottom
  of the README.

## Human and AI contributions

We accept pull requests authored by humans and pull requests authored with
help from AI coding assistants (Claude Code, Copilot, Cursor, Codex, etc.).
The rules are the same in both cases:

- **You are the author of record.** You are responsible for understanding,
  testing, and defending every line of code in the PR — including code an
  assistant wrote for you. "The AI did it" is not a valid response to review
  feedback.
- **No hallucinated APIs or imports.** Don't submit code that calls functions,
  classes, or libraries that don't exist in this repo or in our declared
  dependencies. Build and run it before you push.
- **Disclose AI involvement if it's substantial.** If an assistant generated
  most of the diff, a one-line note in the PR description is appreciated
  (e.g. "Drafted with Claude Code, manually reviewed and tested"). This is a
  courtesy, not a gate.
- **No machine-translated locale files.** Translations go through Crowdin so
  native speakers can review them.

## New contributor: proof of testing

If this is your **first PR** to this repository, the PR description must
include proof that you actually ran the change. This applies whether the
patch was written by you or by an AI assistant.

What "proof" means:

- **Code/logic changes:** paste the output of the relevant test run, the CLI
  command you ran, or the log lines that show the new behavior firing. Tests
  passing in CI is necessary but not sufficient — show the new path
  executing.
- **UI changes (any change to `amethyst/`, `desktopApp/`, or `@Composable`
  code in `commons/`):** attach screenshots or a short screen recording of
  the new or changed screen on a real device or emulator. Include:
  - Light **and** dark themes when the change is visible in both.
  - The empty / loading / error states if your change introduces them.
  - For Android: device model + Android version (e.g. "Pixel 7, Android 14").
  - For Desktop: OS + window size (e.g. "macOS 14, 1440×900").
- **Build / Gradle / packaging changes:** paste the `./gradlew` command and
  the tail of its output, or the produced artifact name from
  `desktopApp/build/compose/binaries/`.
- **Translation-only PRs:** screenshots are not required, but mention which
  locale and which strings you touched.

You don't need to keep doing this on every subsequent PR — once we've seen
that you can run the project end-to-end, a short test plan in the PR
description is enough. Maintainers may still ask for screenshots on visual
changes regardless of contributor seniority.

If you cannot run a particular target locally (e.g. you don't have macOS but
your change affects the DMG build), say so explicitly in the PR — don't
claim it works when you haven't checked. Honest "I couldn't test this on
Windows" is far better than a silent guess.

## Reporting bugs and requesting features

We use GitHub issue templates at
[`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE/). Please use them rather
than opening a blank issue — they exist so triage can route the report
without a follow-up round trip.

### Bug report (`[BUG]` title prefix)

Required fields:

- **Describe the bug** — one-paragraph summary of what's wrong.
- **To Reproduce** — numbered steps (1, 2, 3, …) that walk a maintainer from
  a fresh app launch to the failure. "It crashes sometimes" is not a repro;
  "tap +, paste this nevent, tap Send" is.
- **Expected behavior** — what should have happened.
- **Video and Screenshots** — attach them whenever the bug is visual,
  involves navigation, or is timing-sensitive.
- **Device info** — Phone Brand/Model, Android version, App version, App
  flavour (Google Play / F-Droid / Desktop), and Amber version if you sign
  with NIP-55.
- **Bounty (in Bitcoin sats)** — see below.

### Feature request (`[FEATURE]` title prefix)

Required fields:

- **Describe the solution you'd like** — what the user-facing outcome is.
  Skip implementation suggestions unless they're load-bearing.
- **Bounty (in Bitcoin sats)** — see below.

### Bounties

Both templates include a bounty field, and the practice is unusually
literal in this project: **maintainer time is allocated by bounty size**.
The templates say so plainly — quoting them:

> The size of the bounty is proportional to how much this matters to you. If
> no bounty is offered, not even a small one, this bug will not be worked
> on because it doesn't matter to you.

In practice:

- Bug fixes and features are prioritized roughly by bounty ÷ effort.
- Even a small bounty (a few hundred sats) outranks a zero-bounty issue.
- Bounties are not refunded if you change your mind, so size them
  honestly against how much you want the outcome.
- If you can fix the issue yourself, you don't need a bounty — open the
  PR. Bounties exist to move issues that **nobody is currently working on**.
- Security issues do not need a bounty and should not be filed as public
  issues — see [SECURITY.md](SECURITY.md).

### Sending issues over Nostr

The repository is also mirrored at
[`gitworkshop.dev/repo/amethyst`](https://gitworkshop.dev/repo/amethyst) for
Nostr-native issue tracking, and patches can be sent via
[GitStr](https://github.com/fiatjaf/gitstr) to the address at the bottom of
the README.

## Security issues

**Do not file security vulnerabilities as public GitHub issues.** Use
[GitHub private vulnerability reporting](https://github.com/vitorpamplona/amethyst/security/advisories/new)
instead. See [SECURITY.md](SECURITY.md) for scope, expected response times,
and the disclosure policy.

## Development setup

Prerequisites:

1. **JDK 21+** (Zulu or Temurin recommended)
2. **Android Studio** (for Android development)
3. **Android 8.0+ phone or emulator** (for installing the Android app)
4. **Xcode + iOS simulator** if you're touching `quartz/` iOS code
5. **libsodium** for full local builds (`brew install libsodium` on macOS)

Desktop packaging prerequisites (only if you're building installers) are
listed in [BUILDING.md § Prerequisites](BUILDING.md#prerequisites).

Clone and import:

```bash
git clone https://github.com/vitorpamplona/amethyst.git
cd amethyst
```

Common Gradle entry points:

```bash
# Run desktop app
./gradlew :desktopApp:run

# Install Android debug build
./gradlew :amethyst:installDebug
# or specifically:
./gradlew installFdroidDebug
./gradlew installPlayDebug

# Build Quartz for all targets
./gradlew :quartz:build

# Full build (compiles all modules + runs tests)
./gradlew build
```

## Project layout

```
quartz/         Nostr KMP library — protocol, crypto, models. No UI.
commons/        Shared Compose Multiplatform UI, icons, ViewModels, flows.
quic/           Pure-Kotlin QUIC v1 + HTTP/3 + WebTransport transport library.
nestsClient/    Audio-rooms client (NIP-53) built on :quic and :quartz.
amethyst/       Android app — Activity, layouts, navigation.
desktopApp/     Desktop JVM app — Window, sidebar, keyboard shortcuts.
cli/            `amy` — non-interactive JVM CLI over quartz + commons.
ammolite/       Legacy support module.
```

Per-module design docs live in `<module>/plans/YYYY-MM-DD-<slug>.md`. The
global `docs/plans/` folder is frozen — don't add new files there.

## Where code belongs (sharing philosophy)

Before writing a new class or composable, check whether it already exists.
Most logic is already implemented somewhere in the tree — duplicating it is
the #1 cause of PR churn.

Place new code by purpose:

| What you're adding | Goes in |
|---|---|
| Nostr event types, NIPs, tags, signing, crypto, Bech32 | `quartz/commonMain/` |
| Shared Composables, icons, ViewModels, StateFlows | `commons/commonMain/viewmodels/` or `commons/commonMain/` |
| Android-only screen, navigation, system integration | `amethyst/` |
| Desktop-only window, sidebar, menu bar, shortcut | `desktopApp/` |
| `amy <verb>` subcommand (thin assembly only) | `cli/src/main/kotlin/.../cli/` |
| QUIC / HTTP/3 / WebTransport protocol code | `quic/` |
| MoQ session / audio-room logic | `nestsClient/` |

Hard rules:

- `quartz/` has **no UI**.
- `cli/` has **no Nostr protocol or business logic** — it's a thin assembly
  layer over `quartz` + `commons`. If your CLI command needs new behavior,
  extract that behavior into `commons/` first.
- ViewModels are platform-agnostic and belong in `commons/commonMain/`. Only
  screens (the Composable that wires layout + navigation) stay in the
  platform module.
- For platform-specific behavior in a shared file, use `expect`/`actual` —
  ask `/kotlin-multiplatform` if unsure.

## Workflow

1. **Survey first.** Search the codebase for existing implementations before
   writing new code. The `.claude/CLAUDE.md` file has a recommended set of
   `grep` queries; running them takes a minute and routinely turns up the
   exact class you were about to re-invent.
2. **Open a small PR.** One feature or one fix per PR. Refactors that
   support the change are fine; unrelated refactors are not.
3. **Branch off `main`.**
4. **Run tests + spotless locally** before pushing (see below).
5. **Open the PR** with a description that explains *why*, not just *what*,
   and includes the proof-of-testing notes described above.

## Coding standards

- Kotlin, formatted with Spotless. Always run:
  ```bash
  ./gradlew spotlessApply
  ```
  before considering a task complete. CI runs `./gradlew spotlessCheck` and
  will fail on unformatted code.
- Never use `--no-verify` to bypass pre-commit hooks. If a hook fails,
  fix the cause.
- Don't add features, refactor, or introduce abstractions beyond what the
  task requires. A bug fix doesn't need surrounding cleanup.
- Don't add error handling, fallbacks, or validation for scenarios that
  can't happen. Validate at system boundaries (user input, external APIs).
- Prefer editing existing files to creating new ones. Don't create
  documentation files (`*.md`) unless asked or unless the change genuinely
  warrants one.
- Default to writing no comments. Only add a comment when the *why* is
  non-obvious (a workaround, a subtle invariant, behavior that would
  surprise a reader). Don't restate what the code does.
- Don't reference the current task, fix, or callers in comments
  (e.g. "added for issue #123") — that belongs in the PR description.

## Tests

```bash
# Unit + KMP common tests across all modules
./gradlew test

# Android instrumented tests (needs device or emulator)
./gradlew connectedAndroidTest

# A single module
./gradlew :quartz:test
./gradlew :commons:test
```

Add tests when:

- You fix a bug — a regression test that fails before your fix and passes
  after.
- You add new protocol code in `quartz/` — Nostr event parsing, NIP
  implementations, and crypto paths should have unit coverage.
- You add anything to `commons/` that has non-trivial state transitions.

UI changes don't need automated UI tests, but they do need the screenshots
described in the proof-of-testing section.

## Interoperability tests

Amethyst ships several cross-stack interop harnesses that drive our code
against external reference implementations. They are **not run in CI** —
they're slow, require Rust / bun / Docker / Chromium, and most PRs don't
touch the code they cover. If your change *does* touch a covered area, you
are expected to run the relevant suite locally and paste the result into
the PR description. Reviewers may ask if you didn't.

| Suite | Path | What it covers | When you must run it |
|---|---|---|---|
| **Marmot / MLS (Whitenoise)** | `cli/tests/marmot/` | NIP-EE MLS groups: KeyPackage publish, group create/invite/remove, admin promote/demote, leave, replay, KP rotation — Amethyst (or `amy`) ↔ `whitenoise-rs` Rust reference | Changes to MLS / Marmot code in `quartz/` or `commons/`, or to the Marmot UI / group-chat flow in `amethyst/` |
| **NIP-17 DMs (amy ↔ amy)** | `cli/tests/dm/` | Text + file DM round-trip, strict kind:10050, `--allow-fallback` NIP-65 chain, `dm list --since` window slide | Changes to DM, gift-wrap (NIP-59), or NIP-17 code paths in `quartz/` or `commons/` |
| **Audio rooms (manual)** | `cli/tests/nests/` | 47-test manual harness: Amethyst Android ↔ nostrnests.com reference web client. Host/listener flows, hand-raise, role promotion, kicks, schedule, reconnect, JWT refresh, PIP | Changes to NIP-53 audio rooms (`amethyst/` UI) or `nestsClient/` that affect host/listener UX |
| **MoQ-lite hang-tier** | `nestsClient/tests/hang-interop/` | Rust `hang-listen` / `hang-publish` ↔ Amethyst Kotlin through a real `moq-relay` 0.10.x subprocess. Wire-byte capture, FFT-on-PCM, mute / hot-swap / packet-loss / late-join / 60s broadcast / multi-listener fan-out | Changes to `nestsClient/.../moq/lite/`, `nestsClient/.../audio/`, `MoqLite*Speaker.kt` / `*Listener.kt`, `ReconnectingNests*.kt`, or `quartz/.../nip53` |
| **MoQ-lite browser-tier** | `nestsClient/tests/browser-interop/` | Headless Chromium with `@moq/lite` + `@moq/hang` via Playwright ↔ Amethyst Kotlin (forward + reverse). WebCodecs encode/decode, ALPN negotiation, browser-side reconnect | Same as hang-tier, plus any change to `:quic` (WebTransport, packet header protection, key updates, stream demux) |
| **QUIC interop-runner** | `quic/interop/` | The standard `quic-interop-runner` matrix (ns-3 sim) against aioquic, picoquic, quic-go, quinn. Handshake, transfer, loss, corruption, IPv6, migration, key update, version negotiation | Any change in `:quic` that could affect wire bytes, congestion control, or the TLS state machine |

### Running them

Each suite has its own README with prerequisites and flags. Quick links:

- **Marmot, DM, audio-rooms (CLI):** [`cli/tests/README.md`](cli/tests/README.md).
  Headless variants require only `cargo` + a loopback `nostr-rs-relay`;
  the interactive Marmot variant additionally prompts the human to drive
  the Amethyst Android UI.
- **MoQ-lite hang + browser:** [`nestsClient/tests/README.md`](nestsClient/tests/README.md).
  Opt-in via `-DnestsHangInterop=true` and/or
  `-DnestsBrowserInterop=true` on `:nestsClient:jvmTest`. Cold first run is
  ~10–13 min per tier; cached runs ~3–7 min.
- **QUIC interop-runner:** [`quic/interop/`](quic/interop/) plus its plan
  at `quic/interop/plans/2026-05-06-interop-runner.md`. The standard sweep
  is `for peer in aioquic picoquic quic-go quinn; do
  quic/interop/run-matrix.sh -s $peer; done`. Always sequential —
  `run-matrix.sh` is not safe to invoke concurrently.

If a change is documentation-only, UI-only, build-script-only, or otherwise
cannot affect wire bytes / decoded audio / MLS state / DM envelopes, skip
the interop suites and say so in the PR description.

- Use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`,
  `fix:`, `refactor:`, `docs:`, `chore:`, `test:`, etc.
- One logical change per commit. Squashing during PR review is fine.
- Keep subject lines under ~72 characters. Use the body to explain *why*
  the change was made when it isn't obvious.
- Branch names follow `feat/<scope>-<short-name>` or
  `fix/<scope>-<short-name>` (e.g. `feat/desktop-sidebar-resize`,
  `fix/android-notification-leak`).

## Pull requests

A good PR description has:

1. **Summary** — 1–3 sentences on what changed and why.
2. **Modules touched** — `quartz`, `commons`, `amethyst`, `desktopApp`,
   `cli`, etc.
3. **Test plan** — exactly what you ran, on what platform, with what
   result. Include screenshots / recordings for UI changes (mandatory for
   first-time contributors; expected for all contributors on visual
   changes).
4. **Risk / rollback** — if the change touches relay subscriptions, the
   `NostrClient`, signing, or anything in `Account`/`LocalCache`, call out
   what could break and how to revert.

We aim to give first-pass feedback within a few days. PRs may sit longer if
they're large, touch security-sensitive code, or arrive during a release
window.

## Translations

- Submit translations via
  [Crowdin](https://crowdin.com/project/amethyst-social), not as direct PRs
  to `strings.xml`. Crowdin pushes are integrated through the
  `crowdin.yml` workflow.
- The `/find-missing-translations` skill can help reviewers spot
  untranslated keys for a target locale.

## Releases

Release runbooks (Android AAB upload, desktop packaging on macOS / Windows /
Linux, Homebrew cask, Winget manifest, Apple Developer signing, asset
naming) live in [BUILDING.md § Release runbook](BUILDING.md#release-runbook)
and [BUILDING.md § Bootstrap runbook](BUILDING.md#bootstrap-runbook-one-time).

If your PR needs to be cut into a particular release, mention it in the PR
description and the maintainers will coordinate.

---

Thanks again for contributing.
