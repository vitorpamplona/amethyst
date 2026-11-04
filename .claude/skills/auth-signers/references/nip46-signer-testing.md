# Testing Amethyst as a NIP-46 signer

Amethyst plays both NIP-46 roles: the **client** of someone else's bunker
(`NostrSignerRemote`) and the **bunker** itself (`Nip46SignerState`, Settings › Connected Apps ›
Turn on signer). This page covers testing the bunker role: JVM tests for races, then an emulator
run with `amy` as the app logging in through it. Everything here was used for PR #4750.

## JVM tests

The signer's tests live in `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/model/nip46Signer/`:
`Nip46SignerTestNet.kt` (an in-process relay, the account and its signer state),
`Nip46NostrConnectPairingTest.kt` and `Nip46SignerSwitchRelaysTest.kt`.

- **A timeout on `login.await()` usually means a lost request, not a slow runner.** Kind 24133 is
  ephemeral: a request that reaches the relay while the listener sits between its CLOSE and its new
  REQ is gone, and the app only retries after 65 s. Look for a listener restart before raising a
  timeout.
- **Races hide on a fast laptop and show on CI's macOS runner.** Reproduce under CPU load and count
  over many runs, not one:

  ```bash
  for _ in $(seq 24); do yes > /dev/null & done
  for i in $(seq 15); do ./gradlew :commons:jvmTest --rerun --tests '*.Nip46NostrConnectPairingTest' -q > run$i.log 2>&1 && echo pass || echo FAIL; done
  pkill yes
  ```

- **Then make the race deterministic.** `HeardLate` in `Nip46NostrConnectPairingTest.kt` wraps a
  settings `MutableStateFlow` so its collectors hear each value late, which forces the order that
  only load produced. It needs `@OptIn(ExperimentalForInheritanceCoroutinesApi::class)`. The test
  must fail against the unfixed code every run before you trust the fix.
- **Assert on the wire, not on timing.** `Nip46SignerTestNet.sentTo(relay)` lists the commands the
  signer's client actually sent (recorded from `RelayConnectionListener.onSent`, successful sends
  only), so a test can say "no `ReqCmd` for the transport key's `p` tag after the ack `EventCmd`".
- **Seed a trusted app with `updateLastUsed` before `setPolicy`.** Creating the signer state
  launches `pruneIdle`, which forgets any trusted app without a last-used time. With the other
  order, a prune between the two calls makes pairing return `Declined`.

## Emulator rig

**Build and install.** `./gradlew :cli:installDist :amethyst:assemblePlayDebug`, then
`adb install -r amethyst/build/outputs/apk/play/debug/amethyst-play-arm64-v8a-debug.apk`. The
package is `com.vitorpamplona.amethyst.debug`. Start the AVD with
`emulator -avd <name> -no-snapshot-save -no-boot-anim` and wait for
`adb shell getprop sys.boot_completed` to print `1`.

**Pin the serial.** A real phone may be attached too. Set `ANDROID_SERIAL=emulator-5554` or pass
`-s emulator-5554` on every call. In zsh, `E="-s emulator-5554"; adb $E …` does not split the
words, so use the variable.

**Run `amy` without touching the user's state.** amy has no `--data-dir` flag. It keeps its data
under `$HOME/.amy` and its keys in the OS keychain, so give each role a scratch `HOME` and
plaintext secrets:

```bash
#!/bin/bash
# amyx: amy with a scratch HOME per role (AMYX_HOME=relay|client) and plaintext secrets.
export HOME=$SCRATCH/amyhome-${AMYX_HOME:-default}; mkdir -p "$HOME"
exec "$REPO/cli/build/install/amy/bin/amy" --secret-backend plaintext "$@"
```

**Local relay.** `AMYX_HOME=relay amyx --account relay serve --host 127.0.0.1 --port 7777`, run in
the background.

**Reach it from the emulator at `10.0.2.2`,** the emulator's alias for the host's loopback. On the
Pixel 9a AVD (Android 16), `adb reverse tcp:7777 tcp:7777` reported success but forwarded nothing.
So one relay has two URLs: `ws://127.0.0.1:7777` for amy on the host and `ws://10.0.2.2:7777` for
the app. Rewrite each address as it crosses: a `nostrconnect://` offer from amy gets `10.0.2.2`
before the app sees it, and a `bunker://` address from the app gets `127.0.0.1` before amy sees it.

