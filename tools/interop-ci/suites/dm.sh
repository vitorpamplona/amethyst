# shellcheck shell=bash
# shellcheck disable=SC2034  # REF and VERSION are read by run-suite.sh, which sources this
#
# NIP-17 DMs between two amy accounts (cli/tests/dm/dm-interop-headless.sh).
# No counterpart to fetch. Sourced by tools/interop-ci/run-suite.sh.
#
# The relay runs on 192.0.2.2, which the namespace puts on lo (suites.json
# `addresses`): amy drops loopback relays from another account's kind:10050, so
# on 127.x the inbox tests (dm-01/02/05/06) would only be skipped.

suite_resolve() { REF="-"; VERSION="this commit"; }
suite_prepare() { :; }
suite_run() { "$REPO_ROOT/cli/tests/dm/dm-interop-headless.sh" --no-build --host 192.0.2.2; }
