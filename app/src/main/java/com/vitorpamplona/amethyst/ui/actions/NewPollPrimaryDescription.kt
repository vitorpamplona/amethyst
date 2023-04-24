package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
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

    val colorInValid = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = MaterialTheme.colors.error,
        unfocusedBorderColor = Color.Red
    )
    val colorValid = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = MaterialTheme.colors.primary,
        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    )

    OutlinedTextField(
        value = pollViewModel.message,
        onValueChange = {
            pollViewModel.updateMessage(it)
        },
        label = {
            Text(
                text = stringResource(R.string.poll_primary_description),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
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
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        },
        colors = if (isInputValid) colorValid else colorInValid,
        visualTransformation = UrlUserTagTransformation(MaterialTheme.colors.primary),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
    )
}
