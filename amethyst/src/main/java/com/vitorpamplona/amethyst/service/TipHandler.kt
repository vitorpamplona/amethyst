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
package com.vitorpamplona.amethyst.service

import android.content.Context
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.model.AccountMoneroManager
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.PendingTransaction
import com.vitorpamplona.amethyst.model.TransactionPriority
import com.vitorpamplona.quartz.experimental.moneroTips.TipEvent
import com.vitorpamplona.quartz.experimental.moneroTips.TipSplitSetup
import com.vitorpamplona.quartz.experimental.moneroTips.tipSplitSetup
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TipHandler(
    val cache: LocalCache,
) {
    suspend fun tip(
        note: Note,
        amount: ULong,
        message: String,
        context: Context,
        onError: (String, String) -> Unit,
        onNotEnoughMoney: (Long, Long, Long) -> Unit,
        onProgress: (percent: Float) -> Unit,
        tipType: TipEvent.TipType,
        priority: TransactionPriority,
    ) = withContext(Dispatchers.IO) {
        val accountPubKey: HexKey? = note.author?.pubkeyHex

        val (tipsToSend, tippedUsers) = getNoteTipRecipient(note, cache)
        onProgress(0.10f)

        if (tipsToSend.isEmpty()) {
            onError(
                context.getString(R.string.missing_monero_setup),
                context.getString(
                    R.string.user_does_not_have_a_monero_address_setup_to_receive_tips,
                ),
            )
            return@withContext
        }

        onProgress(0.50f)

        val totalWeight =
            if (tipsToSend.any { it.weight != null }) {
                tipsToSend.sumOf { it.weight ?: 0.0 }
            } else {
                1.0
            }

        val amounts =
            tipsToSend
                .map {
                    val weight = it.weight?.let { w -> w / totalWeight } ?: (totalWeight / tipsToSend.size)
                    (amount.toDouble() * weight).toLong()
                }.toTypedArray()

        val fee =
            AccountMoneroManager.estimateTransactionFee(
                tipsToSend.map { it.addressOrPubKeyHex }.toTypedArray(),
                amounts,
                priority,
            )
        if (AccountMoneroManager.getMoneroBalance() < amount.toLong() + fee) {
            onNotEnoughMoney(AccountMoneroManager.getMoneroBalance(), amount.toLong(), fee)
            return@withContext
        }

        val transaction = AccountMoneroManager.tip(tipsToSend, amount, priority, note.idHex)
        if (transaction == null || transaction.status.type != PendingTransaction.StatusType.OK) {
            val error =
                transaction?.let {
                    "${it.status.error[0].uppercase()}${it.status.error.substring(1)}"
                } ?: "Unknown error"
            onError(
                context.getString(R.string.error_dialog_tip_error),
                error,
            )
            return@withContext
        }

        onProgress(0.75f)

        // TODO: Send tip proof event for non-private tips
        // This requires adaptation of the signer API for the new upstream

        onProgress(1f)
    }
}

fun getUserMoneroAddress(user: com.vitorpamplona.amethyst.commons.model.User): String? =
    user
        .metadata()
        .flow.value
        ?.info
        ?.moneroAddress()

fun getNoteTipRecipient(
    note: Note,
    cache: LocalCache,
): Pair<List<TipSplitSetup>, List<HexKey>> {
    val noteEvent = note.event
    val tipSplitSetup = noteEvent?.tipSplitSetup()

    var accountTipRecipient: String? = null
    var accountPubKey: HexKey? = null

    note.author?.let { author ->
        accountPubKey = author.pubkeyHex
        accountTipRecipient = getUserMoneroAddress(author)
        if (accountTipRecipient == null) {
            author.metadata().flow.value?.info?.about?.split("\\s+".toRegex())?.let { tokens ->
                for (token in tokens) {
                    if (token.length == 95 && AccountMoneroManager.isAddressValid(token)) {
                        accountTipRecipient = token
                        break
                    }
                }
            }
        }
    }

    val tippedUsers = mutableListOf<HexKey>()
    val tips =
        if (!tipSplitSetup.isNullOrEmpty()) {
            tipSplitSetup.mapNotNull { tip ->
                if (!tip.isAddress) {
                    val recipient = cache.checkGetOrCreateUser(tip.addressOrPubKeyHex)
                    recipient?.let { getUserMoneroAddress(it) }?.let { addr ->
                        if (AccountMoneroManager.isAddressValid(addr)) {
                            tippedUsers += tip.addressOrPubKeyHex
                            tip.copy(addressOrPubKeyHex = addr, isAddress = true)
                        } else {
                            null
                        }
                    }
                } else {
                    if (AccountMoneroManager.isAddressValid(tip.addressOrPubKeyHex)) {
                        tip
                    } else {
                        null
                    }
                }
            }
        } else if (noteEvent is LiveActivitiesEvent && noteEvent.hasHost()) {
            noteEvent.hosts().mapNotNull { hostTag ->
                val hostPubKey = hostTag.pubKey
                val hostUser = cache.checkGetOrCreateUser(hostPubKey)
                hostUser?.let { getUserMoneroAddress(it) }?.let { addr ->
                    if (AccountMoneroManager.isAddressValid(addr)) {
                        tippedUsers += hostPubKey
                        TipSplitSetup(addr, null, 1.0, true)
                    } else {
                        null
                    }
                }
            }
        } else {
            accountTipRecipient?.let { tipRecipient ->
                accountPubKey?.let {
                    if (AccountMoneroManager.isAddressValid(tipRecipient)) {
                        tippedUsers += it
                        listOf(TipSplitSetup(tipRecipient, null, 1.0, true))
                    } else {
                        null
                    }
                }
            }
        } ?: listOf()

    return Pair(tips, tippedUsers)
}
