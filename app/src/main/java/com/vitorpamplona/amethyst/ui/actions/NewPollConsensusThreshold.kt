package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun NewPollConsensusThreshold(pollViewModel: NewPostViewModel) {
    var text by rememberSaveable { mutableStateOf("") }

    pollViewModel.isValidConsensusThreshold.value = true
    if (text.isNotEmpty()) {
        try {
            val int = text.toInt()
            if (int < 0 || int > 100) {
                pollViewModel.isValidConsensusThreshold.value = false
            } else { pollViewModel.consensusThreshold = int }
        } catch (e: Exception) { pollViewModel.isValidConsensusThreshold.value = false }
    }

    val colorInValid = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.error,
        unfocusedBorderColor = Color.Red
    )
    val colorValid = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.placeholderText
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
            colors = if (pollViewModel.isValidConsensusThreshold.value) colorValid else colorInValid,
            label = {
                Text(
                    text = stringResource(R.string.poll_consensus_threshold),
                    color = MaterialTheme.colorScheme.placeholderText
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.poll_consensus_threshold_percent),
                    color = MaterialTheme.colorScheme.placeholderText
                )
            }
        )
    }
}

@Preview
@Composable
fun NewPollConsensusThresholdPreview() {
    NewPollConsensusThreshold(NewPostViewModel())
}
