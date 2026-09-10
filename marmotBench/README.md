# marmotBench — quartz Marmot vs MDK, head to head

Measures the same Marmot/MLS operations on both engines so "are we as fast as
the reference" has an answer instead of an opinion.

The quartz half lives here. The MDK half is its own criterion suite:

```sh
# quartz
./gradlew :marmotBench:run                     # table
./gradlew :marmotBench:run --args=--json       # machine-readable

# MDK (the reference)
cd <mdk> && cargo bench -p cgka-engine --bench group_lifecycle
```

## What is compared

| this module           | MDK bench                   |
|-----------------------|-----------------------------|
| `create_group/N`      | `bench_create_group`        |
| `join_welcome`        | `bench_join_welcome`        |
| `send_app_message`    | `bench_app_message_send`    |
| `ingest_app_message`  | `bench_app_message_ingest`  |

Both sides exclude transport crypto and run over in-memory storage, so what is
measured is the engine's own CPU cost. Setup is outside the measured window on
both sides — criterion's `iter_batched(.., PerIteration)` there, an explicit
`setup` lambda here.

**One shape difference, deliberately not hidden:** MDK folds invitees into the
founding group (`FoundingGroupCreated`), while we create at epoch 0 and add in
a second commit to epoch 1. `create_group/N` therefore includes one more commit
on our side. That is a real cost, and averaging it away would be the wrong kind
of favourable.

## Why allocation is reported next to latency

The brief is "as fast if not faster, while avoiding GC as much as possible",
and those are two different measurements. A JVM can win a microbenchmark while
allocating tens of times more per operation; the bill arrives later as GC
pauses on a phone, in a frame the benchmark never renders. So every row carries
**bytes allocated per operation** from `com.sun.management.ThreadMXBean`
(in the JDK — no dependency), alongside p50/p90/p99.

That counter is per-thread, which is why every benchmark body runs inline on
the harness thread via `runBlocking`. Work dispatched elsewhere would allocate
off-book and read as free.

The JVM runs with `-XX:+UseSerialGC` on a fixed 4g heap: the point is to keep
allocation attributable to the benchmark thread and to keep a collection from
landing inside a measured sample and corrupting the percentile it falls in.

## Reading the numbers honestly

- Rust has no GC, so `alloc/op` has no MDK counterpart. It is not a
  head-to-head column — it is our own regression signal, and the number to
  drive down.
- Latency across a JVM and a Rust binary on the same host is a fair comparison
  of *this* workload on *this* machine. It is not a language benchmark.
- `create_group/32` builds 32 KeyPackages in setup. That cost is excluded, but
  it makes each iteration expensive to prepare — hence the low iteration count.
