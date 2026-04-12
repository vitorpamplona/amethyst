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
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Stable
class AddBountyAmountViewModel : ViewModel() {
    private var bounty: Note? = null
    private var onSendBounty: (suspend (BigDecimal, Note) -> Unit)? = null

    var nextAmount by mutableStateOf(TextFieldValue(""))

    fun load(
        bounty: Note?,
        onSendBounty: suspend (BigDecimal, Note) -> Unit,
    ) {
        this.bounty = bounty
        this.onSendBounty = onSendBounty
    }

    fun sendPost() {
        val newValue = nextAmount.text.trim().toBigDecimalOrNull()

        if (newValue != null) {
            viewModelScope.launch(Dispatchers.IO) {
                bounty?.let { bountyInner ->
                    onSendBounty?.invoke(newValue, bountyInner)
                }

                nextAmount = TextFieldValue("")
            }
        }
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
    }

    fun hasChanged(): Boolean = nextAmount.text.trim().toLongOrNull() != null
}
