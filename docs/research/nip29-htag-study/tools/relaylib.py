"""Small helper lib: paginated REQ against nostr relays over the agent proxy."""
import asyncio, json, ssl, time, sys

import websockets

SSL_CTX = ssl.create_default_context(cafile="/root/.ccr/ca-bundle.crt")

def norm(url: str) -> str:
    """Normalize a relay url for identity comparison."""
    if not isinstance(url, str):
        return ""
    u = url.strip().strip('"').strip("'")
    if not u:
        return ""
    if u.startswith("ws://") or u.startswith("wss://"):
        pass
    elif u.startswith("http://"):
        u = "ws://" + u[7:]
    elif u.startswith("https://"):
        u = "wss://" + u[8:]
    elif "://" in u:
        return ""
    else:
        u = "wss://" + u
    # strip trailing slash + lowercase host
    try:
        scheme, rest = u.split("://", 1)
    except ValueError:
        return ""
    if "/" in rest:
        host, path = rest.split("/", 1)
        path = "/" + path
    else:
        host, path = rest, ""
    host = host.lower()
    path = path.rstrip("/")
    if not host or "." not in host and not host.startswith("localhost"):
        return ""
    if " " in host or "," in host:
        return ""
    return f"{scheme}://{host}{path}"


async def fetch(url, filters, timeout=25, max_events=200000, page_by_until=False,
                page_size=500, max_pages=1, quiet=True):
    """Open one connection, run REQ(s), collect events until EOSE/timeout.

    page_by_until: after each EOSE, re-issue the filter with until=oldest-1.
    Returns (events:list, err:str|None)
    """
    events = {}
    err = None
    try:
        async with websockets.connect(url, ssl=SSL_CTX, open_timeout=timeout,
                                      close_timeout=3, max_size=None,
                                      user_agent_header="nip29-study/1.0") as ws:
            until = None
            for page in range(max_pages if page_by_until else 1):
                fs = []
                for f in filters:
                    f = dict(f)
                    if page_by_until:
                        f["limit"] = page_size
                        if until is not None:
                            f["until"] = until
                    fs.append(f)
                subid = f"s{page}"
                await ws.send(json.dumps(["REQ", subid] + fs))
                got_this_page = 0
                oldest = None
                deadline = time.monotonic() + timeout
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise asyncio.TimeoutError("page timeout")
                    raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
                    try:
                        msg = json.loads(raw)
                    except Exception:
                        continue
                    t = msg[0]
                    if t == "EVENT" and len(msg) > 2:
                        ev = msg[2]
                        if ev.get("id") not in events:
                            events[ev["id"]] = ev
                        got_this_page += 1
                        ca = ev.get("created_at", 0)
                        if oldest is None or ca < oldest:
                            oldest = ca
                        if len(events) >= max_events:
                            break
                    elif t == "EOSE" and msg[1] == subid:
                        break
                    elif t == "CLOSED" and msg[1] == subid:
                        err = "CLOSED: " + (msg[2] if len(msg) > 2 else "")
                        break
                    elif t == "NOTICE":
                        pass
                try:
                    await ws.send(json.dumps(["CLOSE", subid]))
                except Exception:
                    pass
                if err or len(events) >= max_events:
                    break
                if not page_by_until:
                    break
                if got_this_page == 0 or oldest is None:
                    break
                if until is not None and oldest >= until:
                    break
                until = oldest  # inclusive; dedup by id handles the overlap
    except Exception as e:
        err = f"{type(e).__name__}: {e}"[:200]
    return list(events.values()), err


async def gather_limited(coros, concurrency=20):
    sem = asyncio.Semaphore(concurrency)
    async def run(c):
        async with sem:
            return await c
    return await asyncio.gather(*[run(c) for c in coros])
