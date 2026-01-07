#!/bin/bash
# Find all @Composable functions in the codebase

set -e

# Default to current directory if no path provided
SEARCH_PATH="${1:-.}"

echo "Searching for @Composable functions in: $SEARCH_PATH"
echo "================================================"
echo ""

# Find all @Composable functions with file paths and line numbers
grep -r -n "@Composable" "$SEARCH_PATH" \
  --include="*.kt" \
  --exclude-dir=build \
  --exclude-dir=.gradle \
  | while IFS=: read -r file line content; do
    # Extract function name if possible
    if [[ $content =~ fun[[:space:]]+([a-zA-Z0-9_]+) ]]; then
      func_name="${BASH_REMATCH[1]}"
      echo "$file:$line - $func_name"
    else
      echo "$file:$line"
    fi
  done

echo ""
echo "Total @Composable functions found:"
grep -r "@Composable" "$SEARCH_PATH" \
  --include="*.kt" \
  --exclude-dir=build \
  --exclude-dir=.gradle \
  | wc -l
