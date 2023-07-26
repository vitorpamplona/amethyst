package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class NewMessageTaggerKeyParseTest {
    @Test
    fun keyParseTestNote() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn")
        assertEquals(Nip19.Type.NOTE, result?.key?.type)
        assertEquals("1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605", result?.key?.hex)
        assertEquals("", result?.restOfWord)
    }

    @Test
    fun keyParseTestPub() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z")
        assertEquals(Nip19.Type.USER, result?.key?.type)
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.key?.hex)
        assertEquals("", result?.restOfWord)
    }

    @Test
    fun keyParseTestNoteWithExtraChars() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn,")
        assertEquals(Nip19.Type.NOTE, result?.key?.type)
        assertEquals("1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605", result?.key?.hex)
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestPubWithExtraChars() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z,")
        assertEquals(Nip19.Type.USER, result?.key?.type)
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.key?.hex)
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestNoteWithExtraCharsAndAt() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("@note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn,")
        assertEquals(Nip19.Type.NOTE, result?.key?.type)
        assertEquals("1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605", result?.key?.hex)
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestPubWithExtraCharsAndAt() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z,")
        assertEquals(Nip19.Type.USER, result?.key?.type)
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.key?.hex)
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestNoteWithExtraCharsAndNostrPrefix() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("nostr:note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn,")
        assertEquals(Nip19.Type.NOTE, result?.key?.type)
        assertEquals("1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605", result?.key?.hex)
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestPubWithExtraCharsAndNostrPrefix() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z,")
        assertEquals(Nip19.Type.USER, result?.key?.type)
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.key?.hex)
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestUppercaseNoteWithExtraCharsAndNostrPrefix() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("Nostr:note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn,")
        assertEquals(Nip19.Type.NOTE, result?.key?.type)
        assertEquals("1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605", result?.key?.hex)
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestUppercasePubWithExtraCharsAndNostrPrefix() {
        val result = NewMessageTagger(message = "").parseDirtyWordForKey("nOstr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z,")
        assertEquals(Nip19.Type.USER, result?.key?.type)
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.key?.hex)
        assertEquals(",", result?.restOfWord)
    }
}
