# Napplet test harness

A self-contained napplet for **on-device verification** of Amethyst's NIP-5D host — it calls every
`window.napplet.*` API and shows each result on screen, so you can confirm the whole
shim → shell → broker → consent round-trip (and the newer `identity.getList/getZaps/getBadges`,
`identity.onChanged`, `keys.onAction`, and `resource.bytes` `nostr:` paths) works on a real device.

`index.html` is the napplet: read-only checks run on load; publish/upload/pay are behind buttons; live
pushes (`identity.changed`, `keys.action`) land in the top banner.

## Publish it

`amy napplet publish` uploads the whole directory to Blossom (BUD-02 signed) and broadcasts the NIP-5D
event in one step, as the account `amy` is logged in as:

```bash
./gradlew :cli:installDist            # builds amy → cli/build/install/amy/bin/amy
amy napplet publish tools/napplet-test \
  --server https://blossom.primal.net \
  --requires identity,relay,storage,value,resource,upload,keys \
  --d napplet-test --title "Napplet Test Harness" \
  --relay wss://relay.damus.io --relay wss://nos.lol
```

- `--d` makes it an addressable kind-35129 napplet; omit it for a root kind-15129.
- `--icon https://…/icon.png` sets the square app icon shown on the launcher card (optional; the card
  falls back to a colored monogram from the title when absent).
- For a plain static site, use `amy nsite publish <dir> --server …` (kind 15128/35128).
- Use the **same key you're logged in as in Amethyst**, so the napplet appears under your account and
  the identity reads (`getProfile`, `getFollows`, …) have data.

Verify it resolves (optional): `amy napplet fetch <your-pubkey-hex> --d napplet-test`.
List everything you've published: `amy napplet list <your-pubkey-hex>` (or `amy nsite list <pubkey>`).

## Preview in a browser (optional)

`amy napplet serve` resolves the published manifest and serves its static content locally (each blob
sha256-verified, just like the device host) so you can eyeball that it loads:

```bash
amy napplet serve <your-pubkey-hex> --d napplet-test --port 8080   # then open http://127.0.0.1:8080
```

This serves the **files only** — the `window.napplet.*` runtime needs the Amethyst host, so the API
rows won't pass in a plain browser. Use it to confirm the files publish and route correctly.

## Run on a device

Build & install the debug app and watch the logs:

```bash
./gradlew :amethyst:installPlayDebug
adb logcat -s NappletHostActivity NappletBrokerService NappletContentServer
```

Logged in as the publishing key, find **"Napplet Test Harness"** in your Apps / Napplets list (or its
feed card) and tap **Open**. It launches in the sandboxed `:napplet` process.

## What to verify

- **On load:** each read row turns green. `shell.supports(identity)` = true, `(bogus)` = false. Every
  capability prompts for consent the first time.
- **identity.getList/getZaps/getBadges:** open your own profile first so the cache has your lists /
  zaps / badges, then relaunch — the rows show your data (empty arrays are valid if you have none).
- **identity.onChanged:** with the napplet open, switch accounts (or log out) → the banner shows
  `identity.changed → <pubkey>`. It must NOT fire on the initial load.
- **keys.onAction:** with a hardware keyboard (or `adb shell input keyevent 47` for "S" while holding
  Ctrl), press **Ctrl+S** → banner shows `keys.action → save fired`, and the keystroke is consumed.
- **resource.bytes `nostr:`:** paste a `nostr:nevent1…` / `note1…` / `naddr1…` / `npub1…` and run →
  it returns the event JSON as a blob. Also try an `https://…` image URL.
- **Side effects (deliberate):** `relay.publish` signs+broadcasts a note as you; `upload.blob` uploads
  a tiny blob; `value.payInvoice` pays a BOLT-11 invoice (needs a connected wallet). Each prompts.
- **Security:** the applet has no direct network (a plain `fetch()` inside it fails — CSP
  `connect-src 'none'`); an undeclared capability is denied even if you'd allow it.

## Notes

- Re-running `amy napplet publish … --d napplet-test` replaces the same addressable event.
