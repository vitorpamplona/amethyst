//! jsonBench Rust harness. Follows jsonBench/HARNESS.md: verify each parser against the
//! manifest digest, then time full rounds over each workload and count allocations.

mod alloc;
mod dom;
mod model;
mod parsers;

use parsers::{Entry, Line};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

#[global_allocator]
static GLOBAL: alloc::Counting = alloc::Counting;

mod crates {
    include!(concat!(env!("OUT_DIR"), "/crates.rs"));
}

const PADDING: usize = 64; // simdjson's SIMDJSON_PADDING
const MAX_ROUNDS: usize = 10_000;

struct Args {
    corpus: PathBuf,
    out: PathBuf,
    workloads: Option<Vec<String>>,
    parsers: Option<Vec<String>>,
    warmup_ms: u64,
    min_time_ms: u64,
    min_rounds: usize,
    list: bool,
}

fn usage() -> ! {
    eprintln!(
        "usage: jsonbench-rust [--corpus DIR] [--out FILE] [--workloads a,b] [--parsers x,y]\n\
         \x20                     [--warmup-ms N] [--min-time-ms N] [--min-rounds N] [--quick] [--list]"
    );
    std::process::exit(2)
}

fn parse_args() -> Args {
    let mut a = Args {
        corpus: PathBuf::from("jsonBench/.corpus"),
        out: PathBuf::from("jsonBench/results/rust.json"),
        workloads: None,
        parsers: None,
        warmup_ms: 1000,
        min_time_ms: 2000,
        min_rounds: 5,
        list: false,
    };
    let mut it = std::env::args().skip(1);
    let csv = |s: String| {
        s.split(',')
            .map(str::trim)
            .filter(|x| !x.is_empty())
            .map(String::from)
            .collect()
    };
    while let Some(flag) = it.next() {
        let mut val = || it.next().unwrap_or_else(|| usage());
        match flag.as_str() {
            "--corpus" => a.corpus = val().into(),
            "--out" => a.out = val().into(),
            "--workloads" => a.workloads = Some(csv(val())),
            "--parsers" => a.parsers = Some(csv(val())),
            "--warmup-ms" => a.warmup_ms = val().parse().unwrap_or_else(|_| usage()),
            "--min-time-ms" => a.min_time_ms = val().parse().unwrap_or_else(|_| usage()),
            "--min-rounds" => a.min_rounds = val().parse().unwrap_or_else(|_| usage()),
            "--quick" => {
                a.warmup_ms = 200;
                a.min_time_ms = 300;
                a.min_rounds = 3;
            }
            "--list" => a.list = true,
            "-h" | "--help" => usage(),
            other => {
                eprintln!("unknown flag {other}");
                usage()
            }
        }
    }
    a
}

/// Loads a workload: every line UTF-8 checked, stored with 64 zero bytes after it, leaked so
/// parsers can borrow it for `'static` (a few tens of MB for the whole run).
fn load_lines(path: &Path) -> Vec<Line> {
    let data = std::fs::read(path).unwrap_or_else(|err| panic!("read {}: {err}", path.display()));
    let mut lines = Vec::new();
    for raw in data.split(|&c| c == b'\n') {
        if raw.is_empty() {
            continue;
        }
        std::str::from_utf8(raw).unwrap_or_else(|err| panic!("{}: invalid UTF-8: {err}", path.display()));
        let mut v = Vec::with_capacity(raw.len() + PADDING);
        v.extend_from_slice(raw);
        v.resize(raw.len() + PADDING, 0);
        let padded: &'static [u8] = Box::leak(v.into_boxed_slice());
        lines.push(Line {
            b: &padded[..raw.len()],
        });
    }
    lines
}

fn crate_info(name: &str) -> (String, String) {
    if name == "std" {
        let v = env!("JSONBENCH_RUSTC").trim_start_matches("rustc ");
        return (
            v.split_whitespace().next().unwrap_or(v).to_string(),
            "MIT OR Apache-2.0".into(),
        );
    }
    crates::CRATES
        .iter()
        .find(|(n, _, _)| *n == name)
        .map(|(_, v, l)| (v.to_string(), l.to_string()))
        .unwrap_or_else(|| ("unknown".into(), "unknown".into()))
}

