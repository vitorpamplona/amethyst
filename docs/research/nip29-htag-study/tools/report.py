"""Emit the study's tables (markdown fragments + CSVs) with explicit evidence tiers."""
import collections, csv, json, os

A = json.load(open("analysis5.json"))
V = json.load(open("verified.json"))
P = json.load(open("profiles.json")) if os.path.exists("profiles.json") else {}
sig39000 = {k: set(v) for k, v in V["sig39000"].items()}
sigany   = {k: set(v) for k, v in V["sigany"].items()}
counts, rows, label = A["counts"], A["rows"], A["key_label"]

# ---- evidence tiers -------------------------------------------------------
# A  >=2 pubkeys with a signature-verified kind 39000 (d = h).  Relay-signed
#    group metadata: the strongest statement that two relays host this id.
# B  >=2 pubkeys verified only via kind 39001/39002.  Weaker: a permissive
#    general-purpose relay will also accept those from ordinary users.
# C  >=2 pubkeys observed in a relay's answer but the raw event could not be
#    re-fetched for verification.
def tier(r):
    if len(sig39000.get(r["h"], ())) > 1: return "A"
    if len(sigany.get(r["h"], ())) > 1:   return "B"
    return "C"

shared = [r for r in rows if r["n_keys"] > 1]
for r in shared:
    r["v39000"] = len(sig39000.get(r["h"], ()))
    r["vany"] = len(sigany.get(r["h"], ()))
    r["tier"] = tier(r)
    r["relay_list"] = sorted({label.get(k) or f"key:{k[:8]}" for k in r["keys"]})
    r["n_urls_known"] = sum(1 for u in r["relay_list"] if not u.startswith("key:"))
order = {"A": 0, "B": 1, "C": 2}
shared.sort(key=lambda r: (order[r["tier"]], -r["n_keys"], -r["v39000"], r["h"]))
byt = collections.Counter(r["tier"] for r in shared)

with open("shared_htags.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["h_tag", "group_name", "id_shape", "evidence_tier", "n_relay_instances",
                "n_verified_39000_signers", "n_verified_39000_39001_39002_signers",
                "relays_or_relay_keys", "relay_pubkeys", "kind10009_claimed_urls"])
    for r in shared:
        w.writerow([r["h"], r["name"], r["class"], r["tier"], r["n_keys"], r["v39000"],
                    r["vany"], " ".join(r["relay_list"]), " ".join(r["keys"]),
                    " ".join(r["claimed_only_urls"])])

with open("all_htags.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["h_tag", "group_name", "id_shape", "n_relay_instances", "relays_or_relay_keys"])
    for r in rows:
        urls = sorted({label.get(k) or f"key:{k[:8]}" for k in r["keys"]}) or r["claimed_only_urls"]
        w.writerow([r["h"], r["name"], r["class"], r["n_keys"], " ".join(urls)])

inv = sorted(((c, label.get(k) or "", k) for k, c in A["groups_per_key"].items()),
             key=lambda t: (-t[0], t[1] or "zzz"))
with open("relay_instances.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["n_groups", "relay_url", "relay_pubkey", "all_urls_seen"])
    for c, u, k in inv:
        w.writerow([c, u, k, " ".join(A["key_urls"].get(k, []))])

out = {"counts": counts, "shared": shared, "tiers": dict(byt),
       "class_all": dict(collections.Counter(r["class"] for r in rows)),
       "class_shared": dict(collections.Counter(r["class"] for r in shared)),
       "class_tierA": dict(collections.Counter(r["class"] for r in shared if r["tier"] == "A")),
       "top_relays": [{"n": c, "url": u, "key": k} for c, u, k in inv[:40]],
       "groups_per_instance_hist": dict(collections.Counter(A["groups_per_key"].values())),
       "shared_with_known_url": sum(1 for r in shared if r["n_urls_known"]),
       }
json.dump(out, open("study.json", "w"), indent=1)

print("tiers:", dict(byt))
print("tier A by id shape:", out["class_tierA"])
print("\nTIER A (>=2 signature-verified kind-39000 signers)")
for r in [x for x in shared if x["tier"] == "A"]:
    print(f"  {r['v39000']:3d}  [{r['class']:19s}] {r['h'][:70]:70s} {r['name'][:24]}")
