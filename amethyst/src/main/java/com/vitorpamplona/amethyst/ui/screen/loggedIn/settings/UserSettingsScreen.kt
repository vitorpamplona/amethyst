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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy10dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import java.util.Locale as JavaLocale

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun UserSettingsScreenPreview() {
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()
    ThemeComparisonRow {
        UserSettingsScreen(accountViewModel, nav)
    }
}

@Composable
fun UserSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.user_preferences), nav)
        },
    ) {
        Column(Modifier.padding(it)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = Size10dp, start = Size20dp, end = Size20dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = SpacedBy10dp,
            ) {
                TranslateToSetting(accountViewModel)
                HorizontalDivider(thickness = DividerThickness)
                DontTranslateFromSetting(accountViewModel)
                HorizontalDivider(thickness = DividerThickness)
                LanguagePreferencesSetting(accountViewModel)
            }
        }
    }
}

private fun getAllLanguagesSorted(): List<JavaLocale> {
    val seen = mutableSetOf<String>()
    return JavaLocale
        .getAvailableLocales()
        .filter { it.language.isNotBlank() && it.country.isBlank() && seen.add(it.language) }
        .sortedBy { it.displayName.lowercase() }
}

@Composable
private fun SearchableLanguageList(
    languages: List<JavaLocale>,
    onSelect: (JavaLocale) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered =
        remember(searchQuery, languages) {
            if (searchQuery.isBlank()) {
                languages
            } else {
                languages.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                        it.language.contains(searchQuery, ignoreCase = true)
                }
            }
        }

    Column(modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringRes(R.string.search_languages)) },
            leadingIcon = {
                Icon(
                    symbol = MaterialSymbols.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(filtered, key = { it.language }) { locale ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(locale) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = locale.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = locale.language,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun TranslateToSetting(accountViewModel: AccountViewModel) {
    val currentTranslateTo by accountViewModel.account.settings.syncedSettings.languages.translateTo
        .collectAsStateWithLifecycle()
    val allLanguages = remember { getAllLanguagesSorted() }
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(
            name = R.string.translate_to,
            description = R.string.translate_to_description,
        ) {
            OutlinedCard(
                modifier = Modifier.clickable { showPicker = !showPicker },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = JavaLocale.forLanguageTag(currentTranslateTo).displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (showPicker) {
            Spacer(modifier = Modifier.height(8.dp))

            SearchableLanguageList(
                languages = allLanguages,
                onSelect = { locale ->
                    accountViewModel.updateTranslateTo(locale.language)
                    showPicker = false
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DontTranslateFromSetting(accountViewModel: AccountViewModel) {
    val selectedLanguages by accountViewModel.account.settings.syncedSettings.languages.dontTranslateFrom
        .collectAsStateWithLifecycle()
    var showAddPicker by remember { mutableStateOf(false) }
    val allLanguages = remember { getAllLanguagesSorted() }

    val availableToAdd =
        remember(selectedLanguages, allLanguages) {
            allLanguages.filter { it.language !in selectedLanguages }
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(
            name = R.string.dont_translate_from,
            description = R.string.dont_translate_from_description,
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            selectedLanguages.forEach { languageCode ->
                InputChip(
                    selected = true,
                    onClick = { accountViewModel.removeDontTranslateFrom(languageCode) },
                    label = { Text(JavaLocale.forLanguageTag(languageCode).displayName) },
                    trailingIcon = {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = stringRes(R.string.remove_language, languageCode),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                )
            }

            InputChip(
                selected = false,
                onClick = { showAddPicker = !showAddPicker },
                label = { Text(stringRes(R.string.add_language)) },
                leadingIcon = {
                    Icon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = null,
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
            )
        }

        if (showAddPicker) {
            Spacer(modifier = Modifier.height(8.dp))

            SearchableLanguageList(
                languages = availableToAdd,
                onSelect = { locale ->
                    accountViewModel.addDontTranslateFrom(locale.language)
                    showAddPicker = false
                },
            )
        }
    }
}

@Composable
fun LanguagePreferencesSetting(accountViewModel: AccountViewModel) {
    val languagePreferences by
        accountViewModel.account.settings.syncedSettings.languages.languagePreferences
            .collectAsStateWithLifecycle()
    var showAddPair by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsRow(
            name = R.string.language_preferences,
            description = R.string.language_preferences_description,
        )

        if (languagePreferences.isEmpty() && !showAddPair) {
            Text(
                text = stringRes(R.string.no_language_preferences),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        languagePreferences.forEach { (key, preference) ->
            val parts = key.split(",")
            if (parts.size == 2) {
                LanguagePreferenceCard(
                    source = parts[0],
                    target = parts[1],
                    preference = preference,
                    accountViewModel = accountViewModel,
                )
            }
        }

        if (!showAddPair) {
            TextButton(
                onClick = { showAddPair = !showAddPair },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    symbol = MaterialSymbols.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringRes(R.string.add_language_pair))
            }
        } else {
            AddLanguagePairCard(
                accountViewModel = accountViewModel,
                onDismiss = { showAddPair = false },
            )
        }
    }
}

@Composable
private fun LanguagePreferenceCard(
    source: String,
    target: String,
    preference: String,
    accountViewModel: AccountViewModel,
) {
    val sourceName = JavaLocale.forLanguageTag(source).displayName
    val targetName = JavaLocale.forLanguageTag(target).displayName

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    symbol = MaterialSymbols.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringRes(R.string.language_preference_pair, sourceName, targetName),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        accountViewModel.prefer(source, target, preference)
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Delete,
                        contentDescription = stringRes(R.string.delete_preference),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { accountViewModel.prefer(source, target, source) }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = preference == source,
                    onClick = { accountViewModel.prefer(source, target, source) },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringRes(R.string.show_first, sourceName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { accountViewModel.prefer(source, target, target) }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = preference == target,
                    onClick = { accountViewModel.prefer(source, target, target) },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringRes(R.string.show_first, targetName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AddLanguagePairCard(
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val allLanguages = remember { getAllLanguagesSorted() }
    var selectedSource by remember { mutableStateOf<JavaLocale?>(null) }
    var selectedTarget by remember { mutableStateOf<JavaLocale?>(null) }
    var selectedPreference by remember { mutableStateOf<JavaLocale?>(null) }
    var pickingSource by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringRes(R.string.add_language_pair),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedCard(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                pickingSource = true
                                pickingTarget = false
                            },
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = stringRes(R.string.source_language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedSource?.displayName ?: stringRes(R.string.quick_action_select),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedSource != null) FontWeight.Medium else FontWeight.Normal,
                            color =
                                if (selectedSource != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }

                Icon(
                    symbol = MaterialSymbols.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )

                OutlinedCard(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                pickingTarget = true
                                pickingSource = false
                            },
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = stringRes(R.string.target_language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedTarget?.displayName ?: stringRes(R.string.quick_action_select),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedTarget != null) FontWeight.Medium else FontWeight.Normal,
                            color =
                                if (selectedTarget != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }

            if (pickingSource) {
                Spacer(modifier = Modifier.height(8.dp))
                SearchableLanguageList(
                    languages = allLanguages,
                    onSelect = { locale ->
                        selectedSource = locale
                        pickingSource = false
                        if (selectedTarget == null) {
                            pickingTarget = true
                        }
                    },
                )
            }

            if (pickingTarget) {
                Spacer(modifier = Modifier.height(8.dp))
                SearchableLanguageList(
                    languages = allLanguages.filter { it.language != selectedSource?.language },
                    onSelect = { locale ->
                        selectedTarget = locale
                        pickingTarget = false
                    },
                )
            }

            val selectedSource = selectedSource
            val selectedTarget = selectedTarget
            if (selectedSource != null && selectedTarget != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                accountViewModel.prefer(selectedSource.language, selectedTarget.language, selectedSource.language)
                                onDismiss()
                            }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedPreference == selectedSource,
                        onClick = {
                            accountViewModel.prefer(selectedSource.language, selectedTarget.language, selectedSource.language)
                            onDismiss()
                        },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringRes(R.string.show_first, selectedSource.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                accountViewModel.prefer(selectedSource.language, selectedTarget.language, selectedTarget.language)
                                onDismiss()
                            }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedPreference == selectedTarget,
                        onClick = {
                            accountViewModel.prefer(selectedSource.language, selectedTarget.language, selectedTarget.language)
                            onDismiss()
                        },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringRes(R.string.show_first, selectedTarget.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
