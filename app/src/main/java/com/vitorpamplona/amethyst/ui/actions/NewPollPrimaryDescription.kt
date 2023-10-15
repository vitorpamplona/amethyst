package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewPollPrimaryDescription(pollViewModel: NewPostViewModel) {
    // initialize focus reference to be able to request focus programmatically
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var isInputValid = true
    if (pollViewModel.message.text.isEmpty()) {
        isInputValid = false
    }

    val colorInValid = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.error,
        unfocusedBorderColor = Color.Red
    )
    val colorValid = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.placeholderText
    )

    OutlinedTextField(
        value = pollViewModel.message,
        onValueChange = {
            pollViewModel.updateMessage(it)
        },
        label = {
            Text(
                text = stringResource(R.string.poll_primary_description),
                color = MaterialTheme.colorScheme.placeholderText
            )
        },
        keyboardOptions = KeyboardOptions.Default.copy(
            capitalization = KeyboardCapitalization.Sentences
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    keyboardController?.show()
                }
            },
        placeholder = {
            Text(
                text = stringResource(R.string.poll_primary_description),
                color = MaterialTheme.colorScheme.placeholderText
            )
        },
        colors = if (isInputValid) colorValid else colorInValid,
        visualTransformation = UrlUserTagTransformation(MaterialTheme.colorScheme.primary),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
    )
}
