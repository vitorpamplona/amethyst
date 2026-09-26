#!/usr/bin/env bash
#
# The reverse interop direction: ts-mls checks OUR output.
#
# `quartz/cordn`'s own tests prove we can read what ts-mls writes. That is half of
# interoperating -- a client can parse everything correctly and still emit
# something nobody accepts, and that failure keeps our tests green while every
# peer silently drops us. This script closes the loop by handing our artifacts
# to the implementation cordn's client actually runs.
#
# It is opt-in because it needs two checkouts and a Node toolchain, which CI
# for this repo does not carry. `KotlinArtifactProducerTest` writes the
# artifacts unconditionally; only the verification below needs the extras.
#
# Usage:
#   quartz/interop/verify-with-ts-mls.sh
#
# Environment:
#   CORDN_DIR       cordn checkout with `pnpm install` run (default ../cordn)
#   STAIRCASE_DIR   staircase checkout, for its verify.ts (default ../staircase)
#
#   git clone https://github.com/Cordn-msg/cordn            ../cordn && (cd ../cordn && pnpm install)
#   git clone https://code.relay.tools/opensauce/staircase  ../staircase
#
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
CORDN="${CORDN_DIR:-$REPO/../cordn}"
STAIRCASE="${STAIRCASE_DIR:-$REPO/../staircase}"
OUT="$REPO/quartz/build/interop"
FIXTURES="$REPO/quartz/src/commonTest/resources/tsmls"
VERIFY="$STAIRCASE/conformance/fixtures-gen/verify.ts"

die() { echo "error: $*" >&2; exit 2; }

[ -d "$CORDN/packages/cli" ] || die "no cordn checkout at $CORDN (set CORDN_DIR)"
[ -d "$CORDN/node_modules" ] || die "run 'pnpm install' in $CORDN first"
[ -f "$VERIFY" ] || die "no staircase checkout at $STAIRCASE (set STAIRCASE_DIR)"

# Node, not bun. staircase's own run.sh uses bun, and bun's WebCrypto has no
# X25519 DHKEM, so ts-mls there cannot open a Welcome at all -- not even one it
# produced itself. The failure surfaces as `DecapError: The algorithm is not
# supported` deep inside HPKE and looks exactly like a wire-format mismatch,
# which is worth knowing before spending an afternoon on it.
command -v node >/dev/null || die "node is required"

echo "==> producing artifacts from quartz"
"$REPO/gradlew" -p "$REPO" :quartz:jvmTest --tests '*KotlinArtifactProducerTest*' --rerun-tasks -q

# verify.ts reads <root>/gen for the ts-mls KeyPackage it joins with, and
# <root>/kotlin for everything we produced.
echo "==> assembling fixture root at $OUT"
mkdir -p "$OUT/gen"
cp "$FIXTURES/bob2-kp.bin" "$FIXTURES/bob2-privkp.bin" "$OUT/gen/"

STAGE="$CORDN/packages/cli/.staircase"
mkdir -p "$STAGE"
cp "$VERIFY" "$STAGE/"

echo "==> running ts-mls against them"
cd "$CORDN/packages/cli"
node --experimental-strip-types "$STAGE/verify.ts" "$OUT"
