# jsonBench harness contract

Every language harness (`jsonBench/<harness>/`) follows this contract so that one
report can rank parsers across languages. A harness that breaks it produces numbers
that cannot be compared, so read this before adding a parser.

## Input: the corpus

`jsonBench/.corpus/` (built by `corpus/prepare.py`, git-ignored):

- `manifest.json`: `workloads[]` with `name`, `file`, `events`, `bytes`, `digest`.
- `<workload>.ndjson`: one Nostr event per line, exactly the bytes a relay sends
  (UTF-8, `\n`-terminated, no `\n` inside a line). `bytes` in the manifest is the sum
  of line lengths without the newlines; throughput is computed against it.

Every event has exactly the seven NIP-01 fields: `id`, `pubkey`, `sig`, `content`
(strings), `created_at`, `kind` (non-negative integers, `created_at < 2^53`,
`kind < 2^31`) and `tags` (array of arrays of strings). Strings contain JSON escapes
(`\"`, `\\`, `\n`, `\uXXXX` including surrogate pairs for emoji); no lone surrogates.

## The task a parser performs

**One parse = one event line → one in-memory event.** The harness loads every line
of a workload into memory *before* timing, as the language's WebSocket text-frame type
(see "Input" below), then times parsing all of them.

Each parser entry declares a `mode`:

| mode | the result of one parse | example |
|---|---|---|
| `typed` | the language's natural event struct/class with **owned**, fully decoded strings (Java `String`, Rust `String`, Go `string`, C++ `std::string`, JS/Python objects), integers as integers, tags as nested lists of strings | Jackson streaming → `Event`, serde → `struct Event { String… }` |
| `borrowed` | a typed struct whose strings point into the input or parser buffers (no copy when there is no escape) | serde `Cow<str>`/`&str`, simdjson on-demand `string_view` |
| `dom` | the library's generic tree (`JsonNode`, `serde_json::Value`, `yyjson_doc`…); the timed loop only reads `kind` and the tag count from it | `JSON.parse` → object counts as `typed` in JS; `readTree` is `dom` |
| `baseline` | not a parser: a reference cost such as UTF-8 decoding a byte array into a string | `new String(bytes, UTF_8)` |

