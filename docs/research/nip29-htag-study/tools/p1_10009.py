import asyncio, json, sys
from relaylib import fetch, gather_limited

SEEDS = [
 "wss://search-staging.brainstorm.world",
 "wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net",
 "wss://relay.nostr.band", "wss://nostr.wine", "wss://purplepag.es",
 "wss://offchain.pub", "wss://relay.snort.social", "wss://nostr.mom",
 "wss://relay.nostr.bg", "wss://nostr-pub.wellorder.net", "wss://relay.mostr.pub",
 "wss://relay.0xchat.com", "wss://groups.0xchat.com", "wss://relay.nos.social",
 "wss://nostr21.com", "wss://relay.wellorder.net", "wss://nostrelites.org",
 "wss://relay.nostrplebs.com", "wss://history.nostr.watch", "wss://relay.nostr.watch",
 "wss://relay.utxo.one", "wss://nostr.bitcoiner.social", "wss://relay.noswhere.com",
]

async def main():
    res = await gather_limited([
        fetch(u, [{"kinds":[10009]}], timeout=30, page_by_until=True,
              page_size=500, max_pages=40, max_events=60000)
        for u in SEEDS], concurrency=12)
    all_ev = {}
    for u, (evs, err) in zip(SEEDS, res):
        print(f"{u:45s} {len(evs):6d}  {err or ''}", file=sys.stderr)
        for e in evs:
            all_ev[e["id"]] = e
    with open("events_10009.json","w") as f:
        json.dump(list(all_ev.values()), f)
    print(f"TOTAL unique 10009: {len(all_ev)}", file=sys.stderr)

asyncio.run(main())
