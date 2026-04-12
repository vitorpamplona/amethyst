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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.nip57Zaps.ZapPollValidation
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

@Stable
data class PollOption(
    val option: Int,
    val descriptor: String,
    var zappedValue: MutableState<BigDecimal> = mutableStateOf(BigDecimal.ZERO),
    var tally: MutableState<Float> = mutableFloatStateOf(0f),
    var consensusThreadhold: MutableState<Boolean> = mutableStateOf(false),
    var zappedByLoggedIn: MutableState<Boolean> = mutableStateOf(false),
)

@Stable
class PollNoteViewModel : ViewModel() {
    private lateinit var account: Account
    private var pollNote: Note? = null

    private var pollEvent: ZapPollEvent? = null
    private var pollOptions: Map<Int, String>? = null
    private var valueMaximum: Long? = null
    private var valueMinimum: Long? = null
    private var valueMaximumBD: BigDecimal? = null
    private var valueMinimumBD: BigDecimal? = null

    private var closedAt: Long? = null
    private var consensusThreshold: BigDecimal? = null

    private var totalZapped: BigDecimal = BigDecimal.ZERO
    private var wasZappedByLoggedInAccount: Boolean = false

    var canZap = mutableStateOf(false)
    var tallies: List<PollOption> = emptyList()

    fun init(acc: Account) {
        account = acc
    }

    fun load(note: Note?) {
        if (pollNote != note) {
            pollNote = note
            pollEvent = pollNote?.event as ZapPollEvent
            pollOptions = pollEvent?.pollOptions()
            valueMaximum = pollEvent?.maxAmount()
            valueMinimum = pollEvent?.minAmount()
            valueMinimumBD = valueMinimum?.let { BigDecimal(it) }
            valueMaximumBD = valueMaximum?.let { BigDecimal(it) }
            consensusThreshold = pollEvent?.consensusThreshold()?.toBigDecimal()
            closedAt = pollEvent?.closedAt()

            totalZapped = BigDecimal.ZERO
            wasZappedByLoggedInAccount = false

            canZap.value = checkIfCanZap()

            tallies = pollOptions?.keys?.map { option ->
                PollOption(
                    option,
                    pollOptions?.get(option) ?: "",
                )
            } ?: emptyList()
        }
    }

    fun refreshTallies() {
        viewModelScope.launch(Dispatchers.IO) {
            totalZapped = totalZapped()
            wasZappedByLoggedInAccount = false
            wasZappedByLoggedInAccount = account.calculateIfNoteWasZappedByAccount(pollNote, 0)
            canZap.value = checkIfCanZap()

            tallies.forEach {
                val zappedValue = zappedPollOptionAmount(it.option)
                val tallyValue =
                    if (totalZapped > BigDecimal.ZERO) {
                        zappedValue.divide(totalZapped, 2, RoundingMode.HALF_UP)
                    } else {
                        BigDecimal.ZERO
                    }

                it.zappedValue.value = zappedValue
                it.tally.value = tallyValue.toFloat()
                it.consensusThreadhold.value = consensusThreshold != null && tallyValue >= consensusThreshold!!
                it.zappedByLoggedIn.value = account.userProfile().let { it1 -> cachedIsPollOptionZappedBy(it.option, it1) }
            }
        }
    }

    fun checkIfCanZap(): Boolean {
        val account = account
        val note = pollNote ?: return false
        return account.userProfile() != note.author && !wasZappedByLoggedInAccount
    }

    fun isVoteAmountAtomic() = valueMaximum != null && valueMinimum != null && valueMinimum == valueMaximum

    fun isPollClosed(): Boolean =
        closedAt?.let {
            // allow 2 minute leeway for zap to propagate
            pollNote?.createdAt()?.plus(it * (86400 + 120))!! < TimeUtils.now()
        } == true

    fun voteAmountPlaceHolderText(sats: String): String = ZapPollValidation.voteAmountPlaceholder(valueMinimum, valueMaximum, sats)

    fun inputVoteAmountLong(textAmount: String) = ZapPollValidation.parseVoteAmount(textAmount)

    fun isValidInputVoteAmount(amount: BigDecimal?): Boolean = ZapPollValidation.isValidVoteAmount(amount, valueMinimumBD, valueMaximumBD)

    fun isValidInputVoteAmount(amount: Long?): Boolean = ZapPollValidation.isValidVoteAmount(amount, valueMinimum, valueMaximum)

    suspend fun isPollOptionZappedBy(
        option: Int,
        user: User,
        afterTimeInSeconds: Long,
    ): Boolean = pollNote?.isZappedBy(option, user, afterTimeInSeconds, account) == true

    fun cachedIsPollOptionZappedBy(
        option: Int,
        user: User,
    ): Boolean =
        pollNote!!.zaps.any {
            val zapEvent = it.value?.event as? LnZapEvent
            val privateZapAuthor =
                (it.key.event as? LnZapRequestEvent)?.let {
                    account.privateZapsDecryptionCache.cachedPrivateZap(it)
                }
            zapEvent?.zappedPollOption() == option &&
                (it.key.author?.pubkeyHex == user.pubkeyHex || privateZapAuthor?.pubKey == user.pubkeyHex)
        }

    private fun zappedPollOptionAmount(option: Int): BigDecimal =
        pollNote?.zaps?.values?.sumOf {
            val event = it?.event as? LnZapEvent
            val zapAmount = event?.amount ?: BigDecimal.ZERO
            val isValidAmount = isValidInputVoteAmount(event?.amount)

            if (isValidAmount && event?.zappedPollOption() == option) {
                zapAmount
            } else {
                BigDecimal.ZERO
            }
        }
            ?: BigDecimal.ZERO

    private fun totalZapped(): BigDecimal =
        pollNote?.zaps?.values?.sumOf {
            val zapEvent = (it?.event as? LnZapEvent)
            val zapAmount = zapEvent?.amount ?: BigDecimal.ZERO
            val isValidAmount = isValidInputVoteAmount(zapEvent?.amount)

            if (isValidAmount && zapEvent?.zappedPollOption() != null) {
                zapAmount
            } else {
                BigDecimal.ZERO
            }
        }
            ?: BigDecimal.ZERO

    fun createZapOptionsThatMatchThePollingParameters(zapPaymentChoices: List<Long>): List<Long> = ZapPollValidation.filterZapChoices(zapPaymentChoices, valueMinimum, valueMaximum)
}
