# Amethyst Privacy Policy and Terms of Use

## Privacy Policy

Effective as of Jun 12, 2023

The Amethyst app for Android does not collect or process any personal information from its users. 

The app is used to browse third-party Nostr servers (called Relays) that may or may not collect personal information and are not covered by this privacy policy. Each third-party relay server comes equipped with its own privacy policy and terms of use that can be viewed through the app or through that server's website. The developers of this open-source project or maintainers of the distribution channels (app stores) do not have access to the data located in the user's phone. Accounts are fully maintained by the user. We do not have control over them. 

The app may collect a per-device token, your public key, and a preferred Relay to connect to and provide push notification services through Google's Firebase Cloud Messaging. Other than that, the data from connected accounts is only stored locally on the device when it's required for the functionality and performance of Amethyst. This data is strictly confidential and cannot be accessed by other apps (on non-rooted devices). Phone data can be deleted by clearing Amethyst's local storage or uninstalling the app.

Amethyst offers several options for uploading pictures and videos to post online. You can choose the server at your discretion. Similar to relays, such services are independent of the app and have their own privacy policy and terms of use. 

### Privacy with Relay services

Your Internet Protocol (IP) address is exposed to the relays you connect to. If you want to improve your privacy, consider utilizing a service that masks your IP address (e.g., a VPN) from trackers online.

The relay can also see which public keys you are using and what information you are requesting from the network. Your public key is tied to your IP address and your relay filters.

Relays have all your data in raw text. They know your IP, your name, your location (guessed from IP), your pub key, all your contacts, and other relays, and can read every action you do (post, like, boost, quote, report, etc) with the exception of the content inside Private Zaps and Private DMs.

While the content of direct messages (DMs) is only visible to you and your DM Nostr counterparty, everyone can see when you and your counterparty are DM-ing each other. Image uploads in the DM screen use one of the chosen image servers and simply paste the image link into the DM text. Your uploaded pictures are available to anyone with that direct link. 

### Visibility & Permanence of Your Content on Nostr Relays

#### Information Visibility

Content that you share can be shared with other relays by any user of the network. 
The information you share is publicly visible to anyone reading from relays that have access to your information. Your information may also be visible to Nostr users who do not share relays with you.

#### Information Permanence

Information shared on Nostr should be assumed permanent for privacy purposes. There is no way to guarantee deleting or editing any content once posted.

## Child Safety Standards

These are the published Child Safety Standards for Amethyst, an Android Nostr client developed by Vitor Pamplona and distributed on Google Play. They are published to satisfy Google Play's Child Safety Standards policy and to set out the developer's public position on child safety.

These Standards are a **community standard and published policy**. They do **not** modify, supersede, or add restrictions to the software license that governs the Amethyst source code; see the **Free Software License** note at the end of this section.

### How Amethyst Works (and Why That Matters Here)

Amethyst is a decentralized Nostr client. **The app itself does not host, store, or moderate any user-generated content.** All content is hosted by independent third-party servers called **relays** that the user freely chooses to connect to. Amethyst is a viewer and a publisher; it has no central database, no upload servers, and no ability to delete content from the network. Content moderation, takedowns, and legal reporting are the responsibility of the **relay operators** who actually host the content.

What these Standards cover is (1) a clear prohibition of CSAE as a community standard, (2) the in-app tools available to users to report content, hide content, and disconnect from abusive relays, and (3) a contact point for escalation.

### Prohibition of Child Sexual Abuse and Exploitation (CSAE)

These Standards prohibit using Amethyst to create, upload, share, solicit, or distribute child sexual abuse and exploitation (CSAE) material, including child sexual abuse material (CSAM), in any form, or to groom, exploit, endanger, or otherwise harm minors. Users who use Amethyst for these purposes are in violation of these Standards and of the laws of essentially every jurisdiction.

### In-App User Feedback and Reporting Mechanism

Amethyst provides in-app mechanisms for users to flag, hide, and disconnect from harmful content:

- **Report Post** — use the dropdown menu on any note to report it as illegal content, nudity, impersonation, spam, profanity, or other violations. The report is published as a signed Nostr report event so that relay operators and other clients can act on it.
- **Report Account** — open a user's profile and use the report action to flag the account, with the same publication behavior.
- **Block Post / Block Account** — hide a note or a user locally on your device.
- **Block Relay (NIP-51 Blocked Relay List)** — if a particular relay is hosting CSAE/CSAM or refuses to act on reports, add it to your Blocked Relay List so Amethyst will no longer fetch from or publish to it. This is the strongest tool the app provides: it cuts your client off from servers that won't moderate.
- **Mute Words and Hashtags** — filter out unwanted content from your feeds.

