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

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.lang.TranslationsCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.MaxWidthPaddingTop5dp
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import java.util.Locale

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
    val languages = accountViewModel.account.settings.syncedSettings.languages
    val translateTo by languages.translateTo.collectAsStateWithLifecycle()
    val dontTranslateFrom by languages.dontTranslateFrom.collectAsStateWithLifecycle()
    val languagePreferences by languages.languagePreferences.collectAsStateWithLifecycle()

    val translatedTextState =
        remember(id, content, translateTo, dontTranslateFrom) {
            mutableStateOf(
                TranslationsCache.get(content, translateTo, dontTranslateFrom)
                    ?: TranslationConfig(content, null, null),
            )
        }

    LaunchedEffect(content, translateTo, dontTranslateFrom) {
        TranslationsCache.get(content, translateTo, dontTranslateFrom)?.let {
            translatedTextState.value = it
            return@LaunchedEffect
        }

        val noOp = TranslationConfig(content, null, null)
        try {
            val task = LanguageTranslatorService.autoTranslate(content, dontTranslateFrom, translateTo)
            // ML Kit cancels the task to signal "no translation needed" (same language, "und",
            // blocklisted). await() bridges that into a CancellationException; cache the no-op so
            // we don't re-run language identification next time the same text scrolls into view.
            val raw =
                try {
                    task.await()
                } catch (e: CancellationException) {
                    coroutineContext.ensureActive()
                    TranslationsCache.set(content, translateTo, dontTranslateFrom, noOp)
                    translatedTextState.value = noOp
                    return@LaunchedEffect
                }

            coroutineContext.ensureActive()

            val translated = raw.result
            val source = raw.sourceLang
            val target = raw.targetLang
            val newConfig =
                if (
                    translated != null &&
                    source != null &&
                    target != null &&
                    source != target &&
                    translated != content
                ) {
                    TranslationConfig(translated, source, target)
                } else {
                    noOp
                }
            TranslationsCache.set(content, translateTo, dontTranslateFrom, newConfig)
            translatedTextState.value = newConfig
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Network / model download / translator failure — keep showing the original. Do not
            // cache: a transient failure shouldn't block future attempts on the same text.
        }
    }

    RenderTextWithTranslateOptions(
        translatedTextState = translatedTextState.value,
        content = content,
        languagePreferences = languagePreferences,
        translationMessageModifier = translationMessageModifier,
        accountViewModel = accountViewModel,
        displayText = displayText,
    )
}

@Composable
private fun RenderTextWithTranslateOptions(
    translatedTextState: TranslationConfig,
    content: String,
    languagePreferences: Map<String, String>,
    translationMessageModifier: Modifier = MaxWidthPaddingTop5dp,
    accountViewModel: AccountViewModel,
    displayText: @Composable (String) -> Unit,
) {
    val source = translatedTextState.sourceLang
    val target = translatedTextState.targetLang
    val translationOccurred = source != null && target != null && source != target

    val storedPreference = if (translationOccurred) languagePreferences["$source,$target"] else null
    var showOriginal by
        remember(translatedTextState, storedPreference) {
            mutableStateOf(storedPreference == source)
        }

    val toBeViewed = if (showOriginal || !translationOccurred) content else translatedTextState.result

    Column {
        displayText(toBeViewed)

        if (translationOccurred) {
            TranslationMessage(
                source = source,
                target = target,
                modifier = translationMessageModifier,
                accountViewModel = accountViewModel,
            ) { showOriginal = it }
        }
    }
}

@Composable
private fun TranslationMessage(
    source: String,
    target: String,
    modifier: Modifier = MaxWidthPaddingTop5dp,
    accountViewModel: AccountViewModel,
    onChangeWhatToShow: (Boolean) -> Unit,
) {
    var langSettingsPopupExpanded by remember { mutableStateOf(false) }

    val sourceDisplay = remember(source) { Locale.forLanguageTag(source).displayName }
    val targetDisplay = remember(target) { Locale.forLanguageTag(target).displayName }
    val autoLabel = stringRes(R.string.translations_auto)
    val translatedFromLabel = stringRes(R.string.translations_translated_from)
    val toLabel = stringRes(R.string.translations_to)

    Row(modifier = modifier) {
        val textColor = MaterialTheme.colorScheme.lessImportantLink

        Text(
            text =
                buildAnnotatedString {
                    appendLink(autoLabel, textColor) { langSettingsPopupExpanded = !langSettingsPopupExpanded }
                    append(" $translatedFromLabel ")
                    appendLink(sourceDisplay, textColor) { onChangeWhatToShow(true) }
                    append(" $toLabel ")
                    appendLink(targetDisplay, textColor) { onChangeWhatToShow(false) }
                },
            style =
                LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                    fontSize = Font14SP,
                ),
            overflow = TextOverflow.Visible,
            maxLines = 3,
        )

        if (langSettingsPopupExpanded) {
            LangSettingsDropdown(
                expanded = true,
                source = source,
                target = target,
                sourceDisplay = sourceDisplay,
                targetDisplay = targetDisplay,
                accountViewModel = accountViewModel,
                onDismiss = { langSettingsPopupExpanded = false },
            )
        }
    }
}

@Composable
private fun LangSettingsDropdown(
    expanded: Boolean,
    source: String,
    target: String,
    sourceDisplay: String,
    targetDisplay: String,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val deviceLocales =
        remember {
            val list = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
            (0 until list.size()).mapNotNull { list.get(it) }
        }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                CheckmarkRow(
                    checked = source in accountViewModel.dontTranslateFrom(),
                    label = stringRes(R.string.translations_never_translate_from_lang, sourceDisplay),
                )
            },
            onClick = {
                accountViewModel.toggleDontTranslateFrom(source)
                onDismiss()
            },
        )
        HorizontalDivider(thickness = DividerThickness)
        DropdownMenuItem(
            text = {
                CheckmarkRow(
                    checked = accountViewModel.account.settings.preferenceBetween(source, target) == source,
                    label = stringRes(R.string.translations_show_in_lang_first, sourceDisplay),
                )
            },
            onClick = {
                accountViewModel.prefer(source, target, source)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = {
                CheckmarkRow(
                    checked = accountViewModel.account.settings.preferenceBetween(source, target) == target,
                    label = stringRes(R.string.translations_show_in_lang_first, targetDisplay),
                )
            },
            onClick = {
                accountViewModel.prefer(source, target, target)
                onDismiss()
            },
        )
        HorizontalDivider(thickness = DividerThickness)

        for (lang in deviceLocales) {
            DropdownMenuItem(
                text = {
                    CheckmarkRow(
                        checked = accountViewModel.account.settings.translateToContains(lang.language),
                        label = stringRes(R.string.translations_always_translate_to_lang, lang.displayName),
                    )
                },
                onClick = {
                    onDismiss()
                    accountViewModel.updateTranslateTo(lang.language)
                },
            )
        }
    }
}

@Composable
private fun CheckmarkRow(
    checked: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (checked) {
            Icon(
                symbol = MaterialSymbols.Check,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.size(10.dp))
        Text(label)
    }
}
