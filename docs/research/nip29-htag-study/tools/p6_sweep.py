"""Cheap NIP-29 detection sweep across every relay NIP-66 knows about."""
import asyncio, json, sys, re, os
import relaylib2 as R

SKIP = re.compile(r"//(localhost|127\.|0\.0\.0\.0|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1)"
                  r"|\.onion|\.i2p")

allr = json.load(open("nip66_relays.json"))
known = set(json.load(open("from_10009.json"))["relay_hits"].keys())
targets = sorted({r for r in allr if not SKIP.search(r) and r.startswith(("ws://", "wss://"))} - known)
print(f"sweeping {len(targets)} relays", file=sys.stderr, flush=True)

out, done = {}, 0
async def one(u):
    global done
    evs, err, meta = await R.fetch(u, [{"kinds": [39000], "limit": 1}], timeout=12,
                                   page_size=1, max_pages=2, max_events=5)
    done += 1
    out[u] = {"hit": len(evs) > 0, "err": err, "authed": meta["authed"]}
    if evs:
        print(f"HIT [{done}/{len(targets)}] {u}", file=sys.stderr, flush=True)
    if done % 500 == 0:
        print(f"... {done}/{len(targets)} hits={sum(1 for v in out.values() if v['hit'])}",
              file=sys.stderr, flush=True)
        json.dump(out, open("sweep_partial.json", "w"))

async def main():
    await R.gather_limited([one(u) for u in targets], concurrency=60)
    json.dump(out, open("sweep.json", "w"))
    print(f"DONE hits={sum(1 for v in out.values() if v['hit'])}", file=sys.stderr, flush=True)

asyncio.run(main())
