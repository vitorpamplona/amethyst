#!/usr/bin/env python3
"""
Reads one or more startup timelines (the tab-separated file StartupTimeline writes) and prints, per
run, the milestones and the moment the top of the home feed stopped changing.

    startup_settle.py [--feed home] [--top 5] run1.tsv [run2.tsv ...]

"Settled" is the last change of the feed's top --top posts, leaving out posts created after the
process started, or up to --grace seconds before it: those are new posts arriving live, which would move the top of any feed, not
backfill the start still owed. Each run's reference is its own final top; the settle time is when
the feed first showed that top and kept it to the end of the run.
"""

import argparse
import statistics
import sys


def parse(path):
    marks, feeds, outbox, relays = {}, {}, [], []
    jvm_start = None
    with open(path) as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            ms, kind, name = int(parts[0]), parts[1], parts[2]
            rest = parts[3:]
            if kind == "mark":
                if name == "jvm-start":
                    jvm_start = int(rest[0]) // 1000
                marks.setdefault(name, ms)
            elif kind == "feed":
                size = int(rest[0])
                top = []
                if len(rest) > 1 and rest[1]:
                    for item in rest[1].split(","):
                        note_id, created = item.rsplit(":", 1)
                        top.append((note_id, int(created)))
                feeds.setdefault(name, []).append((ms, size, top))
            elif kind == "outbox":
                outbox.append((ms, rest))
            elif kind == "relays":
                relays.append((ms, int(rest[0])))
    return jvm_start, marks, feeds, outbox, relays


def settle(rows, jvm_start, top_n, grace):
    """(ms the filtered top last changed, number of changes, final filtered top)."""
    cutoff = None if jvm_start is None else jvm_start - grace

    def visible(top):
        return tuple(n for n, created in top if cutoff is None or created <= cutoff)[:top_n]

    if not rows:
        return None, 0, ()
    final = visible(rows[-1][2])
    settled_at, changes, last = None, 0, None
    for ms, _size, top in rows:
        v = visible(top)
        if v != last:
            changes += 1
            last = v
            settled_at = ms if v == final else None
    return settled_at, changes, final


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--feed", default="home")
    ap.add_argument("--top", type=int, default=5)
    # A post made seconds before the start may still be on its way to the relays: count it as live.
    ap.add_argument("--grace", type=int, default=60, help="seconds before the start that still count as live")
    ap.add_argument("files", nargs="+")
    args = ap.parse_args()

    cols = ["first-composition", "account-loaded", "account-viewmodel", "logged-in-effects", "first-relay", "follows-known", f"{args.feed}-first-posts"]
    print("run\t" + "\t".join(cols) + "\tsettled\ttop-changes\tposts\tend")
    settled_all, first_all = [], []
    per_col = {c: [] for c in cols}
    for path in args.files:
        jvm_start, marks, feeds, _outbox, relays = parse(path)
        if relays:
            marks.setdefault("first-relay", relays[0][0])
        rows = feeds.get(args.feed, [])
        settled_at, changes, _final = settle(rows, jvm_start, args.top, args.grace)
        end = max([r[0] for r in rows] + list(marks.values()) + [0])
        vals = [str(marks.get(c, "-")) for c in cols]
        for c in cols:
            if c in marks:
                per_col[c].append(marks[c])
        posts = rows[-1][1] if rows else 0
        print(f"{path}\t" + "\t".join(vals) + f"\t{settled_at if settled_at is not None else '-'}\t{changes}\t{posts}\t{end}")
        if settled_at is not None:
            settled_all.append(settled_at)
        if f"{args.feed}-first-posts" in marks:
            first_all.append(marks[f"{args.feed}-first-posts"])
    if len(args.files) > 1:
        medians = [f"{statistics.median(per_col[c]):.0f}" if per_col[c] else "-" for c in cols]
        settled_median = f"{statistics.median(settled_all):.0f}" if settled_all else "-"
        print("median\t" + "\t".join(medians) + f"\t{settled_median}")
        for label, values in (("first posts", first_all), ("settled", settled_all)):
            if values:
                print(
                    f"{label}: median {statistics.median(values):.0f} ms, min {min(values)} ms, max {max(values)} ms (n={len(values)})",
                )
    return 0


if __name__ == "__main__":
    sys.exit(main())
