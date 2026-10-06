#!/usr/bin/env bash
#
# Verify the amethyst-nostr release DMG and check that Homebrew has picked it up.
#
# amethyst-nostr is on homebrew-cask's AUTOBUMP list: BrewTestBot opens the
# version-bump PR itself, every ~3 hours, and `brew bump-cask-pr` refuses to
# open a competing one. So for an autobumped cask this script opens nothing. It
# re-verifies the live DMG (sha256 + stapled notarization — BrewTestBot computes
# its own sha256 and never checks notarization), finds BrewTestBot's PR for the
# version, and checks that the sha256 in it matches ours. No token is needed.
#
# Only if Homebrew ever takes the cask off autobump does this fall back to
# opening the PR with `brew bump-cask-pr`, which forks homebrew-cask into the
# token owner's account and so needs a CLASSIC PAT with the `repo` scope. That
# scope grants write to every repository the account can reach, so it stays in
# a maintainer's shell rather than a CI secret. See BUILDING.md § Homebrew cask.
#
# Everything error-prone (version, sha256, notarization check) is already done
# in CI by .github/workflows/bump-homebrew.yml, which opens a PR syncing
# desktopApp/packaging/homebrew/amethyst-nostr.rb. Merge that PR first; this
# script reads the merged values so the two can never disagree.
#
# Usage:
#   scripts/bump-homebrew-cask.sh              # uses the cask file as-is
#   scripts/bump-homebrew-cask.sh v1.13.2      # asserts the cask matches this tag
#   DRY_RUN=1 scripts/bump-homebrew-cask.sh    # print what would happen, do nothing
#                                              # (the autobump path never writes anything)
#
# Requires: macOS and curl; `gh` to look up BrewTestBot's PR. The fallback path
# (cask not autobumped) also needs brew and HOMEBREW_GITHUB_API_TOKEN (classic
# PAT, `repo` scope):
#   https://github.com/settings/tokens/new?scopes=repo&description=Homebrew%20cask%20bump

set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CASK="$REPO_ROOT/desktopApp/packaging/homebrew/amethyst-nostr.rb"
EXPECTED_TAG="${1:-}"
DRY_RUN="${DRY_RUN:-}"

die() { echo "error: $*" >&2; exit 1; }

[[ "$(uname -s)" == "Darwin" ]] || die "casks are macOS-only; run this on a Mac"
[[ -f "$CASK" ]] || die "cask not found at $CASK"

VERSION=$(grep -E '^  version "' "$CASK" | sed -E 's/.*"(.*)".*/\1/')
SHA=$(grep -E '^  sha256 "' "$CASK" | sed -E 's/.*"(.*)".*/\1/')
[[ -n "$VERSION" && -n "$SHA" ]] || die "could not parse version/sha256 out of $CASK"

if [[ -n "$EXPECTED_TAG" && "v$VERSION" != "$EXPECTED_TAG" ]]; then
  die "cask is at v$VERSION but you asked for $EXPECTED_TAG.
Merge the 'chore: sync amethyst-nostr cask to $EXPECTED_TAG' PR first, then pull."
fi

URL="https://github.com/vitorpamplona/amethyst/releases/download/v${VERSION}/amethyst-desktop-${VERSION}-macos-arm64.dmg"

echo "cask    : amethyst-nostr"
echo "version : $VERSION"
echo "sha256  : $SHA"
echo "url     : $URL"
echo

# Re-verify against the live asset. CI already checked this, but the whole point
# of a manual gate is that a human confirms what is about to be advertised to
# every macOS user.
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
echo "==> downloading and verifying the release DMG"
curl -fsSL -o "$TMP/amethyst.dmg" "$URL" || die "could not download $URL"

ACTUAL_SHA=$(shasum -a 256 "$TMP/amethyst.dmg" | awk '{print $1}')
[[ "$ACTUAL_SHA" == "$SHA" ]] || die "sha256 mismatch!
  cask says : $SHA
  actual    : $ACTUAL_SHA"
echo "    sha256 matches"

