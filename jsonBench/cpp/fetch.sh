#!/usr/bin/env bash
# Fetches every third-party library listed in deps.txt, pinned, into jsonBench/.cache/cpp/<name>.
# Nothing is vendored into git; re-running is a no-op once a checkout matches its pin.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
cache="${JB_CPP_CACHE:-$here/../.cache/cpp}"
mkdir -p "$cache"
while read -r name repo ref; do
  [[ -z "${name:-}" || "$name" == \#* ]] && continue
  dir="$cache/$name"
  if [[ -f "$dir/.jb-ref" && "$(cat "$dir/.jb-ref")" == "$ref" ]]; then continue; fi
  rm -rf "$dir"
  echo "fetching $repo @ $ref" >&2
  if [[ "$ref" =~ ^[0-9a-f]{40}$ ]]; then
    git init -q "$dir"
    git -C "$dir" fetch -q --depth 1 "https://github.com/$repo" "$ref"
    git -C "$dir" -c advice.detachedHead=false checkout -q FETCH_HEAD
  else
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$ref" "https://github.com/$repo" "$dir"
  fi
  echo "$ref" > "$dir/.jb-ref"
done < "$here/deps.txt"
