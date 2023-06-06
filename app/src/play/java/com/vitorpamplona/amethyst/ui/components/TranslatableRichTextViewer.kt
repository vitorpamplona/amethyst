package com.vitorpamplona.amethyst.ui.components

import android.content.res.Resources
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.lang.ResultOrError
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TranslatableRichTextViewer(
    content: String,
    canPreview: Boolean,
    modifier: Modifier = Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column {
        var translatedTextState by remember {
            mutableStateOf(ResultOrError(content, null, null, null))
        }

        var showOriginal by remember { mutableStateOf(false) }

        TranslateAndWatchLanguageChanges(content, accountViewModel) { result, newShowOriginal ->
            if (translatedTextState.result != result.result ||
                translatedTextState.sourceLang != result.sourceLang ||
                translatedTextState.targetLang != result.targetLang
            ) {
                translatedTextState = result
            }

            if (showOriginal != newShowOriginal) {
                showOriginal = newShowOriginal
            }
        }

        val toBeViewed by remember(translatedTextState) {
            derivedStateOf {
                if (showOriginal) content else translatedTextState.result ?: content
            }
        }

        ExpandableRichTextViewer(
            toBeViewed,
            canPreview,
            modifier,
            tags,
            backgroundColor,
            accountViewModel,
            nav
        )

        if (translatedTextState.sourceLang != null &&
            translatedTextState.targetLang != null &&
            translatedTextState.sourceLang != translatedTextState.targetLang
        ) {
            TranslationMessage(
                translatedTextState.sourceLang!!,
                translatedTextState.targetLang!!,
                accountViewModel
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
    onChangeWhatToShow: (Boolean) -> Unit
) {
    var langSettingsPopupExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp)
    ) {
        val clickableTextStyle =
            SpanStyle(color = MaterialTheme.colors.primary.copy(alpha = 0.52f))

        val annotatedTranslationString = buildAnnotatedString {
            withStyle(clickableTextStyle) {
                pushStringAnnotation("langSettings", true.toString())
                append(stringResource(R.string.translations_auto))
            }

            append("-${stringResource(R.string.translations_translated_from)} ")

            withStyle(clickableTextStyle) {
                pushStringAnnotation("showOriginal", true.toString())
                append(Locale(source).displayName)
            }

            append(" ${stringResource(R.string.translations_to)} ")

            withStyle(clickableTextStyle) {
                pushStringAnnotation("showOriginal", false.toString())
                append(Locale(target).displayName)
            }
        }

        ClickableText(
            text = annotatedTranslationString,
            style = LocalTextStyle.current.copy(
                color = MaterialTheme.colors.onSurface.copy(
                    alpha = 0.32f
                )
            ),
            overflow = TextOverflow.Visible,
            maxLines = 3
        ) { spanOffset ->
            annotatedTranslationString.getStringAnnotations(spanOffset, spanOffset)
                .firstOrNull()
                ?.also { span ->
                    if (span.tag == "showOriginal") {
                        onChangeWhatToShow(span.item.toBoolean())
                    } else {
                        langSettingsPopupExpanded = !langSettingsPopupExpanded
                    }
                }
        }

        DropdownMenu(
            expanded = langSettingsPopupExpanded,
            onDismissRequest = { langSettingsPopupExpanded = false }
        ) {
            DropdownMenuItem(onClick = {
                scope.launch(Dispatchers.IO) {
                    accountViewModel.dontTranslateFrom(source)
                    langSettingsPopupExpanded = false
                }
            }) {
                if (source in accountViewModel.account.dontTranslateFrom) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.size(10.dp))

                Text(
                    stringResource(
                        R.string.translations_never_translate_from_lang,
                        Locale(source).displayName
                    )
                )
            }
            Divider()
            DropdownMenuItem(onClick = {
                scope.launch(Dispatchers.IO) {
                    accountViewModel.prefer(source, target, source)
                    langSettingsPopupExpanded = false
                }
            }) {
                if (accountViewModel.account.preferenceBetween(source, target) == source) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.size(10.dp))

                Text(
                    stringResource(
                        R.string.translations_show_in_lang_first,
                        Locale(source).displayName
                    )
                )
            }
            DropdownMenuItem(onClick = {
                scope.launch(Dispatchers.IO) {
                    accountViewModel.prefer(source, target, target)
                    langSettingsPopupExpanded = false
                }
            }) {
                if (accountViewModel.account.preferenceBetween(source, target) == target) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.size(10.dp))

                Text(
                    stringResource(
                        R.string.translations_show_in_lang_first,
                        Locale(target).displayName
                    )
                )
            }
            Divider()

            val languageList =
                ConfigurationCompat.getLocales(Resources.getSystem().configuration)
            for (i in 0 until languageList.size()) {
                languageList.get(i)?.let { lang ->
                    DropdownMenuItem(onClick = {
                        scope.launch(Dispatchers.IO) {
                            accountViewModel.translateTo(lang)
                            langSettingsPopupExpanded = false
                        }
                    }) {
                        if (lang.language in accountViewModel.account.translateTo) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }

                        Spacer(modifier = Modifier.size(10.dp))

                        Text(
                            stringResource(
                                R.string.translations_always_translate_to_lang,
                                lang.displayName
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TranslateAndWatchLanguageChanges(content: String, accountViewModel: AccountViewModel, onTranslated: (ResultOrError, Boolean) -> Unit) {
    val accountState by accountViewModel.accountLanguagesLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return

    val scope = rememberCoroutineScope()

    LaunchedEffect(accountState) {
        scope.launch(Dispatchers.IO) {
            LanguageTranslatorService.autoTranslate(
                content,
                account.dontTranslateFrom,
                account.translateTo
            ).addOnCompleteListener { task ->
                if (task.isSuccessful && content != task.result.result) {
                    if (task.result.sourceLang != null && task.result.targetLang != null) {
                        val preference = account.preferenceBetween(task.result.sourceLang!!, task.result.targetLang!!)
                        onTranslated(task.result, preference == task.result.sourceLang)
                    }
                }
            }
        }
    }
}
