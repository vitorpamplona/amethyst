# The Observer: one account's settings and papers on every device

**Date:** 2026-11-20
**Status:** built and unit-tested, the weather included; not yet run between two real devices.
The worker understands both params; Quartz's `KNOWN` still to do.
**Owning modules:** `commons` (`frontpage/`), `commonsUI` (the Observer screen), the two app
shims (wiring only)
**Builds on:** Quartz `quartz/plans/2026-11-11-observer-site.md` ("The morning schedule, synced"
and "The Observer's settings and the Assistant"), NIP-FB.

## Problem

The schedule, the model, the key and the writing effort already live in the account's NIP-FB
task (kind 34817, `d` = `observer`), so every device prints with the same choices and only the
device whose claim wins prints the morning. Two things were still per install:

1. **Publish to my Observer** and the one-time **"your site is public"** answer
   (`ObserverPrefs`, kept in the device's DataStore as `frontpage.observer.<pubkey>`). Turning
   publishing off on the phone left the desktop publishing; saying yes on one device was
   asked again on every other one.
2. **The paper itself.** A device learned of another device's print only from the reader's
   public Observer site. With publishing off (or before the reader said yes, or when the upload
   failed), the phone never saw the desktop's paper: it showed nothing, offered to print the day
   again (and pay again), and its "already printed?" check could not see it, so a manual print
   on the desktop did not stop the phone's scheduled one.

The feature is unreleased, so nothing is migrated: the per-device store is removed.

## Decisions

### `publish`: a param on the schedule

`["param", "publish", <value>]`, read and written like `effort` (`FrontPageParams`):

| value | meaning |
|---|---|
| absent | on (the default), and the reader hasn't said yes to a public site yet: a manual print asks first, a scheduled print never publishes |
| `yes` | on, and the reader said yes |
| `no` | off: nothing is published from any device |
| anything else | read as `no` (fails closed) |

- Turning it on writes nothing new when it is already on; from `no` it removes the param (the
  notice is asked again before the next publish). Saying yes writes `yes`. Turning it off writes
  `no`.
- With no schedule yet, the param goes into a settings-only event (no `cron`), which never runs.
- It is a param, not a private tag of its own, on purpose: NIP-FB's rule that a printer must not
  run a task with a param it doesn't understand is exactly right here. A printer that can't honour
  `no` must not print and publish.

### Every clean paper is shared with the reader's other devices, privately

After a page passes the checks and is kept, the printing device (manual or scheduled):

1. encrypts the page (the bytes kept, with the paper's CSP) with a fresh AES-256-GCM key
   (`AESGCM()`, as NIP-17 file messages do);
2. uploads the ciphertext to the reader's own Blossom servers (kind 10063, never a default), one
   upload auth naming its hash; at least one server must take it;
3. adds a record of it to the schedule's **private** tags in one read-modify-write (NIP-FB
   editing rules: newest read to EOSE on the write relays, more than half answer and accept):

```json
["edition", "<day>", "<printedAt>", "<page sha256>", "<code>", "<by>", "<blob sha256>", "<key hex>", "<nonce hex>", "<headline>"]
```

- `day` is the printing device's local day, `printedAt` unix seconds, `page sha256` the hash of
  the page as kept (the same hash the public site would name), `by` the install's name ("Pixel 8",
  "Desktop (Linux)"), the blob fields empty when the upload failed or there is no server list
  (the record still says the day is printed), `headline` at most 200 characters.
- One record per day, the newest wins; the newest 7 are kept (as many as the device keeps).
- It is a new tag, not a param: it says nothing about how the task runs, so a printer that
  doesn't know it keeps it and still runs. NIP-FB requires every writer to keep tags it
  doesn't know. Only the scheduled `ran` stays a write that changes `ran` alone.
- When the schedule names a runner, the private tags are encrypted to it as well, so the
  Assistant can read these records. It is the reader's own agent; nothing is published.

Every other device of the account, as the schedule arrives:

- lists the records it doesn't keep in its history ("Printed on Desktop (Linux)"), as it lists
  the public site's editions;
- opens one by downloading the blob (by hash, from the reader's servers), decrypting it and
  checking the page's hash before keeping it, the same way it opens a site edition;
- counts the records in the "already printed?" check and in "Last printed by …": a manual print
  on the desktop now stops the phone's scheduled print of that morning.

The paper never leaves the reader's servers unencrypted unless publishing is on.

### Weather: a `place` param, a forecast kept beside the paper

`["param", "place", <name>, <latitude>, <longitude>, "us" | "metric"]`, coordinates to 0.01°
(about a kilometre), private like every tag Amethyst writes. Set once per account from the
Observer screen (Open-Meteo's place search), so every device prints the same city.

- When a paper is kept, the printing device fetches Open-Meteo's forecast for the place (now,
  today's high, low, rain and sun, three more days; 10 s at most, through the client the reader's
  network settings pick for public pages) and keeps it **beside** the paper
  (`SavedEdition.weather`), never in its HTML. The edition record carries it too, so the other
  devices show the same forecast.
- The app draws it as a strip under the dateline (`ObserverWeather.withStrip`) only on the copy
  it shows. What is kept, shared, downloaded and published is the page without it, so the public
  site never names the reader's city. The strip is code: every value escaped, nothing that
  fetches, Open-Meteo credited (CC BY 4.0).
- No place, or no answer in time: the paper is kept without weather. A printer that shows no
  weather (the Assistant) understands the param and prints as before.
- The writer's brief is pinned to Brainstorm's copy, so the model is never asked to place a
  weather box; the app places it.

### Many accounts

Nothing here is per device and account any more except what has to be: this install's id for
claims, its own run record, its "Print from this device" switch, its kept papers and its
platform job. Each account's settings and records are in that account's own schedule, edited
through that account's own signer and relays, so two accounts on one device never share them.

## Interop

- **Quartz** `ObserverParams.KNOWN` must add `publish` and `place`, as `effort` was. Until then,
  Amethyst understands them in `FrontPageParams` (as it did `effort`).
- **The Assistant's worker** (`the-nostr-observer` `service/`) now checks the engine's
  `FrontPageParams.understands`: with `publish` = `no` it doesn't print (`PUBLISH_OFF`), since its
  whole output is the Assistant's public site; `place` it understands and prints without.
- **Brainstorm** keeps the param and the `edition` tags as it keeps every tag it doesn't know.

## Steps

1. `publish` param: read (`FrontPageParams.publishing`), write (`FrontPageMorning.setPublishing`),
   the printer and the screen read it from the synced schedule; `ObserverPrefsStore` and its
   DataStore go.
2. Edition records: the codec and the task transform (`EditionRecord`), sealing and opening
   (`SharedEditions`), the printer shares after keeping, merges records into the history, opens
   them, and counts them in "already printed?" and the status line.
3. Tests for the codec, the transforms, the merge and the already-printed rule.
