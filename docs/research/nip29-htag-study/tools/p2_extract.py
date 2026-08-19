import json, collections
from relaylib import norm

evs = json.load(open("events_10009.json"))
relay_groups = collections.defaultdict(set)   # relay -> set(groupid) as declared in 10009
relay_hits = collections.Counter()
for e in evs:
    for t in e.get("tags", []):
        if not t: continue
        if t[0] == "group" and len(t) >= 3:
            r = norm(t[2])
            if r:
                relay_hits[r] += 1
                relay_groups[r].add(t[1])
        elif t[0] == "r" and len(t) >= 2:
            r = norm(t[1])
            if r:
                relay_hits[r] += 1

print("distinct relays referenced in 10009:", len(relay_hits))
for r, c in relay_hits.most_common(60):
    print(f"{c:7d}  {len(relay_groups.get(r,())):5d} groups  {r}")
json.dump({"relay_hits": relay_hits, "relay_groups": {k: sorted(v) for k,v in relay_groups.items()}},
          open("from_10009.json","w"))
