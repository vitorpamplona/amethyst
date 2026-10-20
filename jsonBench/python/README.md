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

## Input: the text frame

A WebSocket text frame reaches a Python app as a `str` (`websockets` `recv()`, aiohttp
`WSMessage.data`), so every parser starts from a `str`. `input` follows
[`HARNESS.md`](../HARNESS.md):

- `string`: the parser takes the `str` itself (json, orjson, ujson, msgspec, pysimdjson,
  cysimdjson's `parse_string`, rapidjson, simplejson).
- `string→bytes`: the parser only reads bytes, so `s.encode()` runs inside the timed loop
  (jiter: `from_json` refuses a `str`; yyjson: `loads(str)` returns mojibake for non-ASCII,
  `'{"a":"é"}'` comes back as `'Ã©'`, so it gets the encoded bytes).

The old `json-stdlib-bytes` and `baseline-utf8-decode` rows were dropped (they started
from bytes), and orjson, msgspec and the simdjson bindings now take the `str` instead of
bytes. `baseline-utf8-encode` is the `s.encode()` step alone.

**Fresh `str` objects every round.** CPython caches a `str`'s UTF-8 form on the object the
first time the C API asks for it (`PyUnicode_AsUTF8AndSize`). orjson, msgspec, rapidjson,
pysimdjson and cysimdjson do (checked with `sys.getsizeof` before and after one parse);
json, ujson, simplejson and `str.encode` do not. Rounds over the same `str` objects would
give those parsers the encode for free from the second parse on, while a socket hands over
a new `str` per frame. So every round, warm-up included, runs on a new list of `str`s
decoded from the raw lines outside the timer, and each round is timed on its own
(`--min-time-ms` counts timed rounds only). ASCII-only strings have no cache to fill, since
their UTF-8 is their storage; on the `all` workload 7% of the lines (10% of the bytes)
are non-ASCII, so the effect is bounded; a one-off check on this shared machine was too
noisy to size it (orjson 2012 against 2382 ns/event reused vs fresh, msgspec the other way
round).

`websockets` can also hand over the raw bytes of a text frame (`recv(decode=False)`),
which would make bytes free for jiter and yyjson; that is not the default path and is not
measured.

## Parsers

Versions and licenses come from each wheel's metadata and the LICENSE files it ships.

| id | package | version | license | mode | input |
|---|---|---|---|---|---|
| `json-stdlib` | `json` (stdlib) | CPython 3.13 | PSF-2.0 | typed | string |
| `baseline-utf8-encode` | `str.encode` | CPython 3.13 | PSF-2.0 | baseline | string→bytes |
| `orjson` | orjson | 3.13.0 | MPL-2.0 AND (Apache-2.0 OR MIT) | typed | string |
| `ujson` | ujson | 6.0.0 | BSD-3-Clause AND TCL | typed | string |
| `msgspec-typed` | msgspec, `Decoder(Event)` with `Event(msgspec.Struct)` | 0.22.0 | BSD-3-Clause | typed | string |
| `msgspec-generic` | msgspec, `Decoder()` -> dict | 0.22.0 | BSD-3-Clause | typed | string |
| `pysimdjson` | pysimdjson, `Parser().parse` -> lazy proxy | 7.0.2 | MIT (bundles simdjson, Apache-2.0) | dom | string |
| `pysimdjson-recursive` | pysimdjson, `parse(s, recursive=True)` -> dict | 7.0.2 | MIT | typed | string |
| `cysimdjson` | cysimdjson, `JSONParser().parse_string` -> lazy proxy | 26.27 (simdjson 3.8.0) | Apache-2.0 | dom | string |
| `python-rapidjson` | python-rapidjson | 1.25 | MIT | typed | string |
| `jiter` | jiter (pydantic), `from_json(s.encode())` | 0.17.0 | MIT | typed | string→bytes |
| `simplejson` | simplejson with C speedups | 4.2.0 | MIT OR AFL-2.1 | typed | string |
| `yyjson` | yyjson (py_yyjson), `loads(s.encode())` | 4.0.6 | MIT (bundles yyjson, MIT) | typed | string→bytes |

A `dict` counts as `typed` in Python: it is what an app keeps. The two `dom` parsers
reuse one parser, so a document is only valid until the next parse. They are fast
because the timed loop only reads `kind` and the tag count through the proxy, and
nothing else gets converted to Python objects.

All licenses are permissive. ujson's `TCL` part covers its numeric decoder, which is
derived from Tcl's BSD-style license. orjson's MPL-2.0 is file-level copyleft on its
own sources.

## Memory and GC

- `gc_count` / `gc_ms`: `gc.callbacks` counts the cyclic collector's runs and times them
  during the timed rounds. Expect 0. CPython frees parsed events by reference counting as
  soon as the loop drops them, so the gen-0 allocation counter never reaches its
  threshold.
- `alloc_bytes_per_event`, `allocs_per_event`: null. CPython has no cumulative
  allocation counter, and `tracemalloc` slows the parse too much to leave on during
  timing.
- `retained_bytes_per_event`: after timing, one more pass under `tracemalloc` parses every
  event from a fresh `str` and keeps all results in a list allocated before tracing
  starts (so the container is not counted); the value is `get_traced_memory()` current
  after the pass, divided by the event count. Each frame `str` is created before tracing
  and dropped right after its parse, so neither it nor a UTF-8 cache a parser hung on it
  is counted, as in a client that drops the frame once parsed. Objects that already
  existed are free (small ints, interned or cached keys), which is why jiter retains the
  least: its default `cache_mode='all'` also caches string values shorter than 64 bytes
  (checked: a 63-character value comes back as the same object twice, a 64-character one
  does not), so repeated short tag values and relay URLs are shared, but not the
  64-character hex ids and pubkeys. Null for the baseline and for the
  two `dom` rows (`pysimdjson`, `cysimdjson`): their results are views into a reused
  parser document, which cannot be kept.
