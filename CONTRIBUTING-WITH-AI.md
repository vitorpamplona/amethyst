# Contributing to Amethyst with AI Assistance

This adds to [`CONTRIBUTING.md`](CONTRIBUTING.md), which covers every
contribution. It applies to any PR where an AI coding assistant (Claude
Code, Copilot, Cursor, Codex, etc.) wrote a large part of the diff.

## The rule: a human reviews and tests before any PR is opened

**No PR, GitHub or nostr proposal, may be opened until a human has:**

1. **Read and understood the whole diff.** You are the author of record.
   You must be able to explain and defend every line in review.
2. **Tested the app by hand on a real device or emulator.** Install a
   build that includes the change, run the new flow, and check that the
   things it touches still work. Passing tests and green CI do not count
   as manual testing.

**AI agents:** you must not open, publish or submit a PR yourself. When
the code is ready, stop. Give the human the branch, a summary of what
changed, and a test plan they can follow on a device. They open the PR
after they have done both steps above. Drafting the PR description for
them is fine. Do not write "tested on device" unless the human has told
you they did it.

PRs that skip these steps will be closed.

## Before writing code

- **Check the issue is still wanted.** Make sure it is still open, not
  already fixed by a merged PR, and not out of scope. Old bounty issues
  often fail this check. Search open and closed PRs for an earlier attempt.
- **Ask first for sensitive areas.** Open an issue and get a maintainer
  to agree before changing any of these:
  - signers and key storage (`NostrSigner*`, KeyStore);
  - the release pipeline (`.github/workflows/`, signing, Gradle plugins,
    packaging);
  - how Amethyst reads a NIP, or a new kind or tag.
- **Make sure it fits a decentralised client.** No central server, no
  state controlled by the maintainers, no required third-party account.

## What the PR must include

Put these in the **Test plan** section of the
[PR template](.github/PULL_REQUEST_TEMPLATE.md):

- **What the human tested on a device:** device model, Android version,
  the steps taken and what happened. Add screenshots or a recording for
  UI changes.
- **Regression checks:** which existing screens or flows the change could
  break, and how each one was checked. If one could not be checked, say
  so and explain why.
- **Both flavours** if the change could differ between them (UI,
  services, dependencies, manifest, ProGuard): `./gradlew installPlayDebug`
  and `./gradlew installFdroidDebug`. F-Droid has no Google-only libraries.
- **The minified build** (`./gradlew :amethyst:installPlayBenchmark`) if
  the change touches reflection: ViewModel factories, serialization,
  `expect`/`actual`.
- **Automated tests** for new logic in `quartz/` or `commons/`, and a
  regression test for every bug fix. Run any interop suite the change
  touches (see [`CONTRIBUTING.md` § Interoperability tests](CONTRIBUTING.md#interoperability-tests)).
  CI does not run them.
- **For protocol changes** (a new kind, tag or reading of a NIP): quote
  the NIP line that allows it, paste the published event as fetched back
  from a relay (`nak req -i <id> wss://<relay>`), and say which other
  clients and older Amethyst versions understand it.

If testing on the device finds bugs, fix them in separate commits whose
messages name the root cause. Don't squash them into the feature commit.
