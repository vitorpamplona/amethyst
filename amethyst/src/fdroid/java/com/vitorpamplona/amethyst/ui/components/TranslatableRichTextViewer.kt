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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.ui.components.TranslationConfig
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.lang.ResultOrError
import com.vitorpamplona.amethyst.service.lang.TranslationServerConfig
import com.vitorpamplona.amethyst.service.lang.TranslationsCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.MaxWidthPaddingTop5dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TranslatableRichTextViewer(
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier = Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    id: String,
    callbackUri: String? = null,
    authorPubKey: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TranslatableRichTextViewer(
        content = content,
        id = id,
        accountViewModel = accountViewModel,
    ) {
        ExpandableRichTextViewer(
            it,
            canPreview,
            quotesLeft,
            modifier,
            tags,
            backgroundColor,
            id,
            callbackUri,
            authorPubKey,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun TranslatableRichTextViewer(
    content: String,
    id: String,
    translationMessageModifier: Modifier = MaxWidthPaddingTop5dp,
    accountViewModel: AccountViewModel,
    displayText: @Composable (String) -> Unit,
) {
    val context = LocalContext.current
    val languages = accountViewModel.account.settings.syncedSettings.languages
    val translateTo by languages.translateTo.collectAsStateWithLifecycle()
    val dontTranslateFrom by languages.dontTranslateFrom.collectAsStateWithLifecycle()

    val translatedTextState =
        remember(id, content, translateTo, dontTranslateFrom) {
            mutableStateOf(
                TranslationsCache.get(content, translateTo, dontTranslateFrom)
                    ?: TranslationConfig(content, null, null),
            )
        }

    LaunchedEffect(content, translateTo, dontTranslateFrom) {
        try {
            translatedTextState.value =
                withContext(Dispatchers.IO) {
                    translateAndCache(context, content, translateTo, dontTranslateFrom)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Transient network failure — keep showing the original. Do not cache: a
            // one-off failure shouldn't block future attempts on the same text.
        }
    }

    RenderTextWithTranslateOptions(
        translatedTextState = translatedTextState.value,
        content = content,
        translationMessageModifier = translationMessageModifier,
        displayText = displayText,
    )
}

/**
 * The translation of [content] under the current language settings, or [content] unchanged when
 * no translation applies.
 */
@Composable
fun rememberTranslation(
    content: String,
    accountViewModel: AccountViewModel,
): String {
    val context = LocalContext.current
    val languages = accountViewModel.account.settings.syncedSettings.languages
    val translateTo by languages.translateTo.collectAsStateWithLifecycle()
    val dontTranslateFrom by languages.dontTranslateFrom.collectAsStateWithLifecycle()

    val state =
        remember(content, translateTo, dontTranslateFrom) {
            mutableStateOf(
                TranslationsCache.get(content, translateTo, dontTranslateFrom)
                    ?: TranslationConfig(content, null, null),
            )
        }

    LaunchedEffect(content, translateTo, dontTranslateFrom) {
        try {
            state.value =
                withContext(Dispatchers.IO) {
                    translateAndCache(context, content, translateTo, dontTranslateFrom)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Transient network failure — keep the original, same as the viewer does.
        }
    }

    val config = state.value
    val translated = config.sourceLang != null && config.targetLang != null && config.sourceLang != config.targetLang
    return if (translated) config.result else content
}

@Composable
private fun RenderTextWithTranslateOptions(
    translatedTextState: TranslationConfig,
    content: String,
    translationMessageModifier: Modifier = MaxWidthPaddingTop5dp,
    displayText: @Composable (String) -> Unit,
) {
    val source = translatedTextState.sourceLang
    val target = translatedTextState.targetLang
    val translationOccurred = source != null && target != null && source != target

    var showOriginal by remember(translatedTextState) { mutableStateOf(false) }

    val toBeViewed = if (showOriginal || !translationOccurred) content else translatedTextState.result

    Column {
        displayText(toBeViewed)

        if (translationOccurred) {
            Row(
                modifier = translationMessageModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.translations_auto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showOriginal = !showOriginal }) {
                    Text(
                        text =
                            stringRes(
                                if (showOriginal) R.string.translations_show_translation
                                else R.string.translations_show_original,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Returns the translation for [content] under the current language settings, hitting the cache
 * first and falling back to the LibreTranslate-backed service. Cancellations and errors are
 * bridged into a no-op [TranslationConfig] that is itself cached, so the same text scrolling
 * back into view doesn't re-run the network call.
 */
private suspend fun translateAndCache(
    context: android.content.Context,
    content: String,
    translateTo: String,
    dontTranslateFrom: Set<String>,
): TranslationConfig {
    TranslationsCache.get(content, translateTo, dontTranslateFrom)?.let { return it }

    // While translation is disabled, return the original without caching it, so enabling the
    // feature later doesn't serve a stale "not translated" entry for content already seen.
    if (!TranslationServerConfig.isEnabled(context)) return TranslationConfig(content, null, null)

    val noOp = TranslationConfig(content, null, null)
    val raw: ResultOrError =
        try {
            LanguageTranslatorService.autoTranslate(context, content, dontTranslateFrom, translateTo)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Transient network / server failure — keep the original but do not cache: a one-off
            // failure shouldn't block future attempts on the same text.
            return noOp
        }

    val translated = raw.result
    val config =
        if (translated.isNullOrBlank()) {
            noOp
        } else {
            TranslationConfig(translated, raw.sourceLang, raw.targetLang)
        }
    // A no-op here is a genuine service decision (same language, blocklisted, undetected) — cache it.
    TranslationsCache.set(content, translateTo, dontTranslateFrom, config)
    return config
}
