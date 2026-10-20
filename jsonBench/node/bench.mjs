// jsonBench JavaScript harness: one script for Node, Bun and Deno.
// Contract: ../HARNESS.md. Run through ./run.sh.
//
// Without --child the script is the orchestrator: it re-spawns itself once per parser
// (fresh process = fresh JIT profile and heap) and merges the rows into one results file.

import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { Buffer } from 'node:buffer';
import { PerformanceObserver, performance } from 'node:perf_hooks';
import v8 from 'node:v8';

const SCRIPT = fileURLToPath(import.meta.url);
const HERE = path.dirname(SCRIPT);
const BENCH = path.resolve(HERE, '..');
const require = createRequire(import.meta.url);

// ---------------------------------------------------------------- runtime

const RT = globalThis.Bun ? 'bun' : globalThis.Deno ? 'deno' : 'node';

function runtimeInfo() {
  if (RT === 'bun') {
    return {
      label: `Bun ${Bun.version} (JavaScriptCore, WebKit ${process.versions.webkit.slice(0, 12)})`,
      engine: `JavaScriptCore (WebKit ${process.versions.webkit.slice(0, 12)}, Bun ${Bun.version})`,
      engineLicense: 'BSD-2-Clause',
    };
  }
  if (RT === 'deno') {
    return {
      label: `Deno ${Deno.version.deno} (V8 ${Deno.version.v8})`,
      engine: `V8 ${Deno.version.v8} (Deno ${Deno.version.deno})`,
      engineLicense: 'BSD-3-Clause',
    };
  }
  return {
    label: `Node.js ${process.versions.node} (V8 ${process.versions.v8})`,
    engine: `V8 ${process.versions.v8} (Node.js ${process.versions.node})`,
    engineLicense: 'BSD-3-Clause',
  };
}

function pkgMeta(name) {
  const p = JSON.parse(readFileSync(path.join(HERE, 'node_modules', name, 'package.json'), 'utf8'));
  return { version: p.version, license: p.license };
}

// ---------------------------------------------------------------- parsers
//
// Every parser starts from what a WebSocket text frame is in JS: a `string`
// (MessageEvent.data in browsers, Node 22's global WebSocket, Bun, Deno). Each parser:
// id, library, mode, input (HARNESS.md vocabulary: 'string', 'string→bytes', 'bytes'),
// notes, runtimes (where it is listed), meta() -> {version, license}, load() -> parse fn,
// and optionally frame ('buffer': the extra Node `ws` rows), fold(result),
// fields(result) (dom results), verify ('text' | 'bytes' for baselines) and
// retained: false (results not measurable on the JS heap).

const td = new TextDecoder('utf-8');
const te = new TextEncoder();
const foldEvent = (e) => e.kind + e.tags.length + e.content.length;

// string -> UTF-8 bytes for the parsers that only read bytes. encodeInto a reused scratch
// buffer is the fastest encode on all three engines (all workload, ns/event: Node 22
// 465 vs 2737 for TextEncoder.encode and 1170 for Buffer.from; Bun 111 vs 252 / 331;
// Deno 802 vs 3645 / 1254). The parser gets a view of the scratch buffer, valid until the
// next call: fine for decoders that copy every value out (jsonjoy, Bun.JSONL).
let scratch = new Uint8Array(1 << 16);
function encodeUtf8(s) {
  if (s.length * 3 > scratch.length) scratch = new Uint8Array(s.length * 3);
  return scratch.subarray(0, te.encodeInto(s, scratch).written);
}

const WS_EXTRA = 'EXTRA, not the text-frame path: the Node `ws` package delivers every message, text frames included, as a Buffer; the browser, Node 22 global, Bun and Deno WebSocket deliver a string. ';

