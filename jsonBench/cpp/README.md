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

| parser | library | version | license | mode | input |
|---|---|---|---|---|---|
| `simdjson-ondemand-typed` | simdjson | v5.0.5 | Apache-2.0 OR MIT | typed | bytes-padded |
| `simdjson-ondemand-borrowed` | simdjson | v5.0.5 | Apache-2.0 OR MIT | borrowed | bytes-padded |
| `simdjson-dom` | simdjson | v5.0.5 | Apache-2.0 OR MIT | dom | bytes-padded |
| `yyjson-dom` | yyjson | 0.13.0 | MIT | dom | bytes |
| `yyjson-insitu` | yyjson | 0.13.0 | MIT | dom | bytes-copy |
| `yyjson-dom-pool` | yyjson | 0.13.0 | MIT | dom | bytes |
| `yyjson-typed` | yyjson | 0.13.0 | MIT | typed | bytes |
| `yyjson-borrowed` | yyjson | 0.13.0 | MIT | borrowed | bytes |
| `rapidjson-dom` | RapidJSON | master@24b5e7a8b27f | MIT | dom | bytes |
| `rapidjson-insitu` | RapidJSON | master@24b5e7a8b27f | MIT | dom | bytes-copy |
| `rapidjson-dom-pool` | RapidJSON | master@24b5e7a8b27f | MIT | dom | bytes |
| `rapidjson-sax-typed` | RapidJSON | master@24b5e7a8b27f | MIT | typed | bytes |
| `nlohmann-dom` | nlohmann/json | v3.12.0 | MIT | dom | bytes |
| `nlohmann-typed` | nlohmann/json | v3.12.0 | MIT | typed | bytes |
| `nlohmann-sax-typed` | nlohmann/json | v3.12.0 | MIT | typed | bytes |
| `glaze-typed` | glaze | v9.0.0 | MIT | typed | bytes |
| `glaze-borrowed` | glaze | v9.0.0 | MIT | borrowed (partial) | bytes |
| `glaze-dom` | glaze | v9.0.0 | MIT | dom | bytes |
| `cjson-dom` | cJSON | v1.7.19 | MIT | dom | bytes |
| `boostjson-dom` | Boost.JSON | boost-1.92.0 | BSL-1.0 | dom | bytes |
| `boostjson-dom-pool` | Boost.JSON | boost-1.92.0 | BSL-1.0 | dom | bytes |
| `boostjson-typed` | Boost.JSON | boost-1.92.0 | BSL-1.0 | typed | bytes |
| `sajson-dom` | sajson | master@68fe32ed6bcb | MIT | dom | bytes |
| `jsonc-dom` | json-c | json-c-0.19-20260627 | MIT | dom | bytes |
| `jsmn-tokens` | jsmn | v1.1.0 | MIT | dom (tokenizer) | bytes |
| `jansson-dom` | Jansson | v2.15.1 | MIT | dom | bytes |

Every row's `notes` field in the results says exactly what the glue does. The points that
matter when reading the numbers:

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

## Licenses

All permissive. Notes from the LICENSE files:

- simdjson is dual-licensed Apache-2.0 / MIT (`LICENSE`, `LICENSE-MIT`).
- RapidJSON is MIT; its `license.txt` lists BSD-3-Clause `msinttypes` (MSVC-only headers) and
  the non-free JSON License, which covers only `bin/jsonchecker/` (test data we don't use).
- nlohmann/json is MIT; `LICENSES/GPL-3.0-only.txt` applies only to `tests/thirdparty/imapdl`
  (per `.reuse/dep5`), which is not compiled here.
- Jansson is MIT except `src/dtoa.c`, which carries David Gay's permissive dtoa notice.
- Boost.JSON and the Boost headers it needs are BSL-1.0.
