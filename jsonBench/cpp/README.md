# jsonBench: C/C++ harness

Parses the jsonBench corpus with C and C++ JSON libraries under the contract in
[`../HARNESS.md`](../HARNESS.md).

```bash
jsonBench/cpp/run.sh                       # all parsers, all workloads -> jsonBench/results/cpp.json
jsonBench/cpp/run.sh --quick --workloads all
jsonBench/cpp/run.sh --list
```

`run.sh` runs `fetch.sh` (clones every library in `deps.txt`, pinned to a tag or a commit,
into `jsonBench/.cache/cpp/`; nothing is vendored), configures CMake (Release,
`-O3 -march=native`, C++23; Ninja when available) into `jsonBench/cpp/build/`, builds, and runs
the binary from the repo root with your flags. `JB_JOBS=N` caps build parallelism; `CXX`/`CC`
pick the compiler on the first configure (tested with gcc 13.3). Needs `libssl-dev`
(SHA-256 for the digest).

## Parsers

| parser | library | version | license | mode | input | retained |
|---|---|---|---|---|---|---|
| `simdjson-ondemand-typed` | simdjson | v5.0.5 | Apache-2.0 OR MIT | typed | string→bytes-padded | yes |
| `simdjson-ondemand-borrowed` | simdjson | v5.0.5 | Apache-2.0 OR MIT | borrowed | string→bytes-padded | null |
| `simdjson-dom` | simdjson | v5.0.5 | Apache-2.0 OR MIT | dom | string→bytes-padded | null |
| `yyjson-dom` | yyjson | 0.13.0 | MIT | dom | string | yes |
| `yyjson-insitu` | yyjson | 0.13.0 | MIT | dom | bytes-copy | null |
| `yyjson-dom-pool` | yyjson | 0.13.0 | MIT | dom | string | null |
| `yyjson-typed` | yyjson | 0.13.0 | MIT | typed | string | yes |
| `yyjson-borrowed` | yyjson | 0.13.0 | MIT | borrowed | string | yes (doc + views) |
| `rapidjson-dom` | RapidJSON | master@24b5e7a8b27f | MIT | dom | string | yes |
| `rapidjson-insitu` | RapidJSON | master@24b5e7a8b27f | MIT | dom | bytes-copy | null |
| `rapidjson-dom-pool` | RapidJSON | master@24b5e7a8b27f | MIT | dom | string | null |
| `rapidjson-sax-typed` | RapidJSON | master@24b5e7a8b27f | MIT | typed | string | yes |
| `nlohmann-dom` | nlohmann/json | v3.12.0 | MIT | dom | string | yes |
| `nlohmann-typed` | nlohmann/json | v3.12.0 | MIT | typed | string | yes |
| `nlohmann-sax-typed` | nlohmann/json | v3.12.0 | MIT | typed | string | yes |
| `glaze-typed` | glaze | v9.0.0 | MIT | typed | string | yes |
| `glaze-borrowed` | glaze | v9.0.0 | MIT | borrowed (partial) | string | yes (event + frame) |
| `glaze-dom` | glaze | v9.0.0 | MIT | dom | string | yes |
| `cjson-dom` | cJSON | v1.7.19 | MIT | dom | string | yes |
| `boostjson-dom` | Boost.JSON | boost-1.92.0 | BSL-1.0 | dom | string | yes |
| `boostjson-dom-pool` | Boost.JSON | boost-1.92.0 | BSL-1.0 | dom | string | null |
| `boostjson-typed` | Boost.JSON | boost-1.92.0 | BSL-1.0 | typed | string | yes |
| `sajson-dom` | sajson | master@68fe32ed6bcb | MIT | dom | string | yes |
| `jsonc-dom` | json-c | json-c-0.19-20260627 | MIT | dom | string | yes |
| `jsmn-tokens` | jsmn | v1.1.0 | MIT | dom (tokenizer) | string | null |
| `jansson-dom` | Jansson | v2.15.1 | MIT | dom | string | yes |

Every row's `notes` field in the results says exactly what the glue does. The points that
matter when reading the numbers:

- **Input is the WebSocket text frame**: a `std::string` (what IXWebSocket, websocketpp and
  Boost.Beast hand an app). The harness builds one per line from `(data, size)` before timing,
  so `capacity() == size()`: no spare capacity a parser could use as padding. Every parser
  starts from that string inside the timed loop:
  - `string`: the library reads `data()`/`size()` (or `c_str()`, whose NUL is free) directly.
    Copies the library makes on its own (yyjson_read and sajson copy into their document) are
    part of the parse.
  - `string→bytes-padded` (simdjson): the frame lacks `SIMDJSON_PADDING` spare bytes.
    On-Demand's `iterate(const std::string&)` uses the string's capacity and returns
    `INSUFFICIENT_PADDING`; `iterate(std::string&)` would `reserve()` (reallocate) a frame the
    app does not own mutably; `padded_input` skips the copy only by reading past the
    allocation when that stays inside the page. So the On-Demand rows memcpy each frame into one
    reused buffer with `SIMDJSON_PADDING` spare bytes (timed) and iterate a
    `padded_string_view` of it. `simdjson-dom` calls `dom::parser::parse(const std::string&)`,
    simdjson's own string entry point, which sees the missing padding and memcpys into the
    parser's reused padded buffer. (simdjson also has `parse_unpadded` for the DOM; not used.)
  - `bytes-copy` (`yyjson-insitu`, `rapidjson-insitu`): in-situ parsing needs a mutable
    buffer (yyjson also needs `YYJSON_PADDING_SIZE` zero bytes), so each parse memcpys the
    frame into one reused scratch buffer (timed).

