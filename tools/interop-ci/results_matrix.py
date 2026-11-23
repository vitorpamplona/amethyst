#!/usr/bin/env python3
"""Interop results -> feature x counterpart x direction matrix (markdown) and JUnit XML.

Reads the per-scenario JSON lines cli/tests/lib.sh writes (one file per suite,
<results>/<channel>/<suite>.jsonl, with <suite>.meta.json beside it from
tools/interop-ci/run-suite.sh) and the suite registry, tools/interop-ci/suites.json.

    results_matrix.py --results build/interop-ci [--registry tools/interop-ci/suites.json]
                      [--markdown matrix.md] [--junit-dir junit/] [--channel pinned]

The markdown has, per channel: an overview (one row per suite), the matrix (one row
per feature, one column per counterpart, each cell listing the directions tested
with their outcome), the failures with their notes, and the suites that did not run.

A scenario's feature and direction come from, in order: the record itself
(RESULT_FEATURE / RESULT_DIRECTION in the harness), the first matching rule in the
suite's `rules`, and then for the feature the harness section (its last banner) and
for the direction what the id says ("amy->wn", "armada-to-amy", "A<->B", "amy-reads-…"),
then the suite's `default_direction`.

Exit status is 0 unless an input cannot be read; pass/fail is the workflow's call.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import OrderedDict
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

CHANNELS = ("pinned", "latest")
DIR_LABELS = OrderedDict(
    [("amy→cp", "amy→"), ("cp→amy", "→amy"), ("both", "↔"), ("amy", "amy only"), ("", "—")]
)
ICON = {"pass": "✅", "fail": "❌", "gap": "⚠️", "skip": "➖"}

ARROW = re.compile(r"<->|↔|->|→|[A-Za-z0-9_]+")
CONSUMER_VERBS = {
    "reads", "sees", "accepts", "joins", "gets", "follows", "judges", "honors", "decrypts",
    "receives", "opens", "folds", "processes", "discovers", "read", "receive",
}


def load_registry(path: Path) -> OrderedDict:
    data = json.loads(path.read_text(encoding="utf-8"))
    return OrderedDict((s["id"], s) for s in data["suites"])


def infer_direction(test_id: str, parties: dict) -> str:
    """What the id itself says about who produced and who consumed."""
    if not parties:
        return ""
    tokens = ARROW.findall(test_id)
    lowered = [t.lower() for t in tokens]
    alias = {k.lower(): v for k, v in parties.items()}

    def party(tok: str) -> str | None:
        return alias.get(tok)

    for i, tok in enumerate(lowered):
        if tok in ("<->", "↔"):
            return "both"
        if tok in ("->", "→", "to"):
            before = [party(t) for t in lowered[:i] if party(t)]
            after = [party(t) for t in lowered[i + 1:] if party(t)]
            if before and after:
                src, dst = before[-1], after[0]
                if src != dst:
                    return f"{src}→{dst}"
                return "amy" if src == "amy" else ""
    named = [(i, party(t)) for i, t in enumerate(lowered) if party(t)]
    if not named:
        return ""
    for i, who in named:
        if i + 1 < len(lowered) and lowered[i + 1] in CONSUMER_VERBS:
            return "cp→amy" if who == "amy" else "amy→cp"
    first = named[0][1]
    return "amy→cp" if first == "amy" else "cp→amy"


def section_feature(section: str) -> str:
    s = re.sub(r"^\s*(Test\s+)?[0-9A-Za-z]{1,3}[.)]?\s+(—|-)?\s*", "", section or "")
    return s.strip() or ""


def classify(rec: dict, suite: dict) -> tuple[str, str]:
    feature = rec.get("feature") or ""
    direction = rec.get("direction") or ""
    rule_dir = None
    for pattern, rule_feature, rdir in suite.get("rules", []):
        if re.search(pattern, rec.get("id", "")):
            feature = feature or rule_feature
            rule_dir = rdir
            break
    if not feature:
        feature = section_feature(rec.get("section", "")) or rec.get("id", "?")
    if not direction:
        direction = rule_dir or infer_direction(rec.get("id", ""), suite.get("parties", {})) \
            or (suite.get("default_direction") or "")
    return feature, direction


def worst(statuses: list[str]) -> str:
    for s in ("fail", "gap", "pass", "skip"):
        if s in statuses:
            return s
    return "skip"


def read_results(root: Path, registry: OrderedDict) -> dict:
    """{channel: {suite: {"meta": {...}, "records": [...]}}}"""
    runs: dict = {c: OrderedDict() for c in CHANNELS}
    found = [(c, f) for c in CHANNELS for f in sorted((root / c).glob("*.jsonl"))]
    for channel, jsonl in found:
        suite_id = jsonl.stem
        meta_path = jsonl.with_suffix(".meta.json")
        meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else {}
        records = []
        for n, line in enumerate(jsonl.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError as e:
                records.append({"id": f"unparseable line {n}", "status": "fail", "note": str(e)})
        suite = registry.get(suite_id, {"id": suite_id, "name": suite_id, "counterpart": suite_id})
        for rec in records:
            rec["_feature"], rec["_direction"] = classify(rec, suite)
        runs[channel][suite_id] = {"meta": meta, "records": records, "suite": suite}
    return runs


def counts(records: list[dict]) -> dict:
    c = {"pass": 0, "fail": 0, "gap": 0, "skip": 0}
    for r in records:
        st = r.get("status", "skip")
        c[st if st in c else "skip"] += 1
    return c


def md_cell(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ")


def render_channel(channel: str, suites: OrderedDict) -> list[str]:
    out: list[str] = []
    title = "Pinned counterparts (blocking)" if channel == "pinned" else "Latest counterparts (early warning, non-blocking)"
    out += [f"## {title}", ""]
    out += ["| Suite | Counterpart | Version | Pass | Fail | Gap | Skip | Time |", "|---|---|---|---:|---:|---:|---:|---:|"]
    for sid, run in suites.items():
        c = counts(run["records"])
        meta, suite = run["meta"], run["suite"]
        if not run["records"]:
            icon = "❔"  # still running, or died before the run step filed anything
        else:
            icon = ICON["fail"] if c["fail"] else ICON["pass"]
        secs = meta.get("seconds")
        out.append(
            f"| {icon} {md_cell(suite.get('name', sid))} | {md_cell(suite.get('counterpart', ''))} | "
            f"{md_cell(meta.get('version', '?'))} | {c['pass']} | {c['fail']} | {c['gap']} | {c['skip']} | "
            f"{'' if secs is None else f'{secs // 60}m{secs % 60:02d}s'} |"
        )
    out.append("")

    # feature x counterpart, directions inside the cell
    columns = [(sid, run["suite"].get("counterpart", sid)) for sid, run in suites.items()]
    cells: "OrderedDict[str, dict]" = OrderedDict()
    for sid, run in suites.items():
        for rec in run["records"]:
            cells.setdefault(rec["_feature"], {}).setdefault(sid, {}).setdefault(rec["_direction"], []).append(rec.get("status", "skip"))
    if cells:
        out += ["### Feature × counterpart × direction", ""]
        out.append("Each cell: direction (amy→ = amy sends, the counterpart receives; →amy = the reverse; "
                   "↔ = both in one scenario), then the worst outcome and the scenario count. "
                   f"{ICON['pass']} pass · {ICON['fail']} fail · {ICON['gap']} known gap · {ICON['skip']} skipped.")
        out.append("")
        out.append("| Feature | " + " | ".join(md_cell(name) for _, name in columns) + " |")
        out.append("|---|" + "---|" * len(columns))
        for feature, per_suite in cells.items():
            row = [md_cell(feature)]
            for sid, _ in columns:
                dirs = per_suite.get(sid)
                if not dirs:
                    row.append("")
                    continue
                parts = []
                for key, label in DIR_LABELS.items():
                    if key in dirs:
                        st = dirs[key]
                        ok = sum(1 for s in st if s == "pass")
                        parts.append(f"{label} {ICON[worst(st)]} {ok}/{len(st)}")
                row.append("<br>".join(parts))
            out.append("| " + " | ".join(row) + " |")
        out.append("")

    failures = [(sid, r) for sid, run in suites.items() for r in run["records"] if r.get("status") == "fail"]
    if failures:
        out += ["### Failures", "", "| Suite | Scenario | Direction | Note |", "|---|---|---|---|"]
        for sid, r in failures:
            note = (r.get("note") or "")[:300]
            out.append(f"| {sid} | {md_cell(r.get('id', ''))} | {DIR_LABELS.get(r['_direction'], r['_direction'])} | {md_cell(note)} |")
        out.append("")
    gaps = [(sid, r) for sid, run in suites.items() for r in run["records"] if r.get("status") == "gap"]
    if gaps:
        out += ["<details><summary>Known gaps (" + str(len(gaps)) + ")</summary>", ""]
        for sid, r in gaps:
            out.append(f"- **{sid}** `{r.get('id', '')}`: {md_cell((r.get('note') or '')[:300])}")
        out += ["", "</details>", ""]
    return out


def render(runs: dict, registry: OrderedDict) -> str:
    out = ["# Interop nightly", ""]
    if not any(runs[c] for c in CHANNELS):
        out += ["No results were found.", ""]
    for channel in CHANNELS:
        if runs[channel]:
            out += render_channel(channel, runs[channel])
    not_run = [s for sid, s in registry.items() if not s.get("enabled", True)]
    if not_run:
        out += ["## Not run", "", "| Suite | Counterpart | Why |", "|---|---|---|"]
        for s in not_run:
            out.append(f"| {md_cell(s.get('name', s['id']))} | {md_cell(s.get('counterpart', ''))} | {md_cell(s.get('reason', 'disabled'))} |")
        out.append("")
    return "\n".join(out)


def write_junit(runs: dict, junit_dir: Path) -> None:
    junit_dir.mkdir(parents=True, exist_ok=True)
    for channel, suites in runs.items():
        for sid, run in suites.items():
            recs = run["records"]
            c = counts(recs)
            name = f"interop.{channel}.{sid}"
            total_time = sum(int(r.get("elapsed_s") or 0) for r in recs)
            lines = ['<?xml version="1.0" encoding="UTF-8"?>',
                     f'<testsuite name={quoteattr(name)} tests="{len(recs)}" failures="{c["fail"]}" '
                     f'skipped="{c["skip"] + c["gap"]}" errors="0" time="{total_time}">']
            meta = run["meta"]
            if meta:
                lines.append("  <properties>")
                for k in ("counterpart", "version", "ref", "isolation", "exit"):
                    if k in meta:
                        lines.append(f"    <property name={quoteattr(k)} value={quoteattr(str(meta[k]))}/>")
                lines.append("  </properties>")
            for r in recs:
                classname = f"{sid}.{r['_feature']}"
                direction = DIR_LABELS.get(r["_direction"], r["_direction"])
                tname = r.get("id", "?") + (f" [{direction}]" if r["_direction"] else "")
                lines.append(f'  <testcase classname={quoteattr(classname)} name={quoteattr(tname)} time="{int(r.get("elapsed_s") or 0)}">')
                note = r.get("note") or ""
                st = r.get("status")
                if st == "fail":
                    lines.append(f"    <failure message={quoteattr(note[:500])}>{escape(note)}</failure>")
                elif st in ("skip", "gap"):
                    lines.append(f"    <skipped message={quoteattr((('GAP: ' if st == 'gap' else '') + note)[:500])}/>")
                lines.append("  </testcase>")
            lines.append("</testsuite>")
            (junit_dir / f"TEST-{name}.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    here = Path(__file__).resolve().parent
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--results", required=True, type=Path,
                    help="directory holding pinned/<suite>.jsonl and latest/<suite>.jsonl")
    ap.add_argument("--registry", type=Path, default=here / "suites.json")
    ap.add_argument("--markdown", type=Path, help="write the matrix here (default: stdout)")
    ap.add_argument("--junit-dir", type=Path, help="write one JUnit XML file per suite and channel here")
    ap.add_argument("--channel", choices=CHANNELS, help="only this channel")
    args = ap.parse_args()

    registry = load_registry(args.registry)
    if not args.results.is_dir():
        print(f"no results directory {args.results}", file=sys.stderr)
        return 2
    runs = read_results(args.results, registry)
    if args.channel:
        runs = {c: (runs[c] if c == args.channel else OrderedDict()) for c in CHANNELS}
    md = render(runs, registry)
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(md + "\n", encoding="utf-8")
    else:
        print(md)
    if args.junit_dir:
        write_junit(runs, args.junit_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
