#!/usr/bin/env python3
"""Warn when a newly added English string looks like it should be a <plurals>.

A string that counts things has to be a `<plurals>` from the start: a key's
type is fixed by the default catalog, so every locale translates the wrong
shape until someone reshapes it in all of them (#4863 had to redo four keys in
up to 34 locales). This check catches the shape when the key is added.

Two modes:

  (default)  New keys only: `<string>`s in the two default catalogs at --head
             (default HEAD; repeat it for several pushed refs) whose name
             exists in NEITHER catalog at the base. The union across both
             catalogs means a key moved between them
             (tools/strings-migrate/migrate.py) does not count as new. Both
             revisions are read with `git show`, not from the working tree, so
             the check sees what is being pushed. The base is --base, or else
             `git merge-base <head> <remote>/main` for the remote whose URL
             points at vitorpamplona/amethyst; without one it says it skipped.
  --all      Every `<string>` in the working tree's catalogs, for the
             find-missing-translations skill's translation pass. A heuristic:
             review every hit by hand.

Rules, applied to each `<string>`'s raw text:

  counted-noun   `%N$d <word>` where the word is not a unit or a joining word
  two-counts     two or more placeholders that each pass the counted-noun test
  s-hedge        an `(s)` / `(es)` hedge straight after a word (not `http(s)`)
  hardcoded-one  a literal `1 <word>`, with no placeholder

Output: one `<catalog>\t<key>\t<rule>[,<rule>]\t<text>` line per hit, or with
--hook-json the whole report as a PreToolUse hook's JSON for Claude Code and
Codex. This is a warning, not a gate: the exit code is 0 whatever it finds,
1 on bad usage.
"""

import argparse
import json
import re
import subprocess
import sys

CATALOGS = {
    "commonsUI": "commonsUI/src/commonMain/composeResources/values/strings.xml",
    "amethyst": "amethyst/src/main/res/values/strings.xml",
}

# Same as the find-missing-translations skill's scan: raw text, no XML parser.
STRING = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
# Same as orphan_strings_check.py: every declared key, whatever its type.
NAMED = re.compile(r'<(?:string|plurals|string-array)\s+[^>]*name="([^"]+)"')
# %1$d <word>, skipping identifiers (`kind %1$d`), `^%`, `\%` and percentages (`%1$d%`).
COUNT = re.compile(r"(?<!kind )(?<!\^)(?<!\\)%\d+\$d(?!%)\s+([A-Za-z][\w-]*)")
UNITS = {
    "s", "ms", "sec", "min", "h", "hr", "d", "m", "km", "kb", "mb", "gb", "tb", "b", "px", "dp", "sp",
    "x", "sat", "sats", "bytes", "byte", "hz", "khz", "fps",
    "of", "to", "and", "or", "in", "at", "on", "by",
}
# word(s) / word(es); URL schemes like http(s) are not a hedged plural.
S_HEDGE = re.compile(r"\b([A-Za-z]+)\(e?s\)")
NOT_HEDGES = {"http", "ws"}
# A `1` that is a whole number of its own (not `%1$d`, `10`, `1.5`, `2,1`), then a word.
HARDCODED_ONE = re.compile(r"(?<![\w$%.,\\])1 +([A-Za-z][\w-]*)")

UPSTREAM_URL = re.compile(r"[:/]vitorpamplona/amethyst(?:\.git)?/?$")


def rules_for(text):
    counted = [w for w in COUNT.findall(text) if w.lower() not in UNITS]
    rules = []
    if counted:
        rules.append("counted-noun")
    if len(counted) >= 2:
        rules.append("two-counts")
    if any(w.lower() not in NOT_HEDGES for w in S_HEDGE.findall(text)):
        rules.append("s-hedge")
    if any(w.lower() not in UNITS for w in HARDCODED_ONE.findall(text)):
        rules.append("hardcoded-one")
    return rules


def git(*args):
    result = subprocess.run(["git", *args], capture_output=True, text=True, encoding="utf-8")
    return result.stdout if result.returncode == 0 else None