const PARSERS = [
  {
    id: 'json-parse-string',
    library: 'JSON.parse',
    mode: 'typed',
    input: 'string',
    notes: 'JSON.parse(frame string), the web-client path; result is a plain object',
    meta: () => engineMeta(),
    load: () => (s) => JSON.parse(s),
  },
  {
    id: 'json-parse-buffer',
    library: 'Buffer.toString + JSON.parse',
    mode: 'typed',
    input: 'bytes',
    frame: 'buffer',
    notes: WS_EXTRA + 'node:buffer Buffer -> toString("utf8") -> JSON.parse; the decode is timed',
    meta: () => engineMeta(),
    load: () => (b) => JSON.parse(b.toString('utf8')),
  },
  {
    id: 'baseline-buffer-tostring',
    library: 'Buffer.toString',
    mode: 'baseline',
    input: 'bytes',
    frame: 'buffer',
    notes: WS_EXTRA + 'Buffer.toString("utf8") only (no parse): the decode json-parse-buffer pays',
    meta: () => engineMeta(),
    load: () => (b) => b.toString('utf8'),
    fold: (s) => s.length,
    verify: 'text',
  },
  {
    id: 'baseline-utf8-encodeinto',
    library: 'TextEncoder.encodeInto',
    mode: 'baseline',
    input: 'string→bytes',
    notes: 'TextEncoder.encodeInto a reused scratch Uint8Array only (no parse): the string -> bytes step every string→bytes row pays',
    meta: () => engineMeta(),
    load: () => encodeUtf8,
    fold: (b) => b.length,
    verify: 'bytes',
  },
  {
    id: 'baseline-utf8-encode',
    library: 'TextEncoder.encode',
    mode: 'baseline',
    input: 'string→bytes',
    notes: 'TextEncoder.encode (a fresh Uint8Array per frame) only, for comparison with encodeInto',
    meta: () => engineMeta(),
    load: () => (s) => te.encode(s),
    fold: (b) => b.length,
    verify: 'bytes',
  },
  {
    id: 'bun-jsonl-string',
    library: 'Bun.JSONL.parse',
    mode: 'typed',
    input: 'string',
    runtimes: ['bun'],
    notes: 'Bun-native NDJSON parser fed the frame string; returns [event]',
    meta: () => ({ version: `Bun ${Bun.version}`, license: 'MIT' }),
    load: () => (s) => Bun.JSONL.parse(s)[0],
  },
  {
    id: 'bun-jsonl-bytes',
    library: 'Bun.JSONL.parse',
    mode: 'typed',
    input: 'string→bytes',
    runtimes: ['bun'],
    notes: 'Bun.JSONL.parse(Uint8Array) after TextEncoder.encodeInto a reused scratch buffer (timed); returns [event]',
    meta: () => ({ version: `Bun ${Bun.version}`, license: 'MIT' }),
    load: () => (s) => Bun.JSONL.parse(encodeUtf8(s))[0],
  },
  {
    id: 'simdjson-parse',
    library: 'simdjson (npm, luizperes/simdjson_nodejs)',
    mode: 'typed',
    input: 'string',
    notes: 'native N-API addon bundling simdjson 0.9.1; simdjson.parse(string) is its only entry point (it copies the string out as UTF-8 itself) and builds the full JS object through node-addon-api',
    meta: () => pkgMeta('simdjson'),
    load: () => {
      const s = require('simdjson');
      return (str) => s.parse(str);
    },
  },
  {
    id: 'simdjson-lazy',
    library: 'simdjson (npm, luizperes/simdjson_nodejs)',
    mode: 'dom',
    input: 'string',
    retained: false,
    notes: 'lazyParse(string) keeps the simdjson DOM native; the timed loop reads kind and tags via valueForKeyPath (tags materializes the array: the binding has no length accessor). Retained: null, the document lives in native memory the JS heap reading does not see. Under Node 22 the process dies with SIGSEGV (reproduced with a bare lazyParse() loop, cause not traced into the 2021 addon); runs on Bun and Deno',
    meta: () => pkgMeta('simdjson'),
    load: () => {
      const s = require('simdjson');
      return (str) => s.lazyParse(str);
    },
    fold: (d) => d.valueForKeyPath('kind') + d.valueForKeyPath('tags').length,
    fields: (d) => ({
      id: d.valueForKeyPath('id'),
      pubkey: d.valueForKeyPath('pubkey'),
      created_at: d.valueForKeyPath('created_at'),
      kind: d.valueForKeyPath('kind'),
      tags: d.valueForKeyPath('tags'),
      content: d.valueForKeyPath('content'),
      sig: d.valueForKeyPath('sig'),
    }),
  },
  {
    id: 'jsonjoy-decoder',
    library: '@jsonjoy.com/json-pack JsonDecoder',
    mode: 'typed',
    input: 'string→bytes',
    notes: 'pure-JS JSON decoder that only reads a Uint8Array (json-joy): TextEncoder.encodeInto a reused scratch buffer, then decode (both timed); one decoder reused. Does not decode \\uXXXX escapes (its unescape regex only knows \\b \\f \\n \\r \\t \\" \\/ \\\\), so events with escaped emoji fail the digest',
    meta: () => pkgMeta('@jsonjoy.com/json-pack'),
    load: () => {
      const { JsonDecoder } = require('@jsonjoy.com/json-pack/lib/json/JsonDecoder.js');
      const dec = new JsonDecoder();
      return (s) => dec.decode(encodeUtf8(s));
    },
  },
  {
    id: 'streamparser-json',
    library: '@streamparser/json JSONParser',
    mode: 'typed',
    input: 'string',
    notes: 'streaming tokenizer + token parser, one parser reused with separator "" and paths ["$"]; each frame written whole through write(string), which TextEncoder.encodes it internally',
    meta: () => pkgMeta('@streamparser/json'),
    load: async () => {
      const { JSONParser } = await import('@streamparser/json');
      const p = new JSONParser({ paths: ['$'], separator: '' });
      let last;
      p.onValue = ({ value }) => {
        last = value;
      };
      p.onError = (err) => {
        throw err;
      };
      return (s) => {
        last = undefined;
        p.write(s);
        return last;
      };
    },
  },
];

