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

Every line is stored with 64 zero bytes after it. For the `string` rows, `&str` is the
same bytes, validated once at load. `std-from_utf8` shows what that validation costs.
`bytes-copy` rows (simd-json unescapes in place) copy the line into a reused scratch
buffer inside the timed loop.

| parser | crate | version | license | mode | input |
|---|---|---|---|---|---|
| `serde_json-typed` | serde_json | 1.0.154 | MIT OR Apache-2.0 | typed | bytes |
| `serde_json-typed-str` | serde_json | 1.0.154 | MIT OR Apache-2.0 | typed | string |
| `serde_json-borrowed` | serde_json | 1.0.154 | MIT OR Apache-2.0 | borrowed | bytes |
| `serde_json-value` | serde_json | 1.0.154 | MIT OR Apache-2.0 | dom | bytes |
| `serde_json_borrow-value` | serde_json_borrow | 0.9.0 | MIT | dom | bytes |
| `simd_json-typed` | simd-json | 0.18.1 | Apache-2.0 OR MIT | typed | bytes-copy |
| `simd_json-borrowed-struct` | simd-json | 0.18.1 | Apache-2.0 OR MIT | borrowed | bytes-copy |
| `simd_json-borrowed` | simd-json | 0.18.1 | Apache-2.0 OR MIT | dom | bytes-copy |
| `simd_json-owned-value` | simd-json | 0.18.1 | Apache-2.0 OR MIT | dom | bytes-copy |
| `simd_json-tape` | simd-json | 0.18.1 | Apache-2.0 OR MIT | dom | bytes-copy |
| `simd_json_derive-typed` | simd-json-derive | 0.18.0 | Apache-2.0 OR MIT | typed | bytes-copy |
| `simd_json_derive-borrowed` | simd-json-derive | 0.18.0 | Apache-2.0 OR MIT | borrowed | bytes-copy |
| `sonic_rs-typed` | sonic-rs | 0.5.10 | Apache-2.0 | typed | bytes |
| `sonic_rs-borrowed` | sonic-rs | 0.5.10 | Apache-2.0 | borrowed | bytes |
| `sonic_rs-value` | sonic-rs | 0.5.10 | Apache-2.0 | dom | bytes |
| `sonic_rs-lazy` | sonic-rs | 0.5.10 | Apache-2.0 | typed | bytes |
| `jiter-value` | jiter | 0.17.0 | MIT | dom | bytes |
| `jiter-value-scratch` | jiter | 0.17.0 | MIT | dom | bytes |
| `jiter-typed` | jiter | 0.17.0 | MIT | typed | bytes |
| `jiter-serde-typed` | jiter | 0.17.0 | MIT | typed | bytes |
| `jiter-serde-borrowed` | jiter | 0.17.0 | MIT | borrowed | bytes |
| `json-dom` | json | 0.12.4 | MIT OR Apache-2.0 | dom | string |
| `miniserde-typed` | miniserde | 0.1.46 | MIT OR Apache-2.0 | typed | string |
| `nanoserde-typed` | nanoserde | 0.2.1 | MIT OR Apache-2.0 | typed | string |
| `tinyjson-dom` | tinyjson | 2.5.1 | MIT | dom | string |
| `hifijson-value` | hifijson | 0.5.0 | MIT | dom | bytes |
| `hifijson-serde-typed` | hifijson | 0.5.0 | MIT | typed | bytes |
| `struson-typed` | struson | 0.7.2 | MIT OR Apache-2.0 | typed | bytes |
| `simdjson_rust-typed` | simdjson-rust | 0.4.0-alpha.2 | Apache-2.0 | typed | bytes-padded |
| `simdjson_rust-borrowed` | simdjson-rust | 0.4.0-alpha.2 | Apache-2.0 | borrowed | bytes-padded |
| `simdjson_rust-dom` | simdjson-rust | 0.4.0-alpha.2 | Apache-2.0 | dom | bytes-padded |
| `std-from_utf8` | std (rustc) | toolchain | MIT OR Apache-2.0 | baseline | bytes |

Each row's `notes` field in the results says exactly what it calls. Some details:

- **typed**: `Event { id, pubkey, sig, content: String, created_at: u64, kind: u32, tags: Vec<Vec<String>> }`.
  One struct derives serde, miniserde and nanoserde. `jiter-typed`, `struson-typed` and
  `sonic_rs-lazy` fill it by hand from the library's pull or lazy API.
- **borrowed**: for serde over the raw input, every field is a `Cow<str>` with
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
