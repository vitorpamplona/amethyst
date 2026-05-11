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

Use the GitHub issue templates:

- **Bug report** — include device, OS version, app version, app flavour
  (Play / F-Droid / Desktop), and (if you sign with Amber) the Amber version.
- **Feature request** — describe the user-facing outcome you want.

Both templates include a bounty field. Bounties are entirely optional, but
issues with bounties are typically picked up faster — see the templates for
context.

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

## Commits

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
