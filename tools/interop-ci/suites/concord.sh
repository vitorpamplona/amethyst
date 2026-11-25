# shellcheck shell=bash
# shellcheck disable=SC2034  # REF and VERSION are read by run-suite.sh, which sources this
#
# Concord: amy against Armada (cli/tests/concord/armada-interop.sh).
# Sourced by tools/interop-ci/run-suite.sh.
#
# Armada is AGPL-3.0. It is fetched here as a separate program and run on its
# own: nothing of it is copied into this repository or any artifact. (The
# harness copies OUR two driver files into the scratch Armada checkout, not the
# other way round.)
#
# Its canonical repository is the ngit (NIP-34) one below; the GitLab mirror
# stopped updating in September 2026.
#
# pinned  ARMADA_PIN. Bump it after a run against `latest` stays green, and
#         write down what changed in the harness if it did not.
# latest  Armada's default branch.
ARMADA_URL="https://relay.ngit.dev/npub10qdp2fc9ta6vraczxrcs8prqnv69fru2k6s2dj48gqjcylulmtjsg9arpj/armada.git"
ARMADA_PIN="848e12f04f9ca7c0d81750d783f01f862433cfc5"   # 2026-11-20, "Let background notifiers end timed mutes…"
ARMADA_DIR_CI="$SUITE_CACHE/armada"

suite_resolve() {
  if [[ "$1" == pinned ]]; then REF="$ARMADA_PIN"; else REF=$(git_head "$ARMADA_URL"); fi
  [[ -n "$REF" ]] || return 1
  VERSION="${REF:0:8}"
  [[ "$1" == latest ]] && VERSION="main@${REF:0:8}"
  return 0
}

suite_prepare() {
  git_checkout "$ARMADA_URL" "$ARMADA_DIR_CI" "$REF" || return 1
  # node_modules is cached per commit; reinstall only when the lockfile moved.
  local lock_sum
  lock_sum=$(sha256sum "$ARMADA_DIR_CI/package-lock.json" | cut -d' ' -f1)
  if [[ ! -d "$ARMADA_DIR_CI/node_modules/vitest" || "$(cat "$ARMADA_DIR_CI/node_modules/.lock-sum" 2>/dev/null)" != "$lock_sum" ]]; then
    ( cd "$ARMADA_DIR_CI" && npm ci --no-audit --no-fund ) || return 1
    printf '%s\n' "$lock_sum" >"$ARMADA_DIR_CI/node_modules/.lock-sum"
  fi
}

suite_run() {
  # 192.0.2.2 is on the namespace's lo (suites.json `addresses`): Direct Invites
  # resolve the recipient's relay lists, and amy never dials a loopback relay
  # named in someone else's list.
  ARMADA_DIR="$ARMADA_DIR_CI" STATE_DIR="$STATE_OUT/state" RELAY_IP="${RELAY_IP:-192.0.2.2}" \
    "$REPO_ROOT/cli/tests/concord/armada-interop.sh"
}