fn host() -> Value {
    let cpu = std::fs::read_to_string("/proc/cpuinfo")
        .ok()
        .and_then(|s| {
            s.lines()
                .find(|l| l.starts_with("model name"))
                .map(|l| l.split(':').nth(1).unwrap_or("").trim().to_string())
        })
        .unwrap_or_else(|| std::env::consts::ARCH.to_string());
    let cores = std::thread::available_parallelism().map(|n| n.get()).unwrap_or(1);
    let release = std::fs::read_to_string("/proc/sys/kernel/osrelease")
        .map(|s| s.trim().to_string())
        .unwrap_or_default();
    let os = format!(
        "{} {}",
        if std::env::consts::OS == "linux" {
            "Linux"
        } else {
            std::env::consts::OS
        },
        release
    );
    json!({ "cpu": cpu, "cores": cores, "os": os.trim() })
}

struct Verify {
    ok: bool,
    errors: usize,
    error: Option<String>,
}

fn verify(p: &mut dyn parsers::Parser, lines: &[Line], expected: &str) -> Verify {
    let mut h = Sha256::new();
    let mut errors = 0;
    let mut first: Option<String> = None;
    for (i, &l) in lines.iter().enumerate() {
        if let Err(err) = p.digest(l, &mut h) {
            errors += 1;
            if first.is_none() {
                first = Some(format!("line {}: {err}", i + 1));
            }
        }
    }
    let got: String = h.finalize().iter().map(|b| format!("{b:02x}")).collect();
    let ok = errors == 0 && got == expected;
    let error = first.or_else(|| (!ok).then(|| format!("digest mismatch: got {got}")));
    Verify { ok, errors, error }
}

struct Timing {
    rounds: Vec<u64>,
    alloc_bytes: u64,
    allocs: u64,
}

fn measure(p: &mut dyn parsers::Parser, lines: &[Line], a: &Args) -> Timing {
    let mut sink = 0u64;
    let warm = Duration::from_millis(a.warmup_ms);
    let start = Instant::now();
    while start.elapsed() < warm {
        sink = sink.wrapping_add(p.round(lines));
    }
    let min_time = Duration::from_millis(a.min_time_ms);
    let mut rounds = Vec::new();
    let (b0, c0) = alloc::snapshot();
    let start = Instant::now();
    while rounds.len() < MAX_ROUNDS && (rounds.len() < a.min_rounds || start.elapsed() < min_time) {
        let t = Instant::now();
        sink = sink.wrapping_add(p.round(lines));
        rounds.push(t.elapsed().as_nanos() as u64);
    }
    let (b1, c1) = alloc::snapshot();
    std::hint::black_box(sink);
    Timing {
        rounds,
        alloc_bytes: b1 - b0,
        allocs: c1 - c0,
    }
}

