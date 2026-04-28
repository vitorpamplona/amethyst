 <div align="center">

<a href="https://amethyst.social">
    <img src="./docs/design/3rd%20Logo%20-%20Zitron/amethyst.svg" alt="Amethyst Logo" title="Amethyst logo" width="80"/>
</a>

# Amethyst

## Nostr Client for Android

Join the social network you control.

[![GitHub downloads](https://img.shields.io/github/downloads/vitorpamplona/amethyst/total?label=Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/vitorpamplona/amethyst/releases)
[![PlayStore downloads](https://img.shields.io/endpoint?color=green&logo=google-play&logoColor=green&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.vitorpamplona.amethyst%26gl%3DUS%26hl%3Den%26l%3DPlayStore%26m%3D%24shortinstalls)](https://play.google.com/store/apps/details?id=com.vitorpamplona.amethyst)

[![Last Version](https://img.shields.io/github/release/vitorpamplona/amethyst.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://github.com/vitorpamplona/amethyst)
[![JitPack version](https://jitpack.io/v/vitorpamplona/amethyst.svg)](https://jitpack.io/#vitorpamplona/amethyst)
[![CI](https://img.shields.io/github/actions/workflow/status/vitorpamplona/amethyst/build.yml?labelColor=27303D)](https://github.com/vitorpamplona/amethyst/actions/workflows/build.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/vitorpamplona/amethyst?labelColor=27303D&color=0877d2)](/LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/vitorpamplona/amethyst)

## Download and Install

### Android

[<img src="./docs/design/zapstore.svg"
alt="Get it on Zap Store"
height="70">](https://github.com/zapstore/zapstore/releases)
[<img src="./docs/design/obtainium.png"
alt="Get it on Obtaininum"
height="70">](https://github.com/ImranR98/Obtainium)
[<img src="https://github.com/machiav3lli/oandbackupx/raw/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub"
height="70">](https://github.com/vitorpamplona/amethyst/releases)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="70">](https://play.google.com/store/apps/details?id=com.vitorpamplona.amethyst)

### Desktop

| OS | CLI install | Direct download |
|---|---|---|
| macOS (Apple Silicon) | `brew install --cask amethyst-nostr` | [.dmg arm64](https://github.com/vitorpamplona/amethyst/releases/latest) |
| macOS (Intel) | `brew install --cask amethyst-nostr` | [.dmg x64](https://github.com/vitorpamplona/amethyst/releases/latest) |
| Windows 10/11 | `winget install VitorPamplona.Amethyst` | [.msi](https://github.com/vitorpamplona/amethyst/releases/latest) · [.zip portable](https://github.com/vitorpamplona/amethyst/releases/latest) |
| Debian/Ubuntu | — | [.deb](https://github.com/vitorpamplona/amethyst/releases/latest) |
| Fedora/RHEL/openSUSE | — | [.rpm](https://github.com/vitorpamplona/amethyst/releases/latest) |
| Any Linux | — | [AppImage](https://github.com/vitorpamplona/amethyst/releases/latest) · [.tar.gz](https://github.com/vitorpamplona/amethyst/releases/latest) |

_Coming soon (separate PR): Scoop (Windows), AUR (Arch Linux)._

**Build from source:** see [BUILDING.md](BUILDING.md).

**Install troubleshooting** (Gatekeeper / SmartScreen / AppImage): see
[BUILDING.md § Troubleshooting installs](BUILDING.md#troubleshooting-installs).

</div>

## Supported Features

<img align="right" src="./docs/screenshots/home.png" data-canonical-src="./docs/screenshots/home.png" width="350px">

- [x] Basic protocol flow (NIP-01)
- [x] Follow List (NIP-02)
- [x] OpenTimestamps Attestations (NIP-03)
- [x] Encrypted Direct Message (NIP-04)
- [x] DNS-based Identifiers (NIP-05)
- [x] Key Derivation from Mnemonic (NIP-06)
- [ ] window.nostr for Web Browsers (NIP-07, Not applicable)
- [x] Handling Mentions (NIP-08)
- [x] Event Deletion Request (NIP-09)
- [x] Text Notes and Threads (NIP-10)
- [x] Relay Information Document (NIP-11)
- [x] Proof of Work (NIP-13)
- [x] Subject Tag in Text Events (NIP-14)
- [x] Nostr Marketplace (NIP-15)
- [x] Private Direct Messages (NIP-17)
- [x] Reposts (NIP-18)
- [x] bech32-encoded Entities (NIP-19)
- [x] nostr: URI Scheme (NIP-21)
- [x] Comment (NIP-22)
- [x] Long-form Content (NIP-23)
- [x] Extra Metadata Fields and Tags (NIP-24)
- [x] Reactions (NIP-25)
- [x] Delegated Event Signing (NIP-26)
- [x] Text Note References (NIP-27)
- [x] Public Chat (NIP-28)
- [x] Relay-based Groups (NIP-29)
- [x] Custom Emoji (NIP-30)
- [x] Dealing with Unknown Events (NIP-31)
- [x] Labeling (NIP-32)
- [x] git stuff (NIP-34)
- [x] Torrents (NIP-35)
- [x] Sensitive Content (NIP-36)
- [x] Draft Events (NIP-37)
- [x] User Statuses (NIP-38)
- [x] External Identities in Profiles (NIP-39)
- [x] Expiration Timestamp (NIP-40)
- [x] Authentication of Clients to Relays (NIP-42)
- [x] Relay Access Metadata and Requests (NIP-43)
- [x] Encrypted Payloads / Versioned (NIP-44)
- [x] Counting Results (NIP-45)
- [x] Nostr Remote Signing (NIP-46)
- [x] Nostr Wallet Connect (NIP-47)
- [x] Proxy Tags (NIP-48)
- [x] Private Key Encryption (NIP-49)
- [x] Search Capability (NIP-50)
- [x] Lists (NIP-51)
- [x] Calendar Events (NIP-52)
- [x] Live Activities (NIP-53)
- [x] Wiki (NIP-54)
- [x] Android Signer Application (NIP-55)
- [x] Reporting (NIP-56)
- [x] Lightning Zaps (NIP-57)
- [x] Zap Splits (NIP-57)
- [x] Private Zaps (NIP-57)
- [x] Zapraiser (NIP-57)
- [x] Badges (NIP-58)
- [x] Gift Wrap (NIP-59)
- [x] Pubkey Static Websites (NIP-5A)
- [x] Cashu Wallet (NIP-60)
- [x] Nutzaps (NIP-61)
- [x] Request to Vanish (NIP-62)
- [x] Chess / PGN (NIP-64)
- [x] Relay List Metadata (NIP-65)
- [x] Relay Discovery and Liveness Monitoring (NIP-66)
- [x] Picture-first Feeds (NIP-68)
- [x] Peer-to-peer Order Events (NIP-69)
- [x] Protected Events (NIP-70)
- [x] Video Events (NIP-71)
- [x] Moderated Communities (NIP-72)
- [x] External Content IDs (NIP-73)
- [x] Zap Goals (NIP-75)
- [x] Negentropy Syncing (NIP-77)
- [x] Application-specific Data (NIP-78)
- [x] Threads (NIP-7D)
- [x] Highlights (NIP-84)
- [x] Trusted Assertions (NIP-85)
- [x] Relay Management API (NIP-86)
- [x] Ecash Mint Discoverability (NIP-87)
- [x] Polls (NIP-88)
- [x] Recommended Application Handlers (NIP-89)
- [x] Data Vending Machines (NIP-90)
- [x] Media Attachments (NIP-92)
- [x] File Metadata (NIP-94)
- [x] Binary Blobs (NIP-95/Draft)
- [x] HTTP File Storage Integration (NIP-96)
- [x] HTTP Auth (NIP-98)
- [x] Classified Listings (NIP-99)
- [x] Voice Messages (NIP-A0)
- [x] Public Messages (NIP-A4)
- [x] Web Bookmarks (NIP-B0)
- [x] Blossom (NIP-B7)
- [x] Nostr BLE Communications Protocol (NIP-BE)
- [x] Code Snippets (NIP-C0)
- [x] Chats (NIP-C7)
- [ ] MLS Protocol (NIP-EE)
- [x] Audio Tracks (zapstr.live) (kind:31337)
- [x] Lightning Tips
- [x] Image/Video/Url/LnInvoice/Cashu Previews
- [x] Push Notifications (Google and Unified Push)
- [x] In-Device Automatic Translations
- [x] Hashtag Following and Custom Hashtags
- [x] Login with QR
- [x] Bounty support (nostrbounties.com)
- [x] De-googled F-Droid flavor
- [x] Multiple Accounts
- [x] Markdown Support
- [x] Medical Data (NIP-xx/Draft)
- [x] Embed events (NIP-xx/Draft)
- [x] Edit Short Notes (NIP-xx/Draft)
- [x] NIP Events (NIP-xx/Draft)
- [ ] Relationship Status (NIP-xx/Draft)
- [ ] Signed Filters (NIP-xx/Draft)
- [ ] Key Migration (NIP-xx/Draft)
- [x] Image Capture in the app
- [x] Video Capture in the app
- [ ] Local Database
- [ ] Workspaces

## Privacy and Information Permanence

Relays know your IP address, your name, your location (guessed from IP), your pub key, all your contacts, and other relays, and can read every action you do (post, like, boost, quote, report, etc) except for Private Zaps and Private DMs. While the content of direct messages (DMs) is only visible to you and your DM counterparty, everyone can see when you and your counterparty DM each other.

If you want to improve your privacy, consider utilizing a service that masks your IP address (e.g. a VPN or Tor) from trackers online.

The relay also learns which public keys you are requesting, meaning your public key will be tied to your IP address.

Information shared on Nostr can be re-broadcasted to other servers and should be assumed permanent for privacy purposes. There is no way to guarantee the deletion of any content once posted.

# Development Overview

This repository is split between Amethyst, Quartz, Commons, and DesktopApp:
- **Amethyst** - Native Android app with Kotlin and Jetpack Compose
- **Quartz** - Nostr-commons KMP library for protocol classes shared across platforms
- **Commons** - Kotlin Multiplatform module with shared UI components (icons, robohash, blurhash, composables)
- **DesktopApp** - Compose Multiplatform Desktop application reusing commons and quartz

The app architecture consists of the UI, which uses the usual State/ViewModel/Composition, the service layer that connects with Nostr relays,
and the model/repository layer, which keeps all Nostr objects in memory, in a full OO graph.

The repository layer stores Nostr Events as Notes and Users separately. Those classes use LiveData and Flow objects to
allow the UI and other parts of the app to subscribe to each Note/User and receive updates when they happen.
They are also responsible for updating viewModels when needed. As the user scrolls through Events, the Datasource classes
are updated to receive more information about those particular Events.

Most of the UI is reactive to changes in the repository classes. The service layer assembles Nostr filters for each need of the app,
receives the data from the Relay, and sends it to the repository. Connection with relays is never closed during the use of the app.
The UI receives a notification that objects have been updated. Instances of User and Notes are mutable directly.
There will never be two Notes with the same ID or two User instances with the same pubkey.

Lastly, the user's account information (private key/pub key) is stored in the Android KeyStore for security.

## Setup

Make sure to have the following pre-requisites installed:
1. Java 21+
2. Android Studio
3. Android 8.0+ Phone or Emulation setup

Fork and clone this repository and import it into Android Studio
```bash
git clone https://github.com/vitorpamplona/amethyst.git
```

Use an Android Studio build action to install and run the app on your device or a simulator.

## Building

Build the Android app:
```bash
./gradlew assembleDebug
```

Build and run the Desktop app (requires Java 21+):
```bash
./gradlew :desktopApp:run
```
Full build (including tests)
```bash
./gradlew build
```
Requirements:
- Xcode and iOS simulator
- libsodium installed (e.g. via brew: `brew install libsodium`

## Testing
```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Linting
```bash
./gradlew spotlessCheck
./gradlew spotlessApply
```

## Installing on device

For the F-Droid build:
```bash
./gradlew installFdroidDebug
```

For the Play build:
```bash
./gradlew installPlayDebug
```

## Deploying

Full release + bootstrap runbooks (Android AAB upload, desktop packaging,
Homebrew cask, Winget manifest, Apple Developer signing budget time-box) live
in [BUILDING.md § Release runbook](BUILDING.md#release-runbook) and
[BUILDING.md § Bootstrap runbook (one-time)](BUILDING.md#bootstrap-runbook-one-time).

TL;DR for cutting a release:

1. Bump `app` in `gradle/libs.versions.toml` (e.g. `"1.08.1"`)
2. Bump `versionCode` in `amethyst/build.gradle`
3. `git commit -am "chore(release): 1.08.1" && git tag -s v1.08.1 && git push --tags`
4. Wait for `Create Release Assets` workflow — 20 Android assets + 8 desktop assets go live on GH Release; Homebrew + Winget auto-bump on stable tags
5. Upload AAB to Play Store manually (existing step)

## Using the Quartz library

### Installing

Add Maven Central and Google Maven to your repositories:

```gradle
repositories {
    mavenCentral()
    google()
}
```

Add the following line to your `commonMain` dependencies:

```gradle
implementation('com.vitorpamplona.quartz:quartz:1:05.0')
```

Variations to each platform are also available:

```gradle
implementation('com.vitorpamplona.quartz:quartz-android:1:05.0')
implementation('com.vitorpamplona.quartz:quartz-jvm:1:05.0')
implementation('com.vitorpamplona.quartz:quartz-iosarm64:1:05.0')
implementation('com.vitorpamplona.quartz:quartz-iossimulatorarm64:1:05.0')
```

Check versions on [MavenCentral](https://central.sonatype.com/search?q=com.vitorpamplona.quartz)

### How to use

Manage logged in users with the `KeyPair` class

```kt
val keyPair = KeyPair() // creates a random key
val keyPair = KeyPair("hex...".hexToByteArray())
val keyPair = KeyPair("nsec1...".bechToBytes())
val keyPair = KeyPair(Nip06().privateKeyFromMnemonic("<mnemonic>"))
val readOnly = KeyPair(pubKey = "hex...".hexToByteArray())
val readOnly = KeyPair(pubKey = "npub1...".bechToBytes())
```

Create signers that can be Internal, when you have the private key or a read-only public key,
or External, when it is controlled by Amber in NIP-55.

Use either the `NostrSignerInternal` or `NostrSignerExternal` class:

```kt
val signer = NostrSignerInternal(keyPair)
val amberSigner = NostrSignerExternal(
    pubKey = keyPair.pubKey.toHexKey(),
    packageName = signerPackageName, // Amber package name
    contentResolver = appContext.contentResolver,
)
```

Create a single `NostrClient` for the entire application and control which relays it will access by
registering subscriptions and sending events. The pool will automatically change based on filters +
outbox events.

You will need a coroutine scope to process events and if you are using OKHttp, we offer a basic
wrapper to create the socket connections themselves.

```kt
val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
val rootClient = OkHttpClient.Builder().build()
val socketBuilder = BasicOkHttpWebSocket.Builder { url -> rootClient }

val client = NostrClient(socketBuilder, appScope)
```

If you want to auth, given a logged-in `signer`:

```kt
val authCoordinator = RelayAuthenticator(client, appScope) { authTemplate ->
    listOf(
        // for each signed-in user, return an event
        signer.sign(authTemplate)
    )
}
```

To make a request subscription simply do:

```kt
val metadataSub = client.req(
    relay = "wss://nos.lol",
    filter = Filter(
        kinds = listOf(MetadataEvent.KIND),
        authors = listOf(signer.pubkey)
    )
) { event ->
    /* consume event */
}
```

The client will add the relay to the pool, connect to it and start receiving events. The
`metadataSub` will be active until you call `metadataSub.close()`. If the client disconnects
and reconnects, the sub will be active again.

To manage subscriptions that change over time, the simplest approach is to build mutable
subscriptions with a filters lambda that you can change at will.

```kt
val metadataSub = client.req(
    filters = {
        // Let's say you have a list of users that need to be rendered
        val users = pubkeysSeeingInTheScreen()
        // And a cache repository with their outbox relays
        val outboxRelays = outboxRelays(users)

        val filters = listOf(
            Filter(
                kinds = listOf(MetadataEvent.KIND),
                authors = users
            )
        )

        outboxRelays.associateWith { filters }
    }
) { event ->
    /* consume event */
}
```

In that way, you can simply call `metadataSub.updateFilter()` when you need to update
subscriptions to all relays. Or call `metadataSub.close()` to stop the sub
without deleting it.

When your app goes to the background, you can use NostrClient's `connect` and `disconnect`
methods to stop all communication to relays. Add the `connect` to your `onResume` and `disconnect`
to `onPause` methods.

### Feature Parity Table

| Feature Category         | Feature / Component            | Android / JVM Support | iOS Support | Notes                                                                  |
|:-------------------------|:-------------------------------|:---------------------:|:-----------:|:-----------------------------------------------------------------------|
| **Cryptography**         | Secp256k1 (Schnorr, Keys)      |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | LibSodium (ChaCha20, Poly1305) |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | AES Encryption (CBC & GCM)     |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | Hashing (SHA-256, etc.)        |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | MAC (HmacSHA256, etc.)         |        ✅ Full         |   ✅ Full    |                                                                        |
| **Data & Serialization** | JSON Mapping (Optimized)       |        ✅ Full         |   ✅ Full    | A fully custom implementation exists in `commonMain`.                  |
|                          | GZip Compression               |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | BitSet                         |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | LargeCache                     |        ✅ Full         |   ✅ Full    |                                                                        |
| **NIP Support**          | NIP-96 (File Storage Info)     |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | NIP-46 (Remote Signer)         |        ✅ Full         | ⚠️ Partial  | Some methods in `NostrSignerRemote` are unimplemented in `commonMain`. |
|                          | NIP-03 (OTS / Timestamps)      |        ✅ Full         |    ❌ No     | `BitcoinExplorer` and `RemoteCalendar` have stubs in `commonMain`.     |
| **Utilities**            | URL Encoding / Decoding        |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | Unicode Normalization          |        ✅ Full         |   ✅ Full    |                                                                        |
|                          | Platform Logging               |        ✅ Full         |   ✅ Full    | iOS uses `NSLog`, Android uses standard Log.                           |
|                          | Current Time                   |        ✅ Full         |   ✅ Full    | Implemented using `NSDate` on iOS.                                     |


## Contributing

Issues can be logged on: [https://gitworkshop.dev/repo/amethyst](https://gitworkshop.dev/repo/amethyst)

[GitHub issues](https://github.com/vitorpamplona/amethyst/issues) and [pull requests](https://github.com/vitorpamplona/amethyst/pulls) here are also welcome. Translations can be provided via [Crowdin](https://crowdin.com/project/amethyst-social)

You can also send patches through Nostr using [GitStr](https://github.com/fiatjaf/gitstr) to [this nostr address](https://patch34.pages.dev/naddr1qqyxzmt9w358jum5qyg8v6t5daezumn0wd68yvfwvdhk6qg7waehxw309ahx7um5wgkhqatz9emk2mrvdaexgetj9ehx2ap0qy2hwumn8ghj7un9d3shjtnwdaehgu3wvfnj7q3qgcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqxpqqqpmej720gac)

By contributing to this repository, you agree to license your work under the MIT license. Any work contributed where you are not the original author must contain its license header with the original author(s) and source.

# Screenshots

| FollowFeeds                              | ChatsGroup                              | LiveStreams                                    | Notifications                                          |
|-------------------------------------------|----------------------------------------------|-------------------------------------------------|--------------------------------------------------------|
| ![Home Feed](./docs/screenshots/home.png) | ![Messages](./docs/screenshots/messages.png) | ![Live Streams](./docs/screenshots/replies.png) | ![Notifications](./docs/screenshots/notifications.png) |

# Contributors

<a align="center" href="https://github.com/vitorpamplona/amethyst/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=vitorpamplona/amethyst" />
</a>

# MIT License

<pre>
Copyright (c) 2023 Vitor Pamplona

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
</pre>
