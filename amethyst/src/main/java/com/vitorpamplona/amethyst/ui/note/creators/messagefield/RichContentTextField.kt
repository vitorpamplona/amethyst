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
package com.vitorpamplona.amethyst.ui.note.creators.messagefield

import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import com.vitorpamplona.amethyst.ui.theme.placeholderText

private val SUPPORTED_MIME_TYPES =
    arrayOf(
        "image/gif",
        "image/png",
        "image/jpeg",
        "image/webp",
        "image/bmp",
        "video/mp4",
        "video/webm",
    )

@Composable
fun RichContentTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onReceiveUri: (Uri) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.placeholderText.toArgb()
    val density = LocalDensity.current
    val paddingPx =
        with(density) {
            10.dp.roundToPx()
        }
    val verticalPaddingPx =
        with(density) {
            5.dp.roundToPx()
        }

    var isUpdatingFromCompose by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            EditText(context).apply {
                background = null
                setPadding(paddingPx, verticalPaddingPx, paddingPx, verticalPaddingPx)
                hint = placeholder
                setHintTextColor(hintColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.TOP or Gravity.START
                inputType =
                    EditorInfo.TYPE_CLASS_TEXT or
                    EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
                    EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
                minHeight = (36 * context.resources.displayMetrics.density).toInt()
                isFocusableInTouchMode = true

                ViewCompat.setOnReceiveContentListener(
                    this,
                    SUPPORTED_MIME_TYPES,
                    OnReceiveContentListener { _, payload ->
                        val clip = payload.clip
                        var consumed = false
                        for (i in 0 until clip.itemCount) {
                            clip.getItemAt(i).uri?.let { uri ->
                                onReceiveUri(uri)
                                consumed = true
                            }
                        }
                        if (consumed) null else payload
                    },
                )

                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) {}

                        override fun afterTextChanged(s: Editable?) {
                            if (isUpdatingFromCompose) return
                            val text = s?.toString() ?: ""
                            val selStart = selectionStart.coerceIn(0, text.length)
                            val selEnd = selectionEnd.coerceIn(0, text.length)
                            onValueChange(
                                TextFieldValue(
                                    text = text,
                                    selection = TextRange(selStart, selEnd),
                                ),
                            )
                        }
                    },
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
        update = { editText ->
            val currentText = editText.text?.toString() ?: ""
            if (currentText != value.text) {
                isUpdatingFromCompose = true
                editText.setText(value.text)
                val selStart = value.selection.start.coerceIn(0, value.text.length)
                val selEnd = value.selection.end.coerceIn(0, value.text.length)
                editText.setSelection(selStart, selEnd)
                isUpdatingFromCompose = false
            }
        },
    )
}