fn main() {
    let a = parse_args();
    let entries: Vec<Entry> = parsers::all();
    if a.list {
        for e in &entries {
            println!("{}", e.meta.id);
        }
        return;
    }
    if let Some(sel) = &a.parsers {
        for id in sel {
            if !entries.iter().any(|e| e.meta.id == id) {
                eprintln!("unknown parser {id} (see --list)");
                std::process::exit(2);
            }
        }
    }

    let manifest_path = a.corpus.join("manifest.json");
    let manifest: Value = serde_json::from_slice(&std::fs::read(&manifest_path).unwrap_or_else(|err| {
        panic!(
            "{}: {err} (build it with python3 jsonBench/corpus/prepare.py)",
            manifest_path.display()
        )
    }))
    .expect("manifest.json");
    let workloads: Vec<&Value> = manifest["workloads"]
        .as_array()
        .expect("workloads")
        .iter()
        .filter(|w| {
            a.workloads
                .as_ref()
                .is_none_or(|sel| sel.iter().any(|s| w["name"] == s.as_str()))
        })
        .collect();
    if let Some(sel) = &a.workloads {
        for s in sel {
            if !workloads.iter().any(|w| w["name"] == s.as_str()) {
                eprintln!("unknown workload {s}");
                std::process::exit(2);
            }
        }
    }

    let flags = env!("JSONBENCH_RUSTFLAGS");
    let runtime = format!(
        "{}, {}, {} (lto=fat, codegen-units=1)",
        env!("JSONBENCH_RUSTC"),
        if flags.is_empty() { "default RUSTFLAGS" } else { flags },
        env!("JSONBENCH_PROFILE")
    );
    let mut doc = json!({
        "harness": "rust",
        "language": "Rust",
        "runtime": runtime,
        "host": host(),
        "settings": { "warmup_ms": a.warmup_ms, "min_time_ms": a.min_time_ms, "min_rounds": a.min_rounds },
        "corpus_fingerprint": manifest["fingerprint"],
        "results": [],
    });

    for w in workloads {
        let name = w["name"].as_str().unwrap();
        let lines = load_lines(&a.corpus.join(w["file"].as_str().unwrap()));
        let events = lines.len();
        let bytes: usize = lines.iter().map(|l| l.b.len()).sum();
        if events as u64 != w["events"].as_u64().unwrap() || bytes as u64 != w["bytes"].as_u64().unwrap() {
            panic!("{name}: corpus does not match manifest ({events} events, {bytes} bytes)");
        }
        let expected = w["digest"].as_str().unwrap();
        for entry in &entries {
            let m = &entry.meta;
            if a.parsers.as_ref().is_some_and(|sel| !sel.iter().any(|s| s == m.id)) {
                continue;
            }
            let mut p = (entry.make)();
            let baseline = m.mode == "baseline";
            let v = if baseline {
                Verify {
                    ok: true,
                    errors: 0,
                    error: None,
                }
            } else {
                verify(p.as_mut(), &lines, expected)
            };
            let (version, license) = crate_info(m.library);
            let mut row = json!({
                "parser": m.id, "library": m.library, "version": version, "license": license,
                "mode": m.mode, "input": m.input, "notes": m.notes,
                "workload": name, "events": events, "bytes": bytes,
                "digest_ok": if baseline { Value::Null } else { Value::Bool(v.ok) },
                "errors": v.errors, "error": v.error,
                "rounds": 0, "median_ns": null, "min_ns": null, "p90_ns": null,
                "ns_per_event": null, "mb_per_s": null, "events_per_s": null,
                "alloc_bytes_per_event": null, "allocs_per_event": null,
                "gc_count": null, "gc_ms": null,
            });
            if v.ok {
                let t = measure(p.as_mut(), &lines, &a);
                let mut r = t.rounds.clone();
                r.sort_unstable();
                let n = r.len();
                let median = if n % 2 == 1 {
                    r[n / 2] as f64
                } else {
                    (r[n / 2 - 1] + r[n / 2]) as f64 / 2.0
                };
                let p90 = r[((n as f64 * 0.9).ceil() as usize).clamp(1, n) - 1];
                let parsed = (events * n) as f64;
                let o = row.as_object_mut().unwrap();
                o.insert("rounds".into(), json!(n));
                o.insert("median_ns".into(), json!(median.round() as u64));
                o.insert("min_ns".into(), json!(r[0]));
                o.insert("p90_ns".into(), json!(p90));
                o.insert("ns_per_event".into(), json!(round2(median / events as f64)));
                o.insert("mb_per_s".into(), json!(round2(bytes as f64 / (median / 1e9) / 1e6)));
                o.insert(
                    "events_per_s".into(),
                    json!((events as f64 / (median / 1e9)).round() as u64),
                );
                o.insert(
                    "alloc_bytes_per_event".into(),
                    json!(round2(t.alloc_bytes as f64 / parsed)),
                );
                o.insert("allocs_per_event".into(), json!(round2(t.allocs as f64 / parsed)));
                eprintln!(
                    "{name:<10} {:<28} {:>9.1} MB/s {:>8.1} ns/ev {:>8.1} B/ev {:>6.2} allocs/ev  ({n} rounds)",
                    m.id,
                    bytes as f64 / (median / 1e9) / 1e6,
                    median / events as f64,
                    t.alloc_bytes as f64 / parsed,
                    t.allocs as f64 / parsed
                );
            } else {
                eprintln!(
                    "{name:<10} {:<28} DIGEST FAILED: {}",
                    m.id,
                    v.error.as_deref().unwrap_or("")
                );
            }
            doc["results"].as_array_mut().unwrap().push(row);
            write_out(&a.out, &doc);
        }
    }
    write_out(&a.out, &doc);
    eprintln!("wrote {}", a.out.display());
}

fn round2(x: f64) -> f64 {
    (x * 100.0).round() / 100.0
}

fn write_out(path: &Path, doc: &Value) {
    if let Some(dir) = path.parent().filter(|d| !d.as_os_str().is_empty()) {
        std::fs::create_dir_all(dir).unwrap_or_else(|err| panic!("mkdir {}: {err}", dir.display()));
    }
    let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec_pretty(doc).unwrap())
        .unwrap_or_else(|err| panic!("write {}: {err}", tmp.display()));
    std::fs::rename(&tmp, path).unwrap_or_else(|err| panic!("rename {}: {err}", path.display()));
}
