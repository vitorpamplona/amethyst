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
package com.vitorpamplona.amethyst.ui.i2p

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.i2p.I2pType
import com.vitorpamplona.amethyst.commons.i2p.parseI2pType
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.tor.SwitchSettingsRow
import kotlinx.collections.immutable.persistentListOf

// Settings body for I2P. Mirrors PrivacySettingsBody (Tor) but without presets —
// no canonical "default I2P configuration" exists, and the per-feature toggles
// only take effect when I2P is also the preferred clearnet transport (see the
// global picker above this section in PrivacyOptionsScreen).
//
// I2P is EXTERNAL-only: connects to a user-run i2pd / Java I2P installation on
// the device (default SOCKS port 4447). We intentionally do not embed a router —
// see commons I2pType for the rationale.
@Composable
fun I2pSettingsBody(dialogViewModel: I2pDialogViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 5.dp),
        verticalArrangement = Arrangement.spacedBy(Size10dp),
    ) {
        SettingsRow(
            R.string.use_i2p,
            R.string.use_i2p_explainer,
            persistentListOf(
                TitleExplainer(stringRes(I2pType.OFF.resourceId)),
                TitleExplainer(stringRes(I2pType.EXTERNAL.resourceId)),
            ),
            when (dialogViewModel.i2pType.value) {
                I2pType.OFF -> 0
                I2pType.EXTERNAL -> 1
            },
        ) { idx ->
            dialogViewModel.i2pType.value =
                when (idx) {
                    1 -> parseI2pType(I2pType.EXTERNAL.screenCode)
                    else -> parseI2pType(I2pType.OFF.screenCode)
                }
        }

        AnimatedVisibility(
            visible = dialogViewModel.i2pType.value == I2pType.EXTERNAL,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SettingsRow(
                R.string.i2p_socks_port,
                R.string.i2p_socks_port_explainer,
            ) {
                OutlinedTextField(
                    value = dialogViewModel.socksPortStr.value,
                    onValueChange = { dialogViewModel.socksPortStr.value = it },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Number,
                        ),
                    placeholder = {
                        Text(
                            text = "4447",
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                )
            }
        }
    }

    AnimatedVisibility(
        visible = dialogViewModel.i2pType.value != I2pType.OFF,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp),
            verticalArrangement = Arrangement.spacedBy(Size10dp),
        ) {
            SwitchSettingsRow(
                R.string.i2p_use_i2p_address,
                R.string.i2p_use_i2p_address_explainer,
                dialogViewModel.i2pRelaysViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_dm_relays,
                R.string.i2p_use_dm_relays_explainer,
                dialogViewModel.dmRelaysViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_new_relays,
                R.string.i2p_use_new_relays_explainer,
                dialogViewModel.newRelaysViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_trusted_relays,
                R.string.i2p_use_trusted_relays_explainer,
                dialogViewModel.trustedRelaysViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_money_operations,
                R.string.i2p_use_money_operations_explainer,
                dialogViewModel.moneyOperationsViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_nip05_verification,
                R.string.i2p_use_nip05_verification_explainer,
                dialogViewModel.nip05VerificationsViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_url_previews,
                R.string.i2p_use_url_previews_explainer,
                dialogViewModel.urlPreviewsViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_images,
                R.string.i2p_use_images_explainer,
                dialogViewModel.imagesViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_videos,
                R.string.i2p_use_videos_explainer,
                dialogViewModel.videosViaI2p,
            )

            SwitchSettingsRow(
                R.string.i2p_use_media_uploads,
                R.string.i2p_use_media_uploads_explainer,
                dialogViewModel.mediaUploadsViaI2p,
            )
        }
    }
}
