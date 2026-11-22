#!/usr/bin/env python3
"""Warn when a newly added English string looks like it should be a <plurals>.

A string that counts things has to be a `<plurals>` from the start: a key's
type is fixed by the default catalog, so every locale translates the wrong
shape until someone reshapes it in all of them (#4863 had to redo four keys in
up to 34 locales). This check catches the shape when the key is added.

Two modes:

  (default)  New keys only: `<string>`s in the two default catalogs at --head
             (default HEAD) whose name exists in NEITHER catalog at the base.
             The union across both catalogs means a key moved between them
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

Output: one `<catalog>\t<key>\t<rule>[,<rule>]\t<text>` line per hit. This is
a warning, not a gate: the exit code is 0 whatever it finds, 1 on bad usage.

Run by .git-hooks/pre-push (the terminal push), by pre-push-plural-candidates.sh
(Claude Code and Codex), and by hand.
"""

import argparse
import io
import re
import subprocess
import sys

CATALOGS = (
    "commonsUI/src/commonMain/composeResources/values/strings.xml",
    "amethyst/src/main/res/values/strings.xml",
)

# Same as the find-missing-translations skill's scan: raw text, no XML parser.
STRING = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
NAMED = re.compile(r'<(?:string|plurals|string-array)\s+[^>]*name="([^"]+)"')
# %1$d <word>, skipping identifiers (`kind %1$d`), `^%`, `\%` and percentages (`%1$d%`).
COUNT = re.compile(r"(?<!kind )(?<!\^)(?<!\\)%\d+\$d(?!%)\s+([A-Za-z][\w-]*)")
UNITS = {
    "s", "ms", "sec", "min", "h", "hr", "d", "m", "km", "kb", "mb", "gb", "tb", "b", "px", "dp", "sp",
    "x", "sat", "sats", "bytes", "byte", "hz", "khz", "fps",
    "of", "to", "and", "or", "in", "at", "on", "by",
}
# `http(s)` and `ws(s)` are URL schemes, not a hedged plural.
S_HEDGE = re.compile(r"[A-Za-z](?<!\bhttp)(?<!\bws)\((?:e?s)\)")
# A `1` that is a whole number of its own (not `%1$d`, `10`, `1.5`, `2,1`), then a word.
HARDCODED_ONE = re.compile(r"(?<![\w$%.,\\])1 +([A-Za-z][\w-]*)")

ANTI_PATTERNS = 'amethyst/src/main/res/AGENTS.md "Plural anti-patterns to flag"'
SKILL_STEP = ".claude/skills/find-missing-translations/SKILL.md, step 2"
UPSTREAM_URL = re.compile(r"[:/]vitorpamplona/amethyst(?:\.git)?/?$")


def rules_for(text):
    counted = [w for w in COUNT.findall(text) if w.lower() not in UNITS]
    rules = []
    if counted:
        rules.append("counted-noun")
    if len(counted) >= 2:
        rules.append("two-counts")
    if S_HEDGE.search(text):
        rules.append("s-hedge")
    if any(w.lower() not in UNITS for w in HARDCODED_ONE.findall(text)):
        rules.append("hardcoded-one")
    return rules


def catalog_label(path):
    return path.split("/", 1)[0]


def git(*args):
    result = subprocess.run(["git", *args], capture_output=True, text=True, encoding="utf-8")
    return result.stdout if result.returncode == 0 else None


def show(rev, path):
    return git("show", f"{rev}:{path}") or ""


def upstream_main():
    """`<remote>/main` for the first remote whose fetch URL is vitorpamplona/amethyst."""
    urls = git("config", "--get-regexp", r"^remote\..*\.url$") or ""
    for line in urls.splitlines():
        key, _, url = line.partition(" ")
        if UPSTREAM_URL.search(url.strip()):
            remote = key[len("remote.") : -len(".url")]
            ref = f"refs/remotes/{remote}/main"
            if git("rev-parse", "--verify", "--quiet", ref) is not None:
                return f"{remote}/main"
    return None


def hits(sources):
    """sources: [(label, text)] -> [(label, key, rules, value)] for every flagged <string>."""
    found = []
    for label, text in sources:
        for m in STRING.finditer(text):
            rules = rules_for(m[2])
            if rules:
                found.append((label, m[1], rules, m[2]))
    return found


def print_hits(found):
    for label, key, rules, text in found:
        print(f"{label}\t{key}\t{','.join(rules)}\t{text}")


def scan_all():
    sources = []
    for path in CATALOGS:
        with io.open(path, encoding="utf-8") as handle:
            sources.append((catalog_label(path), handle.read()))
    print_hits(hits(sources))
    return 0


def scan_new(head, base):
    if git("rev-parse", "--verify", "--quiet", f"{head}^{{commit}}") is None:
        print(f"plural candidates check skipped: {head} is not a commit")
        return 0
    if base is None:
        main = upstream_main()
        if main is None:
            print("plural candidates check skipped: no remote for vitorpamplona/amethyst with a main branch")
            return 0
        base = (git("merge-base", head, main) or "").strip()
        if not base:
            print(f"plural candidates check skipped: no merge base between {head} and {main}")
            return 0
    elif git("rev-parse", "--verify", "--quiet", f"{base}^{{commit}}") is None:
        print(f"plural candidates check skipped: {base} is not a commit")
        return 0

    known = set()
    for path in CATALOGS:
        known |= set(NAMED.findall(show(base, path)))
    found = [h for h in hits((catalog_label(p), show(head, p)) for p in CATALOGS) if h[1] not in known]
    if not found:
        return 0

    print(f"Plural candidates: {len(found)} new string(s) look like they count something.")
    print("A string that counts things should be a <plurals> before it is translated:")
    print_hits(found)
    print(f"Judge each by meaning, at its call site: see {ANTI_PATTERNS} and {SKILL_STEP}.")
    print("This is a warning; the push is not blocked.")
    return 0


class Parser(argparse.ArgumentParser):
    def error(self, message):
        # argparse exits 2 on bad usage; 2 means "block" to the agent hooks, and this never blocks.
        self.print_usage(sys.stderr)
        print(f"{self.prog}: error: {message}", file=sys.stderr)
        sys.exit(1)


def main():
    parser = Parser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--all", action="store_true", help="scan the whole working-tree catalog")
    parser.add_argument("--head", default="HEAD", help="revision being pushed (default HEAD)")
    parser.add_argument("--base", help="compare against this revision instead of the upstream merge base")
    args = parser.parse_args()
    if args.all and (args.base or args.head != "HEAD"):
        parser.error("--all reads the working tree; it takes no --base or --head")
    return scan_all() if args.all else scan_new(args.head, args.base)


if __name__ == "__main__":
    sys.exit(main())
