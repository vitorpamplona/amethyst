# shellcheck shell=bash
# shellcheck disable=SC2034  # REF and VERSION are read by run-suite.sh, which sources this
#
# Marmot / MLS: amy against MDK's wn/wnd (cli/tests/marmot/marmot-interop-headless.sh).
# Sourced by tools/interop-ci/run-suite.sh.
#
# pinned  the MDK commit cli/tests/marmot/setup.sh pins (MDK_PIN), so the harness
#         and CI never disagree. Bump it there, by the rule written next to it.
# latest  MDK's default branch.
#
# MDK is MIT. It publishes no wn/wnd release binaries (only wn-agent bundles), so
# they are built from source at the commit and cached per commit.
MDK_URL="https://github.com/marmot-protocol/mdk.git"
MDK_DIR="$SUITE_CACHE/mdk"

suite_resolve() {
  if [[ "$1" == pinned ]]; then
    # shellcheck disable=SC2016  # the pattern matches a literal ${MDK_PIN:-…}
    REF=$(sed -n 's/^ *MDK_PIN="\${MDK_PIN:-\([0-9a-f]\{40\}\)}".*/\1/p' "$REPO_ROOT/cli/tests/marmot/setup.sh")
  else
    REF=$(git_head "$MDK_URL")
  fi
  [[ -n "$REF" ]] || return 1
  local tag; tag=$(git_tag_of "$MDK_URL" "$REF" | grep -E '^v[0-9]' | tail -n1)
  VERSION="${tag:-${REF:0:9}}"
  [[ "$1" == latest && -z "$tag" ]] && VERSION="master@${REF:0:9}"
  return 0
}

suite_prepare() {
  git_checkout "$MDK_URL" "$MDK_DIR" "$REF" || return 1
  local bin="$SUITE_CACHE/bin/$REF" rel="$MDK_DIR/target/release"
  if [[ ! -x "$bin/wn" || ! -x "$bin/wnd" ]]; then
    echo "building wn + wnd at $REF"
    ( cd "$MDK_DIR" && cargo build --release -p wn-cli --bin wn --bin wnd ) || return 1
    rm -rf "${SUITE_CACHE:?}/bin"   # keep only this commit's pair in the cache
    mkdir -p "$bin" && cp "$rel/wn" "$rel/wnd" "$bin/"
  fi
  mkdir -p "$rel"
  cp "$bin/wn" "$bin/wnd" "$rel/"
  # The harness rebuilds unless this marker names the checked-out commit.
  printf '%s\n' "$REF" >"$rel/.harness-built-sha"
}

suite_run() {
  MDK_PIN="$REF" WN_REPO="$MDK_DIR" \
    "$REPO_ROOT/cli/tests/marmot/marmot-interop-headless.sh" --no-build
}
