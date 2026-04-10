/*
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
package com.vitorpamplona.amethyst.ios.nwc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Preset zap amounts in sats. */
val ZAP_AMOUNTS = listOf(21L, 69L, 420L, 1_000L, 5_000L)

/**
 * Dialog for choosing a zap amount. Shows preset amounts and a custom input field.
 * Calls [onZap] with the chosen amount in sats when confirmed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZapAmountDialog(
    noteId: String,
    isSending: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onZap: (noteId: String, amountSats: Long) -> Unit,
) {
    var selectedAmount by remember { mutableStateOf(1_000L) }
    var customAmount by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Text(
                "⚡ Zap",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Choose amount (sats)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // Preset amount chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZAP_AMOUNTS.forEach { amount ->
                        val isSelected = !useCustom && selectedAmount == amount
                        Text(
                            text = formatSats(amount),
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .background(
                                        color =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        shape = RoundedCornerShape(20.dp),
                                    ).clickable(enabled = !isSending) {
                                        selectedAmount = amount
                                        useCustom = false
                                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Custom amount input
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = {
                        customAmount = it.filter { c -> c.isDigit() }
                        if (customAmount.isNotEmpty()) {
                            useCustom = true
                            selectedAmount = customAmount.toLongOrNull() ?: 0L
                        }
                    },
                    label = { Text("Custom amount") },
                    placeholder = { Text("Enter sats") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isSending,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Error message
                errorMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = if (useCustom) customAmount.toLongOrNull() ?: 0L else selectedAmount
                    if (amount > 0) {
                        onZap(noteId, amount)
                    }
                },
                enabled = !isSending && selectedAmount > 0,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("⚡ Zap ${formatSats(if (useCustom) customAmount.toLongOrNull() ?: 0L else selectedAmount)}")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSending,
            ) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Formats a sat amount for display (e.g. 1000 -> "1k sats", 21 -> "21 sats").
 */
fun formatSats(amount: Long): String =
    when {
        amount >= 1_000_000 -> {
            val whole = amount / 1_000_000
            val frac = (amount % 1_000_000) / 100_000
            if (frac == 0L) "${whole}M sats" else "$whole.${frac}M sats"
        }

        amount >= 10_000 -> {
            val whole = amount / 1_000
            val frac = (amount % 1_000) / 100
            if (frac == 0L) "${whole}k sats" else "$whole.${frac}k sats"
        }

        else -> {
            "$amount sats"
        }
    }
