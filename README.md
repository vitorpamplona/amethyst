# Amethyst: Nostr client for Android

<img align="right" src="./docs/screenshots/home.png" data-canonical-src="./docs/screenshots/home.png" width="350px"/>

Amethyst brings the best social network to your Android phone. Just insert your Nostr private key and start posting. 

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.vitorpamplona.amethyst/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.vitorpamplona.amethyst)

Or get the latest APK from the [Releases Section](https://github.com/vitorpamplona/amethyst/releases/latest).

# Current Features

- [x] Event Builders / WebSocket Subscriptions (NIP-01, NIP-15)
- [x] Home Feed
- [x] Notifications Feed
- [x] Global Feed
- [x] Reactions (likes NIP-25, boost, reply)
- [x] Image/Video/Url/LnInvoice Previews
- [x] View Threads
- [x] Private Messages (NIP-04)
- [x] User Profiles (edit/follow/unfollow - NIP-02)
- [x] Public Chats (NIP-28)
- [x] Bech Encoding support (NIP-19)
- [x] Notification Dots
- [x] Reporting and Hide User capability (NIP-56)
- [x] Automatic Translations
- [x] Relay Sets (home, dms, public chats, global)
- [x] User/Note Tagging (NIP-08, NIP-10)
- [x] Lightning Tips
- [x] Zaps (private, public, anon, non-zap) (NIP-57)
- [x] URI Support (NIP-21)
- [x] Event Deletion (NIP-09: like, boost, text notes and reports)
- [x] Identity Verification (NIP-05)
- [x] Long-form Content (NIP-23)
- [x] Parameterized Replaceable Events (NIP-33)
- [x] Online Relay Search (NIP-50)
- [x] Internationalization
- [x] Badges (NIP-58)
- [x] Hashtag Following and Custom Hashtags
- [x] Polls (NIP-69)
- [x] Verifiable static content in URLs (NIP-94)
- [x] Login with QR
- [x] Wallet Connect API (NIP-47)
- [x] Accessible uploads
- [x] Bounty support (nostrbounties.com)
- [x] De-googled F-Droid flavor
- [x] External Identity Support (NIP-39)
- [x] Multiple Accounts
- [x] Markdown Support
- [x] Relay Authentication (NIP-42)
- [x] Content stored in relays themselves (NIP-95)
- [ ] Image/Video Capture in the app
- [ ] Local Database 
- [ ] View Individual Reactions (Like, Boost, Zaps, Reports) per Post
- [ ] Bookmarks, Pinned Posts, Muted Events (NIP-51)
- [ ] Sensitive Content (NIP-36) 
- [ ] Relay Pages (NIP-11)
- [ ] Generic Tags (NIP-12)
- [ ] Proof of Work in the Phone (NIP-13, NIP-20)
- [ ] Events with a Subject (NIP-14)
- [ ] Workspaces
- [ ] Expiration Support (NIP-40)
- [ ] Infinity Scroll
- [ ] Relay List Metadata (NIP-65)
- [ ] Signing Requests (NIP-46)
- [ ] Delegated Event Signing (NIP-26)
- [ ] Account Creation / Backup Guidance (NIP-06)
- [ ] Message Sent feedback (NIP-20)


# Development Overview

## Overall Architecture 

This is a native Android app made with Kotlin and Jetpack Compose.
The app uses a modified version of the [nostrpostrlib](https://github.com/Giszmo/NostrPostr/tree/master/nostrpostrlib) to talk to Nostr relays.
The overall architecture consists in the UI, which uses the usual State/ViewModel/Composition, the service layer that connects with Nostr relays,
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
./gradlew ktlintCheck
./gradlew ktlintFormat
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
2. Create 4 Secret Key variables on your GitHub repository and fill in with the signing key information
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
While the content of direct messages (DMs) is only visible to you, and your DM nostr counterparty, everyone can see that and when you and your counterparty are DM-ing each other.

# Visibility & Permanence of Your Content on nostr
## Information Visibility ##
Content that you share can be shared to other relays. 
Information that you share is publicly visible to anyone reading from relays that have your information. Your information may also be visible to nostr users who do not share relays with you.

## Information Permanence ##
Information shared on nostr should be assumed permanent for privacy purposes. There is no way to guarantee deleting or editing any content once posted. 

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
