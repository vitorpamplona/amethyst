# jsonBench: Python harness

`bench.py` follows [`../HARNESS.md`](../HARNESS.md): same flags, digest, timing and
output schema. Output goes to `results/python.json`.

```bash
jsonBench/python/run.sh                 # full run
jsonBench/python/run.sh --quick         # smoke test
jsonBench/python/run.sh --parsers orjson,msgspec-typed --workloads notes
```

`run.sh` creates `.venv` (git-ignored) with `python3.13` (override with `PYTHON=`) and
installs the pinned wheels from `requirements.txt`, wheels only. It reinstalls when
`requirements.txt` changes. Each parser runs in its own interpreter (`--child <id>`).

## Parsers

Versions and licenses come from each wheel's metadata and the LICENSE files it ships.

| id | package | version | license | mode | input |
|---|---|---|---|---|---|
| `json-stdlib` | `json` (stdlib) | CPython 3.13 | PSF-2.0 | typed | str |
| `json-stdlib-bytes` | `json` (stdlib) | CPython 3.13 | PSF-2.0 | typed | bytes |
| `baseline-utf8-decode` | `bytes.decode` | CPython 3.13 | PSF-2.0 | baseline | bytes |
| `orjson` | orjson | 3.13.0 | MPL-2.0 AND (Apache-2.0 OR MIT) | typed | bytes |
| `ujson` | ujson | 6.0.0 | BSD-3-Clause AND TCL | typed | str |
| `msgspec-typed` | msgspec, `Decoder(Event)` with `Event(msgspec.Struct)` | 0.22.0 | BSD-3-Clause | typed | bytes |
| `msgspec-generic` | msgspec, `Decoder()` -> dict | 0.22.0 | BSD-3-Clause | typed | bytes |
| `pysimdjson` | pysimdjson, `Parser().parse` -> lazy proxy | 7.0.2 | MIT (bundles simdjson, Apache-2.0) | dom | bytes |
| `pysimdjson-recursive` | pysimdjson, `parse(b, recursive=True)` -> dict | 7.0.2 | MIT | typed | bytes |
| `cysimdjson` | cysimdjson, `JSONParser().parse` -> lazy proxy | 26.27 (simdjson 3.8.0) | Apache-2.0 | dom | bytes |
| `python-rapidjson` | python-rapidjson | 1.25 | MIT | typed | str |
| `jiter` | jiter (pydantic), `from_json` | 0.17.0 | MIT | typed | bytes |
| `simplejson` | simplejson with C speedups | 4.2.0 | MIT OR AFL-2.1 | typed | str |
| `yyjson` | yyjson (py_yyjson), `loads` | 4.0.6 | MIT (bundles yyjson, MIT) | typed | bytes |

A `dict` counts as `typed` in Python: it is what an app keeps. The two `dom` parsers
reuse one parser, so a document is only valid until the next parse. They are fast
because the timed loop only reads `kind` and the tag count through the proxy, and
nothing else gets converted to Python objects.

All licenses are permissive. ujson's `TCL` part covers its numeric decoder, which is
derived from Tcl's BSD-style license. orjson's MPL-2.0 is file-level copyleft on its
own sources.

## Memory and GC

- `gc_count` / `gc_ms`: `gc.callbacks` counts the cyclic collector's runs and times them
  during the measured rounds. Expect 0. CPython frees parsed events by reference
  counting as soon as the loop drops them, so the gen-0 allocation counter never reaches
  its threshold.
- `alloc_bytes_per_event`, `allocs_per_event`: null. CPython has no cumulative
  allocation counter, and `tracemalloc` slows the parse too much to leave on during
  timing.
