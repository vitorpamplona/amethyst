#!/usr/bin/env bash

set -e

function setup_git_hooks()
{
  echo "Initialising git hooks..."
  ln -sf "$PWD/tools/git-hooks/pre-commit.sh" "$PWD/.git/hooks/pre-commit"
  chmod +x "$PWD/.git/hooks/pre-commit"
  echo "Done"
}

setup_git_hooks