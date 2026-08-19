import asyncio, json, sys
from relaylib import fetch, gather_limited
SEEDS = ["wss://search-staging.brainstorm.world", "wss://relay.nostr.watch",
         "wss://history.nostr.watch", "wss://relay.damus.io", "wss://nos.lol",
         "wss://monitorlizard.nostr1.com", "wss://relay.nostr.band"]
async def main():
    res = await gather_limited([
        fetch(u, [{"kinds":[30166]}], timeout=40, page_by_until=True,
              page_size=500, max_pages=60, max_events=80000) for u in SEEDS], concurrency=7)
    all_ev = {}
    for u,(evs,err) in zip(SEEDS,res):
        print(f"{u:45s} {len(evs):6d} {err or ''}", file=sys.stderr)
        for e in evs: all_ev[e["id"]] = e
    json.dump(list(all_ev.values()), open("events_30166.json","w"))
    print("TOTAL 30166:", len(all_ev), file=sys.stderr)
asyncio.run(main())
