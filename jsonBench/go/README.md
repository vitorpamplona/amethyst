# jsonBench: Go harness

The Go side of [jsonBench](../HARNESS.md): which Go JSON parsers decode Nostr events
fastest and with the least garbage.

```bash
./jsonBench/go/run.sh                      # all parsers, all workloads -> jsonBench/results/go.json
./jsonBench/go/run.sh --quick --workloads all
./jsonBench/go/run.sh --parsers sonic-typed,go-nostr-typed --workloads notes
./jsonBench/go/run.sh --list
```

`run.sh` builds `jsonBench/go/jsonbench` from the pinned `go.mod`/`go.sum` and runs it
from the repo root, so relative `--corpus`/`--out` paths are relative to the repo root.
It takes every HARNESS.md flag.

How it measures:

- **Process isolation.** The binary re-executes itself (`--child --parsers <id>`) once
  per parser, so a parser never inherits another one's heap size, GC pacing or sonic's
  JIT cache. Inside the child, workloads run one after another.
- **Input.** In Go a WebSocket text frame arrives as a `[]byte` (gorilla/websocket
  `ReadMessage`, coder/websocket `Read`), so every parser starts from a `[]byte` line:
  a sub-slice of the workload file (capacity capped at the line end), loaded before
  timing. Every copy or conversion on top of it runs inside the timed loop. The `input`
  column names it: `bytes` (the library reads the frame as is), `bytes-copy` (the
  library copies it into its own scratch buffer: goccy's NUL-terminated context buffer,
  fastjson's reused `Parser` buffer) or `bytes→string` (a `string(frame)` copy: sonic's
  `Unmarshal([]byte)` is `UnmarshalFromString(string(buf))`, `sonic-ast` and the
  baseline do it in the glue).
- **Results that alias the frame.** Some results keep pointers into the frame or into
  one copy of it, so one kept event keeps the whole buffer alive: `segmentio-borrowed`
  (`ZeroCopy`: strings point into the frame's `[]byte`, which a socket that reuses its
  read buffer would also overwrite), `sonic-typed` (`ConfigDefault`: substrings of its
  one `string(frame)` copy), `sonic-ast` (the tree points into that string) and
  `gjson-typed` (tag strings are substrings of one copy of the `tags` text). The
  fastjson and simdjson-go trees point into the frame or a copy of it too, but they are
  reused by the next parse anyway. Every other parser copies each string out.
- **Typed results.** Each typed parse allocates a fresh `*Event` that the caller could
  keep: `{ID, PubKey string; CreatedAt int64; Kind int; Tags [][]string; Content, Sig string}`.
  The Nostr libraries decode into their own `Event` types.
- **Memory and GC.** `runtime.GC()`, then `runtime.ReadMemStats` before and after the
  measured rounds. `alloc_bytes_per_event`/`allocs_per_event` come from
  `TotalAlloc`/`Mallocs`, `gc_count` from `NumGC` and `gc_ms` from `PauseTotalNs`
  (stop-the-world pauses only). An extra field, `gc_cpu_ms`, is the GC's estimated CPU
  time (`/cpu/classes/gc/total:cpu-seconds` in `runtime/metrics`). That includes the
  concurrent mark work that `gc_ms` leaves out.
- **Runtime.** The default `GOGC` (100), with no `GOMEMLIMIT` and `GOMAXPROCS` set to
  the core count. The environment can override all three, and the `runtime` string
  records them. Builds use the default `GOAMD64=v1`.
- **Retained heap** (`retained_bytes_per_event`). After the measured rounds:
  `runtime.GC()`, read `MemStats.HeapAlloc`, parse every event once more and keep every
  result in a slice, `runtime.GC()` again, read `HeapAlloc`. The delta divided by the
  event count is the number; the slice itself is allocated before the first reading,
  so it is not counted (a struct result stored by value has its `unsafe.Sizeof` added, as the JVM and C++ count the event object itself). The kept result is the parser's own: the library's event type
  for typed rows, the tree for dom rows (`map[string]any`, `ast.Node`), the string for
  the baseline. The pass runs 3 times and the median is reported. In this pass each
  line is first copied into a fresh `[]byte` (counted), the way gorilla and coder hand
  each frame to the app in its own buffer. A result that does not point into the frame
  lets that copy die; one that aliases it keeps the frame alive and pays for it.
  Parsing the preloaded corpus lines instead would make aliasing results look free.
  Null for `fastjson-dom` and `simdjson-go-dom`: their tree lives in a reused `Parser` /
  `ParsedJson` that the next parse overwrites, so there is nothing a client could keep.
  Slice capacity counts too: segmentio starts every slice at capacity 10, so each tag
  carries room for 10 strings.
- **Sink.** Typed rows fold `kind + len(tags) + len(content)` into the sink. Dom rows
  fold only `kind` and the tag count, as HARNESS.md specifies. The sink is printed and
  stored per row (`sink`).

## Toolchain pin

The module targets **Go 1.24.7**, the installed toolchain. Two libraries have newer
HEADs that require a newer Go, so they are pinned to their last Go 1.24-compatible commit:

- `github.com/go-json-experiment/json` @ `9b1f9ea2e11b` (2025-08-13). Later commits need
  Go 1.25 and then 1.26.
- `fiatjaf.com/nostr` @ `6367bd71d42a` (2025-11-25). Later commits need Go 1.25. Up to
  HEAD (2026-10), the event decoder differs only by skipping unknown fields.

