#!/usr/bin/env bash
#
# Build the changelog "## Translations" section for a release window.
#
# Pulls a Crowdin "Top Members" report for a date range (the gap between two
# releases), joins each Crowdin contributor against docs/changelog/translators.json
# (Crowdin username/id -> npub), and prints a ready-to-paste credit block grouped
# by language. Contributors with no npub mapping are listed under UNMAPPED so you
# can credit them by hand and backfill translators.json.
#
# Usage:
#   scripts/translators.sh --from <date|tag> [--to <date|tag>]
#
#   --from / --to   A date (YYYY-MM-DD) or a git tag/ref. Tags are resolved to
#                   their commit date. --from defaults to the most recent v* tag
#                   ("since the last release"); --to defaults to now.
#   --mapping PATH  Override mapping file (default docs/changelog/translators.json).
#   --raw           Also dump the raw per-member report rows (for debugging /
#                   discovering Crowdin usernames to add to the mapping).
#   --seed          Instead of printing credits, update translators.json from the
#                   window (see the two lists below). Fill in any blank npubs
#                   afterwards.
#
# Environment (same names crowdin.yml already uses):
#   CROWDIN_PROJECT_ID       Crowdin numeric project id.
#   CROWDIN_PERSONAL_TOKEN   Crowdin personal access token (needs report scope).
#
# docs/changelog/translators.json (kept alongside the changelogs) holds two lists:
#   mappings      A forever-growing Crowdin-username/id -> npub registry. --seed
#                 appends new contributors with a blank npub and never deletes or
#                 overwrites existing entries.
#   sinceLastTag  A rolling snapshot of who has translated since the last release
#                 tag, refreshed on every --seed run.
#
# When printing credits, contributors are grouped by language and resolved to
# npubs via mappings; anyone without an npub is listed under UNMAPPED so you can
# credit them by hand and backfill the registry.
#
# Requires: bash, curl, jq, git.
#
# NOTE: This talks to the live Crowdin REST API (api.crowdin.com). The JSON field
# paths for the downloaded "top-members" report are documented at
# https://developer.crowdin.com/api/v2/#operation/api.projects.reports.post and
# can be adjusted in the jq block below if Crowdin changes the schema.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="https://api.crowdin.com/api/v2"
MAPPING="$REPO_ROOT/docs/changelog/translators.json"
FROM=""
TO=""
RAW=0
SEED=0

die() { echo "error: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --from)    FROM="${2:?--from needs a value}"; shift 2 ;;
    --to)      TO="${2:?--to needs a value}"; shift 2 ;;
    --mapping) MAPPING="${2:?--mapping needs a value}"; shift 2 ;;
    --raw)     RAW=1; shift ;;
    --seed)    SEED=1; shift ;;
    -h|--help) sed -n '2,38p' "$0"; exit 0 ;;
    *)         die "unknown argument: $1" ;;
  esac
done

command -v jq   >/dev/null || die "jq not found"
command -v curl >/dev/null || die "curl not found"
# The window is "since the last release": --from defaults to the most recent v*
# tag (falling back to two months ago if no tag is reachable — e.g. a shallow CI
# checkout without tags). The tag name, when found, is recorded in the file's
# sinceLastTag block.
LAST_TAG="$(git -C "$REPO_ROOT" describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
if [ -z "$FROM" ]; then
  if [ -n "$LAST_TAG" ]; then
    FROM="$LAST_TAG"
  else
    FROM="$(date -u -d '2 months ago' +%Y-%m-%d 2>/dev/null || date -u -v-2m +%Y-%m-%d)"
  fi
fi
[ -n "${CROWDIN_PROJECT_ID:-}" ]     || die "CROWDIN_PROJECT_ID is not set"
[ -n "${CROWDIN_PERSONAL_TOKEN:-}" ] || die "CROWDIN_PERSONAL_TOKEN is not set"
[ -f "$MAPPING" ] || die "mapping file not found: $MAPPING"

