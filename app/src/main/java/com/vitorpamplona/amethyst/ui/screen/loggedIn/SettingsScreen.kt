package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding

@Composable
fun SettingsScreen(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val listItems = arrayOf("Always", "Wifi-only", "Never")
    val selectedItem = remember {
        mutableStateOf(listItems[0])
    }

    val context = LocalContext.current
    Column(
        StdPadding
    ) {
        Section("Account preferences")

        Section("Application preferences")
        Text(
            "Media",
            fontWeight = FontWeight.Bold
        )
        DropDownSettings(
            selectedItem = selectedItem,
            listItems = listItems
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DropDownSettings(
    selectedItem: MutableState<String>,
    listItems: Array<String>
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
            value = selectedItem.value,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = "Automatically load images/gifs") },
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
                DropdownMenuItem(onClick = {
                    selectedItem.value = selectedOption
                    expanded = false
                }) {
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
