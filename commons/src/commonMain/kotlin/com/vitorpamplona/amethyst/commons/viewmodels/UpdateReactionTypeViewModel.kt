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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.util.firstFullChar
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

@Stable
class UpdateReactionTypeViewModel : ViewModel() {
    private var initialReactionChoices: List<String> = emptyList()
    private var onChangeReactionTypes: ((List<String>, () -> Unit) -> Unit)? = null

    var nextChoice by mutableStateOf(TextFieldValue(""))
    var reactionSet by mutableStateOf(listOf<String>())

    fun init(onChangeReactionTypes: (List<String>, () -> Unit) -> Unit) {
        this.onChangeReactionTypes = onChangeReactionTypes
    }

    fun load(reactionChoices: List<String>) {
        this.initialReactionChoices = reactionChoices
        this.reactionSet = reactionChoices
    }

    fun toListOfChoices(commaSeparatedAmounts: String): List<Long> = commaSeparatedAmounts.split(",").map { it.trim().toLongOrNull() ?: 0 }

    fun addChoice() {
        val newValue =
            if (EmojiCoder.isCoded(nextChoice.text)) {
                EmojiCoder.cropToFirstMessage(nextChoice.text)
            } else {
                nextChoice.text.trim().firstFullChar()
            }

        reactionSet = reactionSet + newValue

        nextChoice = TextFieldValue("")
    }

    fun addChoice(customEmoji: EmojiUrlTag) {
        reactionSet = reactionSet + (customEmoji.encode())
    }

    fun removeChoice(reaction: String) {
        reactionSet = reactionSet - reaction
    }

    fun sendPost() {
        onChangeReactionTypes?.invoke(reactionSet) {
            nextChoice = TextFieldValue("")
        }
    }

    fun cancel() {
        nextChoice = TextFieldValue("")
    }

    fun hasChanged(): Boolean = reactionSet != initialReactionChoices
}
