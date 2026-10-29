// V8 baseline for the Kotlin runtimes: the engine a WebView napplet gets.
// Same imports, virtual clock, workload and frame hashes as src/commonMain/kotlin/bench.
//   node tools/napplet-doom/bench/bench-node.mjs [TICS] [IWAD]
// DUMP=dir also writes each checkpoint's frame as a PPM image there.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const built = new URL('../build/', import.meta.url);
const wasm = readFileSync(new URL('doom.wasm', built));
const wadPath = process.argv[3] || new URL('freedoom-0.13.0/freedoom1.wad', built).pathname;
const wadName = wadPath.split('/').pop().toLowerCase();
const wad = readFileSync(wadPath);
const TICS = Number(process.argv[2] || 2100);
const CHECKPOINTS = [175, 350, 700, 1050, 1400, 2100, 3500];
const BUDGET = 1e9 / 35;
const WARMUP = 350;

let memory;
const view = () => new DataView(memory.buffer);
const heap = () => new Uint8Array(memory.buffer);
const text = (p, n) => new TextDecoder().decode(heap().subarray(p, p + n));

// ---- virtual clock (DoomHost) ----
let clockMs = 1, freeRunning = true, baseMs = -1;
const ticksMs = () => { if (baseMs < 0) baseMs = clockMs; const now = clockMs; if (freeRunning) clockMs++; return now; };
const currentTic = () => baseMs < 0 ? 0 : Math.floor((clockMs - baseMs) * 35 / 1000);
const setTic = tic => { freeRunning = false; clockMs = baseMs + Math.floor((tic * 1000 + 34) / 35); };

// ---- in-memory WASI file system ----
const files = new Map([['/' + wadName, { data: new Uint8Array(wad), size: wad.length }]]);
const dirs = new Set(['/']);
const fds = new Map([[0, { kind: 'tty' }], [1, { kind: 'tty' }], [2, { kind: 'tty' }], [3, { kind: 'dir', path: '/' }]]);
let nextFd = 4;
const normalize = p => { const out = []; for (const s of p.split('/')) { if (!s || s === '.') continue; if (s === '..') out.pop(); else out.push(s); } return '/' + out.join('/'); };
const parentOf = p => p.slice(0, p.lastIndexOf('/')) || '/';
const resolve = (fd, p, n) => { const d = fds.get(fd); return d && d.kind === 'dir' ? normalize(d.path + '/' + text(p, n)) : null; };
const openFd = (f, out) => { const fd = nextFd++; fds.set(fd, f); view().setUint32(out, fd, true); return 0; };
const wasi = {
  fd_prestat_get(fd, b) { if (fd !== 3) return 8; view().setUint8(b, 0); view().setUint32(b + 4, 1, true); return 0; },
  fd_prestat_dir_name(fd, p) { if (fd !== 3) return 8; heap()[p] = 0x2f; return 0; },
  fd_fdstat_get(fd, b) { const f = fds.get(fd); if (!f) return 8; const v = view(); v.setUint8(b, f.kind === 'dir' ? 3 : f.kind === 'tty' ? 2 : 4); v.setUint16(b + 2, 0, true); v.setBigInt64(b + 8, -1n, true); v.setBigInt64(b + 16, -1n, true); return 0; },
  fd_fdstat_set_flags: () => 0,
  path_open(dirfd, _df, p, n, oflags, _rb, _ri, fdflags, out) {
    const path = resolve(dirfd, p, n); if (path === null) return 8;
    if (dirs.has(path)) return (oflags & 1) && (oflags & 4) ? 20 : openFd({ kind: 'dir', path }, out);
    if (oflags & 2) return files.has(path) ? 54 : 44;
    let e = files.get(path);
    if (!e) { if (!(oflags & 1) || !dirs.has(parentOf(path))) return 44; e = { data: new Uint8Array(0), size: 0 }; files.set(path, e); }
    else if ((oflags & 1) && (oflags & 4)) return 20;
    if (oflags & 8) e.size = 0;
    return openFd({ kind: 'file', path, entry: e, pos: 0, append: (fdflags & 1) !== 0 }, out);
  },
  fd_read(fd, iovs, count, out) {
    const f = fds.get(fd); if (!f) return 8;
    if (f.kind !== 'file') { view().setUint32(out, 0, true); return f.kind === 'dir' ? 31 : 0; }
    const v = view(), h = heap(); let total = 0;
    for (let i = 0; i < count; i++) {
      const p = v.getUint32(iovs + i * 8, true), len = v.getUint32(iovs + i * 8 + 4, true);
      const n = Math.max(0, Math.min(len, f.entry.size - f.pos));
      h.set(f.entry.data.subarray(f.pos, f.pos + n), p); f.pos += n; total += n; if (n < len) break;
    }
    v.setUint32(out, total, true); return 0;
  },
  fd_write(fd, iovs, count, out) {
    const f = fds.get(fd); if (!f) return 8;
    const v = view(), h = heap(); let total = 0;
    for (let i = 0; i < count; i++) {
      const p = v.getUint32(iovs + i * 8, true), len = v.getUint32(iovs + i * 8 + 4, true);
      if (f.kind === 'file') {
        const e = f.entry; if (f.append) f.pos = e.size; const end = f.pos + len;
        if (end > e.data.length) { const g = new Uint8Array(Math.max(end, e.data.length * 2, 4096)); g.set(e.data.subarray(0, e.size)); e.data = g; }
        e.data.set(h.subarray(p, p + len), f.pos); f.pos = end; e.size = Math.max(e.size, end);
      } else if (f.kind === 'dir') return 8;
      total += len;
    }
    v.setUint32(out, total, true); return 0;
  },
  fd_seek(fd, offset, whence, out) {
    const f = fds.get(fd); if (!f || f.kind !== 'file') return 8;
    const pos = (whence === 0 ? 0 : whence === 1 ? f.pos : f.entry.size) + Number(offset); if (pos < 0) return 28;
    f.pos = pos; view().setBigInt64(out, BigInt(pos), true); return 0;
  },
  fd_close(fd) { if (fd < 3) return 0; return fds.delete(fd) ? 0 : 8; },
  path_create_directory(fd, p, n) { const path = resolve(fd, p, n); if (path === null) return 8; if (dirs.has(path) || files.has(path)) return 20; if (!dirs.has(parentOf(path))) return 44; dirs.add(path); return 0; },
  path_remove_directory(fd, p, n) { const path = resolve(fd, p, n); if (path === null) return 8; return dirs.delete(path) ? 0 : 44; },
  path_unlink_file(fd, p, n) { const path = resolve(fd, p, n); if (path === null) return 8; return files.delete(path) ? 0 : 44; },
  path_rename(f1, p1, n1, f2, p2, n2) { const a = resolve(f1, p1, n1), b = resolve(f2, p2, n2); if (a === null || b === null) return 8; const e = files.get(a); if (!e) return 44; files.delete(a); files.set(b, e); return 0; },
  proc_exit(code) { throw new Error('proc_exit(' + code + ')'); },
};

