package com.vitorpamplona.amethyst.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.theme.Size24dp

@Composable
fun SelectTextDialog(text: String, onDismiss: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
            screenHeight * 0.6f
        } else {
            screenHeight * 0.9f
        }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card {
            Column(
                modifier = Modifier.heightIn(Size24dp, maxHeight)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss
                    ) {
                        ArrowBackIcon()
                    }
                    Text(text = stringResource(R.string.select_text_dialog_top))
                }
                Divider()
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        SelectionContainer {
                            Text(text)
                        }
                    }
                }
            }
        }
    }
}
