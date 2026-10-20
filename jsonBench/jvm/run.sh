#!/usr/bin/env bash
#
# jsonBench JVM harness: builds :jsonBench and runs it with the HARNESS.md flags.
#
#   ./jsonBench/jvm/run.sh                      # everything -> jsonBench/results/jvm.json
#   ./jsonBench/jvm/run.sh --quick              # smoke run
#   ./jsonBench/jvm/run.sh --parsers quartz-jackson,fastjson2-reader --workloads all,notes
#   ./jsonBench/jvm/run.sh --list
#
# The installed `jsonbench` script carries the JVM flags (-Xms2g -Xmx2g -XX:+UseG1GC
# --add-modules jdk.incubator.vector); the parent process forks one child JVM per parser
# with those same flags. Extra JVM flags can be passed through JAVA_OPTS.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

(cd "$ROOT" && ./gradlew -q :jsonBench:installDist)

# Defaults are absolute so the launcher works from any directory; flags given on the
# command line come later and win.
exec "$HERE/build/install/jsonbench/bin/jsonbench" \
    --corpus "$ROOT/jsonBench/.corpus" \
    --out "$ROOT/jsonBench/results/jvm.json" \
    "$@"
