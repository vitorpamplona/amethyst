package com.vitorpamplona.amethyst.ui.note

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.events.CLOSED_AT
import com.vitorpamplona.quartz.events.CONSENSUS_THRESHOLD
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.VALUE_MAXIMUM
import com.vitorpamplona.quartz.events.VALUE_MINIMUM
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

@Immutable
data class PollOption(
    val option: Int,
    val descriptor: String,
    val zappedValue: BigDecimal,
    val tally: BigDecimal,
    val consensusThreadhold: Boolean,
    val zappedByLoggedIn: Boolean
)

@Stable
class PollNoteViewModel : ViewModel() {
    private var account: Account? = null
    private var pollNote: Note? = null

    private var pollEvent: PollNoteEvent? = null
    private var pollOptions: Map<Int, String>? = null
    private var valueMaximum: Long? = null
    private var valueMinimum: Long? = null
    private var valueMaximumBD: BigDecimal? = null
    private var valueMinimumBD: BigDecimal? = null

    private var closedAt: Long? = null
    private var consensusThreshold: BigDecimal? = null

    private var totalZapped: BigDecimal = BigDecimal.ZERO
    private var wasZappedByLoggedInAccount: Boolean = false

    private val _tallies = MutableStateFlow<List<PollOption>>(emptyList())
    val tallies = _tallies.asStateFlow()

    fun load(acc: Account, note: Note?) {
        account = acc
        pollNote = note
        pollEvent = pollNote?.event as PollNoteEvent
        pollOptions = pollEvent?.pollOptions()
        valueMaximum = pollEvent?.getTagLong(VALUE_MAXIMUM)
        valueMinimum = pollEvent?.getTagLong(VALUE_MINIMUM)
        valueMinimumBD = valueMinimum?.let { BigDecimal(it) }
        valueMaximumBD = valueMaximum?.let { BigDecimal(it) }
        consensusThreshold = pollEvent?.getTagLong(CONSENSUS_THRESHOLD)?.toFloat()?.div(100)?.toBigDecimal()
        closedAt = pollEvent?.getTagLong(CLOSED_AT)
    }

