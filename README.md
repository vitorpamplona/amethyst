# Amethyst: Nostr client for Android

<img align="right" src="./docs/screenshots/home.png" data-canonical-src="./docs/screenshots/home.png" width="350px"/>

Amethyst brings the best social network to your Android phone. Just insert your Nostr private key and start posting. 

[<img src="./docs/design/obtainium.png"
alt="Get it on Obtaininum"
height="80">](https://github.com/ImranR98/Obtainium)
[<img src="https://github.com/machiav3lli/oandbackupx/raw/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub"
height="80">](https://github.com/vitorpamplona/amethyst/releases)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.vitorpamplona.amethyst/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.vitorpamplona.amethyst)

# Current Features

- [x] Events / Relay Subscriptions (NIP-01)
- [x] Follow List (NIP-02)
- [ ] OpenTimestamps Attestations (NIP-03)
- [x] Private Messages (NIP-04)
- [x] DNS Address (NIP-05)
- [ ] Mnemonic seed phrase (NIP-06)
- [ ] WebBrowser Signer (NIP-07, Not applicable)
- [x] Old-style mentions (NIP-08)
- [x] Event Deletion (NIP-09)
- [x] Replies, mentions, Threads and Notifications (NIP-10)
- [x] Relay Information Document (NIP-11)
- [x] Generic Tag Queries (NIP-12)
- [x] Proof of Work Display (NIP-13)
- [ ] Proof of Work Calculations (NIP-13)
- [x] Events with a Subject (NIP-14)
- [ ] Marketplace (NIP-15)
- [x] Event Treatment (NIP-16)
- [x] Image/Video/Url/LnInvoice Previews
- [x] Reposts, Quotes, Generic Reposts (NIP-18)
- [x] Bech Encoding support (NIP-19)
- [x] Command Results (NIP-20)
- [x] URI Support (NIP-21)
- [x] Long-form Content (NIP-23)
- [x] User Profile Fields / Relay list (NIP-24)
- [x] Reactions (NIP-25)
- [ ] Delegated Event Signing (NIP-26, Will not implement)
- [x] Text Note References (NIP-27)
- [x] Public Chats (NIP-28)
- [x] Custom Emoji (NIP-30)
- [x] Event kind summaries (NIP-31)
- [ ] Labeling (NIP-32)
- [x] Parameterized Replaceable Events (NIP-33)
- [x] Sensitive Content (NIP-36)
- [x] User Status Event (NIP-38)
- [x] External Identities (NIP-39)
- [x] Expiration Support (NIP-40)
- [x] Relay Authentication (NIP-42)
- [ ] Event Counts (NIP-45, Will not implement)
- [ ] Nostr Connect (NIP-46)
- [x] Wallet Connect API (NIP-47)
- [ ] Proxy Tags (NIP-48, Not applicable)
- [x] Online Relay Search (NIP-50)
- [x] Lists (NIP-51)
- [ ] Calendar Events (NIP-52)
- [x] Live Activities & Live Chats (NIP-53)
- [x] Inline Metadata (NIP-55 - Draft)
- [x] Reporting (NIP-56)
- [x] Lightning Tips
- [x] Zaps (NIP-57)
- [x] Private Zaps
- [x] Zap Splits (NIP-57)
- [x] Zapraiser (NIP-TBD)
- [x] Badges (NIP-58)
- [ ] Relay List Metadata (NIP-65)
- [x] Polls (NIP-69)
- [x] Moderated Communities (NIP-72)
- [ ] Zap Goals (NIP-75)
- [ ] Arbitrary Custom App Data (NIP-78)
- [x] Highlights (NIP-84)
- [x] Recommended Application Handlers (NIP-89)
- [ ] Data Vending Machine (NIP-90)
- [x] Verifiable file URLs (NIP-94)
- [x] Binary Blobs (NIP-95)
- [x] HTTP File Storage Integration (NIP-96 Draft)
- [x] HTTP Auth (NIP-98)
- [x] Classifieds (NIP-99)
- [x] Private Messages and Small Groups (NIP-24/Draft)
- [x] Gift Wraps & Seals (NIP-59/Draft)
- [x] Versioned Encrypted Payloads (NIP-44/Draft)
- [x] Audio Tracks (zapstr.live) (Kind:31337)
- [x] Push Notifications (Google and Unified Push)
- [x] In-Device Automatic Translations
- [x] Hashtag Following and Custom Hashtags
- [x] Login with QR
- [x] Bounty support (nostrbounties.com)
- [x] De-googled F-Droid flavor
- [x] Multiple Accounts
- [x] Markdown Support
- [ ] Image/Video Capture in the app
- [ ] Local Database
- [ ] Workspaces
- [ ] Infinity Scroll

# Development Overview

## Overall Architecture 

This is a native Android app made with Kotlin and Jetpack Compose.
The app uses a modified version of the [nostrpostrlib](https://github.com/Giszmo/NostrPostr/tree/master/nostrpostrlib) to talk to Nostr relays.
The overall architecture consists of the UI, which uses the usual State/ViewModel/Composition, the service layer that connects with Nostr relays,
and the model/repository layer, which keeps all Nostr objects in memory, in a full OO graph.

The repository layer stores Nostr Events as Notes and Users separately. Those classes use LiveData objects to
allow the UI and other parts of the app to subscribe to each individual Note/User and receive updates when they happen.
They are also responsible for updating viewModels when needed. Filters react to changes in the screen. As the user
sees different Events, the Datasource classes are used to receive more information about those particular Events.

Most of the UI is reactive to changes in the repository classes. The service layer assembles Nostr filters for each need of the app,
receives the data from the Relay, and sends it to the repository. Connection with relays is never closed during the use of the app.
The UI receives a notification that objects were updated. Instances of User and Notes are mutable directly.
There will never be two Notes with the same ID or two User instances with the same pubkey.

Lastly, the user's account information (priv key/pub key) is stored in the Android KeyStore for security.

## Setup

Make sure to have the following pre-requisites installed:
1. Java 17
2. Android Studio
3. Android 8.0+ Phone or Emulation setup

Fork and clone this repository and import into Android Studio
```bash
git clone https://github.com/vitorpamplona/amethyst.git
```

Use one of the Android Studio builds to install and run the app in your device or a simulator.

## Building
Build the app:
```bash
./gradlew assembleDebug
```

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

## How to Deploy

1. Generate a new signing key
```
keytool -genkey -v -keystore <my-release-key.keystore> -alias <alias_name> -keyalg RSA -keysize 2048 -validity 10000
openssl base64 < <my-release-key.keystore> | tr -d '\n' | tee some_signing_key.jks.base64.txt
```
2. Create four Secret Key variables on your GitHub repository and fill in the signing key information
    - `KEY_ALIAS` <- `<alias_name>`
    - `KEY_PASSWORD` <- `<your password>`
    - `KEY_STORE_PASSWORD` <- `<your key store password>`
    - `SIGNING_KEY` <- the data from `<my-release-key.keystore>`
3. Change the `versionCode` and `versionName` on `app/build.gradle`
4. Commit and push.
5. Tag the commit with `v{x.x.x}`
6. Let the [Create Release GitHub Action](https://github.com/vitorpamplona/amethyst/actions/workflows/create-release.yml) build a new `aab` file.
7. Add your CHANGE LOG to the description of the new release
8. Download the `aab` file and upload it to the` PlayStore.

# Privacy on Relays & nostr
Your internet protocol (IP) address is exposed to the relays you connect to. If you want to improve your privacy, consider utilizing a service that masks your IP address (e.g. a VPN) from trackers online. 

The relay also learns which public keys you are requesting, meaning your public key will be tied to your IP address.

Relays have all your data in raw text. They know your IP, your name, your location (guessed from IP), your pub key, all your contacts, and other relays, and can read every action you do (post, like, boost, quote, report, etc) with the exception of Private Zaps and Private DMs.

# DM Privacy #
While the content of direct messages (DMs) is only visible to you and your DM counterparty, everyone can see when you and your counterparty DM each other.

# Visibility & Permanence of Your Content on nostr
## Information Visibility ##
Content that you share can be shared to other relays. 
Information that you share publicly is visible to anyone reading from relays that have your information. Your information may also be visible to nostr users who do not share relays with you.

## Information Permanence ##
Information shared on nostr should be assumed permanent for privacy purposes. There is no way to guarantee edit or deletion of any content once posted. 

# Screenshots

| FollowFeeds                              | ChatsGroup                              | LiveStreams                                    | Notifications                                          |
|-------------------------------------------|----------------------------------------------|-------------------------------------------------|--------------------------------------------------------|
| ![Home Feed](./docs/screenshots/home.png) | ![Messages](./docs/screenshots/messages.png) | ![Live Streams](./docs/screenshots/replies.png) | ![Notifications](./docs/screenshots/notifications.png) |

# Contributing

[Issues](https://github.com/vitorpamplona/amethyst/issues) and [pull requests](https://github.com/vitorpamplona/amethyst/pulls) are very welcome.

## Contributors

<a align="center" href="https://github.com/vitorpamplona/amethyst/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=vitorpamplona/amethyst" />
</a>

# MIT License

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
