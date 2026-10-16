# Spell checking in the desktop composers

Status: built. Hunspell path tested headless on Linux; the macOS and Windows engines compile but
have not run on those systems yet (see "Testing by hand").

## What the user gets

Misspelled words in the composers (posts, replies, DMs, chats, long-form, classifieds) and in the
prose fields across the app (bios, descriptions, captions, notes, reasons, titles) get a wavy
underline. Right-clicking one lists suggestions first, then "Add to dictionary", then the usual
Cut / Copy / Paste / Select all. Settings › Compose › Spell checking picks the engine:

| Choice | Engine |
|---|---|
| Automatic (default) | macOS `NSSpellChecker` / Windows `ISpellChecker` when it loads and passes a self-test; the bundled Hunspell en_US otherwise (always on Linux) |
| Built-in | Bundled Hunspell en_US on every OS |
| Off | Nothing |

Android and iOS show no setting: the keyboard already checks spelling there (`AppPlatform.spellCheck`
is null).

## Why both engines, and why the system one is not always better

| | System (macOS, Windows) | Bundled Hunspell |
|---|---|---|
| Languages | Whatever the user installed; macOS detects per word | en_US only, for now |
| Learned words | Shared with every app (we do not write to it) | — |
| Linux | none | yes |
| Testable in CI / headless | no | yes |
| Failure mode | JNA into native code: a wrong call crashes the JVM | plain Java |
| Same results everywhere | no | yes |

So "Automatic" prefers the system checker, and "Built-in" exists for people who want predictable
results or hit a system-checker problem.

## Pieces

- `commons/…/service/spellcheck/` (headless): `SpellChecker` (an engine), `SpellCheckTokenizer`
  (skips links, `nostr:`/bech32, hashtags, `@mentions`, `:shortcodes:`, e-mail, Lightning/Cashu,
  code, words with digits, acronyms, camelCase/snake_case), `NostrVocabulary`, `SpellCheckService`
  (verdict + suggestion caches, the user's dictionary, one dispatcher for every engine call).
- `commonsUI/…/ui/spellcheck/`: `SpellCheckFieldState` (re-checks after a 250 ms pause, carries
  underlines across edits, hides the word under the cursor), the wavy underline, right-click
  tracking, and `SpellCheckContextMenu` (desktop actual swaps `LocalTextContextMenu` so suggestions
  come first; Compose's `ContextMenuDataProvider` can only append after Select all, and
  `appendTextContextMenuComponents` needs `ComposeFoundationFlags.isNewContextMenuEnabled`, which is
  off on desktop and would change every text menu in the app).
- `VisualOffsetMapping`: `UrlUserTagOutputTransformation` draws `nostr:npub1…` as `@Name`, so the
  checker (which reads the stored text) maps its ranges to the drawn text through the same
  replacement list.
- `ThinPaddingTextField` / `OutlinedThinPaddingTextField` take `spellCheck = true` (the composers).
- `SpellCheckedOutlinedTextField` / `SpellCheckedTextField` (`commonsUI/…/ui/components/SpellCheckedTextFields.kt`)
  are drop-ins for Material's `OutlinedTextField` / `TextField` on prose fields, for `String`,
  `TextFieldValue` and `TextFieldState` callers. Without a checker (Android, iOS) they are
  Material's own field; on desktop they mirror the caller's value into a `TextFieldState` (echoes of
  the field's own recent reports are ignored, anything else replaces the text). Instead of
  `keyboardActions` they take `onImeAction` (the keyboard's action key on phones, Enter in a
  single-line field on desktop), which runs after the caller has been handed the field's latest
  text. A length cap goes in `maxLength`, not in `onValueChange`.
- Which fields: ~90 prose fields (descriptions, bios, captions, alt text, notes, comments, reasons,
  summaries, titles, poll options, chat composers, the post signature, the drawer status, Buzz
  prompts). Left out on purpose: search, URLs, keys, handles/NIP-05, amounts, names of
  people/lists/groups, geocache mission answers, and the quoted highlight passage.
- `desktopApp/…/spellcheck/`: `HunspellSpellChecker` (Lucene `lucene-analysis-common` 10.5.1,
  Apache-2.0; dictionary from `dictionary-en` 4.0.0 = SCOWL 2020.12.07, licence in
  `resources/spellcheck/en_US-LICENSE.txt`), `MacSpellChecker`, `WindowsSpellChecker`,
  `DesktopSpellCheck` (engine choice, crash marker, `spelling-dictionary.txt`).

Measured (en_US, JDK 21, this container): load ~0.4 s (~30 MB of short-lived garbage, ~1–2 MB
kept; it loads under `-Xmx8m`), `spell()` ~2 µs/word,
`suggest()` 20–190 ms (Lucene caps it at 250 ms). Nothing loads until a composer first opens.

## Native safety

- `MacSpellChecker` runs every call on the AppKit main thread (`dispatch_sync_f` on the main queue)
  from the spell-check thread. The main thread never waits on that thread, and the AWT event thread
  never calls an engine directly (suggestions are fetched in a coroutine and fill the open menu),
  so no wait cycle exists.
- `WindowsSpellChecker` is created and used only on the spell-check thread, which joins the MTA.
  Vtable slots come from `spellcheck.idl` (mingw-w64): factory `IsSupported`=4,
  `CreateSpellChecker`=5; checker `Check`=4, `Suggest`=5; enumerators `Next`=3.
- `DesktopSpellCheck` writes `spellcheck-native-probe` (the app version) before the first native
  call and deletes it after the self-test. If the file survives (the probe crashed the JVM), that
  app version never tries the system checker again and uses Hunspell.

## Testing by hand (macOS and Windows)

1. Run the desktop app, open a new post, type `I definately recieve zaps on Nostr. Teh end.`
2. Expect underlines under definately, recieve, Teh only. The log line
   `[DesktopSpellCheck] Spell checking with macOS (AUTOMATIC)` / `Windows (en-US)` names the engine;
   `Hunspell (en_US)` there means the system checker failed — the warning above it says why.
3. Right-click `recieve`: suggestions first, then Add to dictionary, then Cut/Copy/Paste/Select all.
   Pick one; it replaces the word.
4. Settings › Compose › Spell checking › Built-in: the log switches to Hunspell; Off removes the
   underlines.
5. If the app crashed during step 1, relaunch: it should come up on Hunspell and log
   "crashed this version of the app before".

## Next

- More bundled dictionaries (licences vary per language; check each).
- An LLM "Improve" action for desktop: `AccountViewModelHost.createWritingAssistant()` already
  drives the Android on-device assistant (`WritingAssistant`, tones incl. `CORRECT`); a desktop
  host could back it with a local model server.
