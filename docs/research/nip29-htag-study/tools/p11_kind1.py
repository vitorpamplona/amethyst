"""Distinguish a dedicated NIP-29 relay from a general relay that merely stores
39000 copies: general relays are full of kind-1 notes, relay29-style ones are not."""
import asyncio, json, os, sys
import relaylib2 as R

probe = {}
for fn in ("probe2.json", "probe3.json"):
    if os.path.exists(fn): probe.update(json.load(open(fn)))
targets = sorted(r for r, v in probe.items() if v.get("groups"))
print(f"kind-1 test on {len(targets)} relays", file=sys.stderr, flush=True)

out, done = {}, 0
async def one(u):
    global done
    try:
        evs, err, meta = await asyncio.wait_for(
            R.fetch(u, [{"kinds": [1], "limit": 20}], timeout=15, page_size=20,
                    max_pages=2, max_events=25), timeout=60)
    except asyncio.TimeoutError:
        evs, err = [], "cap"
    done += 1
    out[u] = {"n_kind1": len(evs), "authors": len({e["pubkey"] for e in evs}), "err": err}
    if done % 200 == 0:
        print(f"... {done}/{len(targets)}", file=sys.stderr, flush=True)
        json.dump(out, open("kind1_partial.json", "w"))

async def main():
    await R.gather_limited([one(u) for u in targets], concurrency=40)
    json.dump(out, open("kind1.json", "w"))
    print("DONE", file=sys.stderr, flush=True)
asyncio.run(main())
