<!--
Thanks for contributing to Amethyst! Before opening this PR, please skim
CONTRIBUTING.md — especially the "Proof of testing" and "Interoperability
tests" sections if this is your first PR.

Delete any section below that doesn't apply.
-->

## Summary

<!-- 1–3 sentences. What changed and why. Not "what files changed" — the
diff already shows that. -->

## Test plan

<!-- Required. What did you actually run, on what platform, with what result?
"CI is green" is necessary but not sufficient — show the new path firing.

For UI changes, attach screenshots (light + dark) or a short recording.
For Android: device model + Android version. For Desktop: OS + window size.
For build/packaging changes: paste the `./gradlew` command + tail of output.

If this is your FIRST PR to this repo, this section is required regardless
of how small the change is — see CONTRIBUTING.md § Proof of testing. -->

- [ ] Ran `./gradlew spotlessApply` — repo is formatted
- [ ] Ran `./gradlew test` (or the relevant module's tests)
- [ ] Manually exercised the change (see notes below)

Notes / screenshots:

<!-- paste here -->

## Interop suites

<!-- The interop suites listed in CONTRIBUTING.md § Interoperability tests
are NOT run in CI. If your change touches the relevant code paths, run them
locally and tick the box. If your change can't possibly affect them
(docs-only, UI-only on unrelated screens, etc.), tick "N/A". -->

- [ ] N/A — change can't affect wire bytes / decoded audio / MLS state / DM envelopes
- [ ] Marmot / MLS — `cli/tests/marmot/marmot-interop-headless.sh` (NIP-EE / `whitenoise-rs`)
- [ ] NIP-17 DM — `cli/tests/dm/dm-interop-headless.sh`
- [ ] Audio rooms manual — `cli/tests/nests/nests-interop.sh` (Amethyst ↔ nostrnests.com)
- [ ] MoQ-lite hang-tier — `:nestsClient:jvmTest -DnestsHangInterop=true`
- [ ] MoQ-lite browser-tier — `:nestsClient:jvmTest -DnestsBrowserInterop=true`
- [ ] QUIC interop-runner — `quic/interop/run-matrix.sh -s {aioquic,picoquic,quic-go,quinn}`

## AI assistance

<!-- Optional disclosure. We accept AI-assisted PRs (Claude Code, Copilot,
Cursor, Codex, etc.) under the same rules as human PRs — see
CONTRIBUTING.md § Human and AI contributions. A one-line note here is
appreciated when an assistant did the bulk of the diff. -->

- [ ] Drafted with AI assistance, manually reviewed and tested
- [ ] Written by hand

## License

- [ ] By submitting this PR, I agree to license my contribution under the
      MIT license. Any code I did not author personally carries its
      original license header.
