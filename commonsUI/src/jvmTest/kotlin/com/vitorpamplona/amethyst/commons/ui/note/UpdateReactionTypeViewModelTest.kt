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
package com.vitorpamplona.amethyst.commons.ui.note

import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateReactionTypeViewModelTest {
    private fun viewModel(vararg choices: String) = UpdateReactionTypeViewModel().apply { reactionSet = choices.toList() }

    @Test
    fun unicodeTapAddsThenRemoves() {
        val vm = viewModel("+", "🚀")
        vm.toggle("🔥")
        assertEquals(listOf("+", "🚀", "🔥"), vm.reactionSet)
        assertTrue("🔥" in vm.picked.unicode)
        vm.toggle("🔥")
        assertEquals(listOf("+", "🚀"), vm.reactionSet)
    }

    @Test
    fun typedTextKeepsItsFirstCharacter() {
        val vm = viewModel()
        vm.toggle("  👍🏽 and more")
        vm.toggle("   ")
        assertEquals(listOf("👍🏽"), vm.reactionSet)
    }

    @Test
    fun customEmojiIsMatchedByImageNotCode() {
        val vm = viewModel(":oldcode:https://x.com/a.png")
        assertTrue("https://x.com/a.png" in vm.picked.customLinks)

        // Same image under another code: already chosen, so the tap removes it instead of adding a duplicate.
        vm.toggle(EmojiMedia("newcode", "https://x.com/a.png"))
        assertEquals(emptyList(), vm.reactionSet)

        vm.toggle(EmojiMedia("b", "https://x.com/b.png"))
        assertEquals(listOf(":b:https://x.com/b.png"), vm.reactionSet)
    }
}
