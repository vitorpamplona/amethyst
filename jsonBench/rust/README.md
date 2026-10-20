# jsonBench: Rust harness

Times Rust JSON parsers on Nostr events, following [`../HARNESS.md`](../HARNESS.md).

```bash
jsonBench/rust/run.sh                         # everything -> jsonBench/results/rust.json
jsonBench/rust/run.sh --quick --workloads all # smoke test
jsonBench/rust/run.sh --list                  # parser ids
```

`run.sh` builds with `RUSTFLAGS="-C target-cpu=native"` and the release profile
(`lto = "fat"`, `codegen-units = 1`), then passes every HARNESS.md flag through. Everything
runs in one process, single-threaded. Allocations come from a counting `#[global_allocator]`
around `System` (`src/alloc.rs`). A `realloc` counts as one allocation of the new size, and
memory C++ allocates itself (simdjson-rust) is not counted. Versions and licenses come
from `cargo metadata`, read at build time (`build.rs`).

## Input: the text frame

A Nostr WebSocket text frame reaches a Rust app as a `&str` (tungstenite
`Message::Text(Utf8Bytes)`), already UTF-8-validated by the socket. The harness turns every
line into a `String` before timing (validated once, untimed) and every parser starts from that
`&str` inside the timed loop:

- `string`: the row calls the library's `&str` entry point (`serde_json::from_str`,
  `sonic_rs::from_str`, `jiter::serde::from_str`, `miniserde`/`nanoserde`/`json`/`tinyjson`), or,
  for libraries that only take bytes (jiter's `JsonValue`/`Jiter`, hifijson, struson), passes
  `s.as_bytes()`, which is free. A `&str` entry point also skips the UTF-8 check that serde_json's
  and sonic-rs's `from_slice` run on string contents, so the old `from_slice` rows (whose only
  difference was that re-validation) are gone and `serde_json-typed` now calls `from_str`.
- `bytes-copy`: the parser needs its own buffer and the copy is timed. simd-json and
  simd-json-derive unescape in place, so the frame is copied into a reused scratch `Vec<u8>`;
  simdjson-rust calls `dom::Parser::parse_str`, which copies the frame into the parser's reused
  padded buffer.
- `std-from_utf8` (`baseline`) is `std::str::from_utf8` over the frame's bytes: the validation
  the socket already paid to hand over a `&str`. No parser row pays it again.

## Retained heap

