# shellcheck shell=bash
# shellcheck disable=SC2034  # REF and VERSION are read by run-suite.sh, which sources this
#
# Buzz: amy's Buzz + NIP-29 verbs against upstream's buzz-relay
# (cli/tests/buzz/buzz-relay-interop.sh). Sourced by tools/interop-ci/run-suite.sh.
#
# block/buzz is Apache-2.0. The relay needs Postgres, Redis and MinIO from Docker
# (cli/tests/buzz/buzz-relay-compose.yml), whose published ports a network
# namespace cannot reach, so this suite runs on the host (`isolation: loopback`):
# every service binds 127.0.0.1 and amy runs with AMY_LOCAL_ONLY=1, which refuses
# any host that is not this machine.
#
# pinned  BUZZ_PIN. latest  Buzz's default branch.
BUZZ_URL="https://github.com/block/buzz.git"
BUZZ_PIN="52cbc611313a51d620901c0ef16663fe44d8e218"   # 2026-11-20
BUZZ_DIR="$SUITE_CACHE/buzz"

suite_resolve() {
  if [[ "$1" == pinned ]]; then REF="$BUZZ_PIN"; else REF=$(git_head "$BUZZ_URL"); fi
  [[ -n "$REF" ]] || return 1
  VERSION="${REF:0:9}"
  [[ "$1" == latest ]] && VERSION="main@${REF:0:9}"
  return 0
}

suite_prepare() {
  git_checkout "$BUZZ_URL" "$BUZZ_DIR" "$REF" || return 1
  local bin="$SUITE_CACHE/bin/$REF"
  if [[ ! -x "$bin/buzz-relay" ]]; then
    # The profile upstream's own isolated test relay uses.
    ( cd "$BUZZ_DIR" && cargo build --profile ci -p buzz-relay ) || return 1
    rm -rf "${SUITE_CACHE:?}/bin"
    mkdir -p "$bin" && cp "$BUZZ_DIR/target/ci/buzz-relay" "$bin/"
  fi
  docker compose -f "$REPO_ROOT/cli/tests/buzz/buzz-relay-compose.yml" pull --quiet
}

suite_run() {
  "$REPO_ROOT/cli/tests/buzz/buzz-relay-interop.sh" \
    --buzz-src "$BUZZ_DIR" --relay-bin "$SUITE_CACHE/bin/$REF/buzz-relay"
}
