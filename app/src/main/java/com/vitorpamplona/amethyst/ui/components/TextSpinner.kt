package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import kotlinx.collections.immutable.ImmutableList

@Composable
fun TextSpinner(
    label: String,
    placeholder: String,
    options: ImmutableList<String>,
    explainers: ImmutableList<String>? = null,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var optionsShowing by remember { mutableStateOf(false) }
    var currentText by remember { mutableStateOf(placeholder) }

    Box(
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    optionsShowing = true
                    focusRequester.requestFocus()
                }
        )
    }

    if (optionsShowing) {
        options.isNotEmpty().also {
            SpinnerSelectionDialog(options = options, explainers = explainers, onDismiss = { optionsShowing = false }) {
                currentText = options[it]
                optionsShowing = false
                onSelect(it)
            }
        }
    }
}

@Composable
fun SpinnerSelectionDialog(
    options: ImmutableList<String>,
    explainers: ImmutableList<String>?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            border = BorderStroke(0.25.dp, Color.LightGray),
            shape = RoundedCornerShape(5.dp)
        ) {
            LazyColumn() {
                itemsIndexed(options) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 16.dp)
                            .clickable {
                                onSelect(index)
                            }
                    ) {
                        Column() {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Text(text = item, color = MaterialTheme.colors.onSurface)
                            }
                            explainers?.getOrNull(index)?.let {
                                Spacer(modifier = Modifier.height(5.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                ) {
                                    Text(text = it, color = Color.Gray, fontSize = Font14SP)
                                }
                            }
                        }
                    }
                    if (index < options.lastIndex) {
                        Divider(color = Color.LightGray, thickness = 0.25.dp)
                    }
                }
            }
        }
    }
}
