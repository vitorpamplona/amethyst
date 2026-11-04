/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.desktop.spellcheck

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellCheckService
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * On desktop the spell-checked drop-ins hold their own TextFieldState and mirror the caller's
 * value. These pin the mirroring: edits reach the caller, the caller's own changes reach the field,
 * and a length cap holds.
 */
class SpellCheckedTextFieldsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val desktop =
        object : AppPlatform {
            override val spellCheck = SpellCheckService(Dispatchers.Unconfined)
        }

    @Test
    fun stringFieldReportsEditsAndShowsTheCallersChanges() {
        var text by mutableStateOf("draft")
        compose.setContent {
            CompositionLocalProvider(LocalAppPlatform provides desktop) {
                SpellCheckedOutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.testTag("field"))
            }
        }

        compose.onNodeWithTag("field").performTextInput(" one")
        compose.onNodeWithTag("field").performTextInput(" two")
        compose.waitForIdle()
        assertEquals("draft one two", text)

        // The caller clears it, as composers do after sending.
        text = ""
        compose.waitForIdle()
        compose.onNodeWithTag("field").assertTextEquals("")

        compose.onNodeWithTag("field").performTextInput("again")
        compose.waitForIdle()
        assertEquals("again", text)
    }

    @Test
    fun textFieldValueFieldReportsTextAndCursor() {
        var value by mutableStateOf(TextFieldValue("abc", TextRange(3)))
        compose.setContent {
            CompositionLocalProvider(LocalAppPlatform provides desktop) {
                SpellCheckedOutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.testTag("field"))
            }
        }

        compose.onNodeWithTag("field").performTextInput("d")
        compose.waitForIdle()
        assertEquals("abcd", value.text)
        assertEquals(4, value.selection.start)
    }

    @Test
    fun enterRunsTheActionWithTheLatestText() {
        var text by mutableStateOf("")
        val sent = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalAppPlatform provides desktop) {
                SpellCheckedOutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    onImeAction = { sent += text },
                    modifier = Modifier.testTag("field"),
                )
            }
        }

        compose.onNodeWithTag("field").performTextInput("gm nostr")
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("field").performTextInput("!")
        compose.onNodeWithTag("field").performImeAction()
        compose.waitForIdle()

        assertEquals(listOf("gm nostr", "gm nostr!"), sent)
        // Enter in a single-line field sends; it does not add a line.
        assertEquals("gm nostr!", text)
    }

    @Test
    fun maxLengthHolds() {
        var text by mutableStateOf("")
        compose.setContent {
            CompositionLocalProvider(LocalAppPlatform provides desktop) {
                SpellCheckedOutlinedTextField(value = text, onValueChange = { text = it }, maxLength = 3, modifier = Modifier.testTag("field"))
            }
        }

        "abcd".forEach { compose.onNodeWithTag("field").performTextInput(it.toString()) }
        compose.waitForIdle()
        assertEquals("abc", text)
        compose.onNodeWithTag("field").assertTextEquals("abc")
    }
}
