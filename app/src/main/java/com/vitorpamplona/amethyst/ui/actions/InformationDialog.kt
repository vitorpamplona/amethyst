package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer

@Composable
fun InformationDialog(
    title: String,
    textContent: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            SelectionContainer {
                Text(textContent)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = buttonColors, contentPadding = PaddingValues(horizontal = Size16dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Done,
                        contentDescription = null
                    )
                    Spacer(StdHorzSpacer)
                    Text(stringResource(R.string.error_dialog_button_ok))
                }
            }
        }
    )
}
