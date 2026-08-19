"""Signature-verify every colliding group event and split evidence by kind."""
import collections, hashlib, json, os
import schnorr

def load(fn):
    return json.load(open(fn)) if os.path.exists(fn) else []

evs = {}
for fn in ("agg_39000.json", "shared_39000_raw.json", "targeted_raw.json"):
    for e in load(fn):
        evs[e["id"]] = e
print("candidate events:", len(evs))

def check(e):
    ser = json.dumps([0, e["pubkey"], e["created_at"], e["kind"], e["tags"], e["content"]],
                     separators=(",", ":"), ensure_ascii=False)
    eid = hashlib.sha256(ser.encode()).digest()
    if eid.hex() != e["id"]:
        return "bad-id"
    try:
        return "ok" if schnorr.verify(eid, bytes.fromhex(e["pubkey"]),
                                      bytes.fromhex(e["sig"])) else "bad-sig"
    except Exception:
        return "bad-sig-format"

A = json.load(open("analysis5.json"))
shared = {r["h"] for r in A["rows"] if r["n_keys"] > 1}

stat = collections.Counter()
sig39000 = collections.defaultdict(set)   # h -> verified signers of kind 39000
sigany   = collections.defaultdict(set)   # h -> verified signers of 39000/1/2
for e in evs.values():
    if e.get("kind") not in (39000, 39001, 39002):
        continue
    d = next((t[1] for t in e.get("tags", ()) if t and t[0] == "d" and len(t) > 1), None)
    if d is None or d not in shared:
        continue
    r = check(e); stat[r] += 1
    if r != "ok":
        continue
    sigany[d].add(e["pubkey"])
    if e["kind"] == 39000:
        sig39000[d].add(e["pubkey"])

print("signature checks:", dict(stat))
print("h with >=2 verified 39000 signers:", sum(1 for v in sig39000.values() if len(v) > 1))
print("h with >=2 verified 3900x signers:", sum(1 for v in sigany.values() if len(v) > 1))
json.dump({"sig39000": {k: sorted(v) for k, v in sig39000.items()},
           "sigany": {k: sorted(v) for k, v in sigany.items()}},
          open("verified.json", "w"))