def is_commit(rev):
    return git("rev-parse", "--verify", "--quiet", f"{rev}^{{commit}}") is not None


def show(rev, path):
    return git("show", f"{rev}:{path}") or ""


def upstream_main():
    """`<remote>/main` for the first remote whose fetch URL is vitorpamplona/amethyst."""
    urls = git("config", "--get-regexp", r"^remote\..*\.url$") or ""
    for line in urls.splitlines():
        key, _, url = line.partition(" ")
        if UPSTREAM_URL.search(url.strip()):
            remote = key[len("remote.") : -len(".url")]
            if is_commit(f"refs/remotes/{remote}/main"):
                return f"{remote}/main"
    return None


def hits(sources, skip=frozenset()):
    """sources: {label: catalog text} -> ["label\\tkey\\trules\\ttext"] for every flagged <string>."""
    found = []
    for label, text in sources.items():
        for m in STRING.finditer(text):
            rules = [] if m[1] in skip else rules_for(m[2])
            if rules:
                found.append(f"{label}\t{m[1]}\t{','.join(rules)}\t{m[2]}")
    return found


def scan_all():
    sources = {}
    for label, path in CATALOGS.items():
        with open(path, encoding="utf-8") as handle:
            sources[label] = handle.read()
    return hits(sources)


def new_hits(head, base):
    """(hits, None) for the keys head adds over base, or ([], why it skipped)."""
    if not is_commit(head):
        return [], f"{head} is not a commit"
    if base is None:
        main = upstream_main()
        if main is None:
            return [], "no remote for vitorpamplona/amethyst with a main branch"
        base = (git("merge-base", head, main) or "").strip()
        if not base:
            return [], f"no merge base between {head} and {main}"
    elif not is_commit(base):
        return [], f"{base} is not a commit"
    known = {key for path in CATALOGS.values() for key in NAMED.findall(show(base, path))}
    return hits({label: show(head, path) for label, path in CATALOGS.items()}, known), None


def scan_new(heads, base):
    found, skipped = [], []
    for head in heads:
        head_hits, why = new_hits(head, base)
        found += [h for h in head_hits if h not in found]
        if why:
            skipped.append(f"plural candidates check skipped: {why}")
    if not found:
        return skipped
    return [
        f"Plural candidates: {len(found)} new string(s) look like they count something.",
        "A string that counts things should be a <plurals> before it is translated:",
        *found,
        'Judge each by meaning, at its call site: see amethyst/src/main/res/AGENTS.md "Plural anti-patterns'
        ' to flag" and .claude/skills/find-missing-translations/SKILL.md, step 2.',
        "This is a warning; the push is not blocked.",
        *skipped,
    ]


class Parser(argparse.ArgumentParser):
    def error(self, message):
        # argparse exits 2 on bad usage; 2 means "block" to the agent hooks, and this never blocks.
        self.print_usage(sys.stderr)
        print(f"{self.prog}: error: {message}", file=sys.stderr)
        sys.exit(1)


def main():
    parser = Parser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--all", action="store_true", help="scan the whole working-tree catalog")
    parser.add_argument("--head", action="append", help="revision being pushed (default HEAD; repeatable)")
    parser.add_argument("--base", help="compare against this revision instead of the upstream merge base")
    parser.add_argument("--hook-json", action="store_true", help="print the report as PreToolUse hook JSON")
    args = parser.parse_args()
    if args.all and (args.base or args.head):
        parser.error("--all reads the working tree; it takes no --base or --head")

    lines = scan_all() if args.all else scan_new(args.head or ["HEAD"], args.base)
    if not lines:
        return 0
    report = "\n".join(lines)
    if args.hook_json:
        # A PreToolUse hook's plain stdout on exit 0 reaches neither agent's model: additionalContext
        # does (Claude Code and Codex), and systemMessage shows it to the user.
        hook = {"hookEventName": "PreToolUse", "additionalContext": report}
        print(json.dumps({"systemMessage": report, "hookSpecificOutput": hook}))
    else:
        print(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
