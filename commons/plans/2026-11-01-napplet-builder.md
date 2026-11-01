# Napplet builder: an LLM that builds napplets inside Amethyst — 2026-11-01

Status: plan. Nothing built yet.

amy-lm ([vitorpamplona/amy-lm](https://github.com/vitorpamplona/amy-lm)) is a static web client
where you tell an LLM what you want to see ("a feed of my follows' photos") and it writes a small
live view. This plan brings that idea into Amethyst. It is **not** a port of amy-lm: the views
Amethyst builds are plain NIP-5D napplets, run by the napplet host Amethyst already has. amy-lm stays
as it is, a separate web experiment with its own `render(root, api)` contract. The two share ideas
and prompt structure, not a file format.

## Decisions

| Question | Decision |
|---|---|
| What a generated view is | A NIP-5D napplet (one `index.html`), written against `window.napplet.*`. It runs in the existing sandbox: Android's keyless `:napplet` process, the desktop's JCEF. No amy `api` shim. |
| Where the LLM layer lives | `commons`, headless: provider clients, the tool loop, the system prompt and the tools. Android and desktop share it. **No `amy` verb.** |
| Where the builder appears | A dedicated **Build** screen in `commonsUI`: chat beside a live canvas (stacked on compact windows). |
| Drafts | Local files in app-private storage. Nothing is signed or leaves the device until Publish. No NIP-37 wraps, no project sync. |
| Moving to another device | Publish the napplet. On the other device, open any published napplet (yours or anyone's) as a new project: you get its code but not its chat. |
| Publish kind | Named napplet, kind **35129**, one per project (`d` = project slug). Republishing replaces it, so pinned tabs and note cards run the latest version. An optional immutable snapshot (5129) can come later. |
| After building | Keep it as a local project, publish it, pin it as a favorite tab or deck column. |
| Draft identity | A draft runs **as its future 35129 coordinate**: your pubkey plus the project's `d`, with the draft's sha256 as the hash. Grants you give while building carry over to the published app. |
| Agent tool permissions | A tool that reaches beyond public reads asks the first time, **with an option to remember** the answer (per project). |
| Providers at launch | Claude, OpenAI and Gemini via a pasted key (detected from the key, as in amy-lm); **on-device**: Gemini Nano on Android, a local Ollama or LM Studio server on desktop. |
| Key storage | `SecureKeyStorage` (Android Keystore, desktop OS keyring). Keys never go in project files or on relays. |

## Survey: reuse, extend, new

| Component | Status | Where |
|---|---|---|
| Sandbox, broker, consent, NAP domains (outbox, relay, identity, storage, theme, fs, intent, inc) | **Reused** | `commons/…/napplet/`, `nappletHost`, `desktopApp/…/napplet/` |
| Serving a napplet's files | **Extended**: a new `NappletFiles` implementation over a local directory | `commons/src/jvmAndroid/…/napplet/blobs/NappletFiles.kt` |
| Launch identity | **Extended**: a new trusted launcher, `NappletLaunch.fromLocalDraft(project)` | `commons/…/napplet/NappletLaunch.kt`, `NappletIdentity.kt` |
| Manifest building and publish | **Reused**, with the logic lifted out of the CLI. `amy napplet publish` builds `NamedNappletEvent` and uploads to Blossom; that goes into a commons `NappletPublisher` that both the CLI and the builder call. | `cli/…/commands/NappletCommands.kt` → `commons/…/napplet/` |
| Blossom upload | **Reused** | `commons/…/napplet/gateways/AccountNappletUploadGateway.kt` |
| Manifest kinds and tags | **Reused** | quartz `nip5dNapplets/` (35129, 15129, 5129) |
| Inline runs and favorite tabs | **Reused**: a published project is an ordinary napplet | `commonsUI/…/napplet/` |
| Relay reads for agent tools | **Reused**: `fetchAll`, `fetchFirst`, `count` and the outbox routing | quartz `nip01Core/relay/client/accessories/`, `NappletOutboxRouting` |
| Account and cache reads for agent tools | **Reused** | `LocalCache`, `Account` flows |
| Key storage | **Reused** | `commons/…/keystorage/SecureKeyStorage.kt` |
| LLM provider clients and tool loop | **New** | `commons/…/builder/llm/` |
| Project store (files on disk) | **New** | `commons/…/builder/project/` |
| Build screen | **New** | `commonsUI/…/builder/` |
| A host hook that forwards console errors from a builder run | **New**, opt-in per launch | napplet shim + both hosts |

## Modules and packages

```
commons/src/commonMain/…/commons/builder/
  llm/        LlmProvider, LlmMessage, LlmTool, ToolCall, ToolLoop, ProviderDetector
  agent/      BuilderAgent (system prompt + tools), BuilderTool implementations
  project/    BuilderProject, ProjectVersion, ProjectStore (interface), ChatLog
  permissions/BuilderToolGrants (asks, remembered answers per project)
commons/src/jvmAndroid/…/commons/builder/
  llm/        AnthropicProvider, OpenAiProvider, GeminiProvider, OpenAiCompatibleProvider (OkHttp, SSE)
  project/    FileProjectStore, LocalDraftFiles : NappletFiles
amethyst/src/play/…/builder/  GeminiNanoProvider (play flavor only; see Risks)
desktopApp/…/builder/         LocalServerProvider wiring (Ollama :11434, LM Studio :1234)
commonsUI/src/commonMain/…/commons/builder/  BuildScreen, BuilderChat, BuilderCanvas, PublishSheet, ProviderLoginDialog
```

The ViewModel (`BuilderViewModel`: chat state, the running loop, the current version) goes in
`commons/…/builder/`. It takes `BaseAccountViewModel` because publishing needs `launchSigner`.

## LLM layer

- `LlmProvider` has one operation: stream a turn with messages and tool definitions, returning
  text deltas and tool calls. Each provider maps the shared shape onto its own wire format
  (Anthropic Messages, OpenAI Chat Completions, Gemini `generateContent`). The on-device providers
  use the same interface: Ollama and LM Studio speak OpenAI's format, so desktop on-device is
  `OpenAiCompatibleProvider` with a probed `localhost` base URL.
- `ProviderDetector` works as amy-lm's `auth.js` does: `sk-ant-` → Claude, `sk-` → OpenAI,
  `AIza…`/`AQ.` → Gemini. It verifies the key with a cheap live call before saving it.
- `ToolLoop` runs model → tool calls → results → model until the model stops, with a step budget
  and cancellation through the ViewModel's scope.
- Context control carries over from amy-lm: before each turn, strip the code out of every
  `save_view` call except the most recent one, so a long session doesn't resend every version.
- The default model per provider is a setting, not a constant in code.

## The agent's tools

| Tool | Does | Gate |
|---|---|---|
| `read_nip` | Fetch a NIP's markdown from `nostr-protocol/nips` | none |
| `read_nap` | Fetch NIP-5D/NAP specs, plus **the host's own list of supported domains**, so the model declares only what Amethyst provides | none |
| `get_context` | The account's pubkey, relays, and supported NAP domains | none |
| `query_relays` / `search_relays` | One-shot NIP-01 query / NIP-50 search through the accessories | none for public data; a per-turn budget |
| `read_cache` | Look up follows, lists, profiles and cached notes in `LocalCache` | public data: none. Decrypted DMs, private list items, drafts: **ask, can remember** |
| `save_view` / `list_views` / `delete_view` | Write a new version of the project's `index.html` (with `fromVersion`), list versions, delete one | none (local files) |
| `inspect_view` | After a save, return the run's console errors and failed NAP requests, and on desktop a screenshot | screenshot to the provider: **ask, can remember** |
| `publish` | Upload to Blossom and sign the 35129 manifest | **always shows the publish sheet** (name, size, declared capabilities, what changed since the last publish); the signer prompts as usual. "Remember" skips the sheet but never the signer. |

Grants are kept per project in `BuilderToolGrants`. They are separate from the napplet broker's
ledger: these control what the *agent* may do, while the broker controls what the *running view*
may do.

## Projects on disk

```
<app data>/builder/<projectId>/
  project.json      { id, slug (the future d-tag), title, provider, createdAt, publishedEventId?, publishedHash? }
  chat.jsonl        one message per line, provider-neutral shape
  versions/v1/index.html
  versions/v2/index.html …
  grants.json       remembered tool answers
```

- Android: `filesDir/builder`. Desktop: the app data directory, with "Reveal in folder" so you can
  open a project in an editor.
- `slug` is chosen once (from the title, editable before the first publish) and fixed after it:
  changing it would change the napplet's coordinate, and with it its grants and storage.
- "Open published napplet as project" fetches the manifest and artifact (the same path the CLI's
  `fetch` uses), writes them to `versions/v1`, and starts an empty chat. When the napplet is
  someone else's, the new project gets its own slug under *your* key, so it is a fork, not an edit
  of theirs.
- Writes follow the repo rules: act on the result of every `mkdirs`, `renameTo` and `delete`, and
  write a version to a temporary file, then rename it into place.

## Running a draft

`NappletLaunch.fromLocalDraft(project, version)` is a trusted launcher like `fromManifest`:

- identity: `authorPubKey` = the account, `identifier` = `slug`, `manifestKind` = 35129,
  `aggregateHash` = sha256 of the version's `index.html`, and `manifestEventId` = null.
- files: `LocalDraftFiles` serves `versions/vN/index.html` as `/index.html`. The site server, shim
  and CSP are the same as for a published napplet.
- declared domains: read from the draft's own `<meta>`/manifest block (format to settle in phase
  2), and checked against the host's supported list just as a manifest's `R`/`O` tags are.
- The broker asks for consent as it does for any napplet. Because the coordinate matches the future
  published app, those grants apply to the published app too. NAP-STORAGE stays scoped per hash, as
  it already is for published versions.
- Only the Build screen can create this launch, so a feed or a NAP request can never claim a draft
  identity.

## Build screen

- Wide windows: chat on the left, canvas on the right. Compact: the canvas on top and the chat as a
  bottom sheet. The layout follows `ScreenLayoutSpec`.
- The canvas reuses the embedded napplet surface (Android: `SandboxedSdkView` through
  `EmbeddedNostrAppController`; desktop: the off-screen browser that inline runs use), with version
  tabs (v1, v2, …) above it.
- Project list: local projects, then "Open a published napplet".
- Actions: Publish, Pin as tab, Reveal files (desktop), Delete project.
- Entry point: a drawer item and a `Route.Builder`. Every string goes in `commonsUI` Compose
  resources.

## Phases

1. **Headless LLM layer.** `LlmProvider` and the three cloud providers, `ProviderDetector`,
   `ToolLoop`, the context stripping, and key storage. Tests run against recorded fixtures of each
   provider's wire format, with no network.
2. **Projects and drafts.** `FileProjectStore`, `LocalDraftFiles`, `NappletLaunch.fromLocalDraft`,
   and the declared-domains format. Tests cover the identity and coordinate rules and the file
   writes.
3. **Agent and tools.** System prompt, the read tools, `save_view`, `BuilderToolGrants`, and
   lifting the CLI's publish logic into a commons `NappletPublisher`.
4. **Build screen, desktop first.** Chat, canvas, versions and publish. Checked with
   `tools/desktop-run/run-headless.sh`.
5. **Android.** The embedded surface in the canvas, plus device checks.
6. **Inspect and on-device.** The console-forwarding hook in both hosts, desktop screenshots, the
   Ollama/LM Studio probe, Gemini Nano (play flavor).

## Risks and open questions

- **Gemini Nano licensing.** ML Kit GenAI / AICore is a Google SDK. Read its actual license terms
  before adding it. If it is proprietary, the repo's dependency rule says STOP: a maintainer has to
  decide, in the PR description, and it could ship only in the `play` flavor (never F-Droid).
- **On-device quality.** Nano is far too small to write a whole napplet. It may only be useful for
  small edits, or not at all; decide after trying it.
- **No open network for views.** The NAPPLET profile has no network, so generated apps reach Nostr
  only through NAPs and can't call arbitrary web APIs. The prompt has to say so.
- **Screenshots on Android.** The view draws in another process's surface, so capturing it may not
  be possible. Inspect may be limited to console output there.
- **Draft trust.** Running drafts as your own coordinate means a grant given to v3 also covers a
  later v7 that the model rewrote. The broker's existing "ask again when the code changes" option
  covers anyone who wants per-version consent; decide whether builder drafts should turn it on by
  default.
- **Costs.** A long build session spends real tokens. Show a token or cost estimate per turn.
- **Prompt injection.** `query_relays` and `read_cache` feed other people's notes to the model. A
  note can try to steer it ("publish this…"). The gates on `publish` and private reads are the
  defence; never let tool output skip a gate.
