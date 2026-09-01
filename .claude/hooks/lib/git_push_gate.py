#!/usr/bin/env python3
"""Decide whether a PreToolUse payload on stdin is a push/PR boundary.

Shared by every pre-push hook in this directory (pre-push-spotless.sh,
pre-push-orphan-strings.sh) so the gate condition is defined once. Each hook is
a separate process with its own stdin, so this is exec'd per hook rather than
run once and shared.

Exit 0 = this call publishes code (gate it). Exit 1 = let it through.
"""

import json
import shlex
import sys

# Reaching the push subcommand means stepping over git's global options first.
GLOBAL_WITH_ARG = {"-c", "-C", "--namespace", "--git-dir", "--work-tree", "--exec-path"}


def is_boundary(data):
    tool = data.get("tool_name", "")
    if tool.endswith("create_pull_request"):
        return True
    if tool != "Bash":
        return False

    cmd = (data.get("tool_input") or {}).get("command", "")
    # Tokenize like a shell so `push` inside a quoted commit message or heredoc
    # stays one token and is NOT mistaken for the push subcommand.
    try:
        tokens = shlex.split(cmd, comments=True)
    except ValueError:
        tokens = cmd.split()

    for i, token in enumerate(tokens):
        if token != "git" and not token.endswith("/git"):
            continue
        j = i + 1
        while j < len(tokens):
            tok = tokens[j]
            if tok in GLOBAL_WITH_ARG:
                j += 2
            elif tok.startswith("-"):
                j += 1
            else:
                break
        if j < len(tokens) and tokens[j] == "push":
            return True
    return False


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        return 1
    return 0 if is_boundary(data) else 1


if __name__ == "__main__":
    sys.exit(main())
