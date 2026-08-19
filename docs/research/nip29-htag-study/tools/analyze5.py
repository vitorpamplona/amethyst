"""NIP-29 h-tag sharing study -- final analysis.

Identity model
--------------
kind 39000/39001/39002 are ordinary addressable events, so ANY relay may hold a
copy: nos.lol alone stores >1000 group-metadata events it does not host.  Relay
URLs are therefore useless on their own for "who serves group X".

NIP-29 signs group metadata WITH THE RELAY'S OWN KEY, and that signature travels
with every copy.  So this study treats

        relay instance  ==  the pubkey that signs the kind-39000

and resolves those keys back to URLs as a secondary, best-effort step.
"""
import collections, csv, json, os, re

HEX64 = re.compile(r"^[0-9a-f]{64}$")
NIP29_SW = re.compile(r"khatru|relay29|pyramid|zooid|chorus|groups_relay|buzz|newlay|"
                      r"nostrd|relay-tools|hound|29", re.I)
PRIV = re.compile(r"//(localhost|127\.|0\.0\.0\.0|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1)")

def load(fn, d=None):
    return json.load(open(fn)) if os.path.exists(fn) else ({} if d is None else d)

probe = {}
for fn in ("probe2.json", "probe3.json"):
    for r, v in load(fn).items():
        if r not in probe or (v.get("groups") and not probe[r].get("groups")):
            probe[r] = v
nip11 = {k: v["doc"] for k, v in load("nip11.json").items() if isinstance(v.get("doc"), dict)}
kind1 = load("kind1.json") or load("kind1_partial.json")
decl  = load("from_10009.json", {}).get("relay_groups", {})
agg   = load("agg_39000.json", [])

def nips(d):
    s = d.get("supported_nips")
    return {str(x) for x in s} if isinstance(s, list) else set()
def sw(d): return str(d.get("software") or "")
def host(u): return u.split("://", 1)[-1].split("/")[0].lower()

# ---------- per-URL signer profile ----------
modal, share, nsign = {}, {}, {}
for r, v in probe.items():
    sc = collections.Counter()
    for m in (v.get("groups") or {}).values():
        for a in m.get("authors", ()): sc[a] += 1
    if sc:
        top, tc = sc.most_common(1)[0]
        modal[r], share[r], nsign[r] = top, tc / sum(sc.values()), len(sc)

def dedicated(r):
    k = kind1.get(r)
    return bool(k) and not k.get("err") and k.get("n_kind1", 1) == 0

# ---------- URL -> relay signing key ----------
relay_key, key_src = {}, {}
for r in set(probe) | set(nip11):
    d = nip11.get(r) or {}
    s = d.get("self")
    if isinstance(s, str) and HEX64.match(s):
        relay_key[r], key_src[r] = s, "nip11-self"
    elif r in modal and isinstance(d.get("pubkey"), str) and d["pubkey"] == modal[r]:
        # some relay29 builds advertise the relay's own signing key as `pubkey`;
        # trust it only when it actually matches what signs the 39000s here.
        relay_key[r], key_src[r] = modal[r], "nip11-pubkey"
    elif r in modal and share[r] >= 0.9 and "29" in nips(d):
        relay_key[r], key_src[r] = modal[r], "declares-nip29"
    elif r in modal and nsign[r] == 1 and dedicated(r) and NIP29_SW.search(sw(d)):
        relay_key[r], key_src[r] = modal[r], "dedicated-single-signer"

key_urls = collections.defaultdict(set)
for r, k in relay_key.items(): key_urls[k].add(r)

SRC_RANK = {"nip11-self": 0, "nip11-pubkey": 1, "declares-nip29": 2,
            "dedicated-single-signer": 3}
def ngroups_at(u):
    return len((probe.get(u) or {}).get("groups") or {})
def canonical(urls):
    return sorted(urls, key=lambda u: (SRC_RANK.get(key_src.get(u), 9),
                                       -ngroups_at(u), u.count("/") - 2,
                                       len(u), u))[0] if urls else None
key_label = {k: canonical(v) for k, v in key_urls.items()}

# ---------- h -> relay keys ----------
gid_keys = collections.defaultdict(set)
gid_seen_urls = collections.defaultdict(set)
names, flags, kinds_seen = {}, {}, collections.defaultdict(set)

for r, v in probe.items():
    for g, m in (v.get("groups") or {}).items():
        gid_seen_urls[g].add(r)
        kinds_seen[g] |= set(m.get("kinds", ()))
        gid_keys[g] |= set(m.get("authors", ()))
        if m.get("name"): names.setdefault(g, m["name"])
        if 39000 in m.get("kinds", ()):
            flags.setdefault(g, {k: m.get(k) for k in ("public", "private", "open", "closed")})

