package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun ZapRaiserRequest(
    titleText: String? = null,
    newPostViewModel: NewPostViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.lightning),
                null,
                modifier = Size20Modifier,
                tint = Color.Unspecified
            )

            Text(
                text = titleText ?: stringResource(R.string.zapraiser),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Divider()

        Text(
            text = stringResource(R.string.zapraiser_explainer),
            color = MaterialTheme.colors.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        OutlinedTextField(
            label = { Text(text = stringResource(R.string.zapraiser_target_amount_in_sats)) },
            modifier = Modifier.fillMaxWidth(),
            value = if (newPostViewModel.zapRaiserAmount != null) {
                newPostViewModel.zapRaiserAmount.toString()
            } else {
                ""
            },
            onValueChange = {
                runCatching {
                    if (it.isEmpty()) {
                        newPostViewModel.zapRaiserAmount = null
                    } else {
                        newPostViewModel.zapRaiserAmount = it.toLongOrNull()
                    }
                }
            },
            placeholder = {
                Text(
                    text = "1000",
                    color = MaterialTheme.colors.placeholderText
                )
            },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true
        )
    }
}
