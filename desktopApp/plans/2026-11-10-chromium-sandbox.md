# Chromium's sandbox on the desktop (phase 2 spike)

**Date:** 2026-11-10. **Status:** spike done, nothing built. Phase 2 of
`2026-11-09-desktop-in-app-browser.md`; the Critical open item of
`commons/plans/2026-10-31-embed-security-review.md`.

## The question

Desktop Chromium (JCEF) runs napplets, nSites and, since phase 1, any website, with no renderer
sandbox. A renderer exploit runs as the user, beside the keys. Phase 2 was written as "Chromium out
of the process that holds the keys". The spike asked: what actually keeps a compromised renderer
away from the keys, what stops JCEF from using Chromium's sandbox, and what it takes on each OS.

## Answer in short

- **The sandbox is the boundary, not another process.** Chromium's renderers already run in
  processes of their own (`jcef_helper`). Unsandboxed, they run as the user, and on Linux and
  Windows any process of the user reads the OS keyring (below). Moving the browser process out of
  the JVM alone would leave that as it is.
- **One line turns it off.** JCEF's native `Context::Initialize` (`native/context.cpp` at the
  commit we ship, `17e805a`) sets `settings.no_sandbox = true` after reading the Java settings, so
  nothing on the Java side can undo it.
- **Linux: verified.** With that line flipped and nothing else changed, renderers run under
  seccomp-bpf in a user namespace of their own, chrooted to an empty directory, and a message-router
  query (what NIP-07 rides on) still works.
- **macOS: very likely the same line.** The shipped `jcef Helper` already calls
  `CefScopedSandboxContext::Initialize` and links `libcef_sandbox.dylib`; only the browser's
  `--no-sandbox` keeps it off. Not run on a Mac.
- **Windows: not without moving Chromium out of `java.exe`.** Chromium's Windows sandbox lives in
  the browser process's own executable; the JCEF bundle ships no sandbox library, and the browser
  process here is the JVM.

## Findings

### Why JCEF turns it off

```
// Sandbox is not supported because:
// - Use of a separate sub-process executable on Windows.
// - Use of a temporary file to communicate custom schemes to the renderer process.
settings.no_sandbox = true;
```

The second reason doesn't touch us: `jcef_helper`'s `OnRegisterCustomSchemes` reads custom schemes
from a temp file a sandboxed renderer can't open, and Amethyst registers none (napplet.local is
served over https through a resource handler in the browser process). It must stay that way, or a
custom scheme silently stops working in sandboxed renderers.

### Linux, measured

A probe (a JCEF app on the same jcefmaven bundle, `CefAppBuilder`, a `CefMessageRouter`, a page whose
script calls `cefQuery`) ran under Xvfb as a non-root user, once on the stock `libjcef.so` and once
on a copy with both `movl $0x1,0xc8(%rsp)` stores (`no_sandbox`) patched to 0. It read
`/proc/<pid>/status`, `ns/user` and `root` of every `jcef_helper` process:

| Process | Stock | `no_sandbox` = 0 |
|---|---|---|
| renderers | seccomp 0, the JVM's user namespace, root `/` (25 entries) | seccomp 2, own user namespace, root `/proc/<zygote>/fdinfo` (empty, unreadable) |
| storage service | — | seccomp 2, own namespace, chrooted |
| GPU process, broker | seccomp 0 | seccomp 2 |
| network service | seccomp 0 | seccomp 0; seccomp 2 with `--enable-features=NetworkServiceSandbox` |
| `cefQuery('ping')` | answered `pong` | answered `pong` |

