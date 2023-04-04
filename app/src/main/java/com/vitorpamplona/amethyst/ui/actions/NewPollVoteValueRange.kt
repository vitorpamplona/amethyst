package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R

@Composable
fun NewPollVoteValueRange(pollViewModel: NewPostViewModel) {
    var textMax by rememberSaveable { mutableStateOf("") }
    var textMin by rememberSaveable { mutableStateOf("") }

    // check for zapMax amounts < 1
    pollViewModel.isValidvalueMaximum.value = true
    if (textMax.isNotEmpty()) {
        try {
            val int = textMax.toInt()
            if (int < 1) {
                pollViewModel.isValidvalueMaximum.value = false
            } else { pollViewModel.valueMaximum = int }
        } catch (e: Exception) { pollViewModel.isValidvalueMaximum.value = false }
    }

    // check for minZap amounts < 1
    pollViewModel.isValidvalueMinimum.value = true
    if (textMin.isNotEmpty()) {
        try {
            val int = textMin.toInt()
            if (int < 1) {
                pollViewModel.isValidvalueMinimum.value = false
            } else { pollViewModel.valueMinimum = int }
        } catch (e: Exception) { pollViewModel.isValidvalueMinimum.value = false }
    }

    // check for zapMin > zapMax
    if (textMin.isNotEmpty() && textMax.isNotEmpty()) {
        try {
            val intMin = textMin.toInt()
            val intMax = textMax.toInt()

            if (intMin > intMax) {
                pollViewModel.isValidvalueMinimum.value = false
                pollViewModel.isValidvalueMaximum.value = false
            }
        } catch (e: Exception) {
            pollViewModel.isValidvalueMinimum.value = false
            pollViewModel.isValidvalueMaximum.value = false
        }
    }

    val colorInValid = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = MaterialTheme.colors.error,
        unfocusedBorderColor = Color.Red
    )
    val colorValid = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = MaterialTheme.colors.primary,
        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = textMin,
            onValueChange = { textMin = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
            colors = if (pollViewModel.isValidvalueMinimum.value) colorValid else colorInValid,
            label = {
                Text(
                    text = stringResource(R.string.poll_zap_value_min),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.sats),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }
        )
        OutlinedTextField(
            value = textMax,
            onValueChange = { textMax = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
            colors = if (pollViewModel.isValidvalueMaximum.value) colorValid else colorInValid,
            label = {
                Text(
                    text = stringResource(R.string.poll_zap_value_max),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.sats),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }
        )
    }
}

@Preview
@Composable
fun NewPollVoteValueRangePreview() {
    NewPollVoteValueRange(NewPostViewModel())
}
