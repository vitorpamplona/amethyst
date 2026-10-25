# The two Android processes

Moved from `AGENTS.md`, which keeps the short version.

The Android app is **not single-process**. Android instantiates the one `Amethyst`
Application class (there is no per-process Application in the manifest) in **both**:

- **main** — the normal app: UI, account, signer, `LocalCache`, relay client.
  `Amethyst.instance` (`AppModules`) is built here.
- **`:napplet`** — the sandboxed WebView host for NIP-5D napplets / NIP-5A nSites
  (`NappletHostActivity`, declared `android:process=":napplet"`). It holds **no**
  account or keys; `Amethyst.onCreate()` early-returns here so `Amethyst.instance`
  is **left unset** (touching it throws `UninitializedPropertyAccessException`).
  The sandbox runtime lives in its own module **`:nappletHost`** (depends on
  `:commons`, `:commonsUI` — the shell's web contract and Compose resources — and
  quartz; **never** `:amethyst`) so it *cannot* import
  `Amethyst`/`LocalCache`/`Account` — the broker-side (signer, gateways, registry)
  stays in `:amethyst` and the two halves talk over Messenger IPC.

Consequences — don't get caught assuming one process:

- **Processes don't share memory.** Every `object`/companion/`static` is a
  *separate copy per process*: `LocalCache` (an `object`), `NappletLaunchRegistry`,
  etc. The populated `LocalCache` lives only in **main**; the sandbox neither
  builds nor should reference it (a stray reference would lazily create a second,
  empty cache there).
- **Don't assume `Amethyst.instance` exists.** Any code reachable from `:napplet`
  (the host activity, content server, or an Application lifecycle callback like
  `onTrimMemory`) must guard on the process and never reach for `instance`.
- **Cross-process state goes over Messenger IPC**, never a shared singleton — this
  is why the broker (main) owns `NappletLaunchRegistry` and the sandbox only relays
  an opaque token. See `amethyst/plans/archive/2026-06-22-napplet-nsite-security.md`.