function engineMeta() {
  const ri = runtimeInfo();
  return { version: ri.engine, license: ri.engineLicense };
}

function listedParsers() {
  return PARSERS.filter((p) => !p.runtimes || p.runtimes.includes(RT));
}

// ---------------------------------------------------------------- args

function parseArgs(argv) {
  const o = {
    corpus: path.join(BENCH, '.corpus'),
    out: null,
    workloads: null,
    parsers: null,
    warmupMs: 1000,
    minTimeMs: 2000,
    minRounds: 5,
    list: false,
    child: null,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const val = () => {
      if (i + 1 >= argv.length) throw new Error(`missing value for ${a}`);
      return argv[++i];
    };
    switch (a) {
      case '--corpus': o.corpus = path.resolve(val()); break;
      case '--out': o.out = path.resolve(val()); break;
      case '--workloads': o.workloads = val().split(',').filter(Boolean); break;
      case '--parsers': o.parsers = val().split(',').filter(Boolean); break;
      case '--warmup-ms': o.warmupMs = Number(val()); break;
      case '--min-time-ms': o.minTimeMs = Number(val()); break;
      case '--min-rounds': o.minRounds = Number(val()); break;
      case '--quick': o.warmupMs = 200; o.minTimeMs = 300; o.minRounds = 3; break;
      case '--list': o.list = true; break;
      case '--child': o.child = val(); break;
      default: throw new Error(`unknown argument: ${a}`);
    }
  }
  if (!o.out) o.out = path.join(BENCH, 'results', `${RT}.json`);
  return o;
}

// ---------------------------------------------------------------- digest

const US = '\x1f', RS = '\x1e', GS = '\x1d', FS = '\x1c';

function intStr(n, name) {
  if (typeof n === 'bigint') return n.toString();
  if (typeof n !== 'number' || !Number.isSafeInteger(n) || n < 0) throw new Error(`${name} is not a non-negative integer: ${n}`);
  return String(n);
}

function str(s, name) {
  if (typeof s !== 'string') throw new Error(`${name} is not a string (${typeof s})`);
  return s;
}

// The separators are ASCII, so hashing the UTF-8 of the concatenation equals hashing
// each piece's UTF-8 in turn (the digest_event order in corpus/prepare.py).
function digestText(e) {
  let s = str(e.id, 'id') + US + str(e.pubkey, 'pubkey') + US + intStr(e.created_at, 'created_at') + US +
    intStr(e.kind, 'kind') + US;
  const tags = e.tags;
  if (!Array.isArray(tags)) throw new Error('tags is not an array');
  for (const t of tags) {
    if (!Array.isArray(t)) throw new Error('tag is not an array');
    for (const v of t) s += str(v, 'tag element') + US;
    s += RS;
  }
  s += GS + str(e.content, 'content') + US + str(e.sig, 'sig') + US + FS;
  return s;
}

// ---------------------------------------------------------------- child: one parser

function loadWorkload(corpus, w) {
  const buf = readFileSync(path.join(corpus, w.file));
  const lines = [];
  let start = 0;
  for (let i = 0; i < buf.length; i++) {
    if (buf[i] === 0x0a) {
      if (i > start) lines.push(new Uint8Array(buf.subarray(start, i))); // own copy per line
      start = i + 1;
    }
  }
  if (start < buf.length) lines.push(new Uint8Array(buf.subarray(start)));
  return lines;
}

