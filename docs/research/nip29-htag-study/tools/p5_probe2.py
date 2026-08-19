import asyncio, json, sys, re, os
import relaylib2 as R

PRIVATE = re.compile(r"//(localhost|127\.|0\.0\.0\.0|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1)")
TRANSIENT = ("proxy rejected", "ConnectionReset", "TimeoutError", "InvalidMessage", "HTTP 502",
             "HTTP 503", "HTTP 522", "HTTP 530", "ConnectionClosed")

targets = sorted(json.load(open("from_10009.json"))["relay_hits"].keys())
if os.path.exists("extra_relays.json"):
    targets = sorted(set(targets) | set(json.load(open("extra_relays.json"))))
targets = [t for t in targets if not PRIVATE.search(t) and t.startswith(("ws://", "wss://"))]
print(f"probing {len(targets)} relays", file=sys.stderr, flush=True)

FILTER = [{"kinds": [39000, 39001, 39002]}]

async def probe(url):
    for attempt in range(3):
        evs, err, meta = await R.fetch(url, FILTER, timeout=30, page_size=500,
                                       max_pages=40, max_events=40000)
        if not err or not any(s in err for s in TRANSIENT):
            return url, evs, err, meta
        await asyncio.sleep(3 * (attempt + 1))
    return url, evs, err, meta

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
                                  "authors": set(), "created_at": 0,
                                  "public": False, "private": False,
                                  "open": False, "closed": False})
        g["kinds"].add(e.get("kind"))
        g["authors"].add(e.get("pubkey"))
        if e.get("kind") == 39000:
            if e.get("created_at", 0) >= g["created_at"]:
                g["created_at"] = e.get("created_at", 0)
                g["name"] = name
                g["about"] = (e.get("content") or "")[:160]
                flags = {t[0] for t in tags if t}
                g["public"] = "public" in flags; g["private"] = "private" in flags
                g["open"] = "open" in flags;     g["closed"] = "closed" in flags
    return {k: {**v, "kinds": sorted(v["kinds"]), "authors": sorted(v["authors"])}
            for k, v in groups.items()}

async def main():
    out = {}
    done = 0
    async def one(u):
        nonlocal done
        url, evs, err, meta = await probe(u)
        out[url] = {"n_events": len(evs), "err": err, "meta": meta, "groups": summarize(evs)}
        done += 1
        if out[url]["groups"]:
            print(f"[{done}/{len(targets)}] {url:48s} {len(out[url]['groups']):5d} groups"
                  f" auth={meta['authed']} {err or ''}", file=sys.stderr, flush=True)
    await R.gather_limited([one(u) for u in targets], concurrency=25)
    json.dump(out, open("probe2.json", "w"))
    n = sum(1 for v in out.values() if v["groups"])
    print(f"\nDONE relays with groups: {n}  total group rows: "
          f"{sum(len(v['groups']) for v in out.values())}", file=sys.stderr, flush=True)

asyncio.run(main())
