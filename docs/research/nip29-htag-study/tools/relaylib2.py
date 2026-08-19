"""Paginated REQ against nostr relays, with NIP-42 AUTH and adaptive limits."""
import asyncio, hashlib, json, os, ssl, time

import websockets

import schnorr
from relaylib import norm  # reuse url normalizer

SSL_CTX = ssl.create_default_context(cafile="/root/.ccr/ca-bundle.crt")
# throwaway identity used only to satisfy NIP-42 read-auth challenges
SK = bytes.fromhex(os.environ.get("STUDY_SK") or hashlib.sha256(b"nip29-study-2026").hexdigest())
PK = schnorr.pubkey_xonly(SK).hex()


def sign_event(kind, tags, content, created_at=None):
    ev = {"pubkey": PK, "created_at": created_at or int(time.time()),
          "kind": kind, "tags": tags, "content": content}
    ser = json.dumps([0, ev["pubkey"], ev["created_at"], ev["kind"], ev["tags"], ev["content"]],
                     separators=(",", ":"), ensure_ascii=False)
    eid = hashlib.sha256(ser.encode()).digest()
    ev["id"] = eid.hex()
    ev["sig"] = schnorr.sign(eid, SK).hex()
    return ev


async def fetch(url, filters, timeout=25, max_events=200000, page_size=500,
                max_pages=30, do_auth=True):
    """Returns (events, err, meta). Handles AUTH, limit caps, until-pagination."""
    events, err = {}, None
    meta = {"authed": False, "limit_used": page_size, "pages": 0}
    ssl_arg = SSL_CTX if url.startswith("wss://") else None
    challenge = None
    try:
        async with websockets.connect(url, ssl=ssl_arg, open_timeout=timeout,
                                      close_timeout=3, max_size=None,
                                      user_agent_header="nip29-study/1.0") as ws:

            async def do_authenticate():
                nonlocal challenge
                if challenge is None or not do_auth:
                    return False
                ev = sign_event(22242, [["relay", url], ["challenge", challenge]], "")
                await ws.send(json.dumps(["AUTH", ev]))
                # give the relay a moment to OK it
                try:
                    for _ in range(6):
                        raw = await asyncio.wait_for(ws.recv(), timeout=8)
                        m = json.loads(raw)
                        if m[0] == "OK" and m[1] == ev["id"]:
                            meta["authed"] = bool(m[2])
                            return bool(m[2])
                        if m[0] == "AUTH":
                            challenge = m[1]
                        if m[0] == "NOTICE":
                            continue
                except (asyncio.TimeoutError, Exception):
                    pass
                meta["authed"] = True   # optimistic: some relays send nothing
                return True

            until = None
            limit = page_size
            page = 0
            retried_auth = False
            while page < max_pages:
                fs = []
                for f in filters:
                    f = dict(f); f["limit"] = limit
                    if until is not None: f["until"] = until
                    fs.append(f)
                subid = f"s{page}"
                await ws.send(json.dumps(["REQ", subid] + fs))
                got, oldest, retry = 0, None, None
                deadline = time.monotonic() + timeout
                while True:
                    rem = deadline - time.monotonic()
                    if rem <= 0: raise asyncio.TimeoutError("page timeout")
                    raw = await asyncio.wait_for(ws.recv(), timeout=rem)
                    try: msg = json.loads(raw)
                    except Exception: continue
                    t = msg[0]
                    if t == "EVENT" and len(msg) > 2:
                        ev = msg[2]
                        events.setdefault(ev["id"], ev)
                        got += 1
                        ca = ev.get("created_at", 0)
                        if oldest is None or ca < oldest: oldest = ca
                        if len(events) >= max_events: break
                    elif t == "AUTH":
                        challenge = msg[1]
                    elif t == "EOSE" and msg[1] == subid:
                        break
                    elif t == "CLOSED" and msg[1] == subid:
                        reason = msg[2] if len(msg) > 2 else ""
                        low = reason.lower()
                        if "auth" in low and not retried_auth:
                            retried_auth = True
                            if await do_authenticate():
                                retry = "auth"; break
                        if "limit" in low and limit > 20:
                            limit = 100 if limit > 100 else 20
                            meta["limit_used"] = limit
                            retry = "limit"; break
                        err = "CLOSED: " + reason
                        break
                    elif t == "NOTICE":
                        low = str(msg[-1]).lower()
                        if "auth" in low and not retried_auth:
                            retried_auth = True
                            if await do_authenticate():
                                retry = "auth"
                                break
                try: await ws.send(json.dumps(["CLOSE", subid]))
                except Exception: pass
                if retry:
                    page += 1  # burn a page slot, same window
                    continue
                if err or len(events) >= max_events: break
                meta["pages"] += 1
                if got == 0 or oldest is None: break
                if until is not None and oldest >= until: break
                until = oldest
                page += 1
    except Exception as e:
        err = f"{type(e).__name__}: {e}"[:200]
    return list(events.values()), err, meta


async def gather_limited(coros, concurrency=20):
    sem = asyncio.Semaphore(concurrency)
    async def run(c):
        async with sem: return await c
    return await asyncio.gather(*[run(c) for c in coros])
