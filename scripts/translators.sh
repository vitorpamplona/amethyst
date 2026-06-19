#!/usr/bin/env bash
#
# Build the changelog "## Translations" section, or keep its data file seeded.
#
# Default (no flags): OFFLINE. Generate the ready-to-paste credit block straight
# from docs/changelog/translators.json — no token, no network. This is the
# release-time command: it reads the sinceLastTag snapshot (kept fresh by CI,
# with each translator's languages) and resolves npubs via the mappings registry,
# grouping by language. Anyone without an npub is listed under UNMAPPED.
#
#   scripts/translators.sh                 # print the block for the current cycle
#
# --seed: ONLINE. Query Crowdin's "Top Members" report for the window and update
# translators.json (see the two lists below). Used by CI; needs a token.
#
#   scripts/translators.sh --seed
#
# Flags:
#   --seed          Refresh translators.json from Crowdin (online). Window is
#                   --from..--to.
#   --raw           Dump the raw per-member report rows to stderr (online; for
#                   discovering Crowdin usernames). Implies an API call.
#   --from / --to   Window for --seed/--raw. A date (YYYY-MM-DD) or a git tag/ref
#                   (resolved to its commit date). --from defaults to the most
#                   recent v* tag ("since the last release"); --to defaults to now.
#   --mapping PATH  Override the data file (default docs/changelog/translators.json).
#
# Environment (only needed for --seed/--raw; same names crowdin.yml uses):
#   CROWDIN_PROJECT_ID       Crowdin numeric project id.
#   CROWDIN_PERSONAL_TOKEN   Crowdin personal access token (needs report scope).
#
# docs/changelog/translators.json (kept alongside the changelogs) holds two lists:
#   mappings      A forever-growing Crowdin-username/id -> npub registry. --seed
#                 appends new contributors with a blank npub and never deletes or
#                 overwrites existing entries.
#   sinceLastTag  A rolling snapshot of who has translated since the last release
#                 tag — each entry is { user, languages } — refreshed on every
#                 --seed run. The offline credit block is generated from this.
#
# Requires: bash, jq (always); curl, git (only for --seed/--raw).
#
# NOTE: --seed/--raw talk to the live Crowdin REST API (api.crowdin.com). The JSON
# field paths for the "top-members" report are documented at
# https://developer.crowdin.com/api/v2/#operation/api.projects.reports.post and
# can be adjusted in the jq blocks below if Crowdin changes the schema.

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
    -h|--help) sed -n '2,45p' "$0"; exit 0 ;;
    *)         die "unknown argument: $1" ;;
  esac
done

command -v jq >/dev/null || die "jq not found"
[ -f "$MAPPING" ] || die "mapping file not found: $MAPPING"

# Render the "## Translations" block from a {langs, unmapped} accumulator. Shared
# by the offline and online paths so they produce byte-identical output.
RENDER_BLOCK='
  "## Translations\n"
  + ( [ .langs | to_entries[] | "- \(.key) by " + (.value | sort | join(" and ")) ] | sort | join("\n") )
  + ( if (.unmapped|length) > 0
      then "\n\n# UNMAPPED (no npub in translators.json — add it under mappings):\n"
           + ( [ .unmapped | unique[] | "#   - " + . ] | join("\n") )
      else "" end )'

# ---------------------------------------------------------------------------
# Default (offline): generate the credits straight from the committed file.
# This is the release-time command — no token, no network. It reads the
# sinceLastTag snapshot (which CI keeps fresh, with each translator's languages)
# and resolves npubs via the forever-growing mappings registry.
# ---------------------------------------------------------------------------
if [ "$SEED" = "0" ] && [ "$RAW" = "0" ]; then
  jq -r "
    (.mappings // {}) as \$map
    | ( \$map | with_entries(.key |= ascii_downcase) ) as \$byname
    | ( (.sinceLastTag.translators // [])
        | map(if type == \"object\" then . else { user: ., languages: [] } end) ) as \$list
    | reduce \$list[] as \$t ({ langs: {}, unmapped: [] };
        (\$t.user) as \$u
        | ( \$byname[\$u | ascii_downcase] // \$map[\$u] ) as \$npub
        | ( if (\$t.languages | length) > 0 then \$t.languages else [\"(unknown language)\"] end ) as \$langs
        | if (\$npub | type) == \"string\" and (\$npub | length) > 0 then
            reduce \$langs[] as \$l (.; .langs[\$l] = ((.langs[\$l] // []) + [\"@\(\$npub)\"] | unique))
          else
            .unmapped += [\$u]
          end
      )
    | $RENDER_BLOCK
  " "$MAPPING"
  exit 0
fi

# ---------------------------------------------------------------------------
# Online (--seed / --raw): query Crowdin's Top Members report for the window.
# ---------------------------------------------------------------------------
command -v curl >/dev/null || die "curl not found"
# The window is "since the last release": --from defaults to the most recent v*
# tag (falling back to two months ago if no tag is reachable — e.g. a shallow CI
# checkout without tags). The tag name, when found, is recorded in sinceLastTag.
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

# 4) --seed: update translators.json from the window. Two lists are maintained:
#      - mappings    : the forever-growing username -> npub registry. New
#                      contributors are appended with a blank npub; existing
#                      entries (and their npubs) are never touched or removed.
#                      Matching is case-insensitive on username.
#      - sinceLastTag : a rolling snapshot of who has translated since the last
#                      release tag, with each contributor's languages, so the
#                      offline credits can be generated without re-querying.
#                      Fully replaced each run.
if [ "$SEED" = "1" ]; then
  before="$(jq '(.mappings // {}) | length' "$MAPPING")"
  merged="$(jq -n --slurpfile cur "$MAPPING" --argjson rep "$report" \
    --arg tag "$LAST_TAG" --arg since "$DATE_FROM" '
    ($cur[0]) as $file
    | [ ($rep.data // $rep)[] | {
          user: (.user.username // (.user.id|tostring)),
          languages: ([ (.languages // [])[] | .name ] | unique)
        } ] as $contribs
    | ( $contribs | map(.user) ) as $names
    | ( reduce $names[] as $u (($file.mappings // {});
          if ( [keys_unsorted[] | ascii_downcase] | index($u | ascii_downcase) )
          then . else . + { ($u): "" } end) ) as $mappings
    | $file
      + { mappings: $mappings }
      + { sinceLastTag: { tag: $tag, since: $since,
                          translators: $contribs } }
  ')"
  echo "$merged" > "$MAPPING"
  after="$(jq '(.mappings // {}) | length' "$MAPPING")"
  active="$(jq '(.sinceLastTag.translators // []) | length' "$MAPPING")"
  echo "# Seeded $MAPPING: mappings $before -> $after (added $((after - before)) new, npubs blank);" >&2
  echo "#   sinceLastTag = $active contributor(s) since ${LAST_TAG:-$DATE_FROM}." >&2
fi
