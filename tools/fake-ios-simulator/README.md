# fake-ios-simulator

Stand-ins for `xcrun simctl` and idb's `idb_companion`, so the device panel's iOS path runs on a
computer without Xcode (Linux, CI-like containers). They are test tools: nothing ships them.

- `xcrun` answers the `simctl` subcommands the app uses (`list -j`, `boot`, `bootstatus`,
  `shutdown`, `clone`, `delete`, `erase`, `install`, `uninstall`, `launch`, `terminate`, `openurl`,
  `get_app_container`, `spawn … launchctl list`, `spawn … log show`, `io … screenshot`), keeping its
  simulators in `$FAKE_IOS_STATE` (default `/tmp/fake-ios-simulators.json`; delete it to start over).
- `idb_companion` serves gRPC over HTTP/2 on its `--grpc-domain-sock`, as the real one does, for
  `describe`, `screenshot`, `video_stream` (minicap JPEGs), `hid` (touches, keys, text,
  orientation), `accessibility_info`, `get_orientation`/`set_orientation`, `crash_list`,
  `list_apps`. It draws a 375x667-point screen at 2x: a button that counts taps, a text field that
  shows what is typed after a tap on it, and a dot where the finger is.

Needs Python 3 with `h2` and `pillow`:

```bash
pip install h2 pillow
```

Run the desktop app against them:

```bash
export AMETHYST_XCRUN="$PWD/tools/fake-ios-simulator/xcrun"
export AMETHYST_IDB_COMPANION="$PWD/tools/fake-ios-simulator/idb_companion"
tools/desktop-run/run-headless.sh start
```

`commons`'s `FakeSimulatorToolsTest` drives the agents' device tools against them, and skips
itself where Python lacks `h2` or `pillow`.
