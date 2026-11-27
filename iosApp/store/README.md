# App Store submission

Everything App Store Connect asks for when Amethyst goes to App Review, and where the app meets
each review guideline that tends to reject a Nostr client. The build itself comes from
[`.github/workflows/ios-testflight.yml`](../../.github/workflows/ios-testflight.yml).

| Path | What it is |
|---|---|
| `metadata/` | The listing, in fastlane `deliver`'s layout (`fastlane deliver --metadata_path iosApp/store/metadata` uploads it; pasting each file into App Store Connect works as well) |
| `metadata/en-US/` | Name, subtitle, description, keywords, promotional text, what's new, URLs |
| `metadata/review_information/` | The contact and the notes App Review reads |
| `screenshots.sh` | Takes the iPhone and iPad screenshots on the simulators (macOS) |
| `../xcode/ExportOptions.plist` | How the workflow signs and uploads the archive |
| `../xcode/Amethyst/PrivacyInfo.xcprivacy` | The privacy manifest |

Limits App Store Connect enforces: name and subtitle 30 characters, keywords 100, promotional text
170, description 4,000. The listing must describe only what the iOS build does (guideline 2.3.1)
and name no other platform (2.3.10), so it is not a copy of the Play listing. My Fitness, calls,
audio rooms, git repositories and the in-app wallets are not in it.

## One-time setup

1. **Developer account.** The Apple Developer Program, as an individual or an organization. An
   individual account shows a person's name as the seller. The Cashu and on-chain wallets, which
   hold the user's funds, are allowed only from an organization (guideline 3.1.5(i)), so builds
   leave them out unless the workflow's `in_app_wallets` input is on
   (`-Pamethyst.ios.inAppWallets`, `IosBuildInfo.IN_APP_WALLETS`).
2. **App ID.** In Certificates, Identifiers & Profiles, register the explicit App ID
   `com.vitorpamplona.amethyst.ios`. It needs no capability: background audio is an Info.plist
   key, and the app has no push, iCloud, app groups or associated domains.
3. **App record.** In App Store Connect, add a new iOS app with that bundle ID, primary language
   English (U.S.) and an SKU such as `amethyst-ios`. Names are unique across the store: if
   `Amethyst: Nostr Client` (`metadata/en-US/name.txt`) is taken, edit the file to the name used.
4. **Agreements.** The Free Apps agreement is accepted with the program membership. Nothing is sold,
   so the Paid Apps agreement, tax and banking are not needed.
5. **EU trader status** (App Store Connect > Business). The Digital Services Act asks every account
   distributing in the EU whether it is a trader. A trader's address, phone and email are shown on
   the EU listing; a non-trader's are not. This is the account holder's decision.
