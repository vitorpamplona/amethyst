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

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
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
            TopBarWithBackButton(stringRes(id = R.string.user_preferences), nav::popBack)
        },
    ) {
        Column(Modifier.padding(it)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = Size10dp, start = Size20dp, end = Size20dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DontTranslateFromSetting(accountViewModel)
                TranslateToSetting(accountViewModel)
                LanguagePreferencesSetting(accountViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DontTranslateFromSetting(accountViewModel: AccountViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLanguages = accountViewModel.dontTranslateFromFilteredBySpokenLanguages().toMutableSet()

    Column {
        SettingsRow(
            name = R.string.dont_translate_from,
            description = R.string.dont_translate_from_description,
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = stringRes(R.string.quick_action_select),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    selectedLanguages.forEach { languageCode ->
                        DropdownMenuItem(
                            text = { Text(text = JavaLocale.forLanguageTag(languageCode).displayName) },
                            onClick = {
                                accountViewModel.toggleDontTranslateFrom(languageCode)
                                selectedLanguages.remove(languageCode)
                                expanded = false
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringRes(R.string.remove_language, languageCode),
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateToSetting(accountViewModel: AccountViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val currentTranslateTo = accountViewModel.translateTo()
    val languageList = ConfigurationCompat.getLocales(Resources.getSystem().configuration)

    Column {
        SettingsRow(
            name = R.string.translate_to,
            description = R.string.translate_to_description,
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = JavaLocale(currentTranslateTo).displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
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
                                        Text(text = lang.displayName)
                                    }
                                },
                                onClick = {
                                    accountViewModel.updateTranslateTo(lang)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePreferencesSetting(accountViewModel: AccountViewModel) {
    val languagePreferences = accountViewModel.account.settings.syncedSettings.languages.languagePreferences

    if (languagePreferences.isEmpty()) return

    Column {
        SettingsRow(
            name = R.string.language_preferences,
            description = R.string.language_preferences_description,
        ) {}

        languagePreferences.forEach { (key, preference) ->
            val parts = key.split(",")
            if (parts.size == 2) {
                LanguagePreferenceItem(
                    source = parts[0],
                    target = parts[1],
                    preference = preference,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePreferenceItem(
    source: String,
    target: String,
    preference: String,
    accountViewModel: AccountViewModel,
) {
    val sourceName = JavaLocale(source).displayName
    val targetName = JavaLocale(target).displayName
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = stringRes(R.string.language_preference_pair, sourceName, targetName),
            onValueChange = {},
            readOnly = true,
            label = {
                Text(
                    if (preference == source) {
                        stringRes(R.string.translations_show_in_lang_first, sourceName)
                    } else {
                        stringRes(R.string.translations_show_in_lang_first, targetName)
                    },
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (preference == source) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(stringRes(R.string.translations_show_in_lang_first, sourceName))
                    }
                },
                onClick = {
                    accountViewModel.prefer(source, target, source)
                    expanded = false
                },
            )
            HorizontalDivider(thickness = DividerThickness)
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (preference == target) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(stringRes(R.string.translations_show_in_lang_first, targetName))
                    }
                },
                onClick = {
                    accountViewModel.prefer(source, target, target)
                    expanded = false
                },
            )
        }
    }
}