- **typed** builds `struct Event { std::string id, pubkey; uint64_t created_at; uint32_t kind;
  std::vector<std::vector<std::string>> tags; std::string content, sig; }` per event, and its
  destructor runs inside the timed loop. **borrowed** is the same with `std::string_view`,
  valid until the next parse.
- **`*-pool`** rows are the documented low-allocation idioms (yyjson pool allocator, RapidJSON
  `MemoryPoolAllocator` over a user buffer, Boost.JSON `monotonic_resource` + reused `parser`):
  one buffer reused across events, so zero mallocs per parse. `simdjson-dom` reaches zero the
  same way: one reused `dom::parser`.
- **`glaze-borrowed` is a partial borrow.** glaze reads `std::string_view` fields as raw input
  slices *without decoding escapes*, so only `id`, `pubkey` and `sig` (lowercase hex by NIP-01)
  are views; `content` and tags stay `std::string`.
- **`rapidjson-dom`** allocates a 64 KiB `MemoryPoolAllocator` chunk per fresh `Document`, hence
  ~67 KB/event. Its DOM rows call `Parse(const char*)` (NUL-terminated): `Parse(str, len)` goes
  through a `MemoryStream` that skips the SSE4.2 string scan and measured ~1.6x slower. Built
  with `RAPIDJSON_SSE42`.
- **`jsmn-tokens`** is a tokenizer: it records token offsets in a reused array and decodes
  nothing. The timed loop only parses the `kind` digits and reads the `tags` token's size; the
  digest decodes the string slices with the harness's own unescaper (`json_unescape` in
  `main.cpp`). It is a floor for "find the structure", not a parser you could keep strings from.
- **`cjson-dom` fails the digest on `notes` and `all`**: cJSON stores strings NUL-terminated
  with no length, so the one note whose content holds an escaped `\u0000` is truncated. That is
  the library, not the glue; it passes every other workload.
- `sajson` stores integers as `int32`, so `created_at` comes back as a double (exact below 2^53).
- json-c and Jansson both export `json_object_get` and `json_object_iter_next`; the build
  renames Jansson's with `-D` defines (`CMakeLists.txt`).

## Memory

`src/alloc_count.c` defines `malloc`, `calloc`, `realloc`, `free`, `memalign`,
`aligned_alloc`, `posix_memalign`, `valloc` and `pvalloc` in the executable (linked with
`-rdynamic`) and forwards them to glibc's `__libc_*` functions, so it also sees
`operator new` from libstdc++ and every C library. Each successful call is one allocation;
bytes are the requested size (for `realloc`, the full new size). `gc_count` and `gc_ms` are null.

It also keeps a live-byte counter: a block adds its `malloc_usable_size()` when handed out and
subtracts it when freed (`realloc` applies the difference). That is the heap the block really
occupies (the request rounded up to glibc's 16-byte chunks), without the 8-byte chunk header.

**Retained heap** (`retained_bytes_per_event`): after timing, `Runner::keep_all()` parses every
event once more and keeps each result as its own heap object in a pointer vector (reserved
before the first reading, so the vector is not counted): `Event*` for typed rows (the 168-byte
`Event` node is counted, like a JVM object header and fields), and for DOM rows the
independent document (`yyjson_doc*`, `rapidjson::Document*`, `nlohmann::json*`,
`glz::generic_u64*`, `cJSON*`, `boost::json::value*`, `sajson::document*`, `json_object*`,
`json_t*`). Retained = live bytes after minus before, divided by events; then all are freed.
`yyjson-borrowed` keeps each doc with its views. `glaze-borrowed` keeps each event with an
exact-size copy of its frame, standing in for the websocket string the app would have to move
in. The column is null where a result is a view into reused state: the simdjson rows (reused
parser), `*-insitu` (reused scratch buffer), `*-pool` (reused arena) and `jsmn-tokens`.

What it shows: typed rows that size their strings exactly retain about the same (decoded
strings + vectors + node). `simdjson-ondemand-typed` and `rapidjson-sax-typed` grow the tag
vectors with `emplace_back` and retain a little more; `glaze-typed` more again;
`nlohmann-sax-typed` moves the lexer's token buffer into each field, so every string keeps the
capacity it grew to. `rapidjson-dom` keeps its 64 KiB allocator chunk per document.

## Licenses

All permissive. Notes from the LICENSE files:

- simdjson is dual-licensed Apache-2.0 / MIT (`LICENSE`, `LICENSE-MIT`).
- RapidJSON is MIT; its `license.txt` lists BSD-3-Clause `msinttypes` (MSVC-only headers) and
  the non-free JSON License, which covers only `bin/jsonchecker/` (test data we don't use).
- nlohmann/json is MIT; `LICENSES/GPL-3.0-only.txt` applies only to `tests/thirdparty/imapdl`
  (per `.reuse/dep5`), which is not compiled here.
- Jansson is MIT except `src/dtoa.c`, which carries David Gay's permissive dtoa notice.
- Boost.JSON and the Boost headers it needs are BSL-1.0.
