# Napplet test harness

A self-contained napplet for **on-device verification** of Amethyst's NIP-5D host — it calls every
`window.napplet.*` API and shows each result on screen, so you can confirm the whole
shim → shell → broker → consent round-trip (and the newer `identity.getList/getZaps/getBadges`,
`identity.onChanged`, `keys.onAction`, and `resource.bytes` `nostr:` paths) works on a real device.

## Files

- `index.html` — the napplet. Read-only checks run on load; publish/upload/pay are behind buttons;
  live pushes (`identity.changed`, `keys.action`) land in the top banner.
- `publish.sh` — uploads `index.html` to a Blossom server and publishes the napplet event.

## Prerequisites

- [`nak`](https://github.com/fiatjaf/nak) (signs + publishes the events), `curl`, and `sha256sum`
  (or `shasum`/`openssl`).
- A Blossom server that accepts BUD-02 uploads (e.g. `https://blossom.primal.net`,
  `https://cdn.satellite.earth`, or your own).
- Your **nsec** — use the **same key you're logged in as in Amethyst**, so the napplet appears under
  your account and the identity reads (`getProfile`, `getFollows`, …) have data.

## Publish

```bash
cd tools/napplet-test
./publish.sh --sec nsec1yourkey... --server https://blossom.primal.net \
  --relay wss://relay.damus.io --relay wss://nos.lol
```

Then verify it resolves (optional):

```bash
amy napplet fetch <your-pubkey-hex> --d napplet-test
```

## Open it in Amethyst

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

- `publish.sh` uses `nak`'s `-t key=val1;val2` multi-element tag syntax (e.g.
  `path=/index.html;<hash>`). If your `nak` version differs, adjust accordingly.
- Re-running `publish.sh` replaces the same addressable event (`d=napplet-test`).
