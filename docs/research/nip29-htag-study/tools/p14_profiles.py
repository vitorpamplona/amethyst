"""A relay signing key normally has no kind-0 profile; a human user does.
Use that to tell 'many relays share this id' from 'many users wrote fake events'."""
import asyncio, json, sys
import relaylib2 as R

V = json.load(open("verified.json"))
A = json.load(open("analysis5.json"))
keys = set()
for v in V["sigany"].values(): keys |= set(v)
for v in V["sig39000"].values(): keys |= set(v)
control = [k for k, u in A["key_label"].items() if u]      # keys we know are relays
keys |= set(control)
keys = sorted(keys)
print(f"checking kind-0 for {len(keys)} pubkeys", file=sys.stderr, flush=True)

AGG = ["wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net",
       "wss://purplepag.es", "wss://relay.nostr.band",
       "wss://search-staging.brainstorm.world", "wss://relay.mostr.pub"]

found = {}
async def one(u):
    for i in range(0, len(keys), 100):
        chunk = keys[i:i+100]
        try:
            evs, err, _ = await asyncio.wait_for(
                R.fetch(u, [{"kinds": [0], "authors": chunk}], timeout=25,
                        page_size=500, max_pages=3, max_events=3000), timeout=120)
        except asyncio.TimeoutError:
            evs = []
        for e in evs:
            found.setdefault(e["pubkey"], (e.get("content") or "")[:120])

async def main():
    await R.gather_limited([one(u) for u in AGG], concurrency=7)
    json.dump(found, open("profiles.json", "w"))
    print(f"DONE with kind-0: {len(found)}/{len(keys)}", file=sys.stderr, flush=True)
asyncio.run(main())
