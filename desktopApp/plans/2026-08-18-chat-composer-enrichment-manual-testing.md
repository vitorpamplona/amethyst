# Desktop Chat Composer Enrichment — Manual Testing Sheet

**Branch:** `feat/desktop-chat-composer-enrichment`
**Run:** `./gradlew :desktopApp:run`, log in with an account that has NIP-17 DMs, open **Messages**, select a conversation.

Legend: ☐ untested · ✅ pass · ❌ fail (note issue)

## P0 — TextFieldValue composer (foundation)
- ☐ Type text; move the caret with arrow keys/click mid-word — cursor position holds.
- ☐ Cmd/Ctrl+Enter sends; Enter alone inserts a newline.

## P1 — Reply-quoting
- ☐ Hover a message → quote (❝) icon appears; click it → a reply bar shows the quoted author + snippet above the input.
- ☐ Send → recipient sees a threaded reply (reply marker present).
- ☐ Click the ✕ on the reply bar → reply target cleared; next send is a normal message.
- ☐ Reply, then send → reply bar auto-clears after send.

## P2 — Emoji picker
- ☐ Click the 🙂 (EmojiEmotions) button → grid opens.
- ☐ Search "fire" → grid filters; click 🔥 → inserted at the caret (not appended blindly).
- ☐ Insert an emoji mid-word → lands at the cursor, text splits correctly.
- ☐ Works offline (disable network) — emojis still render via system font.

## P3 — NIP-30 custom emoji
- ☐ (Requires an emoji pack selected on the account.) Type `:` + a prefix → suggestion strip shows matching pack emojis with images.
- ☐ Click one → `:shortcode:` inserted at the caret.
- ☐ Send → recipient renders the custom emoji (event carries `emoji` tags).
- ☐ No pack selected → no strip appears, no errors.

## P4 — @mention
- ☐ Type `@` + a name prefix → dropdown of matching cached users.
- ☐ Click one → `nostr:npub…` inserted (with trailing space); dropdown closes.
- ☐ Recipient/link resolves to the mentioned profile.
- ☐ `#hashtag` — typing `#tag` still tags the note on send (no dropdown; deferred — no hashtag index).

## P5 — Per-message image quality
- ☐ Attach a JPEG/PNG → a "Quality:" chip appears above the input.
- ☐ Send at **Low** vs **Uncompressed** → recipient's received file is visibly smaller at Low (check dimensions/size).
- ☐ Attach an animated GIF → sends animated (byte-identical pass-through), quality chip hidden for GIF-only.
- ☐ Quality override resets to the default after each send.
- ☐ EXIF: a photo with GPS EXIF sent at pass-through has EXIF stripped (desktop opts in).

## P6 — Nostr-native GIF search
- ☐ Click **GIF** → panel opens, loads recent GIFs from `relay.gifbuddy.lol`.
- ☐ Search "cat" → grid updates (debounced ~350ms).
- ☐ Click a GIF → its URL inserted into the message; send → renders inline/animated for the recipient.
- ☐ Add a second relay via `DesktopPreferences.gifRelays` → results merge, deduped by URL.
- ☐ Point at an unreachable relay → panel degrades to empty/"No GIFs found", no crash, composer still usable.

## Cross-feature integration
1. ☐ One message mixing a unicode emoji + `:customshortcode:` + `@mention` + `#tag` → sends; recipient renders all; event carries `emoji`, `p`, `t` tags.
2. ☐ Reply to a message and send → reply preserved; `clear()` resets the whole composer.
3. ☐ Switch rooms mid-typing (with an open suggestion) → no stale dropdown / no cross-room leakage.
4. ☐ Compact (narrow) and split (wide) layouts both wire the composer correctly.

## Regression
- ☐ Plain-text send still works.
- ☐ File attach + drag-and-drop still work.
- ☐ Quick-reaction bar on messages still works.
</content>
