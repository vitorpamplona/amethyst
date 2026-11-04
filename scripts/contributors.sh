#!/usr/bin/env bash
#
# Build the changelog "## Contributors" section from git history.
#
# OFFLINE: reads the commit authors (and Co-authored-by trailers) between two
# refs and resolves each to an npub through docs/changelog/github.json. Anyone
# who can't be resolved is listed under UNMAPPED, with their commit count, so a
# contributor is never dropped silently.
#
#   scripts/contributors.sh                          # since the last release tag
#   scripts/contributors.sh --from v1.18.0 --to v1.19.0
#
# Flags:
#   --from REF      Start of the range (exclusive). Defaults to the most recent
#                   release tag (v*, no pre-release suffix) before --to, so it
#                   works both before tagging and once --to is the new tag.
#   --to REF        End of the range (inclusive). Defaults to HEAD.
#   --mapping PATH  Override the data file (default docs/changelog/github.json).
#
# docs/changelog/github.json holds three lists:
#   mappings  GitHub username -> npub. The key is what a GitHub noreply address
#             (`123+user@users.noreply.github.com`) or the author name resolves to.
#   aliases   Other identities -> a mappings key: an author email, an author name,
#             or a GitHub username, for people who commit from several machines or
#             accounts (`m <m@localhost>` is mstrofnone).
#   ignore    Names or emails never credited: AI co-authors and bots.
# Every match is case-insensitive. An identity resolves through, in order: its
# email, the GitHub username in a noreply email, then its name; each is tried
# against aliases first and mappings second.
#
# Requires: bash, git, jq.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAPPING="$REPO_ROOT/docs/changelog/github.json"
FROM=""
TO="HEAD"

die() { echo "error: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --from)    FROM="${2:?--from needs a value}"; shift 2 ;;
    --to)      TO="${2:?--to needs a value}"; shift 2 ;;
    --mapping) MAPPING="${2:?--mapping needs a value}"; shift 2 ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    *)         die "unknown argument: $1" ;;
  esac
done

command -v jq >/dev/null || die "jq not found"
[ -f "$MAPPING" ] || die "mapping file not found: $MAPPING"
git -C "$REPO_ROOT" rev-parse --verify --quiet "$TO^{commit}" >/dev/null || die "unknown ref: $TO"

# Look from the parent of --to, so a --to that is itself the new tag still finds
# the release before it.
if [ -z "$FROM" ]; then
  FROM="$(git -C "$REPO_ROOT" describe --tags --abbrev=0 --match 'v*' --exclude '*-*' "$TO^" 2>/dev/null)" \
    || die "no release tag before $TO; pass --from"
fi
git -C "$REPO_ROOT" rev-parse --verify --quiet "$FROM^{commit}" >/dev/null || die "unknown ref: $FROM"

echo "# Contributors from $FROM to $TO" >&2

# One "name<TAB>email" line per author and per Co-authored-by trailer.
identities() {
  git -C "$REPO_ROOT" log --no-merges --format='%an%x09%ae' "$FROM..$TO"
  git -C "$REPO_ROOT" log --no-merges \
    --format='%(trailers:key=Co-authored-by,valueonly,separator=%x0A)' "$FROM..$TO" \
    | sed -n 's/^[[:space:]]*\(.*[^[:space:]]\)[[:space:]]*<\([^>]*\)>[[:space:]]*$/\1\t\2/p'
}

identities | jq -n -R -r --slurpfile file "$MAPPING" '
  [inputs] | map(select(length > 0) | split("\t") | { name: .[0], email: (.[1] // "") }) as $ids
  | ($file[0]) as $f
  | ( ($f.mappings // {}) | with_entries(.key |= ascii_downcase) ) as $map
  | ( ($f.aliases // {}) | with_entries(.key |= ascii_downcase) ) as $alias
  | ( ($f.ignore // []) | map(ascii_downcase) ) as $ignore
  | def ghuser: (capture("^(?:[0-9]+\\+)?(?<u>[^@]+)@users\\.noreply\\.github\\.com$") | .u) // null;
    def resolve:
      ( [ (.email | ascii_downcase), (.email | ascii_downcase | ghuser), (.name | ascii_downcase) ]
        | map(select(. != null and . != "")) ) as $cands
      | first( $cands[] as $c
               | ( ($alias[$c] // empty | ascii_downcase), (if $map[$c] != null then $c else empty end) ) )
        // null;
  [ $ids[]
    | select( ((.name | ascii_downcase) as $n | $ignore | index($n)) == null
              and ((.email | ascii_downcase) as $e | $ignore | index($e)) == null
              and ((.name | endswith("[bot]")) | not) )
    | . + { key: resolve } ]
  | ( [ .[] | select(.key != null and (($map[.key] // "") | length) > 0) ]
      | group_by(.key) | map({ npub: $map[.[0].key], n: length }) | sort_by(-.n, .npub) ) as $credited
  | ( [ .[] | select(.key == null or (($map[.key] // "") | length) == 0) ]
      | group_by(.key // "\(.name) <\(.email)>")
      | map({ who: (.[0].key // "\(.[0].name) <\(.[0].email)>"), n: length }) | sort_by(-.n) ) as $unmapped
  | "## Contributors\n\n"
    + ( [ $credited[] | "- @\(.npub)" ] | join("\n") )
    + ( if ($unmapped | length) > 0
        then "\n\n# UNMAPPED (no npub in github.json — add it under mappings, or alias the identity):\n"
             + ( [ $unmapped[] | "#   - \(.who) (\(.n) commit\(if .n == 1 then "" else "s" end))" ] | join("\n") )
        else "" end )
'
