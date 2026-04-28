# Manual Testing: Desktop Tor Support

## Prerequisites

All tools are available natively on macOS:
- `tcpdump` — DNS leak monitoring
- `curl` — Tor routing verification
- `lsof` — find SOCKS port

Optional:
```bash
brew install tor    # For external Tor mode testing
```

---

## Step 1: Launch & Find SOCKS Port

```bash
cd AmethystMultiplatform-tor-support
./gradlew :desktopApp:run > /tmp/amethyst-tor.log 2>&1 &

# Wait ~30s for Tor bootstrap, then find the SOCKS port:
lsof -i -P -n | grep java | grep LISTEN
# Look for a high port (e.g. 35607) — that's the kmp-tor SOCKS port
```

---

## Step 2: Verify Tor Routing

Replace `PORT` with the actual SOCKS port from Step 1.

```bash
# Your real IP
curl -s https://icanhazip.com
# → e.g. 203.0.113.42

# IP through Tor
curl -s --socks5-hostname 127.0.0.1:PORT https://icanhazip.com
# → e.g. 185.220.101.xx (different from above)

# Confirm Tor Project says it's Tor
curl -s --socks5-hostname 127.0.0.1:PORT https://check.torproject.org/api/ip
# → {"IsTor":true,"IP":"185.220.101.xx"}
```

---

## Step 3: Verify No DNS Leaks

```bash
# In a separate terminal:
sudo tcpdump -i any port 53 -n -l 2>/dev/null | grep --line-buffered -v "mdns"

# In the app: scroll feed, load profiles, open threads
# tcpdump should show NO relay hostname DNS queries from Java
```

---

## Checklist

### UI — Default State
- [ ] App launches with shield icon visible in sidebar (bottom)
- [ ] Shield starts gray/yellow, turns green after ~10-30s (Tor bootstrap)
- [ ] Hover shield → tooltip shows "Tor: Connected"
- [ ] Click shield → navigates to Settings
- [ ] Settings → Tor section shows **Internal** mode selected
- [ ] Click "Advanced..." → **Full Privacy** preset selected, all toggles ON

### UI — Mode Switching
- [ ] Select "Off" → shield turns gray, relays reconnect directly
- [ ] Select "Internal" → shield yellow → green, relays reconnect via Tor
- [ ] Select "External" → SOCKS port field appears
- [ ] Enter port 9050 (if system Tor running) → shield turns green

### UI — Advanced Dialog
- [ ] Preset radio buttons: Only When Needed / Default / Small Payloads / Full Privacy / Custom
- [ ] Selecting "Only When Needed" → only .onion toggle ON
- [ ] Selecting "Full Privacy" → all toggles ON
- [ ] Toggling individual item → preset auto-switches to "Custom"
- [ ] Scroll dialog content → Cancel/Save buttons stay at bottom (sticky)
- [ ] Cancel → no changes saved
- [ ] Save → settings applied and persisted

### UI — .onion Relay Badge
- [ ] Add a .onion relay URL → badge shows "via Tor" (green) when Tor ON
- [ ] Switch Tor OFF → badge shows "Requires Tor" (red)

### UI — Persistence
- [ ] Change to "Default" preset, close app
- [ ] Relaunch → shows Internal + Default preset, Tor auto-connects

### Network — Tor Routing Verified
- [ ] `curl -s https://icanhazip.com` → real IP: _______________
- [ ] `curl -s --socks5-hostname 127.0.0.1:PORT https://icanhazip.com` → Tor IP: _______________
- [ ] IPs are different: ___
- [ ] `curl --socks5-hostname 127.0.0.1:PORT https://check.torproject.org/api/ip` → `{"IsTor":true}`: ___

### Network — No DNS Leaks
- [ ] `sudo tcpdump -i any port 53 -n` shows no relay hostname queries while Tor active

### Build — No Regressions
- [ ] `./gradlew :commons:jvmTest` — all pass
- [ ] `./gradlew :desktopApp:test` — all pass (1 pre-existing failure in DesktopCachePipelineTest)
- [ ] `./gradlew :amethyst:testPlayDebugUnitTest` — all pass
- [ ] `./gradlew :amethyst:compilePlayDebugKotlin` — Android compiles clean
- [ ] `./gradlew spotlessApply` — formatting clean

---

## Proof of Work Evidence

Paste terminal output of:

```bash
# 1. SOCKS port discovered
lsof -i -P -n | grep java | grep LISTEN

# 2. Tor routing confirmed
curl -s --socks5-hostname 127.0.0.1:PORT https://check.torproject.org/api/ip

# 3. IP mismatch confirmed
echo "Real: $(curl -s https://icanhazip.com) | Tor: $(curl -s --socks5-hostname 127.0.0.1:PORT https://icanhazip.com)"

# 4. Tests passing
./gradlew :commons:jvmTest :desktopApp:test :amethyst:testPlayDebugUnitTest 2>&1 | tail -5
```

Screenshot of:
- Shield icon green in sidebar
- Tor settings section showing Internal + Full Privacy
- Advanced dialog with all toggles ON
