# Desktop platform capabilities: what exists, what is missing, what never will

**Date:** 2026-08-29 · **Owning module:** `desktopApp` · **Status:** living document

The desktop port aims at a real implementation of nearly everything Amethyst
does on Android. A handful of features have no desktop counterpart at all, and
that is a fine outcome — but only if it is *written down*, because the failure
mode otherwise is a stub that quietly does nothing and a button that quietly
does nothing with it.

So there are three states, and the code can tell them apart.

## The three states

| state | how the code says it | what a reviewer should conclude |
|---|---|---|
| **Implemented** | it just works | nothing to do |
| **Not built yet** | `PlatformGaps.report(feature, detail)` on first use | backlog item; a desktop equivalent exists |
| **No equivalent** | `PlatformGaps.declareUnavailable(feature, reason)` at startup | not a defect; hide the control, keep the note |

`PlatformGaps.seen()` is the running list of everything hit at runtime, tagged
with which kind it was. `PlatformGaps.unavailableFeatures()` is the table below,
as data. `PlatformGaps.isUnavailable(feature)` is what a screen should ask
*before* it draws a control, rather than finding out after the user presses it.

The distinction matters because the two ask different things of a reader. "Not
built yet" is a promise. "No equivalent" is a decision, and it needs a reason
good enough that the next person does not quietly re-open it.

## No desktop equivalent

Declared in `DesktopCapabilities.declare()`. A test asserts every entry carries
a real explanation, because a declaration without a reason is a TODO wearing a
hat.

| feature | why there is no counterpart |
|---|---|
| **Health Connect** | An Android system service holding on-device health records. Desktop has no equivalent store, so workout import has nothing to read from. |
| **Picture-in-picture** | Android PiP docks an Activity into a system overlay. The desktop analogue would be an always-on-top window — a different feature with different UX, not a port of this one. |
| **Foreground services** | Desktop processes are not killed for being backgrounded, so there is nothing to keep alive and no notification to justify it. This work belongs to ordinary long-lived objects the app owns. |
| **NIP-55 external signer** | Signing by handing an Intent to a separate app. Desktop has no app-to-app intent bus; remote signing goes over NIP-46 there, which already works. |
| **Google Cast** | Discovery and session management ship inside Google Play services. |
| **UnifiedPush** | Distributors are Android apps. A desktop build holds its own relay connections while running, so it needs no push distributor at all. |
| **Per-app locale settings** | Android exposes a per-app language screen in system settings. Desktop has none; the in-app picker is the whole story. |
| **System share sheet** | No desktop OS has one. Sharing falls back to the clipboard, which is the closest honest equivalent — and says so. |
| **Battery optimization exemptions** | Doze and app standby are Android power-management policies with no desktop analogue, so there is no exemption to request. |

Several of these are less lossy than they look. Foreground services and
UnifiedPush exist on Android to survive a hostile process lifecycle that desktop
does not have — the *feature* they protect (staying connected, getting
notified) works on desktop without them.

## Has a counterpart; already using it

Worth recording so nobody re-litigates them as gaps:

| Android | desktop |
|---|---|
| ExoPlayer | ComposeMediaPlayer — Media Foundation / AVFoundation / GStreamer |
| `Bitmap`, `BitmapFactory` | `BufferedImage` + ImageIO |
| `Handler` | the AWT event queue, with real cancellation |
| `ACTION_VIEW` | `java.awt.Desktop.browse` |
| `ConnectivityManager` | `NetworkInterface`, polled for transitions |
| `TextToSpeech` | `say` / SAPI / `spd-say` |
| `SharedPreferences` | `java.util.prefs` |
| Android Keystore | OS keychain via jkeychain |
| `DateFormat`, `DateUtils` | `java.time` / `java.text`, same CLDR data |
| aapt2 resources | a generated `R` plus locale tables, built from the same `res/` tree |
| WorkManager | a daemon timer with the same retry backoff and constraint waiting |
| `NotificationCompat` + `NotificationManager` | the Nucleus native stack already in `:commons`, behind a swappable presenter |
| `FileProvider` | the file's own `file://` URI — desktop has no inter-app sandbox to hand a grant through |
| `MediaStore` | the XDG user directories, with a ContentResolver insert that creates the real file |
| `ImageDecoder` | ImageIO (AVIF/HEIF excepted — they need a plugin, and decode to null, which callers already handle) |
| `ACTION_SEND` of a file | the clipboard, as a file — every file manager and mail client takes it as a paste |
| `MediaScannerConnection` | nothing to do: desktop file managers read directories, and the indexers that exist watch the filesystem |
| `MediaRecorder` | `javax.sound.sampled` capture, with a real live amplitude for the waveform |
| `MediaPlayer` | `javax.sound.sampled` for what the JDK decodes, the desktop media backend for the rest |
| `compose-audiowaveform` | reimplemented in `:commons` — the app already drew its own waveform and needed only three primitives from it |
| `AlarmManager` | a daemon timer that really sends the PendingIntent |
| `PendingIntent` | a registry with the platform's identity rules, so `cancel` reaches what `schedule` created |
| `coil-gif` | Skia's codec — same formats, first frame only until an animated image lands |
| `coil-video` | Coil's own decoder shape over this platform's `MediaMetadataRetriever` |
| `Image.asDrawable` / `Bitmap.asImage` | a Skia <-> `BufferedImage` conversion, un-premultiplied |
| LightCompressor (upload re-encode) | the ffmpeg already bundled for video thumbnails, behind a shared seam |
| LightCompressor's HLS ladder | one ffmpeg `-f hls` run per rung, uploading each as it finishes |
| `ContentResolver.query` | the file itself — name and size are facts a filesystem can answer |