    fun refreshTallies() {
        viewModelScope.launch(Dispatchers.Default) {
            totalZapped = totalZapped()
            wasZappedByLoggedInAccount = false
            account?.calculateIfNoteWasZappedByAccount(pollNote) {
                wasZappedByLoggedInAccount = true
            }

            val newOptions = pollOptions?.keys?.map { option ->
                val zappedInOption = zappedPollOptionAmount(option)

                val myTally = if (totalZapped.compareTo(BigDecimal.ZERO) > 0) {
                    zappedInOption.divide(totalZapped, 2, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }

                val cachedZappedByLoggedIn = account?.userProfile()?.let { it1 -> cachedIsPollOptionZappedBy(option, it1) } ?: false

                val consensus = consensusThreshold != null && myTally >= consensusThreshold!!

                PollOption(option, pollOptions?.get(option) ?: "", zappedInOption, myTally, consensus, cachedZappedByLoggedIn)
            }

            _tallies.emit(
                newOptions ?: emptyList()
            )
        }
    }

    fun canZap(): Boolean {
        val account = account ?: return false
        val note = pollNote ?: return false
        return account.userProfile() != note.author && !wasZappedByLoggedInAccount
    }

    fun isVoteAmountAtomic() = valueMaximum != null && valueMinimum != null && valueMinimum == valueMaximum

    fun isPollClosed(): Boolean = closedAt?.let { // allow 2 minute leeway for zap to propagate
        pollNote?.createdAt()?.plus(it * (86400 + 120))!! < TimeUtils.now()
    } == true

    fun voteAmountPlaceHolderText(sats: String): String = if (valueMinimum == null && valueMaximum == null) {
        sats
    } else if (valueMinimum == null) {
        "1—$valueMaximum $sats"
    } else if (valueMaximum == null) {
        ">$valueMinimum $sats"
    } else {
        "$valueMinimum—$valueMaximum $sats"
    }

    fun inputVoteAmountLong(textAmount: String) = if (textAmount.isEmpty()) { null } else {
        try {
            textAmount.toLong()
        } catch (e: Exception) { null }
    }

    fun isValidInputVoteAmount(amount: BigDecimal?): Boolean {
        if (amount == null) {
            return false
        } else if (valueMinimum == null && valueMaximum == null) {
            if (amount > BigDecimal.ZERO) {
                return true
            }
        } else if (valueMinimum == null) {
            if (amount > BigDecimal.ZERO && amount <= valueMaximumBD!!) {
                return true
            }
        } else if (valueMaximum == null) {
            if (amount >= valueMinimumBD!!) {
                return true
            }
        } else {
            if ((valueMinimumBD!! <= amount) && (amount <= valueMaximumBD!!)) {
                return true
            }
        }
        return false
    }

    fun isValidInputVoteAmount(amount: Long?): Boolean {
        if (amount == null) {
            return false
        } else if (valueMinimum == null && valueMaximum == null) {
            if (amount > 0) {
                return true
            }
        } else if (valueMinimum == null) {
            if (amount > 0 && amount <= valueMaximum!!) {
                return true
            }
        } else if (valueMaximum == null) {
            if (amount >= valueMinimum!!) {
                return true
            }
        } else {
            if ((valueMinimum!! <= amount) && (amount <= valueMaximum!!)) {
                return true
            }
        }
        return false
    }

    fun isPollOptionZappedBy(option: Int, user: User, onWasZappedByAuthor: () -> Unit) {
        pollNote?.isZappedBy(option, user, account!!, onWasZappedByAuthor)
    }

    fun cachedIsPollOptionZappedBy(option: Int, user: User): Boolean {
        return pollNote!!.zaps
            .any {
                val zapEvent = it.value?.event as? LnZapEvent
                val privateZapAuthor = (it.key.event as? LnZapRequestEvent)?.cachedPrivateZap()
                zapEvent?.zappedPollOption() == option && (it.key.author?.pubkeyHex == user.pubkeyHex || privateZapAuthor?.pubKey == user.pubkeyHex)
            }
    }

    private fun zappedPollOptionAmount(option: Int): BigDecimal {
        return pollNote?.zaps?.values?.sumOf {
            val event = it?.event as? LnZapEvent
            val zapAmount = event?.amount ?: BigDecimal.ZERO
            val isValidAmount = isValidInputVoteAmount(event?.amount)

            if (isValidAmount && event?.zappedPollOption() == option) {
                zapAmount
            } else {
                BigDecimal.ZERO
            }
        } ?: BigDecimal.ZERO
    }

    private fun totalZapped(): BigDecimal {
        return pollNote?.zaps?.values?.sumOf {
            val zapEvent = (it?.event as? LnZapEvent)
            val zapAmount = zapEvent?.amount ?: BigDecimal.ZERO
            val isValidAmount = isValidInputVoteAmount(zapEvent?.amount)

            if (isValidAmount && zapEvent?.zappedPollOption() != null) {
                zapAmount
            } else {
                BigDecimal.ZERO
            }
        } ?: BigDecimal.ZERO
    }

    fun createZapOptionsThatMatchThePollingParameters(): List<Long> {
        val options = account?.zapAmountChoices?.filter { isValidInputVoteAmount(it) }?.toMutableList() ?: mutableListOf()
        if (options.isEmpty()) {
            valueMinimum?.let { minimum ->
                valueMaximum?.let { maximum ->
                    if (minimum != maximum) {
                        options.add(((minimum + maximum) / 2).toLong())
                    }
                }
            }
        }
        valueMinimum?.let { options.add(it) }
        valueMaximum?.let { options.add(it) }

        return options.toSet().sorted()
    }
}