let frame = new Uint8Array(0);
const doom = {
  draw_frame(p, w, h) { const n = w * h * 4; if (frame.length !== n) frame = new Uint8Array(n); frame.set(heap().subarray(p, p + n)); },
  ticks_ms: ticksMs,
  sfx_start() {}, sfx_update() {}, sfx_stop() {}, sfx_playing: () => 0,
  music_play() {}, music_stop() {}, music_volume() {}, music_pause() {},
};
function frameHash() {
  let h = 0xcbf29ce484222325n;
  for (const b of frame) { h ^= BigInt(b); h = (h * 0x100000001b3n) & 0xffffffffffffffffn; }
  return h.toString(16).padStart(16, '0');
}

function dumpFrame(path) {
  // The engine's 0x00RRGGBB little-endian pixels, as a 320x200 binary PPM.
  const rgb = Buffer.alloc(320 * 200 * 3);
  for (let i = 0; i < 320 * 200; i++) { rgb[i * 3] = frame[i * 4 + 2]; rgb[i * 3 + 1] = frame[i * 4 + 1]; rgb[i * 3 + 2] = frame[i * 4]; }
  mkdirSync(process.env.DUMP, { recursive: true });
  writeFileSync(path, Buffer.concat([Buffer.from('P6 320 200 255\n'), rgb]));
}

const t0 = process.hrtime.bigint();
const { instance } = await WebAssembly.instantiate(wasm, { wasi_snapshot_preview1: wasi, doom });
memory = instance.exports.memory;
instance.exports._initialize();
heap().set(new TextEncoder().encode(wadName + '\0'), instance.exports.dg_iwad_name());
const t1 = process.hrtime.bigint();
instance.exports.dg_start();
const t2 = process.hrtime.bigint();
const first = currentTic();
const times = [], hashes = [];
for (let i = 0; i < TICS; i++) {
  setTic(first + i + 1);
  const s = process.hrtime.bigint();
  instance.exports.dg_tick();
  times.push(Number(process.hrtime.bigint() - s));
  if (CHECKPOINTS.includes(i + 1)) {
    hashes.push(`${i + 1}=${frameHash().slice(0, 8)}`);
    if (process.env.DUMP) dumpFrame(`${process.env.DUMP}/tic-${i + 1}.ppm`);
  }
}

const ms = n => Math.round(n / 1e4) / 100;
const row = (label, s) => {
  if (!s.length) return `| ${label} | - | - | - | - | - | - |`;
  const sorted = [...s].sort((a, b) => a - b), pct = p => sorted[Math.floor((sorted.length - 1) * p)];
  const over = s.filter(t => t > BUDGET).length;
  return `| ${label} | ${ms(s.reduce((a, b) => a + b, 0) / s.length)} | ${ms(pct(0.5))} | ${ms(pct(0.95))} | ${ms(pct(0.99))} | ${ms(sorted.at(-1))} | ${Math.floor(over * 1000 / s.length) / 10}% |`;
};
console.log(`Node ${process.version} (V8 ${process.versions.v8})`);
console.log(`| Runtime | load | start | tics run |\n|---|---|---|---|\n| V8 (Node) | ${Number(t1 - t0) / 1e6} | ${Number(t2 - t1) / 1e6} | ${times.length} |`);
console.log('\n| Runtime, tics | mean | p50 | p95 | p99 | max | over budget |\n|---|---|---|---|---|---|---|');
console.log(row(`V8 (Node), first ${WARMUP}`, times.slice(0, WARMUP)));
console.log(row('V8 (Node), after', times.slice(WARMUP)));
console.log('\nFrame hashes at tics:\n  V8 (Node): ' + hashes.join(' '));