function percentile(sorted, p) {
  const k = Math.max(0, Math.ceil(p * sorted.length) - 1);
  return sorted[k];
}

function median(sorted) {
  const n = sorted.length;
  return n % 2 ? sorted[(n - 1) >> 1] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
}

const nowNs = () => Number(process.hrtime.bigint());

function allocCounter() {
  // V8 >= ~13 exposes a cumulative total_allocated_bytes (Deno 2.9 has it, Node 22's V8 12.4
  // does not). Bun's node:v8 shim reports a field of that name that does not track JS heap
  // allocation, so it is not used there.
  if (RT === 'bun') return null;
  const s = v8.getHeapStatistics();
  if (typeof s.total_allocated_bytes !== 'number') return null;
  return () => v8.getHeapStatistics().total_allocated_bytes;
}

function gcRecorder() {
  // Bun: JSC exposes no GC events or counters to JS. Deno 2.9 lists 'gc' as a supported
  // entry type but never delivers an entry (checked: 0 entries over ~70 MB of garbage).
  if (RT !== 'node') return null;
  if (!PerformanceObserver.supportedEntryTypes?.includes('gc')) return null;
  const entries = [];
  const obs = new PerformanceObserver((list) => {
    for (const e of list.getEntries()) entries.push({ start: e.startTime, duration: e.duration });
  });
  obs.observe({ entryTypes: ['gc'] });
  return {
    async collect(t0, t1) {
      await new Promise((r) => setTimeout(r, 100)); // entries are delivered asynchronously
      for (const e of obs.takeRecords?.() ?? []) entries.push({ start: e.startTime, duration: e.duration });
      const inside = entries.filter((e) => e.start >= t0 && e.start <= t1);
      entries.length = 0;
      return { count: inside.length, ms: inside.reduce((a, e) => a + e.duration, 0) };
    },
  };
}

// Retained heap (HARNESS.md "Retained heap"): a full, synchronous collection and the JS
// heap in use. Node and Deno need --expose-gc (childCommand passes it); Bun.gc(true) is
// a synchronous full JSC collection and bun:jsc heapStats().heapSize the live JSC heap.
// Both see the JS heap only: memory a native addon holds (simdjson's documents) or a
// Buffer's external backing store is invisible to them.
async function retainedProbe() {
  if (RT === 'bun') {
    const jsc = await import('bun:jsc');
    return { gc: () => Bun.gc(true), used: () => jsc.heapStats().heapSize };
  }
  if (typeof globalThis.gc !== 'function') return null;
  return { gc: () => globalThis.gc(), used: () => process.memoryUsage().heapUsed };
}

// Parses every event once more from a fresh frame (as a socket would hand it over, so a
// result that pins its input is charged for it), keeps every result alive, and returns
// the median over 3 passes of the heap growth per event. The result array is allocated
// before the first reading, so the container is excluded. Everything alive across the
// readings is touched afterwards: V8's liveness analysis lets the GC free a local that
// is never read again, which would otherwise skew the readings.
function measureRetained(probe, parse, raw, makeFrame) {
  const n = raw.length;
  const full = () => {
    for (let i = 0; i < 4; i++) probe.gc();
  };
  const vals = [];
  let touch = 0;
  for (let rep = 0; rep < 3; rep++) {
    let keep = new Array(n).fill(null);
    full();
    const before = probe.used();
    for (let i = 0; i < n; i++) keep[i] = parse(makeFrame(raw[i]));
    full();
    const after = probe.used();
    vals.push((after - before) / n);
    touch += keep.length + (keep[n - 1] ? 1 : 0) + raw.length;
    keep = null;
  }
  vals.sort((a, b) => a - b);
  return { perEvent: vals[1], touch };
}

