#!/bin/bash
# PreToolUse gate: don't hand-roll a Nostr relay client.
#
# This repo ships `amy` (cli/) precisely so humans, agents and interop tests
# drive relays through quartz + commons instead of re-implementing NIP-01 in
# whatever language is closest to hand. The failure this catches is subtle:
# the CLAUDE.md rule reads as being about *feature code*, so a throwaway
# analysis script in Python or Node feels exempt — and a hand-rolled REQ loop
# lands anyway, with its own pagination, AUTH and verification bugs.
#
# Fires when a Bash/Write/Edit call is about to create code that opens a
# websocket to a relay or speaks NIP-01 frames directly. Exempt: the modules
# that legitimately implement the protocol, and an explicit opt-out.
set -uo pipefail

payload="$(cat)"

verdict="$(
  printf '%s' "$payload" | python3 -c '
import json, re, sys

try:
    data = json.load(sys.stdin)
except Exception:
    print("allow"); sys.exit(0)

tool = data.get("tool_name", "")
if tool not in ("Bash", "Write", "Edit"):
    print("allow"); sys.exit(0)

ti = data.get("tool_input") or {}
blob = " ".join(
    str(ti.get(k, ""))
    for k in ("command", "content", "new_string", "file_path")
)

# Documented escape hatch for the rare genuine raw-socket need.
if "AMY_ALLOW_RAW_RELAY" in blob:
    print("allow"); sys.exit(0)

# Modules that ARE the relay stack (or its benchmarks) may speak raw NIP-01.
EXEMPT = ("quartz/", "geode/", "quic/", "nestsClient/", "cli/", "commons/",
          "relayBench/", "benchmark/", "amethyst/", "desktopApp/")
if any(m in blob for m in EXEMPT):
    print("allow"); sys.exit(0)

# A relay endpoint plus a hand-written client or NIP-01 frame.
has_relay = re.search(r"wss?://", blob) is not None
CLIENT = (
    r"websockets\.connect", r"websocket\.WebSocketApp", r"new\s+WebSocket\(",
    r"\bwebsocat\b", r"require\([\"\x27]ws[\"\x27]\)", r"from\s+websockets",
    r"import\s+websockets", r"nostr-tools",
)
FRAME = (r"\[\s*[\"\x27]REQ[\"\x27]", r"\[\s*[\"\x27]EVENT[\"\x27]",
         r"\[\s*[\"\x27]COUNT[\"\x27]", r"\[\s*[\"\x27]AUTH[\"\x27]")
hit_client = any(re.search(p, blob) for p in CLIENT)
hit_frame = any(re.search(p, blob) for p in FRAME)

print("deny" if (has_relay and (hit_client or hit_frame)) or (hit_client and hit_frame) else "allow")
'
)"

if [ "$verdict" != "deny" ]; then
  exit 0
fi

cat >&2 <<'MSG'
Blocked: this looks like a hand-rolled Nostr relay client.

This repo already drives relays through `amy` (cli/), which is built on the
same quartz + commons code the apps use — and which already handles the
things a fresh script gets wrong: NIP-42 AUTH, per-relay limit caps,
until-pagination, event verification, and the outbox model.

  amy fetch --kind K --relay wss://…  --all     one-shot query, paginated
  amy subscribe --kind K --relay wss://…        streaming
  amy count  --kind K --relay wss://…           NIP-45
  amy publish [EVENT|--file PATH] --relay wss://…   broadcast one or many
  amy sync   --relay wss://… [--up|--down]      NIP-77 negentropy reconcile
  amy store stat                                what the local store holds

`amy --help`, or the `/amy-expert` skill, for the full surface. Output is
human text by default and one JSON object per run under `--json`, so it
pipes into jq/python for the analysis half.

If you genuinely need a raw socket (protocol conformance tests, a new
transport), put AMY_ALLOW_RAW_RELAY=1 in the command to bypass this gate.
MSG
exit 2
