import asyncio, json, os, ssl, sys, re
import urllib.request, concurrent.futures

def http_url(ws):
    return ("https://" + ws[6:]) if ws.startswith("wss://") else ("http://" + ws[5:])

targets = set()
targets |= set(json.load(open("probe2.json")).keys())
for fn in ("sweep.json", "sweep_partial.json"):
    if os.path.exists(fn):
        targets |= {u for u, v in json.load(open(fn)).items() if v.get("hit")}
targets = sorted(targets)
print("nip11 for", len(targets), "relays", file=sys.stderr, flush=True)

ctx = ssl.create_default_context(cafile="/root/.ccr/ca-bundle.crt")
proxy = os.environ.get("HTTPS_PROXY")
opener = urllib.request.build_opener(
    urllib.request.ProxyHandler({"https": proxy, "http": proxy}),
    urllib.request.HTTPSHandler(context=ctx))

def one(ws):
    req = urllib.request.Request(http_url(ws), headers={
        "Accept": "application/nostr+json", "User-Agent": "nip29-study/1.0"})
    try:
        with opener.open(req, timeout=15) as r:
            return ws, json.loads(r.read(400000).decode("utf-8", "replace")), None
    except Exception as e:
        return ws, None, f"{type(e).__name__}: {e}"[:120]

out = {}
with concurrent.futures.ThreadPoolExecutor(max_workers=25) as ex:
    for i, (ws, doc, err) in enumerate(ex.map(one, targets)):
        out[ws] = {"doc": doc, "err": err}
        if (i + 1) % 200 == 0:
            print(f"... {i+1}/{len(targets)}", file=sys.stderr, flush=True)
json.dump(out, open("nip11.json", "w"))
ok = sum(1 for v in out.values() if v["doc"])
print(f"DONE nip11 ok={ok}", file=sys.stderr, flush=True)
