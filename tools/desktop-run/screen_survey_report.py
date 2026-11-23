#!/usr/bin/env python3
"""
Tabulates a screen-survey.sh report folder: per screen, the warm passes' frame rate and UI work, the
images it loaded, whether its content moved (no screen to scroll for the login otherwise), and, when
the boot was profiled, the share of the UI thread's CPU time in Skia's rasterizing, composition,
layout, recording the draw and input, then each screen's heaviest app frames.

    python3 tools/desktop-run/screen_survey_report.py <survey-dir>

The "moved" column needs Pillow (pip install pillow); profiles need JDK 25's `jfr` on PATH.
"""
import collections
import csv
import glob
import json
import os
import shutil
import subprocess
import sys

try:
    from PIL import Image, ImageChops
except ImportError:
    Image = None


def summary(screen_dir):
    """The median of each pass across the screen's boots."""
    passes = collections.defaultdict(list)
    for path in sorted(glob.glob(os.path.join(screen_dir, "run-*", "summary.tsv"))):
        with open(path) as f:
            lines = [line for line in f if not line.startswith("#")]
        for row in csv.DictReader(lines, delimiter="\t"):
            passes[row["pass"]].append(row)

    def median(name, column):
        values = sorted(float(r.get(column, 0) or 0) for r in passes.get(name, []))
        return values[len(values) // 2] if values else float("nan")

    return (passes, median) if passes else (None, None)


def moved(screen_dir):
    """The share of the centre pane that differs between the screenshots after `down` and `up`."""
    if Image is None:
        return None
    a = os.path.join(screen_dir, "run-1", "after-down.png")
    b = os.path.join(screen_dir, "run-1", "after-up.png")
    if not (os.path.exists(a) and os.path.exists(b)):
        return None
    first, second = Image.open(a).convert("RGB"), Image.open(b).convert("RGB")
    w, h = first.size
    box = (int(w * 0.25), int(h * 0.1), int(w * 0.72), h)
    diff = ImageChops.difference(first.crop(box), second.crop(box)).convert("L").point(lambda v: 255 if v > 24 else 0)
    hist = diff.histogram()
    return hist[255] / max(1, sum(hist))


def category(frames):
    stack = " ".join(frames)
    if "_nDrawPicture" in stack or "finishFrame" in stack or "_nClear" in stack:
        return "raster"
    if "ComposerImpl" in stack or "CompositionImpl" in stack or "Recomposer" in stack or "GapComposer" in stack:
        return "compose"
    if "MeasureAndLayoutDelegate" in stack or "remeasure" in stack or "MeasurePassDelegate" in stack:
        return "layout"
    if "NodeCoordinator.draw" in stack or "LayoutNode.draw" in stack or "PictureRecorder" in stack or "drawLayer" in stack:
        return "record"
    if "MouseWheel" in stack or "onMouseWheelEvent" in stack or "processPointerInput" in stack:
        return "input"
    return "other"


def profile(screen_dir):
    """UI-thread samples: per category, the deepest app frames, and the leaf frames."""
    recording = os.path.join(screen_dir, "warm.jfr")
    jfr = shutil.which("jfr")
    if not os.path.exists(recording) or jfr is None:
        return None
    printed = subprocess.run([jfr, "print", "--json", "--stack-depth", "200", "--events", "jdk.CPUTimeSample", recording], capture_output=True, text=True).stdout
    try:
        events = json.loads(printed)["recording"]["events"]
    except (ValueError, KeyError):
        return None
    stacks = []
    for event in events:
        values = event["values"]
        thread = values.get("sampledThread") or values.get("eventThread") or {}
        if "AWT-EventQueue" not in (thread.get("javaName") or ""):
            continue
        trace = values["stackTrace"]
        stacks.append([f["method"]["type"]["name"].replace("/", ".") + "." + f["method"]["name"] for f in trace["frames"]] if trace else [])
    categories = collections.Counter(category(s) for s in stacks)
    deepest = collections.Counter()
    for frames in stacks:
        for frame in frames:
            if frame.startswith("com.vitorpamplona.") and ".bench." not in frame:
                deepest[frame.replace("com.vitorpamplona.amethyst.", "")] += 1
                break
    leaves = collections.Counter(s[0] for s in stacks if s)
    return len(stacks), categories, deepest, leaves


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    root = sys.argv[1]
    rows = []
    for screen_dir in sorted(d for d in glob.glob(os.path.join(root, "*")) if os.path.isdir(d)):
        passes, median = summary(screen_dir)
        rows.append((os.path.basename(screen_dir), passes, median, moved(screen_dir), profile(screen_dir)))

    kinds = ["raster", "compose", "layout", "record", "input", "other"]
    print(f"{'screen':22} {'up':>5} {'warm':>5} {'busy':>5} {'jank%':>6} {'net':>4} {'mem%':>5} {'moved':>6} | " + " ".join(f"{k:>7}" for k in kinds))
    for name, passes, median, moved_share, prof in rows:
        if passes is None:
            print(f"{name:22} no result")
            continue
        net = sum(int(median(p, "img_net")) for p in passes)
        moved_text = f"{moved_share:.3f}" if moved_share is not None else "-"
        line = (
            f"{name:22} {median('up', 'fps'):5.1f} {median('down-again', 'fps'):5.1f} {median('down-again', 'busy_p50'):5.1f}"
            f" {median('down-again', 'jank_pct'):6.1f} {net:4d} {median('down-again', 'img_mem_pct'):5.1f} {moved_text:>6} |"
        )
        if prof:
            samples, categories = prof[0], prof[1]
            line += " ".join(f"{100 * categories[k] / max(1, samples):6.0f}%" for k in kinds)
        print(line)
    print("\nwarm = the down-again pass; busy = its median UI work per frame, ms; moved near 0 = nothing scrolled.")

    for name, _, _, _, prof in rows:
        if not prof:
            continue
        samples, _, deepest, leaves = prof
        print(f"\n== {name}: deepest app frames (share of UI-thread CPU)")
        for frame, count in deepest.most_common(8):
            print(f"   {100 * count / samples:5.1f}%  {frame[:130]}")
        print("   leaf frames: " + "; ".join(f"{k.split('.')[-2]}.{k.split('.')[-1]} {100 * v / samples:.0f}%" for k, v in leaves.most_common(5)))


if __name__ == "__main__":
    main()
