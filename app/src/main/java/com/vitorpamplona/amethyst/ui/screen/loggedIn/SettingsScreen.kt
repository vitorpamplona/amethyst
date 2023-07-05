package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    Column(
        StdPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Section("Account preferences")

        Section("Application preferences")

        Text(
            "Media",
            fontWeight = FontWeight.Bold
        )

        DropDownSettings(
            selectedItem = selectedItem,
            listItems = selectedItens,
            title = "Automatically load images/gifs"
        )

        Spacer(modifier = DoubleVertSpacer)

        DropDownSettings(
            selectedItem = selectedVideoItem,
            listItems = selectedItens,
            title = "Automatically play videos"
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
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.updateGlobalSettings(automaticallyShowImages, automaticallyStartPlayback)
                        LocalPreferences.saveToEncryptedStorage(accountViewModel.account)
                        ServiceManager.pause()
                        ServiceManager.start(context)
                    }
                }
            ) {
                Text(text = "Save")
            }
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
