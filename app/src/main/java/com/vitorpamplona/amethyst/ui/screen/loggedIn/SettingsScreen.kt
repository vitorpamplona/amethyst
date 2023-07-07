package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.navigation.IconRow
import com.vitorpamplona.amethyst.ui.navigation.IconRowRelays
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.RelayPoolViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    var languageIndex = languageEntries.values.toTypedArray().indexOf(Locale.current.toLanguageTag())
    if (languageIndex == -1) languageIndex = languageEntries.values.toTypedArray().indexOf(Locale.current.language)
    if (languageIndex == -1) languageIndex = languageEntries.values.toTypedArray().indexOf("en")
    return languageIndex
}

@Composable
fun SettingsScreen(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val selectedItens = arrayOf("Always", "Wifi-only", "Never")
    val settings = accountViewModel.account.settings
    val index = if (settings.automaticallyShowImages == null) { 0 } else {
        if (settings.automaticallyShowImages == true) 1 else 2
    }
    val videoIndex = if (settings.automaticallyStartPlayback == null) { 0 } else {
        if (settings.automaticallyShowImages == true) 1 else 2
    }
    val selectedItem = remember {
        mutableStateOf(selectedItens[index])
    }
    val selectedVideoItem = remember {
        mutableStateOf(selectedItens[videoIndex])
    }
    val linkIndex = if (settings.automaticallyShowUrlPreview == null) { 0 } else {
        if (settings.automaticallyShowUrlPreview == true) 1 else 2
    }
    val selectedLinkItem = remember {
        mutableStateOf(selectedItens[linkIndex])
    }

    val themeItens = arrayOf("System", "Light", "Dark")
    val themeIndex = themeItens.indexOf(accountViewModel.currentTheme())
    val selectedTheme = remember {
        mutableStateOf(themeItens[themeIndex])
    }

    val context = LocalContext.current

    val languageEntries = context.getLangPreferenceDropdownEntries()
    val languageList = languageEntries.keys.toTypedArray()
    val languageIndex = getLanguageIndex(languageEntries)
    val selectedLanguage = remember {
        mutableStateOf(languageList[languageIndex])
    }
    val relayViewModel: RelayPoolViewModel = viewModel { RelayPoolViewModel() }
    var wantsToEditRelays by remember {
        mutableStateOf(false)
    }
    var backupDialogOpen by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(accountViewModel.account.proxy != null) }
    var disconnectTorDialog by remember { mutableStateOf(false) }
    var conectOrbotDialogOpen by remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf(accountViewModel.account.proxyPort.toString()) }

    Column(
        StdPadding
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Section("Account preferences")

        IconRowRelays(
            relayViewModel = relayViewModel,
            onClick = {
                wantsToEditRelays = true
            }
        )

        IconRow(
            title = stringResource(R.string.security_filters),
            icon = Route.BlockedUsers.icon,
            tint = MaterialTheme.colors.onBackground,
            onClick = {
                nav(Route.BlockedUsers.route)
            }
        )

        IconRow(
            title = stringResource(R.string.backup_keys),
            icon = R.drawable.ic_key,
            tint = MaterialTheme.colors.onBackground,
            onClick = {
                backupDialogOpen = true
            }
        )

        val textTorProxy = if (checked) stringResource(R.string.disconnect_from_your_orbot_setup) else stringResource(R.string.connect_via_tor_short)
        IconRow(
            title = textTorProxy,
            icon = R.drawable.ic_tor,
            tint = MaterialTheme.colors.onBackground,
            onLongClick = {
                conectOrbotDialogOpen = true
            },
            onClick = {
                if (checked) {
                    disconnectTorDialog = true
                } else {
                    conectOrbotDialogOpen = true
                }
            }
        )

        Section("Application preferences")

        DropDownSettings(
            selectedItem = selectedLanguage,
            listItems = languageList,
            title = "Language"
        )

        DropDownSettings(
            selectedItem = selectedTheme,
            listItems = themeItens,
            title = "Theme"
        )

        DropDownSettings(
            selectedItem = selectedItem,
            listItems = selectedItens,
            title = "Automatically load images/gifs"
        )

        DropDownSettings(
            selectedItem = selectedVideoItem,
            listItems = selectedItens,
            title = "Automatically play videos"
        )

        DropDownSettings(
            selectedItem = selectedLinkItem,
            listItems = selectedItens,
            title = "Automatically show url preview"
        )

        Row(
            Modifier.fillMaxWidth(),
            Arrangement.Center
        ) {
            Button(
                onClick = {
                    val automaticallyShowImages = when (selectedItens.indexOf(selectedItem.value)) {
                        1 -> true
                        2 -> false
                        else -> null
                    }
                    val automaticallyStartPlayback = when (selectedItens.indexOf(selectedVideoItem.value)) {
                        1 -> true
                        2 -> false
                        else -> null
                    }
                    val automaticallyShowUrlPreview = when (selectedItens.indexOf(selectedLinkItem.value)) {
                        1 -> true
                        2 -> false
                        else -> null
                    }
                    accountViewModel.changeTheme(selectedTheme.value)

                    scope.launch(Dispatchers.IO) {
                        accountViewModel.updateGlobalSettings(automaticallyShowImages, automaticallyStartPlayback, automaticallyShowUrlPreview)
                        LocalPreferences.saveToEncryptedStorage(accountViewModel.account)
                        LocalPreferences.updateTheme(selectedTheme.value)
                        ServiceManager.pause()
                        ServiceManager.start(context)
                        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageEntries[selectedLanguage.value])
                        AppCompatDelegate.setApplicationLocales(appLocale)
                    }
                }
            ) {
                Text(text = "Save")
            }
        }
    }
    if (wantsToEditRelays) {
        NewRelayListView({ wantsToEditRelays = false }, accountViewModel, nav = nav)
    }
    if (backupDialogOpen) {
        AccountBackupDialog(accountViewModel.account, onClose = { backupDialogOpen = false })
    }
    if (conectOrbotDialogOpen) {
        ConnectOrbotDialog(
            onClose = { conectOrbotDialogOpen = false },
            onPost = {
                conectOrbotDialogOpen = false
                disconnectTorDialog = false
                checked = true
                enableTor(accountViewModel.account, true, proxyPort, context, scope)
            },
            proxyPort
        )
    }

    if (disconnectTorDialog) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.do_you_really_want_to_disable_tor_title))
            },
            text = {
                Text(text = stringResource(R.string.do_you_really_want_to_disable_tor_text))
            },
            onDismissRequest = {
                disconnectTorDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        disconnectTorDialog = false
                        checked = false
                        enableTor(accountViewModel.account, false, proxyPort, context, scope)
                    }
                ) {
                    Text(text = stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        disconnectTorDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.no))
                }
            }
        )
    }
}

private fun enableTor(
    account: Account,
    checked: Boolean,
    portNumber: MutableState<String>,
    context: Context,
    scope: CoroutineScope
) {
    account.proxyPort = portNumber.value.toInt()
    account.proxy = HttpClient.initProxy(checked, "127.0.0.1", account.proxyPort)
    scope.launch(Dispatchers.IO) {
        LocalPreferences.saveToEncryptedStorage(account)
        ServiceManager.pause()
        ServiceManager.start(context)
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
