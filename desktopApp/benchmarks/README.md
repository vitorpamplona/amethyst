# Launch Benchmark

Single-JVM warm benchmark for the cold-boot critical path. See
[`desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md`](../plans/2026-06-17-feat-app-launch-optimization-plan.md)
for the design.

## Run

```bash
AMETHYST_BENCH=true ./gradlew :desktopApp:test \
    --tests "*LaunchBenchmark.run" --rerun-tasks
```

Without the env var the test class skips itself silently so a normal
`./gradlew :desktopApp:test` stays fast.

## Output

A per-git-sha report lands at
`desktopApp/build/benchmarks/launch-<git-sha>.txt`. The benchmark prints the
report to stdout and atomically writes the file via
`Files.move(... ATOMIC_MOVE)` so a killed run does not pollute the trend
data with partial content.

## Reading the numbers

Each row reports `n`, min, q1, median, q3, max in milliseconds. The
benchmark drives the slim "non-Compose" cold-boot scenario:
`AccountManager.loadSavedAccount` → relay subscription via
`InProcessWebsocketBuilder` (fixture relay) → `DesktopLocalCache.consume`.

- `t_account_logged_in` — `AccountManager.accountState` reaches
  `LoggedIn(isReadOnly=true)`.
- `t_first_event` — first `kind:1` flows through `DesktopLocalCache.consume`.
- `t_n_events` — `n`th (default `n=10`) `kind:1` flows through the cache.

Numbers are dominated by the harness floor (`InProcessWebSocket` channel
hops, fixture-server REQ matching, coroutine dispatcher schedule). They
are most useful as a **regression guard** for code already in the
exercised path; they do **not** approximate a real Skia/Swing first paint.
Layered Compose-driven and JVM-fork variants are tracked as deferred
follow-ups in the plan.

## Snapshots committed here

- `baseline-main.txt` — pre-Phase-5.2 baseline.
- `with-phase5-fixes.txt` — post-Phase-5.2 snapshot.

Diff manually with `diff -u baseline-main.txt with-phase5-fixes.txt`.