sonic v1.15.4 supports Go 1.17 through 1.27 on amd64, so it runs here with its JIT.

## Parsers

| id | module | version | license | mode | notes |
|---|---|---|---|---|---|
| `stdlib-typed` | `encoding/json` | go1.24.7 | BSD-3-Clause | typed | `json.Unmarshal` into `Event` |
| `stdlib-map` | `encoding/json` | go1.24.7 | BSD-3-Clause | dom | `map[string]any` |
| `jsonv2-typed` | `github.com/go-json-experiment/json` | v0.0.0-20250813233538-9b1f9ea2e11b | BSD-3-Clause | typed | the json/v2 proposal |
| `goccy-typed` | `github.com/goccy/go-json` | v0.11.2 | MIT | typed | copies the frame into a NUL-terminated buffer, then copies strings out |
| `jsoniter-typed` | `github.com/json-iterator/go` | v1.1.12 | MIT | typed | `ConfigFastest` |
| `jsoniter-typed-compat` | `github.com/json-iterator/go` | v1.1.12 | MIT | typed | `ConfigCompatibleWithStandardLibrary`; both configs decode correctly |
| `sonic-typed` | `github.com/bytedance/sonic` | v1.15.4 | Apache-2.0 | typed | `ConfigDefault`: one `string(frame)` copy; escape-free strings are substrings of it, so a kept event keeps the copy alive |
| `sonic-typed-std` | `github.com/bytedance/sonic` | v1.15.4 | Apache-2.0 | typed | `ConfigStd` (`CopyString` + `ValidateString`) |
| `sonic-ast` | `github.com/bytedance/sonic` | v1.15.4 | Apache-2.0 | dom | `ast.NewRaw` + `LoadAll` (fully loaded, not lazy) |
| `segmentio-typed` | `github.com/segmentio/encoding` | v0.5.4 | MIT | typed | slices start at capacity 10 |
| `segmentio-borrowed` | `github.com/segmentio/encoding` | v0.5.4 | MIT | borrowed | `json.Parse(…, json.ZeroCopy)`: strings alias the frame and keep it alive |
| `easyjson-typed` | `github.com/mailru/easyjson` | v0.9.2 | MIT | typed | generated `easyev/event_easyjson.go` |
| `fastjson-dom` | `github.com/valyala/fastjson` | v1.6.10 | MIT | dom | reused `Parser` (copies the frame into its buffer); strings are unescaped lazily, only when read; no retained number |
| `fastjson-typed` | `github.com/valyala/fastjson` | v1.6.10 | MIT | typed | reused `Parser`, then every field is copied out |
| `jsonparser-typed` | `github.com/buger/jsonparser` | v1.6.1 | MIT | typed | `EachKey` + `ArrayEach`, `ParseString` |
| `simdjson-go-dom` | `github.com/minio/simdjson-go` | v0.4.5 | Apache-2.0 | dom | needs AVX2 + CLMUL (`SupportedCPU()`); reused `ParsedJson`, no retained number |
| `gjson-typed` | `github.com/tidwall/gjson` | v1.20.0 | MIT | typed | one `GetBytes` per field (7 scans); tag strings share one copy of the `tags` text |
| `jx-typed` | `github.com/go-faster/jx` | v1.2.0 | MIT | typed | streaming decoder used by ogen |
| `ojg-dom` | `github.com/ohler55/ojg` | v1.28.7 | MIT | dom | **fails the digest**: decodes surrogate pairs as two U+FFFD |
| `go-nostr-typed` | `github.com/nbd-wtf/go-nostr` | v0.52.3 | MIT | typed | `Event.UnmarshalJSON`, an easyjson-generated decoder |
| `fiatjaf-nostr-typed` | `fiatjaf.com/nostr` | v0.0.0-20251125175014-6367bd71d42a | Unlicense | typed | hand-tuned easyjson lexer; id/pubkey/sig hex-decoded into byte arrays |
| `go-bytes-to-string` | Go runtime | go1.24.7 | BSD-3-Clause | baseline | `string(line)`: allocate and copy |

Every module linked into the binary, transitive dependencies included, is under a
permissive license: MIT, MIT-0, BSD-3-Clause, ISC, Apache-2.0 or the Unlicense.
`licenses.go` holds the SPDX ids, read from each module's own LICENSE file. Versions
come from the binary's build info at run time.

## Glue notes

- **easyjson** cannot generate code into `package main`, so its struct lives in
  `easyev/`. The decoder is generated with `-no_std_marshalers`, which keeps
  `UnmarshalJSON` off every type, so no other library can dispatch into easyjson code.
  To regenerate, run `cd easyev && go run github.com/mailru/easyjson/easyjson@v0.9.2 -no_std_marshalers event.go`.
- **fiatjaf.com/nostr** stores id, pubkey and sig as `[32]byte`/`[64]byte` and treats an
  all-zero array as absent: its encoder omits zero values. Some corpus events are
  unsigned rumors whose `sig` is empty, so the digest glue maps zero back to an empty
  string. Its `Kind` is a `uint16`. That is enough for every kind in the corpus (the
  largest is 34550), but it could not represent a kind of 65536 or above.
- **ojg** is kept in the run but excluded from the rankings. `oj` turns every `\uXXXX`
  escape into its own rune, so an escaped emoji (`\uD83D\uDE44`) comes out as `"\uFFFD\uFFFD"`.
  That changes 2,487 of the 31,161 events in `all`. It passes only on `dms`,
  `giftwraps` and `lists`, the workloads with no escaped emoji.
