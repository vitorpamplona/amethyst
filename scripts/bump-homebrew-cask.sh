#!/usr/bin/env bash
#
# Push the amethyst-nostr cask upstream to Homebrew/homebrew-cask.
#
# This is the ONE release step that is deliberately not automated. `brew
# bump-cask-pr` forks homebrew-cask into the token owner's account, which needs
# a CLASSIC PAT with the `repo` scope — and that scope grants write to every
# repository the account can reach. As a CI secret it would be usable by anyone
# with push access to this repo; in your shell it is not. See BUILDING.md
# § Homebrew cask.
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
#
# Requires: macOS, brew, and HOMEBREW_GITHUB_API_TOKEN exported (classic PAT,
# `repo` scope). Create one at:
#   https://github.com/settings/tokens/new?scopes=repo&description=Homebrew%20cask%20bump

set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CASK="$REPO_ROOT/desktopApp/packaging/homebrew/amethyst-nostr.rb"
EXPECTED_TAG="${1:-}"
DRY_RUN="${DRY_RUN:-}"

die() { echo "error: $*" >&2; exit 1; }

[[ "$(uname -s)" == "Darwin" ]] || die "casks are macOS-only; run this on a Mac"
command -v brew >/dev/null || die "brew not found"
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

if [[ -n "$DRY_RUN" ]]; then
  echo "DRY_RUN set — would now run:"
  echo "  brew bump-cask-pr amethyst-nostr --version $VERSION --sha256 $SHA --url $URL"
  exit 0
fi

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
