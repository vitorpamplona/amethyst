# jsonBench: JVM harness

Which JSON parser on the JVM decodes Nostr events fastest, with the least garbage, and
how does that compare with what Amethyst ships (`quartz-jackson`)? This is the `jvm`
harness of [jsonBench](../HARNESS.md); it follows that contract (flags, digest, timing,
output schema).

## Run

```bash
python3 jsonBench/corpus/prepare.py        # once: builds jsonBench/.corpus/
./jsonBench/jvm/run.sh                     # full run -> jsonBench/results/jvm.json
./jsonBench/jvm/run.sh --quick             # smoke run (seconds per parser, noisy numbers)
./jsonBench/jvm/run.sh --parsers quartz-jackson,fastjson2-reader --workloads all,notes
./jsonBench/jvm/run.sh --list              # parser ids
```

`run.sh` builds `:jsonBench:installDist` (Gradle module, project dir `jsonBench/jvm`) and
runs `build/install/jsonbench/bin/jsonbench`. Flags: `--corpus`, `--out`, `--workloads`,
`--parsers`, `--warmup-ms`, `--min-time-ms`, `--min-rounds`, `--quick`, `--list`.
Extra JVM flags go through `JAVA_OPTS`.

## How it measures

- **One JVM per parser.** The process you start only orchestrates: for each parser it
  forks a child with the same `java`, classpath and JVM flags (`-Xms2g -Xmx2g
  -XX:+UseG1GC --add-modules jdk.incubator.vector`). The child verifies and times that
  parser on every selected workload and hands its rows back through a temp file. The
  harness's own manifest/result JSON goes through a tiny hand-written reader/writer
  (`MiniJson.kt`), so no measured library is loaded or JIT-profiled before its turn.
- **Monomorphic hot loop.** Each parser is its own class whose `parseAll(inputs)` holds
  the loop over the workload; the sink folds `kind + tags.size + content.length` (DOM
  parsers: `kind + tag count`).
- **Verification first.** Every event is decoded once into an `Event` and fed to the
  SHA-256 digest of HARNESS.md; DOM results are converted to an `Event` for this step
  only. A parser that throws is not timed.
- **Memory.** Over the measured rounds only: `ThreadMXBean.getThreadAllocatedBytes`
  for the benchmark thread (`allocs_per_event` is null: the JVM does not count
  allocations) and the summed `GarbageCollectorMXBean` count/time deltas. `System.gc()`
  runs once between warm-up and measurement.

## Parsers

Versions come from the `jsonBench only` block of `gradle/libs.versions.toml` (Jackson
and kotlinx.serialization use the app's versions). Licenses were checked against each
artifact's published POM.

| id | library | version | license | mode | input |
|---|---|---|---|---|---|
| `quartz-jackson` | Quartz `JacksonMapper` (jackson-databind + `EventDeserializer`, intern + `EventFactory`) | 2.22.3 | Apache-2.0 (Quartz: MIT) | typed | string |
| `quartz-kotlinx` | Quartz `KotlinSerializationMapper` (the iOS/native path) | 1.11.0 | Apache-2.0 (Quartz: MIT) | typed | string |
| `jackson-streaming-bytes` / `-string` | jackson-core `JsonFactory`, hand loop | 2.22.3 | Apache-2.0 | typed | bytes / string |
| `jackson-tree` | jackson-databind `readTree` | 2.22.3 | Apache-2.0 | dom | bytes |
| `kotlinx-typed` / `kotlinx-stream` / `kotlinx-element` | kotlinx-serialization-json | 1.11.0 | Apache-2.0 | typed / typed / dom | string / bytes / string |
| `gson-streaming` / `gson-tree` / `gson-reflect` | Gson | 2.14.0 | Apache-2.0 | typed / dom / typed | string |
| `moshi-streaming` | Moshi `JsonReader` + `Options` over okio | 1.15.2 | Apache-2.0 | typed | bytes |
| `fastjson2-reflect-bytes` / `-string` / `fastjson2-reader` / `fastjson2-dom` | fastjson2 | 2.0.66 | Apache-2.0 | typed / typed / typed / dom | bytes / string / bytes / bytes |
| `dsljson-bytes` / `dsljson-reflect` | DSL-JSON (hand loop / runtime reflection, no annotation processor) | 2.0.2 | BSD-3-Clause | typed | bytes |
| `simdjson-java-dom` / `simdjson-java-schema` | simdjson-java | 0.3.0 | Apache-2.0 | dom / typed | bytes-padded |
| `orgjson-dom` | org.json (JSON-java) | 20260814 | Public domain (`LicenseRef-Public-Domain`) | dom | string |
| `jsoniter-iterator` | jsoniter (Java) iterator API | 0.9.23 | MIT | typed | bytes |
| `jsoniter-scala` | jsoniter-scala, `JsonCodecMaker.make` (Scala 3 macro) | 2.41.6 | MIT (+ Scala 3.3.8 library, Apache-2.0) | typed | bytes |
| `parsson-streaming` | Eclipse Parsson (Jakarta JSON-P) | 1.1.9 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 | typed | string |
| `minimaljson-dom` | minimal-json | 0.9.5 | MIT | dom | string |
| `jsonsmart-dom` | json-smart (RFC 4627 mode) | 2.6.0 | Apache-2.0 | dom | string |
| `baseline-utf8-decode` / `-encode` | JDK `String(bytes, UTF_8)` / `encodeToByteArray()` | JDK | GPL-2.0 WITH Classpath-exception | baseline | bytes / string |

Parsson and jakarta.json-api are EPL-2.0 / GPL-2.0-with-Classpath-exception (weak
copyleft); everything else is permissive. None of it ships: this module is never a
dependency of the app.

## Caveats

- **HotSpot is not ART.** These are C2-compiled numbers on a desktop/server JVM.
  Android runs ART (AOT + JIT, a different GC, no C2); orderings mostly carry over for
  scalar-heavy parsers but absolute numbers do not, and anything that leans on
  HotSpot-only machinery does not apply to the app at all.
- **simdjson-java needs `jdk.incubator.vector`**, which does not exist on Android. It is
  here to show the ceiling. 0.4.0 targets Java 24, so the harness (Java 21) uses 0.3.0,
  whose schema-based parse calls `ClassResolver.reset()` on every document and therefore
  re-resolves the record reflectively per event; that is why `simdjson-java-schema` is
  slow and allocation-heavy. The DOM's `JsonValue` points into a reused tape that the
  next parse overwrites.
- **fastjson2 reflect** generates readers with ASM at runtime; Android has its own
  `fastjson2` artifact variants (`.android5`/`.android8`) that cannot do that.
- **jsoniter-scala** needs the Scala 3 runtime on the classpath; the codec is compiled
  by Gradle's `scala` plugin from `src/main/scala`, and the Kotlin side calls it through
  JDK functional interfaces (`JsoniterScala.kt`).
- **DSL-JSON runtime binding** ignores `@JsonAttribute(name)` on a public field, so the
  POJO's field is literally named `created_at`.
- `orgjson-dom` is the upstream JSON-java library, not the older fork in Android's
  `android.jar`.
- Run on a quiet machine: other processes on the same cores move the medians.
