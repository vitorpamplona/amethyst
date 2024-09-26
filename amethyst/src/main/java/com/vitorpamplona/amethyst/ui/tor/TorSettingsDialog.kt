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
package com.vitorpamplona.amethyst.ui.tor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException

@Composable
fun ConnectTorDialog(
    torSettings: TorSettings = TorSettings(),
    onClose: () -> Unit,
    onPost: (torSettings: TorSettings) -> Unit,
    onError: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface {
            TorDialogContents(
                torSettings,
                onClose,
                onPost,
                onError,
            )
        }
    }
}

@Preview
@Composable
fun TorDialogContentsPreview() {
    ThemeComparisonColumn {
        TorDialogContents(
            onClose = {},
            onPost = { },
            onError = {},
            torSettings = TorSettings(torType = TorType.EXTERNAL),
        )
    }
}

@Composable
fun TorDialogContents(
    torSettings: TorSettings,
    onClose: () -> Unit,
    onPost: (torSettings: TorSettings) -> Unit,
    onError: (String) -> Unit,
) {
    val dialogViewModel = viewModel<TorDialogViewModel>()

    LaunchedEffect(dialogViewModel, torSettings) {
        dialogViewModel.reset(torSettings)
    }

    TorDialogContents(dialogViewModel, onClose, onPost, onError)
}

@Composable
fun TorDialogContents(
    dialogViewModel: TorDialogViewModel,
    onClose: () -> Unit,
    onPost: (torSettings: TorSettings) -> Unit,
    onError: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CloseButton(onPress = { onClose() })

            val toastMessage = stringRes(R.string.invalid_port_number)

            SaveButton(
                onPost = {
                    try {
                        onPost(dialogViewModel.save())
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        onError(toastMessage)
                    }
                },
                isActive = true,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 5.dp),
            verticalArrangement = Arrangement.spacedBy(Size10dp),
        ) {
            SettingsRow(
                R.string.use_internal_tor,
                R.string.use_internal_tor_explainer,
                persistentListOf(
                    TitleExplainer(stringRes(TorType.OFF.resourceId)),
                    TitleExplainer(stringRes(TorType.INTERNAL.resourceId)),
                    TitleExplainer(stringRes(TorType.EXTERNAL.resourceId)),
                ),
                dialogViewModel.torType.value.screenCode,
            ) {
                dialogViewModel.torType.value = parseTorType(it)
            }

            AnimatedVisibility(
                visible = dialogViewModel.torType.value == TorType.EXTERNAL,
            ) {
                SettingsRow(
                    R.string.orbot_socks_port,
                    R.string.connect_through_your_orbot_setup_short,
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
                                text = "9050",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = dialogViewModel.torType.value != TorType.OFF,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 5.dp),
                verticalArrangement = Arrangement.spacedBy(Size10dp),
            ) {
                SettingsRow(
                    R.string.tor_preset,
                    R.string.tor_preset_explainer,
                    persistentListOf(
                        TitleExplainer(stringRes(TorPresetType.ONLY_WHEN_NEEDED.resourceId), stringRes(TorPresetType.ONLY_WHEN_NEEDED.explainerId)),
                        TitleExplainer(stringRes(TorPresetType.DEFAULT.resourceId), stringRes(TorPresetType.DEFAULT.explainerId)),
                        TitleExplainer(stringRes(TorPresetType.SMALL_PAYLOADS.resourceId), stringRes(TorPresetType.SMALL_PAYLOADS.explainerId)),
                        TitleExplainer(stringRes(TorPresetType.FULL_PRIVACY.resourceId), stringRes(TorPresetType.FULL_PRIVACY.explainerId)),
                        TitleExplainer(stringRes(TorPresetType.CUSTOM.resourceId), stringRes(TorPresetType.CUSTOM.explainerId)),
                    ),
                    dialogViewModel.preset.value.screenCode,
                ) {
                    dialogViewModel.setPreset(parseTorPresetType(it))
                }

                SwitchSettingsRow(
                    R.string.tor_use_onion_address,
                    R.string.tor_use_onion_address_explainer,
                    dialogViewModel.onionRelaysViaTor,
                )

                SwitchSettingsRow(
                    R.string.tor_use_dm_relays,
                    R.string.tor_use_dm_relays_explainer,
                    dialogViewModel.dmRelaysViaTor,
                )

                SwitchSettingsRow(
                    R.string.tor_use_new_relays,
                    R.string.tor_use_new_relays_explainer,
                    dialogViewModel.newRelaysViaTor,
                )

                SwitchSettingsRow(
                    R.string.tor_use_trusted_relays,
                    R.string.tor_use_trusted_relays_explainer,
                    dialogViewModel.trustedRelaysViaTor,
                )

                SwitchSettingsRow(
                    R.string.tor_use_money_operations,
                    R.string.tor_use_money_operations_explainer,
                    dialogViewModel.moneyOperationsViaTor,
                )

                /**
                 * Too hard to separate Coil into regular images and profile pics
                 SwitchSettingsRow(
                 R.string.tor_use_profile_pictures,
                 R.string.tor_use_profile_pictures_explainer,
                 dialogViewModel.profilePicsViaTor,
                 )
                 */

                SwitchSettingsRow(
                    R.string.tor_use_nip05_verification,
                    R.string.tor_use_nip05_verification_explainer,
                    dialogViewModel.nip05VerificationsViaTor,
                )

                SwitchSettingsRow(
                    R.string.tor_use_url_previews,
                    R.string.tor_use_url_previews_explainer,
                    dialogViewModel.urlPreviewsViaTor,
                )

                SwitchSettingsRow(
                    R.string.tor_use_images,
                    R.string.tor_use_images_explainer,
                    dialogViewModel.imagesViaTor,
                )

                SwitchSettingsRow(
                    R.string.tor_use_videos,
                    R.string.tor_use_videos_explainer,
                    dialogViewModel.videosViaTor,
                )

                SwitchSettingsRow(
                    R.string.tor_use_nip96_uploads,
                    R.string.tor_use_nip96_uploads_explainer,
                    dialogViewModel.nip96UploadsViaTor,
                )
            }
        }
    }
}

@Composable
fun SwitchSettingsRow(
    name: Int,
    desc: Int,
    checked: MutableState<Boolean>,
) {
    SettingsRow(
        name,
        desc,
    ) {
        Switch(checked.value, onCheckedChange = { checked.value = it })
    }
}
