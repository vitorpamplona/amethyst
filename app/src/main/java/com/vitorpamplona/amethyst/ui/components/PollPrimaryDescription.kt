package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
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
import com.vitorpamplona.amethyst.ui.actions.NewPollViewModel
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PollPrimaryDescription(pollViewModel: NewPollViewModel) {
    // initialize focus reference to be able to request focus programmatically
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = pollViewModel.message,
        onValueChange = {
            pollViewModel.updateMessage(it)
        },
        keyboardOptions = KeyboardOptions.Default.copy(
            capitalization = KeyboardCapitalization.Sentences
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    keyboardController?.show()
                }
            },
        placeholder = {
            Text(
                text = stringResource(R.string.primary_poll_description),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        },
        colors = TextFieldDefaults
            .outlinedTextFieldColors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            ),
        visualTransformation = UrlUserTagTransformation(MaterialTheme.colors.primary),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
    )
}