**Turn Tor off before logging in.** "Untrusted relays via Tor" (`TorSettings.newRelaysViaTor`,
on by default) sends the app's own relays through Tor, and Tor can't reach `10.0.2.2`. Pairing then
times out with no error. On the login screen, open Tor settings, set Active Tor Engine to Off, and
Save. `pm clear` resets this.

**Use a throwaway signer account.** Generate one with `amy key generate --json > signer-key.json`.
On the login screen, tap the "nsec, npub or bunker://" field, run `adb shell input text <nsec>`,
press BACK, then tap Login. Turn the signer on from Settings: search "signer", open Connected Apps,
tap Turn on signer, and wait for "Live".

**Drive the UI by text.** `uiautomator dump` lists Compose nodes with their text and
content-desc, so tap a node by its label instead of hard-coding coordinates:

```python
# ui.py tap LABEL: tap the first node whose text or content-desc is exactly LABEL
import re, subprocess, sys, xml.etree.ElementTree as ET
adb = ["adb", "-s", "emulator-5554"]
subprocess.run(adb + ["shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
xml = subprocess.run(adb + ["exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode()
for n in ET.fromstring(xml[xml.index("<"):]).iter("node"):
    if sys.argv[2] in (n.get("text"), n.get("content-desc")):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        subprocess.run(adb + ["shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
        break
else:
    sys.exit(f"not found: {sys.argv[2]}")
```

## Pairing and signing

**`nostrconnect://` (the app starts it).** Amethyst opens the link and asks first
(`amethyst/src/main/AndroidManifest.xml` has the intent filter):

```bash
AMYX_HOME=client amyx --account app1 login --nostrconnect --relay ws://127.0.0.1:7777 --name app1 --timeout 150 > app1.out 2>&1 &
sleep 3; URI=$(grep -o 'nostrconnect://[^ ]*' app1.out | head -1 | sed 's/127.0.0.1/10.0.2.2/')
adb shell "am start -a android.intent.action.VIEW -d '$URI' com.vitorpamplona.amethyst.debug"
```

Quote the URI inside the shell string, because its query contains `&`. Tap Connect. On first
contact the app then asks for a trust level: tap "I fully trust it", then Connect. A re-pair skips
that step. amy exits once it is logged in. Time from the last tap to amy's exit: a healthy pairing
takes seconds, and a lost first request takes about 65 s.

**`bunker://` (the signer's address).** The Connected Apps screen shows the address as a QR code.
Grab it with `adb exec-out screencap -p > qr.png` and decode it with OpenCV in a scratch venv:
`pip install --only-binary=:all: opencv-python-headless`, then `cv2.QRCodeDetector().detectAndDecode`.
`zxing-cpp` has no wheel for this Mac. Rewrite the relay to `127.0.0.1`, run
`amyx --account bk1 login '<bunker uri>'`, and approve the trust prompt on the emulator.

**Check that signing works without publishing.** `amyx --account app1 event --kind 1 --content test`
signs through the bunker and prints the event without sending it anywhere; read its `pubkey:` and
`sig:` lines. macOS has no `timeout` command, so bound the wait with
`perl -e 'alarm shift; exec @ARGV' 60 amyx …`.

## Regression checklist

| Check                                      | Expect                                                       |
|--------------------------------------------|--------------------------------------------------------------|
| Signer off, open a `nostrconnect://` link  | Logs in at once (the pairing turns the signer on)            |
| Signer on, pair a second app               | Logs in at once; both apps sign in a second or two           |
| Force-stop and relaunch                    | Paired apps sign again without re-pairing                    |
| Signer off, then on                        | No answer while off (lost, not queued); answers once back on |
| New address (Generate)                     | Old address goes dark; the new one pairs and signs           |
| Change the inbox relays while on           | The `bunker://` address follows the new set                  |
| `bunker://` login from another client      | Works after the trust prompt                                 |

## Traps

- **`switch_relays` takes traffic off the local rig.** After a `nostrconnect://` pairing the signer
  moves the app to the account's inbox relays, which on a fresh throwaway account are the public
  defaults. Editing the inbox relays also publishes the relay list to public relays.
- **Replacing all the inbox relays strands moved apps.** They keep asking on the relays they were
  moved to. This is existing behaviour, not a failed test.
- **Clean up afterwards.** Stop `amy serve`, run `adb -s emulator-5554 emu kill`, and delete the
  scratch `HOME`s.
