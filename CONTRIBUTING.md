# Contributing to Amethyst

Thanks for your interest in improving Amethyst. This document captures the
expectations, conventions, and review rules for code, documentation, and
translation contributions across all modules in this repository (`amethyst/`,
`desktopApp/`, `quartz/`, `commons/`, `cli/`, `quic/`, `nestsClient/`).

By contributing, you agree to license your work under the MIT license. Any
work contributed where you are not the original author must contain its
license header with the original author(s) and source.

- [Ways to contribute](#ways-to-contribute)
- [Proof of testing (new / occasional contributors)](#proof-of-testing-new--occasional-contributors)
- [Reporting bugs and requesting features](#reporting-bugs-and-requesting-features)
- [Security issues](#security-issues)
- [Development setup](#development-setup)
- [Where code belongs](#where-code-belongs)
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
  or the Nostr mirror at
  [gitworkshop.dev/repo/amethyst](https://gitworkshop.dev/repo/amethyst).
- Send patches over Nostr using
  [GitStr](https://github.com/fiatjaf/gitstr) — see the address at the bottom
  of the README.

## Proof of testing (new / occasional contributors)

We accept pull requests authored by humans and pull requests authored with
help from AI coding assistants (Claude Code, Copilot, Cursor, Codex, etc.)
under the same rules:

- **You are the author of record.** You are responsible for understanding,
  testing, and defending every line — including code an assistant wrote for
  you. "The AI did it" is not a valid response to review feedback.
- **No hallucinated APIs or imports.** Don't submit code that calls
  functions, classes, or libraries that don't exist in this repo or its
  declared dependencies. Build and run it before you push.
- **No machine-translated locale files.** Translations go through Crowdin so
  native speakers can review them.
- **Disclose substantial AI involvement** with a one-line note in the PR
  description. Courtesy, not a gate.

If you are **not a regular contributor** to this repository — first PR, or
sporadic enough that maintainers wouldn't recognize your handle — the PR
description must include proof that you actually ran the change:

- **Code/logic changes:** paste the test output, CLI command, or log lines
  that show the new path executing. CI green is necessary but not
  sufficient.
- **UI changes** (`amethyst/`, `desktopApp/`, or `@Composable` code in
  `commons/`): attach screenshots or a short recording from a real device or
  emulator. See the PR template for the exact checklist (light + dark,
  device / OS info, empty / loading / error states).
- **Build / Gradle / packaging changes:** paste the `./gradlew` command and
  the tail of its output.
- **Translation-only PRs:** mention which locale and which strings you
  touched; screenshots not required.

Once you have an established track record, a short test plan is enough on
subsequent PRs. Maintainers may still ask for screenshots on visual changes.

If you can't run a particular target locally (e.g. no macOS, but your change
affects the DMG build), say so explicitly. Honest "I couldn't test this on
Windows" beats a silent guess.

If you used an AI coding assistant for a substantial portion of the diff,
also read [`CONTRIBUTING-WITH-AI.md`](CONTRIBUTING-WITH-AI.md) — it adds
gates specific to AI-authored PRs (research before code, both-flavour
build, performance footguns, automated tests, regression test plan,
second-agent code review).

## Reporting bugs and requesting features

Use the GitHub issue templates at
[`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE/) rather than opening a
blank issue — they exist so triage can route the report without a follow-up
round trip.

**Bug report (`[BUG]` title prefix):** describe the bug, give numbered repro
steps from a fresh app launch, state expected behavior, attach video /
screenshots for anything visual or timing-sensitive, list device info (phone
brand/model, Android version, app version, flavour, Amber version if
applicable), and include a bounty.

**Feature request (`[FEATURE]` title prefix):** describe the user-facing
outcome and include a bounty. Skip implementation suggestions unless they're
load-bearing.

### Bounties

Maintainer time is allocated by bounty size. From the templates: *"If no
bounty is offered, not even a small one, this bug will not be worked on
because it doesn't matter to you."* Issues are prioritized by bounty ÷
effort, even a few hundred sats outrank a zero-bounty issue, and bounties
aren't refunded. If you can fix it yourself, just open the PR — bounties
exist to move issues nobody is currently working on. Security issues do not
need a bounty and should not be filed publicly; see
[SECURITY.md](SECURITY.md).

## Security issues

**Do not file security vulnerabilities as public GitHub issues.** Use
[GitHub private vulnerability reporting](https://github.com/vitorpamplona/amethyst/security/advisories/new)
instead. See [SECURITY.md](SECURITY.md) for scope, expected response times,
and disclosure policy.

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
./gradlew :desktopApp:run         # Run desktop app
./gradlew :amethyst:installDebug  # Install Android debug build
./gradlew :quartz:build           # Build Quartz for all targets
./gradlew build                   # Full build + tests
```

## Where code belongs

Modules:

- `quartz/` — Nostr KMP library (protocol, crypto, models). **No UI.**
- `commons/` — Shared Compose Multiplatform UI, icons, ViewModels, flows.
- `quic/` — Pure-Kotlin QUIC v1 + HTTP/3 + WebTransport.
- `nestsClient/` — Audio-rooms client (NIP-53) built on `:quic` and
  `:quartz`.
- `amethyst/` — Android app: Activity, layouts, navigation.
- `desktopApp/` — Desktop JVM app: Window, sidebar, keyboard shortcuts.
- `cli/` — `amy`, a non-interactive JVM CLI over `quartz` + `commons`.
- `ammolite/` — Legacy support module.

Per-module design docs live in `<module>/plans/YYYY-MM-DD-<slug>.md`. The
global `docs/plans/` folder is frozen.

Before writing a new class or composable, check whether it already exists.
Most logic is already implemented somewhere — duplicating it is the #1 cause
of PR churn. Place new code by purpose:

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
  extract it into `commons/` first.
- ViewModels belong in `commons/commonMain/`. Only screens (the Composable
  that wires layout + navigation) stay in the platform module.
- For platform-specific behavior in a shared file, use `expect`/`actual`.

## Workflow

1. **Survey first.** Search the codebase for existing implementations before
   writing new code. The `.claude/CLAUDE.md` file has a recommended set of
   `grep` queries; running them routinely turns up the exact class you were
   about to re-invent.
2. **Open a small PR.** One feature or one fix per PR. Refactors that
   support the change are fine; unrelated refactors are not.
3. **Branch off `main`.**
4. **Run tests + spotless locally** before pushing.
5. **Open the PR** with a description that explains *why*, not just *what*.

## Coding standards

- Kotlin, formatted with Spotless. Always run `./gradlew spotlessApply`
  before considering a task complete. CI runs `spotlessCheck` and will fail
  on unformatted code.
- Never use `--no-verify` to bypass pre-commit hooks. If a hook fails, fix
  the cause.
- Default to writing no comments. Add one only when the *why* is non-obvious
  (a workaround, a subtle invariant, behavior that would surprise a
  reader). Don't restate what the code does, and don't reference the
  current task or callers — that belongs in the PR description.

## Tests

```bash
./gradlew test                 # Unit + KMP common tests, all modules
./gradlew connectedAndroidTest # Android instrumented tests (needs device)
./gradlew :quartz:test         # Single module
```

Add tests when:

- You fix a bug — a regression test that fails before your fix and passes
  after.
- You add new protocol code in `quartz/` — Nostr event parsing, NIP
  implementations, and crypto paths should have unit coverage.
- You add anything to `commons/` with non-trivial state transitions.

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

Each suite has its own README; the non-obvious bits worth flagging up
front:

- **MoQ-lite tiers** are opt-in via `-DnestsHangInterop=true` and
  `-DnestsBrowserInterop=true` on `:nestsClient:jvmTest`. Cold first run is
  ~10–13 min per tier; cached runs ~3–7 min.
- **`quic/interop/run-matrix.sh` is not concurrency-safe.** Run peers
  sequentially: `for peer in aioquic picoquic quic-go quinn; do
  quic/interop/run-matrix.sh -s $peer; done`. Plan at
  `quic/interop/plans/2026-05-06-interop-runner.md`.
- **CLI suites** ([`cli/tests/README.md`](cli/tests/README.md)): headless
  variants need only `cargo` + a loopback `nostr-rs-relay`; the interactive
  Marmot variant prompts a human to drive the Android UI.

If a change is documentation-only, UI-only, build-script-only, or otherwise
cannot affect wire bytes / decoded audio / MLS state / DM envelopes, skip
the interop suites and say so in the PR description.

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
2. **Test plan** — exactly what you ran, on what platform, with what
   result. Include screenshots / recordings for UI changes (mandatory for
   non-regular contributors; expected for everyone on visual changes).

We aim to give first-pass feedback within a few days. PRs may sit longer if
they're large, touch security-sensitive code, or arrive during a release
window.

## Translations

- Submit translations via
  [Crowdin](https://crowdin.com/project/amethyst-social), not as direct PRs
  to `strings.xml`. Crowdin pushes are integrated through the `crowdin.yml`
  workflow.
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