WorkManager is worth a note because it is where a stub would have been most
expensive: scheduled posts and calendar reminders are the whole feature, and a
scheduler that records an enqueue and never fires means a post the user
scheduled silently never publishes. So the desktop one really runs the work —
periodic repeats, `Result.retry()` with WorkManager's own 30s-doubling-to-5h
backoff, network constraints waited on rather than assumed, and KEEP / REPLACE /
UPDATE / CANCEL_AND_REENQUEUE implemented exactly. What it cannot do is
Android's other half: JobScheduler can start a stopped app, and no desktop OS
offers that to an ordinary application. Work therefore runs while the app is
open, and the app re-enqueues what is still due at the next launch — "next
launch" being the desktop analogue of "next boot". That one difference is
declared as `WorkManager.wakeWhileAppClosed`, not papered over.

Notifications needed no new backend: `:commons` already carries
`NucleusNotificationDispatcher`, which delivers through
`UNUserNotificationCenter`, WinRT toasts and freedesktop D-Bus and falls back to
an AWT balloon. What was missing was the adapter, and — more to the point — the
content: `NotificationCompat.Builder` accepted a title and a body and dropped
both, so every notification would have arrived blank while looking, from the
calling code, exactly like a working one. `AndroidNotificationBridge` is that
adapter. What the OS model does not have is Android's persistent row, so an
*ongoing* or progress notification is delivered once and its latest state kept
in `AndroidNotificationBridge.ongoing` for an in-app status row, rather than
firing a toast per progress tick. Action buttons do not survive: a click
deep-links instead. `RemoteInput`'s intent plumbing is implemented exactly, so a
presenter that *can* collect text — a reply window, or a platform with inline
replies — needs no change downstream.

`AlarmManager` is worth a note for the bug it was hiding rather than the timer
it gained. The watchdog schedules an alarm with one `PendingIntent` and cancels
it with another, built from scratch — which works on Android because two
intents with the same kind, request code and target *are* the same token. A
stub handing back a fresh object each time makes every `cancel` a silent no-op,
and `FLAG_NO_CREATE` returning non-null makes the caller cancel a token it
never set. So `PendingIntent` now keeps a registry with the platform's matching
rules (action, data, type, component, categories — not extras) and the flags
that depend on it, and the alarm itself fires on a daemon timer. The one real
difference is the same as WorkManager's: Android's alarm can start a stopped
app, and no desktop OS offers that — which for a watchdog over an in-process
service is the lifetime that matters anyway, since the thing it watches is gone
too.

Voice messages needed the microphone, which is not an Android-only capability:
`MediaRecorder` opens a `TargetDataLine`, and `getMaxAmplitude` is computed from
the captured samples, so the live waveform is the real signal. What the JDK does
not have is an *encoder* — `setAudioEncoder(AAC)` cannot be honoured — so the
default backend writes WAV/PCM and reports the substitution, because a file
silently labelled `audio/mp4` that is really a WAV only shows up at the far end.
A build with a real encoder installs one through `MediaRecorder.setEncoder`, and
`MediaPlayer` takes the matching seam: the JDK decodes WAV, AU and AIFF, and
anything else (AAC-in-MP4, which is what Android records) goes to the desktop
media backend.

One asterisk: `ConnectivityManager` cannot report whether a connection is
metered — no JDK API exposes that on any OS — so desktop assumes unmetered and
declares that single sub-capability unavailable rather than presenting a guess
as fact.