xcrun stapler validate "$TMP/amethyst.dmg" >/dev/null 2>&1 \
  || die "the DMG has NO stapled notarization ticket.
Users would hit a Gatekeeper block. Do not submit this to homebrew-cask.
Check the notarizeReleaseDmg step in .github/workflows/create-release.yml."
echo "    notarization ticket stapled"
echo

# Autobumped casks: BrewTestBot owns the PR. Find it and check its sha256.
AUTOBUMP=$(curl -fsSL "https://formulae.brew.sh/api/cask/amethyst-nostr.json" 2>/dev/null \
  | python3 -c 'import sys, json; print(str(json.load(sys.stdin).get("autobump")).lower())' 2>/dev/null || echo unknown)

if [[ "$AUTOBUMP" == "true" ]]; then
  echo "==> amethyst-nostr is autobumped; looking for BrewTestBot's PR"
  command -v gh >/dev/null || die "gh not found — needed to look up the homebrew-cask PR"
  PR=$(gh pr list --repo Homebrew/homebrew-cask --state all --search "amethyst-nostr $VERSION in:title" \
    --json number,state,title --jq ".[] | select(.title == \"amethyst-nostr $VERSION\") | \"\(.number) \(.state)\"" | head -1)
  if [[ -z "$PR" ]]; then
    echo "    no PR yet. BrewTestBot runs about every 3 hours; check again later:"
    echo "    https://github.com/Homebrew/homebrew-cask/pulls?q=amethyst-nostr+$VERSION"
    exit 0
  fi
  read -r PR_NUMBER PR_STATE <<<"$PR"
  echo "    #$PR_NUMBER ($PR_STATE) https://github.com/Homebrew/homebrew-cask/pull/$PR_NUMBER"
  PR_SHA=$(gh pr diff "$PR_NUMBER" --repo Homebrew/homebrew-cask | grep -E '^\+ *sha256 "' | sed -E 's/.*"(.*)".*/\1/' | head -1)
  if [[ -z "$PR_SHA" ]]; then
    echo "    could not read a sha256 from the PR diff; compare by hand against $SHA"
  elif [[ "$PR_SHA" == "$SHA" ]]; then
    echo "    sha256 in the PR matches"
  else
    die "BrewTestBot's PR advertises a different sha256!
  PR #$PR_NUMBER : $PR_SHA
  release DMG   : $SHA
Comment on the PR before it merges."
  fi
  exit 0
fi

echo "==> amethyst-nostr is not autobumped (autobump=$AUTOBUMP); opening the PR ourselves"

if [[ -n "$DRY_RUN" ]]; then
  echo "DRY_RUN set — would now run:"
  echo "  brew bump-cask-pr amethyst-nostr --version $VERSION --sha256 $SHA --url $URL"
  exit 0
fi

command -v brew >/dev/null || die "brew not found"
[[ -n "${HOMEBREW_GITHUB_API_TOKEN:-}" ]] || die "HOMEBREW_GITHUB_API_TOKEN is not set.
Create a CLASSIC PAT with the 'repo' scope:
  https://github.com/settings/tokens/new?scopes=repo&description=Homebrew%20cask%20bump
then:  export HOMEBREW_GITHUB_API_TOKEN=ghp_...
Prefer a dedicated bot account — 'repo' reaches every repo that account can see."

if ! brew info --cask amethyst-nostr >/dev/null 2>&1; then
  cat >&2 <<EOF
error: cask 'amethyst-nostr' does not exist in homebrew-cask yet.

  \`brew bump-cask-pr\` can only bump an EXISTING cask. The first submission is a
  manual, human-reviewed new-cask PR. See BUILDING.md § Homebrew cask
  (one-time initial PR) for that flow; come back to this script for every
  release after it merges.
EOF
  exit 1
fi

echo "==> opening the homebrew-cask PR"
brew bump-cask-pr amethyst-nostr --version "$VERSION" --sha256 "$SHA" --url "$URL" --no-browse
echo
echo "Done. Watch the PR at https://github.com/Homebrew/homebrew-cask/pulls"