for ev in agg:
    if ev.get("kind") != 39000: continue
    d = name = None
    for t in ev.get("tags", ()):
        if not t: continue
        if t[0] == "d" and len(t) > 1: d = t[1]
        elif t[0] == "name" and len(t) > 1: name = t[1]
    if d is None: continue
    gid_keys[d].add(ev["pubkey"]); kinds_seen[d].add(39000)
    if name: names.setdefault(d, name)

claimed = collections.defaultdict(set)
for r, gids in decl.items():
    if PRIV.search(r): continue
    for g in gids: claimed[g].add(r)

def classify(g):
    if g == "_": return "relay-wide `_`"
    if g.startswith("npub1") and ":" in g: return "npub-namespaced"
    if re.fullmatch(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", g):
        return "uuid"
    for n in (4, 6, 8, 12, 16, 32, 64):
        if re.fullmatch(r"[0-9a-f]{%d}" % n, g): return f"random hex-{n}"
    if re.fullmatch(r"\d{10,20}", g): return "numeric id"
    if re.fullmatch(r"[0-9a-zA-Z]{8,16}", g) and re.search(r"\d", g) \
       and not re.search(r"[-_]", g): return "random alnum"
    if re.fullmatch(r"[a-z]{9,14}", g) and len(set(g)) >= 7 \
       and not re.search(r"(chat|group|test|nostr|relay)", g): return "random letters"
    return "human-readable slug"

rows = []
for g in set(gid_keys) | set(claimed) | set(gid_seen_urls):
    keys = sorted(gid_keys[g])
    ru = set()
    for k in keys: ru |= key_urls.get(k, set())
    cl = sorted(claimed[g] - ru)
    rows.append({
        "h": g, "class": classify(g), "name": names.get(g, ""),
        "n_keys": len(keys), "keys": keys,
        "key_labels": [key_label.get(k) for k in keys],
        "resolved_urls": sorted(ru), "claimed_only_urls": cl,
        "n_hosts": len({host(x) for x in ru | set(cl)}),
        "n_claim_hosts": len({host(x) for x in claimed[g]}),
        "flags": flags.get(g), "kinds": sorted(kinds_seen[g]),
    })
rows.sort(key=lambda r: (-r["n_keys"], -r["n_hosts"], r["h"]))

allkeys = collections.Counter()
for r in rows:
    for k in r["keys"]: allkeys[k] += 1

counts = {
    "kind_10009_events_collected": len(load("events_10009.json", [])),
    "relays_known_from_nip66": len(load("nip66_relays.json", [])),
    "relay_urls_probed_for_39000": len(probe),
    "relay_urls_that_returned_39000_family": sum(1 for v in probe.values() if v.get("groups")),
    "nip11_docs_fetched": len(nip11),
    "nip11_declaring_nip29": sum(1 for d in nip11.values() if "29" in nips(d)),
    "distinct_relay_instances": len(allkeys),
    "relay_instances_bound_to_a_url": len(key_urls),
    "relay_urls_bound_to_an_instance": len(relay_key),
    "distinct_h_tags": len(rows),
    "h_with_at_least_one_relay_key": sum(1 for r in rows if r["n_keys"]),
    "h_shared_by_2plus_relay_instances": sum(1 for r in rows if r["n_keys"] > 1),
    "h_reachable_at_2plus_relay_hosts": sum(1 for r in rows if r["n_hosts"] > 1),
    "h_claimed_at_2plus_hosts_in_kind10009": sum(1 for r in rows if r["n_claim_hosts"] > 1),
}
json.dump({"counts": counts, "rows": rows, "relay_key": relay_key, "key_src": key_src,
           "key_urls": {k: sorted(v) for k, v in key_urls.items()},
           "key_label": key_label, "groups_per_key": dict(allkeys)},
          open("analysis5.json", "w"))

shared = [r for r in rows if r["n_keys"] > 1]
with open("shared_htags.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["h_tag", "group_name", "id_shape", "n_relay_instances",
                "relay_urls_resolved", "relay_urls_claimed_in_10009", "relay_pubkeys"])
    for r in shared:
        w.writerow([r["h"], r["name"], r["class"], r["n_keys"],
                    " ".join(r["resolved_urls"]), " ".join(r["claimed_only_urls"]),
                    " ".join(r["keys"])])
with open("all_htags.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["h_tag", "group_name", "id_shape", "n_relay_instances", "relay_urls"])
    for r in rows:
        w.writerow([r["h"], r["name"], r["class"], r["n_keys"],
                    " ".join(r["resolved_urls"] + r["claimed_only_urls"])])

for k, v in counts.items(): print(f"{k:42s} {v}")
print(f"\nshared by id shape: {collections.Counter(r['class'] for r in shared).most_common()}")
print(f"all ids by shape:   {collections.Counter(r['class'] for r in rows).most_common()}")
print(f"\ngroups-per-relay-instance: "
      f"{sorted(collections.Counter(allkeys.values()).items())[:10]}")