async function runChild(opts, manifest, workloads) {
  const P = PARSERS.find((p) => p.id === opts.child);
  if (!P) throw new Error(`unknown parser ${opts.child}`);
  let meta = { version: null, license: null };
  let parse = null;
  let loadError = null;
  try {
    meta = P.meta();
    parse = await P.load();
  } catch (e) {
    loadError = `load failed: ${e?.message ?? e}`;
  }
  const fold = P.fold ?? foldEvent;
  const fields = P.fields ?? ((e) => e);
  const gcRec = gcRecorder();
  const alloc = allocCounter();
  const probe = await retainedProbe();
  const rows = [];
  let sinkAll = 0;

  for (const w of workloads) {
    const row = {
      parser: P.id,
      library: P.library,
      version: meta.version,
      license: meta.license,
      mode: P.mode,
      input: P.input,
      notes: P.notes,
      workload: w.name,
      events: w.events,
      bytes: w.bytes,
      digest_ok: false,
      errors: 0,
      error: null,
      rounds: 0,
      median_ns: null, min_ns: null, p90_ns: null,
      ns_per_event: null, mb_per_s: null, events_per_s: null,
      alloc_bytes_per_event: null, allocs_per_event: null,
      gc_count: null, gc_ms: null,
      retained_bytes_per_event: null,
    };
    rows.push(row);
    if (loadError) {
      row.error = loadError;
      continue;
    }
    const raw = loadWorkload(opts.corpus, w);
    if (raw.length !== w.events) {
      row.error = `line count ${raw.length} != manifest events ${w.events}`;
      continue;
    }
    // The frame object, prepared untimed: a JS string (TextDecoder output: a flat,
    // sequential string like the one a WebSocket hands over), or a Buffer for the
    // extra `ws` rows.
    const makeFrame = P.frame === 'buffer' ? (b) => Buffer.from(b) : (b) => td.decode(b);
    const inputs = raw.map(makeFrame);

    // 2. verify (untimed)
    const h = createHash('sha256');
    for (let i = 0; i < inputs.length; i++) {
      try {
        const r = parse(inputs[i]);
        if (P.verify === 'text') {
          // a decode baseline produces the line text: re-parse it with JSON.parse to digest it
          h.update(digestText(JSON.parse(r)), 'utf8');
        } else if (P.verify === 'bytes') {
          // an encode baseline produces the line's UTF-8: decode and parse it to digest it
          h.update(digestText(JSON.parse(td.decode(r))), 'utf8');
        } else {
          h.update(digestText(fields(r)), 'utf8');
        }
      } catch (e) {
        row.errors++;
        if (!row.error) row.error = `event ${i}: ${e?.message ?? e}`;
      }
    }
    const digest = h.digest('hex');
    row.digest_ok = row.errors === 0 && digest === w.digest;
    if (!row.digest_ok) {
      if (!row.error) row.error = `digest mismatch: ${digest} != ${w.digest}`;
      continue;
    }

    const n = inputs.length;
    const round = () => {
      let s = 0;
      for (let i = 0; i < n; i++) s += fold(parse(inputs[i]));
      return s;
    };

    // 3. warm up
    let sink = 0;
    const wEnd = nowNs() + opts.warmupMs * 1e6;
    do sink += round(); while (nowNs() < wEnd);

    // 4. measure
    const times = [];
    const t0 = performance.now();
    const a0 = alloc ? alloc() : 0;
    const mStart = nowNs();
    const mEnd = mStart + opts.minTimeMs * 1e6;
    let last = mStart;
    while (times.length < 10000) {
      sink += round();
      const t = nowNs();
      times.push(t - last);
      last = t;
      if (times.length >= opts.minRounds && t >= mEnd) break;
    }
    const a1 = alloc ? alloc() : 0;
    const t1 = performance.now();
    sinkAll += sink;

    times.sort((a, b) => a - b);
    const med = median(times);
    row.rounds = times.length;
    row.median_ns = Math.round(med);
    row.min_ns = times[0];
    row.p90_ns = percentile(times, 0.9);
    row.ns_per_event = med / w.events;
    row.mb_per_s = w.bytes / (med / 1e9) / 1e6;
    row.events_per_s = w.events / (med / 1e9);
    if (alloc) row.alloc_bytes_per_event = (a1 - a0) / (times.length * w.events);
    if (gcRec) {
      const g = await gcRec.collect(t0, t1);
      row.gc_count = g.count;
      row.gc_ms = g.ms;
    }
    // 5. retained heap (untimed), not for baselines or results held outside the JS heap
    if (probe && P.mode !== 'baseline' && P.retained !== false) {
      const r = measureRetained(probe, parse, raw, makeFrame);
      row.retained_bytes_per_event = r.perEvent;
      sink += r.touch + inputs.length;
    }
    process.stderr.write(
      `  ${RT} ${P.id.padEnd(26)} ${w.name.padEnd(10)} ${row.mb_per_s.toFixed(1).padStart(8)} MB/s ` +
        `${row.ns_per_event.toFixed(0).padStart(7)} ns/ev  rounds=${row.rounds}` +
        (row.retained_bytes_per_event == null ? '' : ` retained=${row.retained_bytes_per_event.toFixed(0)} B/ev`) +
        ` sink=${sink}\n`,
    );
  }
  process.stdout.write('@@ROWS ' + JSON.stringify(rows) + '\n');
  process.stderr.write(`  (sink ${sinkAll})\n`);
}