After timing, `retained_bytes_per_event` parses every event once more and keeps every result in
a `Vec` (allocated up front, so its own buffer is not counted, but each result's inline `size_of::<T>()` is added, as the JVM and C++ count the event object itself). The allocator also counts freed
bytes (`dealloc` and the old size of a `realloc`), and the figure is live bytes (allocated -
freed) after the pass minus before it, per event. It is reported for:

- every `typed` row (the owned `Event`; serde-driven rows grow `tags` by doubling, so they keep
  more than simd-json/simdjson, which know each array's length);
- `dom` rows whose value is independent and owned (`serde_json::Value`, `simd_json::OwnedValue`,
  `sonic_rs::Value`, `json`, `tinyjson`);
- `borrowed` and `dom` rows that borrow **only the input frame** (serde `Cow` structs over the
  `&str`, `serde_json_borrow::Value`, `jiter::JsonValue`, hifijson's value): the frames are kept
  alongside, as a client keeping the borrowed events would, and the number counts only the
  results' own heap (owned `Cow`s for escaped strings, `Vec`s, nodes), not the frames.

It is null for results that borrow a reused parser buffer or tape, which cannot all be kept at
once: `simd_json-borrowed-struct`, `simd_json-borrowed`, `simd_json-tape`,
`simd_json_derive-borrowed`, `simdjson_rust-borrowed`, `simdjson_rust-dom`, and the baseline.
simdjson-rust's C++ buffers are invisible to the allocator either way.

| parser | crate | version | license | mode | input | retained |
|---|---|---|---|---|---|---|
| `serde_json-typed` | serde_json | 1.0.154 | MIT OR Apache-2.0 | typed | string | yes |
| `serde_json-borrowed` | serde_json | 1.0.154 | MIT OR Apache-2.0 | borrowed | string | yes (input-borrowing) |
| `serde_json-value` | serde_json | 1.0.154 | MIT OR Apache-2.0 | dom | string | yes |
| `serde_json_borrow-value` | serde_json_borrow | 0.9.0 | MIT | dom | string | yes (input-borrowing) |
| `simd_json-typed` | simd-json | 0.18.1 | Apache-2.0 OR MIT | typed | bytes-copy | yes |
| `simd_json-borrowed-struct` | simd-json | 0.18.1 | Apache-2.0 OR MIT | borrowed | bytes-copy | null |
| `simd_json-borrowed` | simd-json | 0.18.1 | Apache-2.0 OR MIT | dom | bytes-copy | null |
| `simd_json-owned-value` | simd-json | 0.18.1 | Apache-2.0 OR MIT | dom | bytes-copy | yes |
| `simd_json-tape` | simd-json | 0.18.1 | Apache-2.0 OR MIT | dom | bytes-copy | null |
| `simd_json_derive-typed` | simd-json-derive | 0.18.0 | Apache-2.0 OR MIT | typed | bytes-copy | yes |
| `simd_json_derive-borrowed` | simd-json-derive | 0.18.0 | Apache-2.0 OR MIT | borrowed | bytes-copy | null |
| `sonic_rs-typed` | sonic-rs | 0.5.10 | Apache-2.0 | typed | string | yes |
| `sonic_rs-borrowed` | sonic-rs | 0.5.10 | Apache-2.0 | borrowed | string | yes (input-borrowing) |
| `sonic_rs-value` | sonic-rs | 0.5.10 | Apache-2.0 | dom | string | yes |
| `sonic_rs-lazy` | sonic-rs | 0.5.10 | Apache-2.0 | typed | string | yes |
| `jiter-value` | jiter | 0.17.0 | MIT | dom | string | yes (input-borrowing) |
| `jiter-value-scratch` | jiter | 0.17.0 | MIT | dom | string | yes (input-borrowing) |
| `jiter-typed` | jiter | 0.17.0 | MIT | typed | string | yes |
| `jiter-serde-typed` | jiter | 0.17.0 | MIT | typed | string | yes |
| `jiter-serde-borrowed` | jiter | 0.17.0 | MIT | borrowed | string | yes (input-borrowing) |
| `json-dom` | json | 0.12.4 | MIT OR Apache-2.0 | dom | string | yes |
| `miniserde-typed` | miniserde | 0.1.46 | MIT OR Apache-2.0 | typed | string | yes |
| `nanoserde-typed` | nanoserde | 0.2.1 | MIT OR Apache-2.0 | typed | string | yes |
| `tinyjson-dom` | tinyjson | 2.5.1 | MIT | dom | string | yes |
| `hifijson-value` | hifijson | 0.5.0 | MIT | dom | string | yes (input-borrowing) |
| `hifijson-serde-typed` | hifijson | 0.5.0 | MIT | typed | string | yes |
| `struson-typed` | struson | 0.7.2 | MIT OR Apache-2.0 | typed | string | yes |
| `simdjson_rust-typed` | simdjson-rust | 0.4.0-alpha.2 | Apache-2.0 | typed | bytes-copy | yes |
| `simdjson_rust-borrowed` | simdjson-rust | 0.4.0-alpha.2 | Apache-2.0 | borrowed | bytes-copy | null |
| `simdjson_rust-dom` | simdjson-rust | 0.4.0-alpha.2 | Apache-2.0 | dom | bytes-copy | null |
| `std-from_utf8` | std (rustc) | toolchain | MIT OR Apache-2.0 | baseline | bytes | null |

Each row's `notes` field in the results says exactly what it calls. Some details:

- **typed**: `Event { id, pubkey, sig, content: String, created_at: u64, kind: u32, tags: Vec<Vec<String>> }`.
  One struct derives serde, miniserde and nanoserde. `jiter-typed`, `struson-typed` and
  `sonic_rs-lazy` fill it by hand from the library's pull or lazy API.
- **borrowed**: for serde over the input `&str`, every field is a `Cow<str>` with
  `#[serde(borrow)]`, and tag strings use a `CowStr` newtype. A plain `&str` fails on the
  first escaped string, and a bare `Vec<Cow<str>>` always copies. simd-json and simdjson
  unescape into a buffer they own, so their borrowed rows use `&str`.
- **simd-json-derive** 0.18.0 depends on simd-json **0.17.3**, and its derive emits
  `::simd_json` paths. Its rows therefore live in the `sjd/` glue crate and run on simd-json
  0.17.3. The other simd-json rows run on 0.18.1.
- **simdjson-rust** is a pre-release (`0.4.0-alpha.2`) binding to C++ simdjson, built with
  `-march=native` (crate feature `native`). It needs a C++ toolchain and cmake.

The licenses of every crate in the build, transitive ones included, are permissive:
MIT, Apache-2.0, BSD-2-Clause, Zlib, BSL-1.0 or Unicode-3.0, alone or in combination.
