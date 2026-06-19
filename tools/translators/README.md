# Translator credits for the changelog

Generates the `## Translations` block for a release by asking Crowdin **who
translated between two releases** and joining them against the npub mapping kept
in the changelog folder.

## Pieces

- **`docs/changelog/translators.json`** — the Crowdin-user → npub mapping (lives
  next to the changelogs). Keyed by Crowdin username (case-insensitive) or numeric
  user id. Add a row whenever a translator gives you their npub.
- **`tools/translators/translators.sh`** — pulls a Crowdin *Top Members* report
  for a date window, joins it against the mapping, and prints the credit block
  grouped by language. Anyone Crowdin reports who isn't in the mapping is listed
  under `UNMAPPED` so you can credit them by hand and backfill the JSON.

## Usage

```bash
export CROWDIN_PROJECT_ID=...        # same env vars crowdin.yml already uses
export CROWDIN_PERSONAL_TOKEN=...    # token needs the "reports" scope

# Between the previous tag and now:
tools/translators/translators.sh --from v1.12.00

# Between two tags:
tools/translators/translators.sh --from v1.11.00 --to v1.12.00

# Discover Crowdin usernames to add to translators.json:
tools/translators/translators.sh --from v1.12.00 --raw
```

`--from` / `--to` accept either a `YYYY-MM-DD` date or a git tag/ref (resolved to
its commit date). `--to` defaults to now.

Requires `bash`, `curl`, `jq`, `git`.

## How the window maps to "between two versions"

A release is a git tag, so the contribution window is the commit date of the
previous tag → the commit date of the new tag. The Crowdin Top Members report
takes that `dateFrom`/`dateTo` and returns every member who translated/approved
in it, per language.

> The script talks to the live `api.crowdin.com` REST API. The JSON field paths
> for the downloaded report follow Crowdin's `top-members` schema; if Crowdin
> changes it, adjust the `jq` block at the bottom of `translators.sh`.