`typed` is what a Nostr client actually needs (an `Event` it can keep), so it is the
headline ranking. `borrowed` and `dom` show what a library could do if the app adapted
to it. Production-path entries (e.g. Quartz's own `JacksonMapper`) are `typed`.

The timed loop must make the result observable so the compiler cannot drop it: fold
something from every parsed event into a sink (e.g. `sink += kind + tags.size +
content.length`) and print or return the sink at the end.

## Input: the WebSocket text frame (`input`)

Nostr arrives as WebSocket **text frames**, so every parser starts from what the
language's usual WebSocket client hands an app for one text frame. The harness prepares
that object before timing; everything a parser needs on top of it (encode to bytes,
copy into a padded or mutable buffer, wrap in a reader) is part of the parse and runs
**inside** the timed loop, with the parser's own allocations counted.

| harness | a text frame arrives as | free for byte parsers? |
|---|---|---|
| JVM | `java.lang.String` (OkHttp `onMessage(ws, text: String)`, Ktor `Frame.Text.readText()`) | no: `String → byte[]` is an encode, timed |
| Rust | `&str` / `Utf8Bytes` (tungstenite `Message::Text`), already UTF-8-validated | yes: `as_bytes()`; a padded or mutable copy is timed |
| C/C++ | `std::string` (IXWebSocket, websocketpp, Boost.Beast `flat_buffer`) | yes: `data()`/`c_str()`; a padded or mutable copy is timed |
| Go | `[]byte` (gorilla/websocket and coder/websocket `ReadMessage`) | yes; a `string` conversion or copy is timed |
| JS | JS `string` (`MessageEvent.data` in browsers, Node 22, Bun, Deno) | no: `TextEncoder.encode` is timed |
| Python | `str` (`websockets`, aiohttp `WSMessage.data`) | no: `str.encode()` is timed |

Use each library's most natural adaptation from that type (a native string entry point
when it has one, e.g. jsoniter-scala `readFromString` or okio `writeUtf8`, otherwise the
platform's fastest encode/copy) and say which in `notes`. `input` names the path:
`string`, `bytes` (the frame type is already bytes), `string→bytes` (encoded inside the
loop), `string→bytes-padded`, `bytes-copy` (copied into a mutable or padded scratch
buffer inside the loop), or the library entry point in parentheses.

A `baseline` row may still report a conversion on its own (JVM `String → byte[]`),
so readers can see how much of a row it is.

## Verification: the digest

Before timing, each parser parses every event of the workload once and feeds the
result into SHA-256 as follows (all strings as UTF-8 of the *decoded* value; integers
as ASCII decimal with no sign, padding or exponent):

```
for each event, in file order:
    id      0x1F
    pubkey  0x1F
    created_at 0x1F
    kind    0x1F
    for each tag:
        for each element:  element 0x1F
        0x1E
    0x1D
    content 0x1F
    sig     0x1F
    0x1C
```

The hex digest must equal `manifest.workloads[i].digest` (`digest_ok: true`). A parser
that throws on an event or produces a different digest is still reported, with
`digest_ok: false` and `errors`/`error` filled in, and is excluded from the rankings.
The reference implementation is `digest_event` in `corpus/prepare.py`.

## Timing

Per (workload, parser):

1. load inputs; 2. verify (above, untimed); 3. warm up: full rounds for `--warmup-ms`;
4. measure: full rounds until at least `--min-rounds` rounds **and** `--min-time-ms`
   have elapsed (stop at 10 000 rounds). One round parses every event of the workload once,
   single-threaded, in file order. Time each round with a monotonic nanosecond clock.

Report the median round (headline), the minimum and the 90th percentile. Derived:
`ns_per_event = median_ns / events`, `mb_per_s = bytes / (median_ns / 1e9) / 1e6`,
`events_per_s = events / (median_ns / 1e9)`.

**Memory / GC** over the measured rounds only (null when the runtime can't tell):

- `alloc_bytes_per_event`, `allocs_per_event`: bytes and allocation count per parsed
  event (JVM: `ThreadMXBean.getThreadAllocatedBytes`, count null; Go: `MemStats`
  `TotalAlloc`/`Mallocs`; Rust: a counting `#[global_allocator]`; C/C++: interposed
  `malloc`/`calloc`/`realloc`/`free` + `operator new`).
- `gc_count`, `gc_ms`: collections and total GC time during measurement
  (JVM `GarbageCollectorMXBean`s, Go `NumGC`/`PauseTotalNs`, JS `PerformanceObserver`
  `gc` entries). Null for runtimes without a tracing GC.

**Retained heap** (optional, `retained_bytes_per_event`): parse every event of the
workload once more and keep all results alive, the way a client keeps the events it
shows; report the heap still in use after a full collection, divided by the event
count. This is the number that shows what de-duplication such as `String.intern()`
buys: allocation per event measures garbage, retained heap measures what stays. JVM:
used heap between `System.gc()` calls; Go: `HeapAlloc` after `runtime.GC()`; Rust/C/C++:
live bytes from the allocation counter. Null when not measured.

Isolation: harnesses on JIT runtimes must run each parser in a **fresh process** so
one parser's JIT profile and heap don't tax the next (the JVM harness forks itself per
parser, JMH-style). Compiled harnesses may run everything in one process.

## Command line

Every harness exposes the same flags (through its own launcher, e.g. `run.sh`):

```
--corpus DIR          default jsonBench/.corpus
--out FILE            default jsonBench/results/<harness>.json
--workloads a,b       default: all workloads in the manifest
--parsers x,y         default: all parsers of the harness
--warmup-ms N         default 1000
--min-time-ms N       default 2000
--min-rounds N        default 5
--quick               warmup 200, min-time 300, min-rounds 3 (smoke test)
--list                print parser ids, one per line, and exit
```

## Output

`jsonBench/results/<harness>.json`:

```json
{
  "harness": "rust",
  "language": "Rust",
  "runtime": "rustc 1.97.0, -C target-cpu=native, release",
  "host": { "cpu": "…", "cores": 4, "os": "Linux 6.x" },
  "settings": { "warmup_ms": 1000, "min_time_ms": 2000, "min_rounds": 5 },
  "corpus_fingerprint": "<manifest.fingerprint>",
  "results": [
    {
      "parser": "serde_json-typed",
      "library": "serde_json",
      "version": "1.0.140",
      "license": "MIT OR Apache-2.0",
      "mode": "typed",
      "input": "bytes",
      "notes": "",
      "workload": "notes",
      "events": 3568, "bytes": 3225314,
      "digest_ok": true, "errors": 0, "error": null,
      "rounds": 412,
      "median_ns": 4812345, "min_ns": 4790000, "p90_ns": 4900000,
      "ns_per_event": 1348.7, "mb_per_s": 670.2, "events_per_s": 741430,
      "alloc_bytes_per_event": 1840.2, "allocs_per_event": 14.0,
      "gc_count": null, "gc_ms": null,
      "retained_bytes_per_event": null
    }
  ]
}
```

`parser` ids are unique within a harness, lowercase, `<library>-<variant>`. Record the
library's resolved version and its license (SPDX) as found in its own LICENSE file or
package metadata.
