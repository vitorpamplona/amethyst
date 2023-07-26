package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.parseConnectivityType
import com.vitorpamplona.amethyst.ui.screen.ThemeViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

fun Context.getLocaleListFromXml(): LocaleListCompat {
    val tagsList = mutableListOf<CharSequence>()
    try {
        val xpp: XmlPullParser = resources.getXml(R.xml.locales_config)
        while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xpp.eventType == XmlPullParser.START_TAG) {
                if (xpp.name == "locale") {
                    tagsList.add(xpp.getAttributeValue(0))
                }
            }
            xpp.next()
        }
    } catch (e: XmlPullParserException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return LocaleListCompat.forLanguageTags(tagsList.joinToString(","))
}

fun Context.getLangPreferenceDropdownEntries(): Map<String, String> {
    val localeList = getLocaleListFromXml()
    val map = mutableMapOf<String, String>()

    for (a in 0 until localeList.size()) {
        localeList[a].let {
            map.put(it!!.getDisplayName(it).replaceFirstChar { char -> char.uppercase() }, it.toLanguageTag())
        }
    }
    return map
}

fun getLanguageIndex(languageEntries: Map<String, String>): Int {
    val language = LocalPreferences.getPreferredLanguage()
    var languageIndex = -1
    if (language.isNotBlank()) {
        languageIndex = languageEntries.values.toTypedArray().indexOf(language)
    } else {
        languageIndex = languageEntries.values.toTypedArray().indexOf(Locale.current.toLanguageTag())
    }
    if (languageIndex == -1) languageIndex = languageEntries.values.toTypedArray().indexOf(Locale.current.language)
    if (languageIndex == -1) languageIndex = languageEntries.values.toTypedArray().indexOf("en")
    return languageIndex
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun SettingsScreen(
    accountViewModel: AccountViewModel,
    themeViewModel: ThemeViewModel
) {
    val scope = rememberCoroutineScope()
    val selectedItens = persistentListOf(
        stringResource(ConnectivityType.ALWAYS.reourceId),
        stringResource(ConnectivityType.WIFI_ONLY.reourceId),
        stringResource(ConnectivityType.NEVER.reourceId)
    )
    val settings = accountViewModel.account.settings
    val index = settings.automaticallyShowImages.screenCode
    val videoIndex = settings.automaticallyStartPlayback.screenCode
    val linkIndex = settings.automaticallyShowUrlPreview.screenCode

    val themeItens = persistentListOf(
        stringResource(R.string.system),
        stringResource(R.string.light),
        stringResource(R.string.dark)
    )
    val themeIndex = themeViewModel.theme.value ?: 0

    val context = LocalContext.current

    val languageEntries = context.getLangPreferenceDropdownEntries()
    val languageList = languageEntries.keys.toImmutableList()
    val languageIndex = getLanguageIndex(languageEntries)

    Column(
        StdPadding
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Section(stringResource(R.string.application_preferences))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextSpinner(
                label = stringResource(R.string.language),
                placeholder = languageList[languageIndex],
                options = languageList,
                onSelect = {
                    GlobalScope.launch(Dispatchers.Main) {
                        val job = scope.launch(Dispatchers.IO) {
                            val locale = languageEntries[languageList[it]]
                            accountViewModel.account.settings.preferredLanguage = locale
                            LocalPreferences.saveToEncryptedStorage(accountViewModel.account)
                        }
                        job.join()
                        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageEntries[languageList[it]])
                        AppCompatDelegate.setApplicationLocales(appLocale)
                    }
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                    .weight(1f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextSpinner(
                label = stringResource(R.string.theme),
                placeholder = themeItens[themeIndex],
                options = themeItens,
                onSelect = {
                    themeViewModel.onChange(it)
                    scope.launch(Dispatchers.IO) {
                        LocalPreferences.updateTheme(it)
                    }
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                    .weight(1f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextSpinner(
                label = stringResource(R.string.automatically_load_images_gifs),
                placeholder = selectedItens[index],
                options = selectedItens,
                onSelect = {
                    val automaticallyShowImages = parseConnectivityType(it)

                    scope.launch(Dispatchers.IO) {
                        accountViewModel.updateAutomaticallyShowImages(automaticallyShowImages)
                        LocalPreferences.saveToEncryptedStorage(accountViewModel.account)
                    }
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                    .weight(1f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextSpinner(
                label = stringResource(R.string.automatically_play_videos),
                placeholder = selectedItens[videoIndex],
                options = selectedItens,
                onSelect = {
                    val automaticallyStartPlayback = parseConnectivityType(it)

                    scope.launch(Dispatchers.IO) {
                        accountViewModel.updateAutomaticallyStartPlayback(automaticallyStartPlayback)
                        LocalPreferences.saveToEncryptedStorage(accountViewModel.account)
                    }
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                    .weight(1f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextSpinner(
                label = stringResource(R.string.automatically_show_url_preview),
                placeholder = selectedItens[linkIndex],
                options = selectedItens,
                onSelect = {
                    val automaticallyShowUrlPreview = parseConnectivityType(it)

                    scope.launch(Dispatchers.IO) {
                        accountViewModel.updateAutomaticallyShowUrlPreview(automaticallyShowUrlPreview)
                        LocalPreferences.saveToEncryptedStorage(accountViewModel.account)
                    }
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                    .weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DropDownSettings(
    selectedItem: MutableState<String>,
    listItems: Array<String>,
    title: String
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    ExposedDropdownMenuBox(
        modifier = Modifier.padding(8.dp),
        expanded = expanded,
        onExpandedChange = {
            expanded = !expanded
        }
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = selectedItem.value,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = title) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listItems.forEach { selectedOption ->
                DropdownMenuItem(
                    onClick = {
                        selectedItem.value = selectedOption
                        expanded = false
                    }
                ) {
                    Text(text = selectedOption)
                }
            }
        }
    }
}

@Composable
fun Section(text: String) {
    Spacer(modifier = DoubleVertSpacer)
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    )
    Spacer(modifier = DoubleVertSpacer)
}