6. **Signing and upload.** Create an App Store Connect API key with the Admin role (Users and
   Access > Integrations > Team Keys) and add the four secrets the workflow lists: `APPLE_TEAM_ID`,
   `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID` and `APP_STORE_CONNECT_KEY` (the
   `.p8` file's contents). Xcode's cloud-managed signing then creates the distribution
   certificate and the profile on the first run; none is kept in the repository.

## Each release

1. Push the release tag (`v*`), or run **iOS TestFlight** from the Actions tab. The workflow
   archives the release build (the version is `app` from `gradle/libs.versions.toml`, the build
   number `appCode.<run number>`) and uploads it. App Store Connect processes it for 10 to 30
   minutes, then emails.
2. Answer the export question on the build if App Store Connect asks (see Export compliance).
3. Try the build from TestFlight on an iPhone and an iPad before submitting: App Review uses both,
   on an IPv6-only network.
4. In the version page, set "What's New" (`metadata/en-US/release_notes.txt`), pick the build, and
   submit for review. The first submission also needs the screenshots and the answers below.

## Screenshots

Required: a 6.9-inch iPhone (1320 × 2868 or 1290 × 2796) and a 13-inch iPad (2064 × 2752 or
2048 × 2732), 1 to 10 each. They must show the app itself, signed in, without placeholder text.

```bash
iosApp/store/screenshots.sh ~/Library/Developer/Xcode/DerivedData/Amethyst-*/Build/Products/Debug-iphonesimulator/Amethyst.app
```

A good set: the home feed, a thread, a profile, direct messages, the Pictures or Shorts feed, the
around-me feed, and the Tor and Face ID settings.

## App Store Connect answers

### App Privacy: Data Not Collected

Apple counts data as collected when it leaves the device and the developer, or a partner of the
developer, can keep it beyond answering the request. The developer runs no server. The relays and
media servers Amethyst talks to are chosen by the user, are run by unrelated operators, and are
not the developer's partners, so what they receive is not collected data in Apple's sense. The
privacy manifest (`NSPrivacyCollectedDataTypes` empty, no tracking) says the same, and both must
agree.

Two cases to keep under review. A crash report goes to the developer only when the user chooses
to send it, each time. That fits Apple's exception for optional, infrequent data the user
provides. If a release adds a service the developer runs (a push proxy, a default relay or media
server the developer operates, analytics), this answer and the manifest change with it.

Privacy policy URL: `metadata/en-US/privacy_url.txt` (PRIVACY.md, which covers the App Store build).

### Age rating

Answer the questionnaire for what users can post, since the app shows content from anyone:

- User-generated content: **Yes**. Messaging and chat: **Yes**. Unrestricted web access: **Yes**
  (the in-app browser, nSites and links). Advertising: **No**. Contests, gambling and loot boxes: **No**.
- For the content categories (profanity, mature or suggestive themes, violence, alcohol, tobacco,
  drugs, medical, horror), answer **Infrequent/Mild**. Nudity and sexual content: **Infrequent**.
  They are user posts, hidden behind a content warning when marked sensitive.
- The result should be **18+**, the rating PRIVACY.md states. If the form gives less, raise it to
  18+ with the option to choose a higher rating.

### Content rights

"Does your app contain, show, or access third-party content?" **Yes**. "Do you have the necessary
rights?" **Yes**: the app shows what users publish to relays, under the terms of use, and hosts
none of it.

### Export compliance

`ITSAppUsesNonExemptEncryption` is `true` in the Info.plist, because the app implements standard
encryption (NIP-44 and NIP-04 for messages, MLS for groups) and includes a Tor client, beyond the
HTTPS iOS provides. App Store Connect then asks:

- Type of encryption: **standard encryption algorithms instead of, or in addition to, the
  encryption in Apple's operating system**.
- **Available in France?** App Store Connect asks for the French encryption declaration (ANSSI)
  for an app with non-exempt encryption sold in France. File the declaration and upload it, or
  leave France out of Availability until it is filed.
- US export law: the mass-market classification (EAR 5D992.c, License Exception ENC) needs the
  annual self-classification report to BIS. Because the whole source is published under MIT, the
  encryption may instead count as publicly available (EAR 742.15(b)), which needs a one-time email
  to BIS and the NSA with the repository URL. Which applies is a legal question for the account
  holder; this file is not legal advice.

Once the classification is settled, Apple can issue an export compliance code. Putting it in
`ITSEncryptionExportComplianceCode` in `xcode/project.yml` stops App Store Connect from asking on
every build.

### Sign-in information

Not required: the reviewer creates an account on the first screen (`Sign Up`), with no email or
phone number. The notes say so. Leave "Sign-in required" unchecked.

### App Review notes

`metadata/review_information/notes.txt` covers how to sign in, where the report, block, filter and
account deletion controls are, why zaps are not in-app purchases, and what the Nostr apps are.
Contact: `metadata/review_information/` (add a phone number in App Store Connect; it is not
committed).

## Guidelines the app is built to pass

| Guideline | Where |
|---|---|
| 1.2 User-generated content: terms, filter, report, block, contact | `StoreTermsGate` shows the terms before the first sign-in (commonsUI `account/ui/legal/TermsGate.kt`); report and block on every post and profile; Security Filters; sensitive posts behind a warning by default (`showSensitiveContent = null`); contact in PRIVACY.md and the notes |
| 2.1 Completeness | Screens iOS lacks are left out of menus (`canOpen`); a link to one opens the note it is about, or the system browser for a web page (`UnavailableScreen`) |
| 2.3.10 No other platforms | Neither the listing nor the iOS screens mention Android: the fallback notice and the camera-permission text have iOS wording |
| 3.1.1 In-app purchase | Nothing is sold and nothing is unlocked. Zaps are gifts between users, paid from the user's own wallet (3.2.1(vii)); the developer donation card is off on iOS (`releaseNotesId` is null) |
| 3.1.5(i) Wallets | The wallets that hold funds are off unless the build is for an organization (`offersInAppWallets`) |
| 3.2.2(i) No store of other apps | The NIP-82 software catalogue ("App Store" in the menu) is withdrawn on iOS (`IosAppRoot.registerDestinations`); a software app note opens as a note |
| 4.7 Mini apps and games | Napplets and nSites run as HTML5 in WKWebView, listed with names and descriptions, with every signing request confirmed by the user; carts run in a sandboxed interpreter |
| 5.1.1(i) Privacy policy | Linked in the listing and in Settings > Legal (`storeLegalSettingsCategory`) |
| 5.1.1(v) Account deletion | Settings > Danger Zone > Request to Vanish (NIP-62); a settings search for "delete account" finds it |
| 5.1.2 Data use | Data Not Collected; privacy manifest with the required-reason APIs |
| Permissions | Each usage string in `xcode/project.yml` says when the app asks; location is approximate and while in use |
| 2.5.4 Background modes | `audio` only, for playback on the lock screen and in picture in picture |

## What may still come back from review

- **Zaps (3.1.1).** Apple has asked Nostr clients before to remove zaps on posts, treating a tip
  attached to a post as payment for digital content. The notes argue they are person-to-person
  gifts paid by the user's own wallet. If a reviewer disagrees, the fallback is to keep zaps on
  profiles and hide them on posts in the iOS build, behind a platform flag like
  `offersInAppWallets`.
- **Moderation response (1.2).** Reviewers sometimes ask how the developer removes reported
  content within 24 hours. The app cannot delete from relays it does not run. The answer in the
  notes is that reports are read and forwarded within 24 hours, and that the reporting user stops
  seeing the content at once. A developer-maintained mute list applied by default would be the
  next step if that is not enough.
- **IPv6-only networks.** App Review tests on NAT64. Relays and media go through NSURLSession,
  which handles it. Tor, the relay connections it carries, and audio rooms (not in this build) are
  the paths to watch on a TestFlight build on such a network.
- **iPad.** Review runs the iPhone layout and the wide layout. Check both on TestFlight.
