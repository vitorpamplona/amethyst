#!/usr/bin/env python3
"""
Merges jsonBench/results/<harness>.json files into one Markdown report.

    python3 jsonBench/report/report.py                    # results/*.json -> results/report.md
    python3 jsonBench/report/report.py --results DIR --out FILE

Only parsers whose digest matched the corpus manifest are ranked; the rest are listed
under "Failed verification" with their error.
"""
import argparse
import glob
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BENCH = os.path.dirname(HERE)

MODE_TITLES = {
    "typed": "Typed: JSON → the language's own Event with owned strings (what a client keeps)",
    "borrowed": "Borrowed: Event whose strings point into the input buffer",
    "dom": "DOM: the library's generic tree (not converted into an Event)",
    "baseline": "Baselines (not parsers)",
}
BASELINE_PARSER = ("jvm", "quartz-jackson")


def load(results_dir):
    runs = []
    for path in sorted(glob.glob(os.path.join(results_dir, "*.json"))):
        try:
            with open(path) as f:
                run = json.load(f)
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(run, dict) and "results" in run and "harness" in run:
            runs.append(run)
    return runs


def fmt(v, digits=0):
    if v is None:
        return "–"
    if isinstance(v, float) and math.isnan(v):
        return "–"
    if digits == 0:
        return f"{v:,.0f}"
    return f"{v:,.{digits}f}"


def gc_cell(r):
    if r.get("gc_count") is None:
        return "–"
    return f"{r['gc_count']} / {fmt(r.get('gc_ms'), 0)} ms"


