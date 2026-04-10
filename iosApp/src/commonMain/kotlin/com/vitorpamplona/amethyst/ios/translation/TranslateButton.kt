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
package com.vitorpamplona.amethyst.ios.translation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode

/**
 * Displays a "Translate from <lang>" button when the note content is detected
 * to be in a foreign language. Tapping opens Apple's system translation sheet
 * (iOS 17.4+) via the Translation framework, or falls back to a notice.
 *
 * On iOS < 17.4, the Translation framework is unavailable in Compose Multiplatform,
 * so the button copies the text to the clipboard and suggests using the system
 * Translate app.
 */
@Composable
fun TranslateButton(
    noteContent: String,
    modifier: Modifier = Modifier,
    onTranslateRequest: ((String, String) -> Unit)? = null,
) {
    val detected = remember(noteContent) { LanguageDetector.detect(noteContent) }

    if (detected == null || !detected.isForeign) return

    val langName =
        remember(detected.languageCode) {
            NSLocale.currentLocale.localizedStringForLanguageCode(detected.languageCode)
                ?: detected.languageCode
        }

    var showTranslation by remember { mutableStateOf(false) }

    TextButton(
        onClick = {
            if (onTranslateRequest != null) {
                onTranslateRequest(noteContent, detected.languageCode)
            } else {
                showTranslation = !showTranslation
                // Copy to pasteboard for manual translate fallback
                platform.UIKit.UIPasteboard.generalPasteboard.string = noteContent
            }
        },
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🌐",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (showTranslation) "Hide translation info" else "Translate from $langName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (showTranslation) {
        Text(
            "Text copied — paste into the Translate app to translate from $langName.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}
