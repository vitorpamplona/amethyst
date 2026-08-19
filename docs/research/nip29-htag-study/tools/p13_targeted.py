"""Pull every copy of the colliding group events by explicit #d filter."""
import asyncio, json, sys
import relaylib2 as R

A = json.load(open("analysis5.json"))
hs = sorted({r["h"] for r in A["rows"] if r["n_keys"] > 1})
AGG = ["wss://search-staging.brainstorm.world", "wss://relay.damus.io", "wss://nos.lol",
       "wss://relay.mostr.pub", "wss://nostr.mom", "wss://relay.ditto.pub",
       "wss://relay.dreamith.to", "wss://nostr.oxtr.dev", "wss://relay.primal.net",
       "wss://nostr.wine", "wss://relay.nostr.band", "wss://syb.lol",
       "wss://relay.gulugulu.moe", "wss://relay.highlighter.com",
       "wss://nostr.superfriends.online", "wss://relay.beginningend.com",
       "wss://relay.nearhood.co.uk", "wss://relay.momostr.pink"]
AGG += sorted({u for k in A["key_urls"].values() for u in k})
AGG = sorted(set(AGG))
print(f"{len(hs)} h-tags x {len(AGG)} relays", file=sys.stderr, flush=True)

out = []
async def one(u):
    for i in range(0, len(hs), 60):
        chunk = hs[i:i+60]
        try:
            evs, err, _ = await asyncio.wait_for(
                R.fetch(u, [{"kinds": [39000, 39001, 39002], "#d": chunk}], timeout=20,
                        page_size=500, max_pages=4, max_events=5000), timeout=90)
        except asyncio.TimeoutError:
            evs = []
        out.extend(evs)

async def main():
    await R.gather_limited([one(u) for u in AGG], concurrency=25)
    d = {e["id"]: e for e in out}
    json.dump(list(d.values()), open("targeted_raw.json", "w"))
    print(f"DONE unique events: {len(d)}", file=sys.stderr, flush=True)
asyncio.run(main())
