#!/usr/bin/env python3
"""Compare macrobenchmark arms by *per-occurrence* section time.

A TraceSectionMetric in Mode.Sum reports the summed duration of every matching
slice in an iteration. That sum is only comparable across arms if each iteration
renders the same number of cards -- and on a corpus of real notes it does not:
card heights vary, so a fixed-distance swipe crosses a different number of cards
every run. Measured counts ranged 8..13 for NoteCard in three arms of identical
code, which alone moved the summed metrics by 36-73%.

Dividing SumSumMs by SumCount removes that denominator. In the run that motivated
this script it took DrawAuthor from unreadable (41.8% apparent swing) to a 4.6%
drift floor with a clear +74% effect.

Usage: normalize.py armA.json armB.json [...]   (arm name = file stem)
"""
import json, sys

def load(path):
    b = json.load(open(path))["benchmarks"][0]
    out = {}
    for src in ("metrics", "sampledMetrics"):
        for k, v in b.get(src, {}).items():
            if isinstance(v, dict) and "median" in v:
                out[k] = v["median"]
    return out

def main(paths):
    arms = {p.split("/")[-1].removesuffix(".json"): load(p) for p in paths}
    names = list(arms)
    secs = sorted({k[:-8] for k in arms[names[0]] if k.endswith("SumSumMs")})
    print(f"{'section (ms per occurrence)':<26}" + "".join(f"{n:>10}" for n in names))
    print("-" * (26 + 10 * len(names)))
    for s in secs:
        vals = []
        for n in names:
            ms, cnt = arms[n].get(s + "SumSumMs"), arms[n].get(s + "SumCount")
            vals.append(ms / cnt if ms is not None and cnt else None)
        if any(v is None for v in vals):
            continue
        print(f"{s:<26}" + "".join(f"{v:>10.3f}" for v in vals))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
