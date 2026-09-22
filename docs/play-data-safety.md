# Play Console — Data safety form

Source text for the **Data safety** form (Play Console → *Monitor and improve → Policy and
programs → App content → Data safety*). Keep this file and the form in sync, the same way
`health-connect-play-declaration.md` tracks the Health apps declaration. Google cross-checks the
two against each other and against `PRIVACY.md`; a disagreement between them is what a reviewer
finds first.

## Why this file exists

The form previously said **"App doesn't collect or share data"** and **"Data isn't encrypted"**.
The first is wrong. The second is right, but only became defensible once the first was corrected:
with nothing collected there is no encryption answer to give, so the pair was self-contradictory.

The error came from reading "collect" as "the developer receives it". Google does not define it
that way:

> **"Collect" means transmitting data from your app off a user's device.** This includes data
> transmitted by libraries/SDKs and webviews controlled by your app.

Amethyst has no server, and that remains true and worth saying in the listing — but the test is
whether data *leaves the phone*, not who receives it. `PRIVACY.md` § "Data sent off-device"
already lists six categories that do.

**The trap:** Google's *user-initiated transfer* and *service provider* carve-outs are exceptions
to **sharing**, not to **collection**. A user tapping Post is a transfer they expect, so it need
not be declared as sharing — but it is still collection and must be declared.

## Two exemptions Amethyst genuinely earns

- **Private DMs — end-to-end encryption.** "User data that is sent off device, but that is
  unreadable by you or anyone other than the sender and recipient as a result of end-to-end
  encryption does not need to be disclosed." NIP-17/NIP-44 qualifies. Do not declare DM contents.
- **The My Fitness dashboard — on-device only.** "User data accessed by your app that is only
  processed locally on the user's device and not sent off device does not need to be disclosed."
  The dashboard reads Health Connect, computes on device, displays, and transmits nothing. Only a
  workout the user *publishes* is collected.

## 1. Overall questions

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **No** — see § 4 |
| Do you provide a way for users to request that their data is deleted? | **Yes** — see § 5 |

## 2. Data types — collected

Everything below is **optional** (the user chooses to post, to upload, to enable push, to connect
Health Connect) and its purpose is **App functionality** only. Amethyst ships no analytics, crash
reporting, advertising or attribution SDK — verified: no Crashlytics, Firebase Analytics, AppsFlyer,
Adjust, Sentry or Bugsnag anywhere in `libs.versions.toml` or any module's `build.gradle.kts`. So
never tick Analytics, Advertising or marketing, Fraud prevention, or Personalization.

| Category | Type | Collected | Shared | Why |
| --- | --- | --- | --- | --- |
| Location | Approximate location | Yes | No | A geohash the user attaches to a post or a location chat. `ACCESS_COARSE_LOCATION` only — never precise. |
| Personal info | Name | Yes | No | Display name in the user's published kind-0 profile, if they set one. |
| Personal info | User IDs | Yes | No | The Nostr public key accompanies every published event and the push registration. |
| Personal info | Other info | Yes | No | Profile bio, picture and website, if set. |
| Financial info | Other financial info | Yes | No | Zap amounts appear in published zap events; NWC relays payment instructions to the user's own wallet. |
| Health and fitness | Health info | Yes | No | Heart rate, **only** in a workout the user chooses to publish. |
| Health and fitness | Fitness info | Yes | No | Exercise, distance, steps, calories, elevation, **only** in a published workout. |
| Messages | Other in-app messages | Yes | No | Public posts, replies, articles. **Private DMs are excluded** — E2EE exemption. |
| Photos and videos | Photos / Videos | Yes | No | Uploads to the media server the user selects. |
| Audio | Voice or sound recordings | Yes | No | Voice notes, and speaking in a NIP-53 audio room. |
| Calendar | Calendar events | Yes | No | NIP-52 calendar events the user publishes. |
| Device or other IDs | Device or other IDs | Yes | **See § 3** | *(Play build, push enabled)* FCM registration token. |

## 3. Data types — not collected

**Contacts** (follow lists are Nostr public keys, not device contacts) · **Web browsing history** ·
**App info and performance** (no crash or analytics SDK) · **Files and docs** (covered by photos,
videos and audio) · **Personal info → Email address** · **Financial info → payment info or purchase
history** · **Precise location** · **App activity**.

### The one item needing your decision

The **push token** path is the only place a third party plausibly receives data outside a
user-initiated publish: `PRIVACY.md` says the token, public key and a preferred relay are
"registered with Google Firebase Cloud Messaging so a notification proxy can wake the app."

- If that proxy processes the data **on your behalf**, the *service provider* exception applies →
  **Shared: No**.
- If it is an independent operator, it is a third party → **Shared: Yes** for *Device or other IDs*.

Decide this from how the proxy is actually operated. Everything else in the table is No because
publishing to relays is a user-initiated transfer the user reasonably expects.

> **This is the judgment call in the whole form.** Answering "shared with third parties" for
> *Health and fitness* would make the public label read that Amethyst shares health data with third
> parties — which is precisely the prohibited use the Health Connect policy names, and would
> undercut the declaration. "Collected, not shared" is both accurate under Google's definition and
> the answer to defend. Be ready to defend it: the user takes a deliberate action, sees the post,
> and confirms it.

## 4. Encrypted in transit — answer **No**, deliberately

Amethyst's own traffic is encrypted: relays are `wss://`, media uploads are HTTPS, and private DMs
are NIP-44 on top of that. But `amethyst/src/main/res/xml/network_security_config.xml` sets
`cleartextTrafficPermitted="true"` on the global `base-config`, because **a user must be able to
connect to any relay they choose, including a `ws://` one.** That is a deliberate product
decision, not an oversight: narrowing it to loopback and `.onion` would let this answer be "Yes",
and it was considered and rejected — cutting off plain `ws://` relays is not an acceptable price
for a nicer label.

Google only permits "yes" when encryption covers *all* collected data, so the honest answer is
**No**.

**That answer is not the problem, and it never was.** What made the old form incoherent was
pairing it with "App doesn't collect or share data" — with nothing collected there is no
encryption question to answer at all. Once § 2 declares collection truthfully, a "No" here is
simply accurate, and it is defensible in one sentence if anyone asks:

> Amethyst connects over TLS by default. Cleartext is possible only for a relay address the user
> typed in themselves, because the user chooses their own relays and the app does not override
> that choice.

Say the same thing in `PRIVACY.md` if it is ever queried, so the two agree.

### Known consequence, for a health reviewer

A user who configures a `ws://` relay and then publishes a workout sends that kind 1301 in
cleartext. Nothing in § 2 hides this, and the "No" above is what discloses it. If this is ever
raised in review, the options that preserve `ws://` entirely are to warn at the point a cleartext
relay is added, or to warn before publishing a health-derived event to one. **Neither is
implemented today** — recorded here so the choice is a choice rather than an oversight.

## 5. Data deletion

The developer runs no server and holds nothing, so there is no developer-held copy to request
deletion of. Users can:

- wipe everything local by clearing app storage or uninstalling (`PRIVACY.md` § "Data stored on
  your device");
- delete an account's data in-app;
- request deletion of published events with NIP-09 — noting, as `PRIVACY.md` already says, that
  relays may not honour it and public content should be assumed permanent.

Say this plainly rather than claiming deletion guarantees the protocol cannot give.

## 6. Keep these three in sync

The Health apps declaration, this form, and `PRIVACY.md` are one statement split across three
places. A reviewer reads all three. Before any resubmission, check that each data type here also
appears in `PRIVACY.md`, and that nothing here contradicts
`health-connect-play-declaration.md` § 4.
