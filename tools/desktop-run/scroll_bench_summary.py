#!/usr/bin/env python3
"""Summarizes the reports of tools/desktop-run/scroll-bench.sh.

    scroll_bench_summary.py <dir>            the median of each pass across the dir's run-*/ boots
    scroll_bench_summary.py <before> <after> both, then the change from before to after

Each boot's summary.tsv (written by the app's ScrollBenchmark) holds one row per pass. Warm-up boots
(warmup-*/) are left out.
"""

import glob
import os
import statistics
import sys

# The columns worth comparing, and whether lower is better.
COLUMNS = [
    ("fps", False),
    ("interval_p50", True),
    ("interval_p90", True),
    ("interval_p99", True),
    ("jank_pct", True),
    ("hitch_pct", True),
    ("busy_p50", True),
    ("busy_p90", True),
    ("ui_busy_pct", True),
    ("gc_ms", True),
    ("jit_ms", True),
    ("img_mem_pct", False),
    ("img_net", True),
    ("img_load_p90", True),
]


def read_summary(path):
    header = None
    env = {}
    rows = {}
    with open(path) as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            if line.startswith("# "):
                key, _, value = line[2:].partition("=")
                env[key] = value
                continue
            cells = line.split("\t")
            if header is None:
                header = cells
                continue
            rows[cells[0]] = dict(zip(header, cells))
    return env, rows


def load(directory):
    boots = sorted(glob.glob(os.path.join(directory, "run-*", "summary.tsv")))
    if not boots:
        sys.exit(f"No run-*/summary.tsv under {directory}")
    env = {}
    passes = {}
    for path in boots:
        env, rows = read_summary(path)
        for name, row in rows.items():
            passes.setdefault(name, []).append(row)
    medians = {}
    for name, rows in passes.items():
        # Older reports lack the image columns: 0 there.
        medians[name] = {col: statistics.median(float(r.get(col, 0)) for r in rows) for col, _ in COLUMNS}
        medians[name]["boots"] = len(rows)
    return env, medians


def table(title, env, medians):
    print(f"\n{title}")
    keys = ["version", "java", "cores", "render_api", "window", "notches", "interval_ms"]
    print("  " + ", ".join(f"{k}={env[k]}" for k in keys if k in env))
    names = list(medians)
    print("  " + "pass".ljust(12) + "".join(col.rjust(13) for col, _ in COLUMNS) + "boots".rjust(7))
    for name in names:
        m = medians[name]
        print("  " + name.ljust(12) + "".join(f"{m[col]:13.1f}" for col, _ in COLUMNS) + f"{m['boots']:7d}")


def compare(before, after):
    print("\nChange, after vs before (+ better, - worse: each column knows which way is better)")
    print("  " + "pass".ljust(12) + "".join(col.rjust(13) for col, _ in COLUMNS))
    for name in before:
        if name not in after:
            continue
        cells = []
        for col, lower_is_better in COLUMNS:
            b, a = before[name][col], after[name][col]
            if b == 0:
                cells.append("n/a".rjust(13))
                continue
            change = 100.0 * (a - b) / b
            better = -change if lower_is_better else change
            cells.append(f"{better:+.1f}%".rjust(13))
        print("  " + name.ljust(12) + "".join(cells))


def main(args):
    if len(args) not in (1, 2):
        sys.exit(__doc__)
    loaded = [load(d) for d in args]
    for directory, (env, medians) in zip(args, loaded):
        table(directory, env, medians)
    if len(loaded) == 2:
        compare(loaded[0][1], loaded[1][1])


if __name__ == "__main__":
    main(sys.argv[1:])
