import asyncio, json, os, sys
import relaylib2 as R

def summarize(evs):
    groups = {}
    for e in evs:
        d = name = None
        tags = e.get("tags", [])
        for t in tags:
            if not t: continue
            if t[0] == "d" and len(t) > 1: d = t[1]
            elif t[0] == "name" and len(t) > 1: name = t[1]
        if d is None: continue
        g = groups.setdefault(d, {"kinds": set(), "name": None, "about": None,
                                  "authors": set(), "created_at": 0, "public": False,
                                  "private": False, "open": False, "closed": False})
        g["kinds"].add(e.get("kind")); g["authors"].add(e.get("pubkey"))
        if e.get("kind") == 39000 and e.get("created_at", 0) >= g["created_at"]:
            g["created_at"] = e.get("created_at", 0)
            g["name"] = name
            g["about"] = (e.get("content") or "")[:160]
            f = {t[0] for t in tags if t}
            g["public"] = "public" in f; g["private"] = "private" in f
            g["open"] = "open" in f;     g["closed"] = "closed" in f
    return {k: {**v, "kinds": sorted(v["kinds"]), "authors": sorted(v["authors"])}
            for k, v in groups.items()}

sweep = {}
for fn in ("sweep.json", "sweep_partial.json"):
    if os.path.exists(fn): sweep.update(json.load(open(fn)))
targets = sorted(u for u, v in sweep.items() if v.get("hit"))
print(f"probe3: {len(targets)} sweep hits", file=sys.stderr, flush=True)

out, done = {}, 0
async def one(u):
    global done
    try:
        evs, err, meta = await asyncio.wait_for(
            R.fetch(u, [{"kinds": [39000, 39001, 39002]}], timeout=25,
                    page_size=500, max_pages=25, max_events=30000),
            timeout=180)
    except asyncio.TimeoutError:
        evs, err, meta = [], "wall-clock cap", {"authed": False}
    done += 1
    out[u] = {"n_events": len(evs), "err": err, "meta": meta, "groups": summarize(evs)}
    if done % 100 == 0:
        print(f"... {done}/{len(targets)}", file=sys.stderr, flush=True)
        json.dump(out, open("probe3_partial.json", "w"))

async def main():
    await R.gather_limited([one(u) for u in targets], concurrency=35)
    json.dump(out, open("probe3.json", "w"))
    print(f"DONE relays with groups: {sum(1 for v in out.values() if v['groups'])}",
          file=sys.stderr, flush=True)
asyncio.run(main())
