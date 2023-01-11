# Amethyst: Nostr client for Android

<img align="right" src="./docs/screenshots/home.png" data-canonical-src="./docs/screenshots/home.png" width="350px"/>

Amethyst brings the best social network to your Android phone. Just insert your Nostr private key and start posting. 

# Current Features

[x] Account Management
[x] Home Feed
[x] Notifications Feed
[x] Global Feed
[x] Reactions (like, boost, reply)
[x] Image Preview
[x] Url Preview
[ ] View Threads
[ ] Private Messages
[ ] Communities
[ ] Profile Edit
[ ] Relay Edit

# Development Overview

## Setup

Make sure to have the following pre-requisites installed:
1. Java 11
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

## Installing on device
```bash
./gradlew installDebug
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

# Contributing

[Issues](https://github.com/vitorpamplona/amethyst/issues) and [pull requests](https://github.com/vitorpamplona/amethyst/pulls) are very welcome.

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