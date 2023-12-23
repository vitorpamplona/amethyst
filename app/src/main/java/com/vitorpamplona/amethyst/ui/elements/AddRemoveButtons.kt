package com.vitorpamplona.amethyst.ui.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ThemeComparison

@Composable
@Preview
fun AddButtonPreview() {
    ThemeComparison(
        onDark = {
            Row() {
                Column {
                    AddButton(isActive = true) {}
                    AddButton(isActive = false) {}
                }

                Column {
                    RemoveButton(isActive = true) {}
                    RemoveButton(isActive = false) {}
                }
            }
        },
        onLight = {
            Row() {
                Column {
                    AddButton(isActive = true) {}
                    AddButton(isActive = false) {}
                }

                Column {
                    RemoveButton(isActive = true) {}
                    RemoveButton(isActive = false) {}
                }
            }
        }
    )
}

@Composable
fun AddButton(
    text: Int = R.string.add,
    isActive: Boolean = true,
    modifier: Modifier = Modifier.padding(start = 3.dp),
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onClick()
            }
        },
        shape = ButtonBorder,
        enabled = isActive,
        contentPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp)
    ) {
        Text(text = stringResource(text), color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
fun RemoveButton(
    isActive: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier.padding(start = 3.dp),
        onClick = {
            if (isActive) {
                onClick()
            }
        },
        shape = ButtonBorder,
        enabled = isActive,
        contentPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp)
    ) {
        Text(text = stringResource(R.string.remove), color = Color.White)
    }
}
