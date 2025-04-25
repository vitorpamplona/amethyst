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
package com.vitorpamplona.amethyst.ui.components

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.lang.TranslationsCache
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var translatedTextState by translateAndWatchLanguageChanges(content, id, accountViewModel)

    CrossfadeIfEnabled(targetState = translatedTextState, accountViewModel = accountViewModel) {
        RenderText(
            translatedTextState = it,
            content = content,
            canPreview = canPreview,
            quotesLeft = quotesLeft,
            modifier = modifier,
            tags = tags,
            backgroundColor = backgroundColor,
            id = id,
            callbackUri = callbackUri,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun RenderText(
    translatedTextState: TranslationConfig,
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    id: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showOriginal by
        remember(translatedTextState) { mutableStateOf(translatedTextState.showOriginal) }

    val toBeViewed by
        remember(translatedTextState) {
            derivedStateOf { if (showOriginal) content else translatedTextState.result ?: content }
        }

    Column {
        ExpandableRichTextViewer(
            toBeViewed,
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

        if (
            translatedTextState.sourceLang != null &&
            translatedTextState.targetLang != null &&
            translatedTextState.sourceLang != translatedTextState.targetLang
        ) {
            TranslationMessage(
                translatedTextState.sourceLang,
                translatedTextState.targetLang,
                accountViewModel,
            ) {
                showOriginal = it
            }
        }
    }
}

@Composable
private fun TranslationMessage(
    source: String,
    target: String,
    accountViewModel: AccountViewModel,
    onChangeWhatToShow: (Boolean) -> Unit,
) {
    var langSettingsPopupExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
    ) {
        val textColor = MaterialTheme.colorScheme.lessImportantLink

        Text(
            text =
                buildAnnotatedString {
                    appendLink(stringRes(R.string.translations_auto), textColor) { langSettingsPopupExpanded = !langSettingsPopupExpanded }
                    append("-${stringRes(R.string.translations_translated_from)} ")
                    appendLink(Locale(source).displayName, textColor) { onChangeWhatToShow(true) }
                    append(" ${stringRes(R.string.translations_to)} ")
                    appendLink(Locale(target).displayName, textColor) { onChangeWhatToShow(false) }
                },
            style =
                LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                    fontSize = Font14SP,
                ),
            overflow = TextOverflow.Visible,
            maxLines = 3,
        )

        DropdownMenu(
            expanded = langSettingsPopupExpanded,
            onDismissRequest = { langSettingsPopupExpanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (source in accountViewModel.dontTranslateFrom()) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }

                        Spacer(modifier = Modifier.size(10.dp))

                        Text(
                            stringRes(
                                R.string.translations_never_translate_from_lang,
                                Locale(source).displayName,
                            ),
                        )
                    }
                },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.account.toggleDontTranslateFrom(source)
                        langSettingsPopupExpanded = false
                    }
                },
            )
            HorizontalDivider(thickness = DividerThickness)
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (accountViewModel.account.settings.preferenceBetween(source, target) == source) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }

                        Spacer(modifier = Modifier.size(10.dp))

                        Text(
                            stringRes(
                                R.string.translations_show_in_lang_first,
                                Locale(source).displayName,
                            ),
                        )
                    }
                },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.account.prefer(source, target, source)
                        langSettingsPopupExpanded = false
                    }
                },
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (accountViewModel.account.settings.syncedSettings.languages
                                .preferenceBetween(source, target) == target
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }

                        Spacer(modifier = Modifier.size(10.dp))

                        Text(
                            stringRes(
                                R.string.translations_show_in_lang_first,
                                Locale(target).displayName,
                            ),
                        )
                    }
                },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.account.prefer(source, target, target)
                        langSettingsPopupExpanded = false
                    }
                },
            )
            HorizontalDivider(thickness = DividerThickness)

            val languageList = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
            for (i in 0 until languageList.size()) {
                languageList.get(i)?.let { lang ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (accountViewModel.account.settings.translateToContains(lang)) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(24.dp))
                                }

                                Spacer(modifier = Modifier.size(10.dp))

                                Text(
                                    stringRes(
                                        R.string.translations_always_translate_to_lang,
                                        lang.displayName,
                                    ),
                                )
                            }
                        },
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                accountViewModel.account.updateTranslateTo(lang)
                                langSettingsPopupExpanded = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun translateAndWatchLanguageChanges(
    content: String,
    id: String,
    accountViewModel: AccountViewModel,
): MutableState<TranslationConfig> {
    var translatedTextState = remember(id) { mutableStateOf(TranslationsCache.get(content)) }

    TranslateAndWatchLanguageChanges(
        content,
        accountViewModel,
    ) { result ->
        if (
            !translatedTextState.value.result.equals(result.result, true) ||
            translatedTextState.value.sourceLang != result.sourceLang ||
            translatedTextState.value.targetLang != result.targetLang
        ) {
            TranslationsCache.set(content, result)
            translatedTextState.value = result
        }
    }

    return translatedTextState
}

@Composable
fun TranslateAndWatchLanguageChanges(
    content: String,
    accountViewModel: AccountViewModel,
    onTranslated: (TranslationConfig) -> Unit,
) {
    LaunchedEffect(Unit) {
        // This takes some time. Launches as a Composition scope to make sure this gets cancel if this
        // item gets out of view.
        withContext(Dispatchers.IO) {
            LanguageTranslatorService
                .autoTranslate(
                    content,
                    accountViewModel.dontTranslateFrom(),
                    accountViewModel.translateTo(),
                ).addOnCompleteListener { task ->
                    if (task.isSuccessful && !content.equals(task.result.result, true)) {
                        if (task.result.sourceLang != null && task.result.targetLang != null) {
                            val preference =
                                accountViewModel.account.settings.preferenceBetween(
                                    task.result.sourceLang!!,
                                    task.result.targetLang!!,
                                )
                            val newConfig =
                                TranslationConfig(
                                    result = task.result.result,
                                    sourceLang = task.result.sourceLang,
                                    targetLang = task.result.targetLang,
                                    showOriginal = preference == task.result.sourceLang,
                                )

                            onTranslated(newConfig)
                        }
                    }
                }
        }
    }
}