def geomean(values):
    values = [v for v in values if v and v > 0]
    if not values:
        return None
    return math.exp(sum(math.log(v) for v in values) / len(values))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", default=os.path.join(BENCH, "results"))
    ap.add_argument("--manifest", default=os.path.join(BENCH, ".corpus", "manifest.json"))
    ap.add_argument("--out", default=None)
    args = ap.parse_args()
    out = args.out or os.path.join(args.results, "report.md")

    runs = load(args.results)
    manifest = None
    if os.path.exists(args.manifest):
        with open(args.manifest) as f:
            manifest = json.load(f)
    workloads = [w["name"] for w in manifest["workloads"]] if manifest else []

    rows = []
    for run in runs:
        for r in run["results"]:
            r = dict(r)
            r["harness"] = run["harness"]
            r["language"] = run.get("language", run["harness"])
            rows.append(r)
            if r["workload"] not in workloads:
                workloads.append(r["workload"])

    ok = [r for r in rows if r.get("mode") == "baseline" or (r.get("digest_ok") and r.get("median_ns"))]
    failed = [r for r in rows if r.get("mode") != "baseline" and not (r.get("digest_ok") and r.get("median_ns"))]

    base = {
        r["workload"]: r["median_ns"] / r["events"]
        for r in ok
        if (r["harness"], r["parser"]) == BASELINE_PARSER
    }

    L = []
    L.append("# jsonBench report\n")
    if manifest:
        L.append(
            f"Corpus `{manifest['source']}` — {manifest['events']:,} unique events, "
            f"fingerprint `{manifest['fingerprint'][:16]}`"
            + (", re-serialized canonically" if manifest.get("canonical") else ", raw bytes as received")
            + ".\n"
        )
    L.append("| harness | runtime | host | settings |")
    L.append("|---|---|---|---|")
    for run in runs:
        host = run.get("host", {})
        s = run.get("settings", {})
        stale = ""
        if manifest and run.get("corpus_fingerprint") not in (None, manifest["fingerprint"]):
            stale = " ⚠ different corpus"
        L.append(
            f"| {run['harness']} | {run.get('runtime', '')}{stale} | {host.get('cpu', '')} ×{host.get('cores', '?')} "
            f"| warmup {s.get('warmup_ms')} ms, ≥{s.get('min_time_ms')} ms, ≥{s.get('min_rounds')} rounds |"
        )
    L.append("")
    L.append(
        "MB/s is input bytes per second of median round (higher is better). Single thread. "
        "`× quartz` is speed relative to Amethyst's production parser (`jvm/quartz-jackson`, "
        "Jackson + Quartz's EventDeserializer) on the same workload. Alloc is heap bytes and "
        "allocation count per parsed event; GC is collections / total GC time during measurement.\n"
    )

    if manifest:
        L.append("## Workloads\n")
        L.append("| workload | events | MB | avg bytes | avg tags | what |")
        L.append("|---|---:|---:|---:|---:|---|")
        for w in manifest["workloads"]:
            L.append(
                f"| {w['name']} | {w['events']:,} | {w['bytes'] / 1e6:.2f} | {w['avg_bytes']} | {w['avg_tags']} | {w['description']} |"
            )
        L.append("")

    headline = "all" if "all" in workloads else (workloads[0] if workloads else None)
    for mode in ("typed", "borrowed", "dom", "baseline"):
        mrows = [r for r in ok if r.get("mode") == mode and r["workload"] == headline]
        if not mrows:
            continue
        mrows.sort(key=lambda r: -(r.get("mb_per_s") or 0))
        L.append(f"## {MODE_TITLES[mode]} — `{headline}` workload\n")
        L.append("| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |")
        L.append("|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|")
        for i, r in enumerate(mrows, 1):
            nspe = r["median_ns"] / r["events"]
            rel = base.get(r["workload"])
            L.append(
                f"| {i} | {r['language']} | `{r['parser']}` | {r.get('input', '')} | {fmt(r.get('mb_per_s'))} "
                f"| {fmt(nspe)} | {fmt(rel / nspe, 2) + '×' if rel else '–'} "
                f"| {fmt(r.get('alloc_bytes_per_event'))} | {fmt(r.get('allocs_per_event'), 1)} | {gc_cell(r)} "
                f"| {fmt(r.get('retained_bytes_per_event'))} | {r.get('license') or ''} |"
            )
        L.append("")

    # Matrix: every verified parser x workload, MB/s, plus geometric mean of × quartz.
    keyed = {}
    for r in ok:
        if r.get("mode") == "baseline":
            continue
        keyed.setdefault((r["language"], r["parser"], r.get("mode")), {})[r["workload"]] = r
    if keyed:
        L.append("## Every workload (MB/s)\n")
        L.append(
            "Geomean × quartz is the geometric mean, over the workloads both ran, of the speed relative "
            "to `quartz-jackson` — one number per parser that doesn't let the big `all` workload dominate.\n"
        )
        L.append("| language | parser | mode | " + " | ".join(workloads) + " | geomean × quartz |")
        L.append("|---|---|---|" + "---:|" * len(workloads) + "---:|")

        def score(item):
            per = item[1]
            g = geomean([base[w] / (per[w]["median_ns"] / per[w]["events"]) for w in per if w in base])
            return g or 0

        for (lang, parser, mode), per in sorted(keyed.items(), key=lambda it: -score(it)):
            cells = [fmt(per[w].get("mb_per_s")) if w in per else "" for w in workloads]
            g = score(((lang, parser, mode), per))
            L.append(f"| {lang} | `{parser}` | {mode} | " + " | ".join(cells) + f" | {fmt(g, 2) + '×' if g else '–'} |")
        L.append("")

    # The interning trade-off: Quartz's production path against its own loop with and without
    # String.intern(), per workload: what interning costs in parse time, what it saves in heap.
    trade = {}
    for r in ok:
        if r["harness"] == "jvm" and r["parser"] in ("quartz-jackson", "quartz-loop", "quartz-loop-nointern", "quartz-loop-plain", "jackson-streaming-string"):
            trade.setdefault(r["workload"], {})[r["parser"]] = r
    if any("quartz-loop" in v and "quartz-loop-nointern" in v for v in trade.values()):
        L.append("## Interning: parse cost vs. memory kept (JVM)\n")
        L.append(
            "`quartz-jackson` is the production path (databind → `EventDeserializer`, `String.intern()` on id, "
            "pubkey and every tag value, `EventFactory`). `quartz-loop` is the same loop on a bare jackson-core "
            "parser; `quartz-loop-nointern` drops the intern calls; `quartz-loop-plain` also drops `EventFactory`. "
            "*Retained* is the heap still held after parsing the whole workload and keeping every event "
            "(what a client's cache holds), so its difference is what interning saves; the ns/event difference "
            "is what it costs.\n"
        )
        L.append("| workload | quartz-jackson ns/ev | quartz-loop ns/ev | no-intern ns/ev | plain ns/ev | intern cost ns/ev | retained B/ev (intern) | retained B/ev (no intern) | intern saves B/ev | saves % |")
        L.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")

        def nspe(r):
            return r["median_ns"] / r["events"] if r else None

        for w in workloads:
            t = trade.get(w)
            if not t or "quartz-loop" not in t or "quartz-loop-nointern" not in t:
                continue
            a, b = t["quartz-loop"], t["quartz-loop-nointern"]
            ra, rb = a.get("retained_bytes_per_event"), b.get("retained_bytes_per_event")
            saves = rb - ra if ra is not None and rb is not None else None
            L.append(
                f"| {w} | {fmt(nspe(t.get('quartz-jackson')))} | {fmt(nspe(a))} | {fmt(nspe(b))} | {fmt(nspe(t.get('quartz-loop-plain')))} "
                f"| {fmt(nspe(a) - nspe(b))} | {fmt(ra)} | {fmt(rb)} | {fmt(saves)} | {fmt(100 * saves / rb, 1) + '%' if saves is not None and rb else '–'} |"
            )
        L.append("")

    if failed:
        L.append("## Failed verification (excluded from rankings)\n")
        L.append("| language | parser | workload | errors | error |")
        L.append("|---|---|---|---:|---|")
        for r in sorted(failed, key=lambda r: (r["language"], r["parser"], r["workload"])):
            err = (r.get("error") or ("digest mismatch" if not r.get("digest_ok") else "no timing")).replace("|", "\\|")
            L.append(f"| {r['language']} | `{r['parser']}` | {r['workload']} | {r.get('errors', '')} | {err[:160]} |")
        L.append("")

    libs = {}
    for r in rows:
        libs.setdefault((r["language"], r.get("library") or r["parser"]), (r.get("version"), r.get("license")))
    L.append("## Libraries\n")
    L.append("| language | library | version | license |")
    L.append("|---|---|---|---|")
    for (lang, lib), (ver, lic) in sorted(libs.items()):
        L.append(f"| {lang} | {lib} | {ver or ''} | {lic or ''} |")
    L.append("")

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w") as f:
        f.write("\n".join(L))
    print(f"wrote {out} ({len(ok)} verified rows, {len(failed)} failed)")


if __name__ == "__main__":
    main()
