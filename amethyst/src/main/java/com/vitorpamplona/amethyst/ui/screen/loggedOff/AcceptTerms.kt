/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedOff

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.ClickableText
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun AcceptTerms(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )

        val regularText = SpanStyle(color = MaterialTheme.colorScheme.onBackground)
        val clickableTextStyle = SpanStyle(color = MaterialTheme.colorScheme.primary)

        val annotatedTermsString =
            buildAnnotatedString {
                withStyle(regularText) { append(stringRes(R.string.i_accept_the)) }

                withStyle(clickableTextStyle) {
                    pushStringAnnotation("openTerms", "")
                    append(stringRes(R.string.terms_of_use))
                    pop()
                }
            }

        val uri = LocalUriHandler.current

        ClickableText(
            annotatedTermsString,
        ) { spanOffset ->
            annotatedTermsString.getStringAnnotations(spanOffset, spanOffset).firstOrNull()?.also { span ->
                if (span.tag == "openTerms") {
                    runCatching {
                        uri.openUri("https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md")
                    }
                }
            }
        }
    }
}