# Resolve a date (YYYY-MM-DD) or a git ref to an ISO-8601 timestamp.
resolve_ts() {
  local v="$1"
  if [[ "$v" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    echo "${v}T00:00:00+00:00"
  elif git -C "$REPO_ROOT" rev-parse -q --verify "$v" >/dev/null 2>&1; then
    git -C "$REPO_ROOT" log -1 --format=%cI "$v"
  else
    die "--from/--to value '$v' is neither a YYYY-MM-DD date nor a known git ref"
  fi
}

DATE_FROM="$(resolve_ts "$FROM")"
DATE_TO="$( [ -n "$TO" ] && resolve_ts "$TO" || date -u +%Y-%m-%dT%H:%M:%S+00:00 )"
echo "# Crowdin contributors from $DATE_FROM to $DATE_TO" >&2

auth=(-H "Authorization: Bearer ${CROWDIN_PERSONAL_TOKEN}" -H "Content-Type: application/json")
base="${API}/projects/${CROWDIN_PROJECT_ID}/reports"

# 1) Kick off a top-members report for the window.
gen_body="$(jq -n --arg from "$DATE_FROM" --arg to "$DATE_TO" '{
  name: "top-members",
  schema: { unit: "words", format: "json", dateFrom: $from, dateTo: $to }
}')"
report_id="$(curl -fsS "${auth[@]}" -X POST "$base" -d "$gen_body" | jq -r '.data.identifier')"
[ -n "$report_id" ] && [ "$report_id" != "null" ] || die "Crowdin did not return a report identifier"

# 2) Poll until the report is finished.
for _ in $(seq 1 60); do
  status="$(curl -fsS "${auth[@]}" "${base}/${report_id}" | jq -r '.data.status')"
  case "$status" in
    finished) break ;;
    failed)   die "Crowdin report generation failed" ;;
    *)        sleep 2 ;;
  esac
done
[ "$status" = "finished" ] || die "report did not finish in time (last status: $status)"

# 3) Download the report JSON.
dl_url="$(curl -fsS "${auth[@]}" "${base}/${report_id}/download" | jq -r '.data.url')"
[ -n "$dl_url" ] && [ "$dl_url" != "null" ] || die "Crowdin did not return a download url"
report="$(curl -fsS "$dl_url")"

if [ "$RAW" = "1" ]; then
  echo "$report" | jq '(.data // .)' >&2
fi

# 4a) --seed: update translators.json from the window. Two lists are maintained:
#       - mappings   : the forever-growing username -> npub registry. New
#                      contributors are appended with a blank npub; existing
#                      entries (and their npubs) are never touched or removed.
#                      Matching is case-insensitive on username.
#       - sinceLastTag : a rolling snapshot of who has translated since the last
#                      release tag. Fully replaced each run.
if [ "$SEED" = "1" ]; then
  before="$(jq '(.mappings // {}) | length' "$MAPPING")"
  merged="$(jq -n --slurpfile cur "$MAPPING" --argjson rep "$report" \
    --arg tag "$LAST_TAG" --arg since "$DATE_FROM" \
    --arg updated "$(date -u +%Y-%m-%dT%H:%M:%S+00:00)" '
    ($cur[0]) as $file
    | [ ($rep.data // $rep)[] | .user | (.username // (.id|tostring)) ] as $contributors
    | ( reduce $contributors[] as $u (($file.mappings // {});
          if ( [keys_unsorted[] | ascii_downcase] | index($u | ascii_downcase) )
          then . else . + { ($u): "" } end) ) as $mappings
    | $file
      + { mappings: $mappings }
      + { sinceLastTag: { tag: $tag, since: $since, updated: $updated,
                          translators: ($contributors | unique) } }
  ')"
  echo "$merged" > "$MAPPING"
  after="$(jq '(.mappings // {}) | length' "$MAPPING")"
  active="$(jq '(.sinceLastTag.translators // []) | length' "$MAPPING")"
  echo "# Seeded $MAPPING: mappings $before -> $after (added $((after - before)) new, npubs blank);" >&2
  echo "#   sinceLastTag = $active contributor(s) since ${LAST_TAG:-$DATE_FROM}." >&2
  exit 0
fi

# 4b) Join report members against the npub mapping, grouped by language.
#    Mapping keys are lower-cased; we match by username (lower) or numeric id.
echo "$report" | jq -r --slurpfile m "$MAPPING" '
  ($m[0].mappings // {}) as $map
  | ( $map | with_entries(.key |= ascii_downcase) ) as $byname
  | (.data // .) as $members
  | reduce $members[] as $mem ({langs:{}, unmapped:[]};
      ($mem.user // {}) as $u
      | ( ($u.username // "") | ascii_downcase ) as $uname
      | ( $byname[$uname] // $map[($u.id|tostring)] ) as $npub
      | if $npub == null then
          .unmapped += [ ($u.fullName // $u.username // ("id " + ($u.id|tostring))) ]
        else
          reduce ( ($mem.languages // []) | if length>0 then . else [{name:"(unknown language)"}] end | .[] ) as $l (.;
            .langs[$l.name] = ((.langs[$l.name] // []) + ["@\($npub)"] | unique))
        end
    )
  | "## Translations\n"
    + ( [ .langs | to_entries[] | "- \(.key) by " + (.value | sort | join(" and ")) ] | sort | join("\n") )
    + ( if (.unmapped|length)>0
        then "\n\n# UNMAPPED (no npub in translators.json — credit by hand, then add them):\n"
             + ( [ .unmapped | unique[] | "#   - " + . ] | join("\n") )
        else "" end )
'