- **As root**, Chromium refuses its sandbox ("Running as root without --no-sandbox is not
  supported", crbug 638180). The `--no-sandbox` switch still turns it off on a patched build, so
  tests and CI that run as root keep working with it passed explicitly.
- **Not reproduced here:** a kernel without unprivileged user namespaces. Ubuntu 23.10 and later
  restrict them through AppArmor (`kernel.apparmor_restrict_unprivileged_userns`) unless the binary
  has a profile, which Chrome's and Electron apps' packages install. Chromium's fallback, the setuid
  `chrome-sandbox` (shipped in the bundle), needs a root-owned 4755 file, which our layout can't
  give: jcefmaven unpacks Chromium at run time into the user's own folder. Without either, Chromium
  stops with "No usable sandbox!".

### macOS, from the shipped binaries

`jcef Helper` (release 1.0.71, macosx-arm64) contains `CefScopedSandboxContext::Initialize` and
loads `Chromium Embedded Framework.framework/Libraries/libcef_sandbox.dylib`, which carries the
Seatbelt client (`--seatbelt-client=`). jcefbuild configures CEF with its defaults (`USE_SANDBOX`
on for macOS) and signs `libcef_sandbox.dylib`. With `no_sandbox` false the helpers should enter
Seatbelt at start; that needs checking on a Mac (`ps` shows the helpers; Activity Monitor's
"Sandbox" column, or `sandbox-exec`-style denials in Console, show the profile).

### Windows, from the shipped bundle

The windows-amd64 bundle has `jcef_helper.exe`, `libcef.dll`, `jcef.dll` and no sandbox library.
Chromium's Windows sandbox is a broker in the browser process that launches every child with a
restricted token, which CEF supports only when its sandbox code is in the main executable; here the
main executable is `java.exe`. Confining `jcef_helper.exe` from outside (a launcher with a restricted
token or an AppContainer) without Chromium's broker is a sandbox of our own, not a switch. So on
Windows phase 2 is the original plan: Chromium in a native host process of its own (frames and
input over shared memory and IPC, NIP-07 queries proxied to the app), with CEF's sandbox in it.

### What an unsandboxed renderer reaches today

Desktop keys sit in the OS keyring (`SecureKeyStorage`, `commons/plans/2026-10-12-desktop-key-storage.md`),
or in a NIP-49 file whose password lives only in the JVM.

- **Linux, Secret Service / KWallet:** no per-application access control; any process of the user
  reads an unlocked item over D-Bus.
- **Windows, Credential Manager:** any process of the user reads it (`CredRead`); it can also open
  the JVM and read its memory.
- **macOS, Keychain:** an item trusts the signed app that wrote it; another binary gets an "allow"
  prompt. But the strict read path asks `/usr/bin/security find-generic-password`, so a user who
  once clicked *Always Allow* there has trusted that tool, and through it every process, for that
  item. Worth fixing on its own (read through the app's own Keychain calls).
- **Everywhere:** code running as the user can also change what runs next (autostart entries, the
  app's own files), which no key storage protects against.

So moving Chromium out of the JVM, without the sandbox, would still let a renderer exploit read the
keys on Linux and Windows. The renderer sandbox is what closes it.

## Shipping it

### Getting a JCEF without the hard-coded line

| | How | Cost |
|---|---|---|
| **A. Upstream** | A java-cef change: `no_sandbox` becomes a `CefSettings` field, default true; still forced on Windows. jcefbuild and jcefmaven pick it up in a release. | Small change; when it ships isn't ours to decide. |
| **B. Our natives** | Fork jcefbuild, apply the one-line patch, publish the natives ourselves and point jcefmaven at them (`CefAppBuilder` mirrors). Re-sign and notarize the macOS helpers with our identity. | Unblocks now; a build to run on every CEF bump. |
| C. Patch the installed binary | What the spike did. | Rejected: it breaks the macOS code signature, and the instruction moves with every build. |

Recommendation: propose A upstream and do B meanwhile; B goes away once A ships.

### App changes once the natives allow it

- **Turn it on and check it took:** start Chromium without `no_sandbox`, and after the first page,
  confirm from `/proc` (Linux) that renderers have their own namespace and a seccomp filter; on
  macOS, that the helpers are sandboxed.
- **When Linux has no usable sandbox** (AppArmor-restricted user namespaces, no setuid helper),
  decide one of:
  1. ship Chromium in the `.deb` at a fixed path with an AppArmor profile, as Chrome does (the
     runtime download stays for the tarball and AppImage);
  2. or detect it before starting (`unshare -U` in a child, or the sysctls) and keep websites in
     the system browser while napplets run as they do now;
  3. or refuse Chromium altogether there.
  Never fall back to `--no-sandbox` without telling the user.
- **Network service:** try `NetworkServiceSandbox` with the SOCKS router and the resource handlers.
- **Tests:** CI runs as root, so the Chromium suites pass `--no-sandbox`; add one job (or a local
  script) that runs them as a non-root user with the sandbox on, the way the spike did, so the app's
  own paths (off-screen rendering, the guard extension, DevTools scripts, downloads, napplet blobs,
  the WebRTC guard) are known to work sandboxed.
- **Windows:** keep websites in the system browser (or behind the opt-in setting) until there is a
  native browser host; scope that host as its own plan.

### Order

1. Open the java-cef change (A); file the jcefmaven side.
2. Build our natives (B) for Linux and macOS; run the probe and the Chromium suites sandboxed.
3. App: start sandboxed, verify it took, the Linux no-sandbox policy, the non-root test job.
4. Windows: decide between a native browser host and keeping websites out of Chromium there.

## How to repeat the Linux check

1. As root, make a non-root user. Give it a copy of the JCEF bundle. In the copy, patch the two
   `movl $0x1,0xc8(%rsp)` stores in `Context::Initialize` (find them with `objdump -d` from
   `Java_org_cef_CefApp_N_1Initialize` → `Context::Initialize`; the settings struct is the one
   whose first field is set to `0x1c0`, its size).
2. Run a JCEF program as that user under `xvfb-run`. It needs Chromium's background networking off
   (`--disable-background-networking --disable-component-update --no-proxy-server`, or startup
   waits forever behind a TLS-breaking proxy). It loads a page that calls `cefQuery`.
3. Read `/proc/<pid>/status` (`Seccomp:`), `ns/user` and `root` of each `jcef_helper`.
