#!/usr/bin/env bash
#
# Push the VitorPamplona.Amethyst manifests upstream to microsoft/winget-pkgs.
#
# Like scripts/bump-homebrew-cask.sh, this is deliberately not automated: it
# needs push access to a fork of winget-pkgs, and the previous design kept that
# as a classic `public_repo` PAT in the WINGET_TOKEN Actions secret, handed to a
# third-party action. That scope grants write to every public repo the account
# can reach, and any Actions secret is usable by anyone with push access to this
# repo. See BUILDING.md § Winget.
#
# Unlike the Homebrew script this needs NO new token: it drives `gh`, which you
# are already authenticated with. It also does not need `wingetcreate` (which is
# Windows-only) — winget manifests are plain YAML, so this works from macOS or
# Linux.
#
# Everything error-prone (version, sha256, ProductCode) is already done in CI by
# .github/workflows/bump-winget.yml, which opens a PR syncing
# desktopApp/packaging/winget/. Merge that PR first; this script reads the merged
# values so the two can never disagree.
#
# Usage:
#   scripts/bump-winget.sh                # uses the manifests as-is
#   scripts/bump-winget.sh v1.13.2        # asserts the manifests match this tag
#   DRY_RUN=1 scripts/bump-winget.sh      # print what would happen, do nothing

set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SRC="$REPO_ROOT/desktopApp/packaging/winget"
PKG_ID="VitorPamplona.Amethyst"
UPSTREAM="microsoft/winget-pkgs"
EXPECTED_TAG="${1:-}"
DRY_RUN="${DRY_RUN:-}"

die() { echo "error: $*" >&2; exit 1; }

command -v gh >/dev/null || die "gh not found — https://cli.github.com"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated; run: gh auth login"
[[ -d "$SRC" ]] || die "manifests not found at $SRC"

VERSION=$(awk '/^PackageVersion: /{print $2; exit}' "$SRC/$PKG_ID.yaml")
[[ -n "$VERSION" ]] || die "could not parse PackageVersion out of $SRC/$PKG_ID.yaml"

if [[ -n "$EXPECTED_TAG" && "v$VERSION" != "$EXPECTED_TAG" ]]; then
  die "manifests are at v$VERSION but you asked for $EXPECTED_TAG.
Merge the 'chore: sync winget manifests to $EXPECTED_TAG' PR first, then pull."
fi

# InstallerSha256 and ProductCode are single-quoted in the YAML (see the
# comments there); strip the quotes when reading them back.
SHA=$(awk '/^  InstallerSha256: /{print $2; exit}' "$SRC/$PKG_ID.installer.yaml" | tr -d "'")
URL=$(awk '/^  InstallerUrl: /{print $2; exit}' "$SRC/$PKG_ID.installer.yaml")
PC=$(awk '/^  ProductCode: /{print $2; exit}' "$SRC/$PKG_ID.installer.yaml" | tr -d "'")

[[ "$SHA" =~ ^0+$ ]] && die "InstallerSha256 is still the placeholder.
Run the sync workflow first: gh workflow run bump-winget.yml -f tag=v$VERSION"

echo "package : $PKG_ID"
echo "version : $VERSION"
echo "sha256  : $SHA"
echo "product : $PC"
echo "url     : $URL"
echo

# Re-verify against the live asset. CI already did, but the whole point of a
# manual gate is that a human confirms what is about to be published.
echo "==> verifying the release MSI"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
curl -fsSL -o "$TMP/amethyst.msi" "$URL" || die "could not download $URL"

if command -v sha256sum >/dev/null; then
  ACTUAL=$(sha256sum "$TMP/amethyst.msi" | awk '{print $1}')
else
  ACTUAL=$(shasum -a 256 "$TMP/amethyst.msi" | awk '{print $1}')
fi
ACTUAL=$(echo "$ACTUAL" | tr '[:lower:]' '[:upper:]')
[[ "$ACTUAL" == "$SHA" ]] || die "sha256 mismatch!
  manifest says : $SHA
  actual        : $ACTUAL"
echo "    sha256 matches"
echo

DEST="manifests/v/VitorPamplona/Amethyst/$VERSION"
BRANCH="$PKG_ID-$VERSION"

if [[ -n "$DRY_RUN" ]]; then
  echo "DRY_RUN set — would now:"
  echo "  fork $UPSTREAM (if needed), branch $BRANCH"
  echo "  add  $DEST/{$PKG_ID.yaml,$PKG_ID.installer.yaml,$PKG_ID.locale.en-US.yaml}"
  echo "  open a PR against $UPSTREAM"
  exit 0
fi

# A new package needs a human-reviewed first submission; after that this script
# handles every version bump.
if ! gh api "repos/$UPSTREAM/contents/manifests/v/VitorPamplona/Amethyst" >/dev/null 2>&1; then
  echo "note: $PKG_ID is not in $UPSTREAM yet — this will be the NEW-PACKAGE submission."
  echo "      Expect a slower, human-reviewed first pass."
  echo
fi

WORK="$TMP/winget-pkgs"
echo "==> preparing the fork"
gh repo fork "$UPSTREAM" --clone=false --remote=false >/dev/null 2>&1 || true
FORK="$(gh api user --jq .login)/winget-pkgs"

# Shallow clone of the fork only — winget-pkgs full history is enormous.
gh repo clone "$FORK" "$WORK" -- --depth=1 >/dev/null 2>&1 \
  || die "could not clone $FORK"

cd "$WORK"
git remote add upstream "https://github.com/$UPSTREAM.git" 2>/dev/null || true
git fetch --depth=1 upstream master >/dev/null 2>&1 || git fetch --depth=1 upstream main >/dev/null 2>&1
DEFAULT_BRANCH=$(gh api "repos/$UPSTREAM" --jq .default_branch)
git checkout -B "$BRANCH" "upstream/$DEFAULT_BRANCH" >/dev/null 2>&1

mkdir -p "$DEST"
cp "$SRC/$PKG_ID.yaml" "$SRC/$PKG_ID.installer.yaml" "$SRC/$PKG_ID.locale.en-US.yaml" "$DEST/"

# The reference copies carry an Amethyst-repo header comment; upstream manifests
# should not. Strip leading comment lines, keep the yaml-language-server line.
for f in "$DEST"/*.yaml; do
  python3 - "$f" <<'PY'
import sys
p = sys.argv[1]
lines = open(p).read().splitlines(keepends=True)
out, started = [], False
for ln in lines:
    if not started and ln.startswith('#') and 'yaml-language-server' not in ln:
        continue
    started = True
    out.append(ln)
open(p, 'w').writelines(out)
PY
done

git add "$DEST"
git -c user.name="$(gh api user --jq '.name // .login')" \
    -c user.email="$(gh api user --jq .id)+$(gh api user --jq .login)@users.noreply.github.com" \
    commit -q -m "$PKG_ID version $VERSION"
git push -q -u origin "$BRANCH" --force

echo "==> opening the PR"
gh pr create --repo "$UPSTREAM" \
  --base "$DEFAULT_BRANCH" \
  --head "$(gh api user --jq .login):$BRANCH" \
  --title "$PKG_ID version $VERSION" \
  --body "Automated manifest sync for [Amethyst](https://github.com/vitorpamplona/amethyst) $VERSION.

- Installer: \`amethyst-desktop-$VERSION-windows-x64.msi\` from the official GitHub release
- SHA256 verified against the published asset
- ProductCode read from the MSI Property table

Manifests are generated from \`desktopApp/packaging/winget/\` in the upstream repo."

echo
echo "Done. Watch the PR at https://github.com/$UPSTREAM/pulls"
