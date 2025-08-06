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
package com.vitorpamplona.amethyst.ui.navigation.topbars

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HalfHorzPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTopBar(
    postRes: Int,
    titleRes: Int? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
) {
    ShorterTopAppBar(
        title = {
            if (titleRes != null) {
                Text(
                    text = stringRes(titleRes),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            CloseButton(
                modifier = HalfHorzPadding,
                onPress = onCancel,
            )
        },
        actions = {
            Button(
                modifier = HalfHorzPadding,
                enabled = isActive(),
                onClick = onPost,
            ) {
                Text(text = stringRes(postRes))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostingTopBar(
    titleRes: Int? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
) = ActionTopBar(
    titleRes = titleRes,
    postRes = R.string.post,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingTopBar(
    titleRes: Int? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
) = ActionTopBar(
    titleRes = titleRes,
    postRes = R.string.save,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatingTopBar(
    titleRes: Int? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
) = ActionTopBar(
    titleRes = titleRes,
    postRes = R.string.create,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
)
