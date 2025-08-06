/**
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.note.creators.zapsplits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import java.util.Locale
import kotlin.math.round

@Composable
fun ForwardZapTo(
    postViewModel: IZapField,
    accountViewModel: AccountViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
        ) {
            ZapSplitIcon()

            Text(
                text = stringRes(R.string.zap_split_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
            )

            OutlinedButton(onClick = { postViewModel.updateZapFromText() }) {
                Text(text = stringRes(R.string.load_from_text))
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringRes(R.string.zap_split_explainer),
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        postViewModel.forwardZapTo.value.items.forEachIndexed { index, splitItem ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = Size10dp),
            ) {
                BaseUserPicture(splitItem.key, Size55dp, accountViewModel = accountViewModel)

                Spacer(modifier = DoubleHorzSpacer)

                Column(modifier = Modifier.weight(1f)) {
                    UsernameDisplay(splitItem.key, accountViewModel = accountViewModel)
                    Text(
                        text = String.format(Locale.getDefault(), "%.0f%%", splitItem.percentage * 100),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }

                Spacer(modifier = DoubleHorzSpacer)

                Slider(
                    value = splitItem.percentage,
                    onValueChange = { sliderValue ->
                        val rounded = (round(sliderValue * 100)) / 100.0f
                        postViewModel.updateZapPercentage(index, rounded)
                    },
                    modifier = Modifier.weight(1.5f),
                )
            }
        }

        OutlinedTextField(
            value = postViewModel.forwardZapToEditting.value,
            onValueChange = { postViewModel.updateZapForwardTo(it) },
            label = { Text(text = stringRes(R.string.zap_split_search_and_add_user)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringRes(R.string.zap_split_search_and_add_user_placeholder),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            singleLine = true,
            visualTransformation =
                UrlUserTagTransformation(
                    MaterialTheme.colorScheme.primary,
                ),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        )
    }
}
