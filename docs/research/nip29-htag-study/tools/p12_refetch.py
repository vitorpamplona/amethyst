"""Re-fetch the raw kind-39000 events behind every reported collision, so each
row of the table can be verified id+signature rather than trusted."""
import asyncio, collections, json, sys
import relaylib2 as R

A = json.load(open("analysis5.json"))
shared = [r for r in A["rows"] if r["n_keys"] > 1]
probe = {}
for fn in ("probe2.json", "probe3.json"):
    probe.update(json.load(open(fn)))

# which relay URLs served each shared h
targets = collections.defaultdict(set)   # url -> {h}
for r in shared:
    for u, v in probe.items():
        if r["h"] in (v.get("groups") or {}):
            targets[u].add(r["h"])
print(f"{len(shared)} shared h-tags across {len(targets)} relay urls", file=sys.stderr, flush=True)

out, done = [], 0
async def one(u, hs):
    global done
    hs = sorted(hs)
    for i in range(0, len(hs), 100):
        chunk = hs[i:i+100]
        try:
            evs, err, _ = await asyncio.wait_for(
                R.fetch(u, [{"kinds": [39000], "#d": chunk}], timeout=20,
                        page_size=500, max_pages=4, max_events=5000), timeout=90)
        except asyncio.TimeoutError:
            evs = []
        for e in evs: out.append(e)
    done += 1
    if done % 100 == 0:
        print(f"... {done}/{len(targets)} events={len(out)}", file=sys.stderr, flush=True)

async def main():
    await R.gather_limited([one(u, hs) for u, hs in targets.items()], concurrency=30)
    dedup = {e["id"]: e for e in out}
    json.dump(list(dedup.values()), open("shared_39000_raw.json", "w"))
    print(f"DONE raw events: {len(dedup)}", file=sys.stderr, flush=True)
asyncio.run(main())
