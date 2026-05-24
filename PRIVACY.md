# Amethyst Privacy Policy and Terms of Use

**App:** Amethyst (Android Nostr client)
**Publisher:** Vitor Pamplona
**Contact:** amethyst@vitorpamplona.com
**Last updated:** 2026-05-24

Amethyst is free, open-source software (MIT License — see `LICENSE`). It is not a service. There is no Amethyst server, no Amethyst account, and the developer has no access to data stored on your device.

Amethyst connects to third-party Nostr **relays** that you choose. Those relays host the content. They are independent of Amethyst, with their own operators and their own policies.

This document explains what data leaves your phone, who can see it, and the standards that apply to use of the app.

## Privacy

### Data sent off-device

Using the app causes the following data to leave your phone:

- **Nostr events** you publish, sent to the relays you have configured.
- **Subscriptions** (filters describing what you want to read), sent to those relays.
- **Media uploads** (images, audio, video), sent to the media server you select.
- *(Google Play build, push notifications enabled)* a per-device push token, your public key, and a preferred relay, registered with Google Firebase Cloud Messaging so a notification proxy can wake the app.
- *(F-Droid build, push notifications enabled)* a per-device token registered with whichever UnifiedPush distributor you install (e.g. ntfy).

The developer does not run any server that aggregates or stores this data.

### Data stored on your device

Configuration, cached events, keys, drafts, and other operational data live in the app's local storage. Other apps cannot read it on a standard, non-rooted Android device. You can wipe it by clearing the app's storage or uninstalling.

### What relays can see

A relay you connect to sees:

- Your IP address.
- Your public key.
- The events you publish (posts, reactions, reposts, reports, etc.).
- The filters you subscribe to.

A relay does **not** see the plaintext of:

- Private Direct Messages (encrypted to the recipient under NIP-17 / NIP-44).
- Private Zaps.

A relay can still see *that* you and another user are exchanging DMs even though it cannot read them. To reduce what a relay can correlate to you, route the app over a VPN or Tor.

### Media uploads

Uploads go to the media server you select. That server is independent of Amethyst and has its own policy. Anyone holding the resulting link — including media attached to a DM — can fetch the file.

### Public content is effectively permanent

Anything you publish to a relay can be copied to other relays or clients. Once published, you should assume it cannot be reliably deleted from the network.

## Child Safety Standards

These are the published Child Safety Standards for **Amethyst**, the Android Nostr client published on Google Play by **Vitor Pamplona**. They are published under Google Play's Child Safety Standards policy.

They are a community standard, not a license restriction. Amethyst's source code remains licensed under the MIT License in `LICENSE`.

### Prohibition

Using Amethyst to create, upload, share, solicit, or distribute child sexual abuse and exploitation (CSAE) material — including child sexual abuse material (CSAM) — or to groom, exploit, or harm a minor is prohibited and is illegal in essentially every jurisdiction.

### In-app tools

Amethyst provides:

- **Report Post** and **Report Account** — publish a signed Nostr report (including the "Illegal Content" reason) so relays and other clients can act on it.
- **Block Post** / **Block Account** — hide content locally on your device.
- **Block Relay** — add a relay to your NIP-51 Blocked Relay List so the app stops fetching from or publishing to it. This is the strongest tool the app offers against a relay that refuses to moderate.
- **Mute Words / Hashtags** — filter unwanted content from your feeds.

### Addressing CSAM

Amethyst does not host content, so the app cannot remove CSAM. Only the relay hosting the content can remove it. In the United States, 18 U.S.C. §2258A makes hosting providers — not viewer applications — the entities required to report to the National Center for Missing & Exploited Children (NCMEC).

If you encounter CSAM through Amethyst:

1. Report the content in-app and select "Illegal Content."
2. Add the hosting relay to your Blocked Relay List.
3. Report directly to **NCMEC** at https://report.cybertip.org/ (United States) or to an **INHOPE** hotline at https://www.inhope.org/ (other jurisdictions). These bodies can compel the hosting provider to act.
4. You may also email **amethyst@vitorpamplona.com** with the relay URL and event ID. The developer cannot remove content from third-party relays, but may forward the report to relay operators it is in contact with and may stop recommending the offending relay in any list shipped with the app.

### Compliance

Amethyst is distributed under Google Play's Child Safety Standards policy and applicable law. Obligations attached to the **hosting** of content rest with relay operators.

### Age rating

Amethyst's Google Play listing is rated 17+. The app does not request or store age information.

## Terms of Use

### Google Play build

You agree not to use the Google Play build of Amethyst to submit Objectionable Content to relays. Objectionable Content includes:

- Sexually explicit material.
- Obscene, defamatory, libelous, slanderous, violent, or unlawful content.
- Content that infringes third-party rights (copyright, trademark, privacy, publicity).
- Content that is deceptive or fraudulent.
- Content promoting illegal drugs, tobacco, firearms, ammunition, or illegal gambling.

These Terms apply only to the Google Play distribution of Amethyst.

### F-Droid and other source-built distributions

The MIT License in `LICENSE` is the only instrument governing your right to use, study, modify, and redistribute the software. No additional terms are imposed on these builds. Any dispute over distribution through F-Droid is between you and F-Droid.

## Updates

This document may change. The current version is published at https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md.