// ---------------------------------------------------------------- orchestrator

function childCommand(args) {
  // --expose-gc gives the child a synchronous full gc() for the retained-heap pass. It
  // does not change how V8 collects during the timed rounds.
  if (RT === 'deno') return [Deno.execPath(), ['run', '-A', '--v8-flags=--expose-gc', SCRIPT, ...args]];
  if (RT === 'bun') return [process.execPath, [SCRIPT, ...args]];
  const execArgv = process.execArgv.includes('--expose-gc') ? process.execArgv : [...process.execArgv, '--expose-gc'];
  return [process.execPath, [...execArgv, SCRIPT, ...args]];
}

function hostInfo() {
  let cpu = os.cpus()[0]?.model ?? 'unknown';
  try {
    const m = readFileSync('/proc/cpuinfo', 'utf8').match(/^model name\s*:\s*(.+)$/m);
    if (m) cpu = m[1].trim();
  } catch {}
  return { cpu, cores: os.cpus().length, os: `${os.type()} ${os.release()}` };
}

async function main() {
  const opts = parseArgs(RT === 'deno' ? Deno.args : process.argv.slice(2));
  if (opts.list) {
    for (const p of listedParsers()) console.log(p.id);
    return;
  }
  const manifestPath = path.join(opts.corpus, 'manifest.json');
  if (!existsSync(manifestPath)) throw new Error(`no corpus at ${opts.corpus}: run python3 jsonBench/corpus/prepare.py`);
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  let workloads = manifest.workloads;
  if (opts.workloads) {
    for (const n of opts.workloads) if (!workloads.some((w) => w.name === n)) throw new Error(`unknown workload ${n}`);
    workloads = workloads.filter((w) => opts.workloads.includes(w.name));
  }

  if (opts.child) return runChild(opts, manifest, workloads);

  let parsers = listedParsers();
  if (opts.parsers) {
    for (const n of opts.parsers) if (!parsers.some((p) => p.id === n)) throw new Error(`unknown parser ${n} for ${RT}`);
    parsers = parsers.filter((p) => opts.parsers.includes(p.id));
  }

  const passArgs = [
    '--corpus', opts.corpus,
    '--workloads', workloads.map((w) => w.name).join(','),
    '--warmup-ms', String(opts.warmupMs),
    '--min-time-ms', String(opts.minTimeMs),
    '--min-rounds', String(opts.minRounds),
  ];
  const results = [];
  for (const p of parsers) {
    process.stderr.write(`[${RT}] ${p.id}\n`);
    const [cmd, args] = childCommand([...passArgs, '--child', p.id]);
    const r = spawnSync(cmd, args, { cwd: HERE, stdio: ['ignore', 'pipe', 'inherit'], maxBuffer: 64 << 20, encoding: 'utf8' });
    const line = (r.stdout ?? '').split('\n').find((l) => l.startsWith('@@ROWS '));
    if (r.status === 0 && line) {
      results.push(...JSON.parse(line.slice(7)));
    } else {
      const why = `child exited with ${r.status ?? r.signal}${r.error ? ': ' + r.error.message : ''}`;
      process.stderr.write(`  ${why}\n`);
      for (const w of workloads) {
        results.push({
          parser: p.id, library: p.library, version: null, license: null, mode: p.mode, input: p.input,
          notes: p.notes, workload: w.name, events: w.events, bytes: w.bytes, digest_ok: false, errors: 0,
          error: why, rounds: 0, median_ns: null, min_ns: null, p90_ns: null, ns_per_event: null, mb_per_s: null,
          events_per_s: null, alloc_bytes_per_event: null, allocs_per_event: null, gc_count: null, gc_ms: null,
          retained_bytes_per_event: null,
        });
      }
    }
  }

  const doc = {
    harness: RT,
    language: 'JavaScript',
    runtime: runtimeInfo().label,
    host: hostInfo(),
    settings: { warmup_ms: opts.warmupMs, min_time_ms: opts.minTimeMs, min_rounds: opts.minRounds },
    corpus_fingerprint: manifest.fingerprint,
    results,
  };
  mkdirSync(path.dirname(opts.out), { recursive: true });
  writeFileSync(opts.out, JSON.stringify(doc, null, 2) + '\n');
  process.stderr.write(`wrote ${opts.out}\n`);
}

await main();
