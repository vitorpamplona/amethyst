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
package com.vitorpamplona.amethyst.commons.account.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_logo
import com.vitorpamplona.amethyst.commons.resources.app_name
import com.vitorpamplona.amethyst.commons.resources.connect_via_tor2
import com.vitorpamplona.amethyst.commons.resources.ic_tor
import com.vitorpamplona.amethyst.commons.resources.login_or
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.tor.ConnectTorDialog

/** The two ends of the logo's gradient: the logged-off screens glow in them. */
val AmethystPurple = Color(0xFF652D80)
val AmethystBlue = Color(0xFF2598CF)

/** Below this width the brand sits above the form; from it, the two share the window side by side. */
private val TwoPaneMinWidth = 840.dp
private val FormMaxWidth = 420.dp

/**
 * The frame of the logged-off screens (login, sign-up): the brand, the [form] and a [footer] that
 * switches between them, over a soft glow in the logo's colors. A window wide enough splits into
 * the brand on one side and the form, on a card, on the other.
 */
@Composable
fun LoggedOffScaffold(
    torSettingsFlow: TorSettingsFlow,
    onTorError: (String) -> Unit,
    footer: @Composable () -> Unit,
    form: @Composable ColumnScope.() -> Unit,
) {
    val glow = if (MaterialTheme.colorScheme.isLight) 0.16f else 0.30f

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                // Built once per size and theme, not on every redraw of the screen.
                val purple =
                    Brush.radialGradient(
                        listOf(AmethystPurple.copy(alpha = glow), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.05f),
                        radius = size.maxDimension * 0.7f,
                    )
                val blue =
                    Brush.radialGradient(
                        listOf(AmethystBlue.copy(alpha = glow * 0.8f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.95f),
                        radius = size.maxDimension * 0.6f,
                    )
                onDrawBehind {
                    drawRect(purple)
                    drawRect(blue)
                }
            },
    ) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BrandMark(logoSize = 180.dp, large = true)
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .imePaddingSafe()
                            .verticalScroll(rememberScrollState())
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                        tonalElevation = 2.dp,
                        modifier = Modifier.widthIn(max = FormMaxWidth + 64.dp),
                    ) {
                        FormColumn(torSettingsFlow, onTorError, footer, Modifier.padding(32.dp), form)
                    }
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .imePaddingSafe()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMark(logoSize = 96.dp, large = false)
                Spacer(Modifier.height(32.dp))
                FormColumn(torSettingsFlow, onTorError, footer, Modifier.widthIn(max = FormMaxWidth), form)
            }
        }
    }
}

@Composable
private fun FormColumn(
    torSettingsFlow: TorSettingsFlow,
    onTorError: (String) -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier,
    form: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        form()
        Spacer(Modifier.height(4.dp))
        footer()
        TorSettingsChip(torSettingsFlow, onTorError)
    }
}

@Composable
private fun BrandMark(
    logoSize: Dp,
    large: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            imageVector = CustomHashTagIcons.Amethyst,
            contentDescription = stringRes(Res.string.app_logo),
            modifier = Modifier.size(logoSize),
            contentScale = ContentScale.Inside,
        )
        Spacer(Modifier.height(if (large) 24.dp else 12.dp))
        Text(
            text = stringRes(Res.string.app_name),
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The form's headline, left out where the brand already names the screen. */
@Composable
fun LoggedOffTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "or", between the main way in and the alternatives. */
@Composable
fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = stringRes(Res.string.login_or),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** "Don't have a Nostr account? Sign Up": a question and the link that answers it. */
@Composable
fun SwitchScreenFooter(
    question: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClick) {
            Text(action, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TorSettingsChip(
    torSettingsFlow: TorSettingsFlow,
    onError: (String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    AssistChip(
        onClick = { dialogOpen = true },
        label = { Text(stringRes(Res.string.connect_via_tor2)) },
        leadingIcon = {
            Icon(
                painter = painterRes(Res.drawable.ic_tor, 1),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        shape = RoundedCornerShape(50),
    )

    if (dialogOpen) {
        ConnectTorDialog(
            torSettings = torSettingsFlow.toSettings(),
            onClose = { dialogOpen = false },
            onPost = { torSettings ->
                dialogOpen = false
                torSettingsFlow.update(torSettings)
            },
            onError = onError,
        )
    }
}
