# jsonBench: JavaScript harness (Node, Bun, Deno)

One ESM script, `bench.mjs`, no build step, run by three engines. It follows
[`../HARNESS.md`](../HARNESS.md): same flags, digest, timing and output schema.

```bash
jsonBench/node/run.sh                         # Node -> results/node.json
jsonBench/node/run.sh --runtime bun --quick   # Bun  -> results/bun.json
jsonBench/node/run.sh --runtime all           # node, bun, deno in turn
jsonBench/node/run.sh --runtime deno --parsers json-parse-string --workloads notes
```

`run.sh` runs `npm ci` on first use. Node is the system `node` (22.x); Bun and Deno come
from npm as pinned devDependencies (`bun@1.4.3`, `deno@2.9.6`), so `package-lock.json`
pins every engine except Node. With `--runtime all`, `--out` is refused (each runtime
writes `results/<runtime>.json`).

The script re-spawns itself once per parser (`--child <id>`), so each parser gets a fresh
JIT profile and heap. Results: `harness` = `node` / `bun` / `deno`, `runtime` = engine
and version.

## Parsers

| id | library | version | license | mode | input | runtimes |
|---|---|---|---|---|---|---|
| `json-parse-string` | `JSON.parse` | engine | V8: BSD-3-Clause, JSC: BSD-2-Clause | typed | string | all |
| `json-parse-bytes` | `TextDecoder` + `JSON.parse` | engine | as above | typed | bytes | all |
| `json-parse-buffer` | `Buffer.toString('utf8')` + `JSON.parse` | engine | as above | typed | bytes (Buffer) | all |
| `baseline-utf8-decode` | `TextDecoder.decode` | engine | as above | baseline | bytes | all |
| `baseline-buffer-tostring` | `Buffer.toString('utf8')` | engine | as above | baseline | bytes (Buffer) | all |
| `bun-jsonl-bytes` | `Bun.JSONL.parse(Uint8Array)` | Bun 1.4.3 | MIT | typed | bytes | bun |
| `bun-jsonl-string` | `Bun.JSONL.parse(string)` | Bun 1.4.3 | MIT | typed | string | bun |
| `simdjson-parse` | [`simdjson`](https://github.com/luizperes/simdjson_nodejs) N-API addon, `parse()` | 0.9.2 (simdjson 0.9.1) | Apache-2.0 | typed | string | all |
| `simdjson-lazy` | same, `lazyParse()` + `valueForKeyPath` | 0.9.2 | Apache-2.0 | dom | string | all (crashes on Node) |
| `jsonjoy-decoder` | `@jsonjoy.com/json-pack` `JsonDecoder` | 18.30.0 | Apache-2.0 | typed | bytes | all (fails digest) |
| `streamparser-json` | `@streamparser/json` `JSONParser` | 0.0.26 | MIT | typed | string | all |

`json-parse-string` is the realistic web-client path: a WebSocket text frame arrives as
a JS string and goes straight into `JSON.parse`. The byte variants show what a Node
relay or client using `ws` (which hands out a `Buffer`) pays, and the baselines are the
decode cost alone.

Known failures, kept in the results with `digest_ok: false`:

- `simdjson-lazy` on Node 22: the child dies with SIGSEGV (a bare `lazyParse()` loop
  reproduces it). The same addon runs on Bun and Deno. The package was last published
  in 2021.
- `jsonjoy-decoder`: it does not decode `\uXXXX` escapes. Its unescape regex only knows
  `\b \f \n \r \t \" \/ \\`, so events whose content has escaped emoji come back with a
  literal `\uD83E\uDD8A` and fail the digest. Only `lists` passes.

The `simdjson` addon is slow everywhere because it builds every JS value through
node-addon-api and allocates a new `dom::parser` per call. On Node 22 it is slower still,
about 20x behind Bun on the same addon. On Deno, `simdjson-lazy` collapses on the
31k-event `all` workload: about 1.4 ms per event, against about 19 µs on `lists`. That
is probably a backlog of native finalizers; it was not investigated.

## Memory and GC

| | `gc_count` / `gc_ms` | `alloc_bytes_per_event` |
|---|---|---|
| Node 22 | `PerformanceObserver` `gc` entries that start inside the measured window | null: V8 12.4 has no cumulative allocation counter |
| Deno 2.9 | null: lists `gc` as supported but delivers no entries | `v8.getHeapStatistics().total_allocated_bytes` delta (V8 15 cumulative counter), V8 heap only: excludes native memory such as the simdjson addon's buffers |
| Bun 1.4 | null: JSC exposes no GC events | null: the `node:v8` shim's `total_allocated_bytes` does not track JS allocation |

`allocs_per_event` is always null.

## Licensing notes

Every package used is permissive. One oddity: npm lists `@jsonjoy.com/json-equal`
(a transitive dependency of `json-pack`, not loaded by `JsonDecoder`) as
`AGPL-3.0-only` in its `package.json`, but the `LICENSE` file it ships is Apache-2.0.
It is a dev-only benchmark dependency and never ships in an Amethyst artifact.
