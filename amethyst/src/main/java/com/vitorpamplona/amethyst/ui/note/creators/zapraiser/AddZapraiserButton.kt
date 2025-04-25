/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.note.creators.zapraiser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange

@Composable
fun AddZapraiserButton(
    isLnInvoiceActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        Box(
            Modifier
                .height(20.dp)
                .width(25.dp),
        ) {
            if (!isLnInvoiceActive) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ShowChart,
                    null,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .align(Alignment.TopStart),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringRes(R.string.add_zapraiser),
                    modifier =
                        Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ShowChart,
                    null,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .align(Alignment.TopStart),
                    tint = BitcoinOrange,
                )
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringRes(R.string.cancel_zapraiser),
                    modifier =
                        Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd),
                    tint = BitcoinOrange,
                )
            }
        }
    }
}
