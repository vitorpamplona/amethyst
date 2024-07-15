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
package com.vitorpamplona.amethyst.ui.actions.relays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.ui.actions.RelayInfoDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache

@Composable
fun Kind3RelaySetupInfoProposalDialog(
    item: Kind3RelayProposalSetupInfo,
    onAdd: (Kind3RelayProposalSetupInfo) -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var relayInfo: RelayInfoDialog? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    relayInfo?.let {
        RelayInformationDialog(
            onClose = { relayInfo = null },
            relayInfo = it.relayInfo,
            relayBriefInfo = it.relayBriefInfo,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    Kind3RelaySetupInfoProposalRow(
        item = item,
        loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
        loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        onAdd = {
            onAdd(item)
        },
        accountViewModel = accountViewModel,
        onClick = {
            accountViewModel.retrieveRelayDocument(
                item.url,
                onInfo = {
                    relayInfo = RelayInfoDialog(RelayBriefInfoCache.RelayBriefInfo(item.url), it)
                },
                onError = { url, errorCode, exceptionMessage ->
                    val msg =
                        when (errorCode) {
                            Nip11Retriever.ErrorCode.FAIL_TO_ASSEMBLE_URL ->
                                stringRes(
                                    context,
                                    R.string.relay_information_document_error_assemble_url,
                                    url,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_TO_REACH_SERVER ->
                                stringRes(
                                    context,
                                    R.string.relay_information_document_error_assemble_url,
                                    url,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_TO_PARSE_RESULT ->
                                stringRes(
                                    context,
                                    R.string.relay_information_document_error_assemble_url,
                                    url,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_WITH_HTTP_STATUS ->
                                stringRes(
                                    context,
                                    R.string.relay_information_document_error_assemble_url,
                                    url,
                                    exceptionMessage,
                                )
                        }

                    accountViewModel.toast(
                        stringRes(context, R.string.unable_to_download_relay_document),
                        msg,
                    )
                },
            )
        },
        nav = nav,
    )
}
