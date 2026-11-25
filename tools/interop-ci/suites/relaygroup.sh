# shellcheck shell=bash
# shellcheck disable=SC2034  # REF and VERSION are read by run-suite.sh, which sources this
#
# NIP-29 client round-trip against geode (cli/tests/relaygroup/relaygroup-headless.sh).
# No counterpart to fetch. Sourced by tools/interop-ci/run-suite.sh.

suite_resolve() { REF="-"; VERSION="this commit"; }
suite_prepare() { :; }
suite_run() { "$REPO_ROOT/cli/tests/relaygroup/relaygroup-headless.sh" --no-build; }