### Addressing CSAM

Because Amethyst does not host content, CSAM cannot be removed by Amethyst — it can only be removed by the relay operator who is actually hosting it, and reported to authorities by that operator under the laws that apply to them (in the United States, 18 U.S.C. §2258A makes hosting providers — not viewer applications — the entities required to report to the National Center for Missing & Exploited Children).

If you become aware of CSAM accessible via Amethyst, please:

1. **Report the content in-app** (select "Illegal Content") so the report propagates to relays and other clients, and
2. **Block the relay** that is hosting the content using the in-app Blocked Relay List, so your client disconnects from it, and
3. **Report the relay and the material directly to the authorities** who have jurisdiction over the hosting provider — for content reachable from the United States that is the **National Center for Missing & Exploited Children (NCMEC) CyberTipline** at https://report.cybertip.org/. For other jurisdictions see INHOPE members at https://www.inhope.org/.
4. **Optionally email the contact below** with the relay URL and event ID. We cannot remove the content from the relay, but we can amplify the report to other relay operators we know, and where appropriate we will recommend that the offending relay be removed from any default relay list shipped with Amethyst.

### Compliance with Child Safety Laws

Amethyst is built and distributed to comply with applicable child safety laws and regulations, including Google Play's Child Safety Standards policy. Where Amethyst itself is subject to a legal obligation (for example, as a distributor on Google Play), we will cooperate with lawful requests from child-safety authorities. Obligations that attach to the **hosting** of content (such as 18 U.S.C. §2258A NCMEC reporting in the U.S.) apply to the relay operators, not to the viewer application.

### Child Safety Point of Contact

Questions, reports of relays hosting CSAE/CSAM, or requests related to child safety on Amethyst should be sent to:

- **Name:** Vitor Pamplona (developer, Amethyst for Android)
- **Email:** amethyst@vitorpamplona.com
- **What we can do:** acknowledge the report, forward it to relay operators we are in contact with, and consider removing the offending relay from any default/suggested relay list shipped with Amethyst. We cannot delete content from third-party relays — that is the relay operator's responsibility.
- **What you should also do:** report directly to NCMEC (https://report.cybertip.org/) or your local INHOPE hotline, who can compel the actual hosting provider to act.

### Age Rating

Amethyst is rated 17+. The app does not knowingly collect information from children and has no account-creation flow that targets minors. We rely on Google Play's age-gating to restrict downloads to users 17+.

### Free Software License

These Child Safety Standards are a public statement of the developer's commitments and the published policy that users of Amethyst on Google Play are expected to follow. They are **not** a restriction added to the source code license. Amethyst's source code is licensed under the MIT License (see the `LICENSE` file in the source repository); these Standards do not modify, supersede, or add conditions to that license. All users — including users of builds distributed by F-Droid, by other repositories, or built from source — retain every right granted by the MIT License, including the freedom to use, study, modify, and redistribute the software.

## Terms of Use

### For versions downloaded from Google's Play Store

You cannot use the Amethyst app for Android to submit Objectionable Content to relays. Objectionable Content includes but is not limited to: (i) sexually explicit materials; (ii) obscene, defamatory, libelous, slanderous, violent and/or unlawful content or profanity; (iii) content that infringes upon the rights of any third party, including copyright, trademark, privacy, publicity or other personal or proprietary rights, or that is deceptive or fraudulent; (iv) content that promotes the use or sale of illegal or regulated substances, tobacco products, ammunition and/or firearms; and (v) illegal content related to gambling.

### For versions downloaded from F-Droid

We do not control the distribution of the application in F-Droid. Legal matters should be resolved between the user and F-Droid. 

## Other Notes

We reserve the right to modify this Privacy Policy and Terms of Use at any time. Any modifications to this document will be effective upon our posting of the new terms and/or upon implementation of the new changes on the Service (or as otherwise indicated at the time of posting). In all cases, your continued use of the app after the posting of any modified Privacy Policy and Terms of Use indicates your acceptance of the terms of the modified Privacy Policy and/or Terms of Use.

If you have any questions about Amethyst or this privacy policy, you can send a message to amethyst@vitorpamplona.com
