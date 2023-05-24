package com.vitorpamplona.amethyst.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

data class PollOption(
    val option: Int,
    val descriptor: String,
    val zappedValue: BigDecimal,
    val tally: BigDecimal,
    val consensusThreadhold: Boolean,
    val zappedByLoggedIn: Boolean
)

class PollNoteViewModel : ViewModel() {
    var account: Account? = null
    private var pollNote: Note? = null

    var pollEvent: PollNoteEvent? = null
    var pollOptions: Map<Int, String>? = null
    var valueMaximum: Int? = null
    var valueMinimum: Int? = null
    private var closedAt: Int? = null
    var consensusThreshold: BigDecimal? = null

    var totalZapped: BigDecimal = BigDecimal.ZERO
    var wasZappedByLoggedInAccount: Boolean = false

    var tallies by mutableStateOf<List<PollOption>>(emptyList())

    fun load(acc: Account, note: Note?) {
        account = acc
        pollNote = note
        pollEvent = pollNote?.event as PollNoteEvent
        pollOptions = pollEvent?.pollOptions()
        valueMaximum = pollEvent?.getTagInt(VALUE_MAXIMUM)
        valueMinimum = pollEvent?.getTagInt(VALUE_MINIMUM)
        consensusThreshold = pollEvent?.getTagInt(CONSENSUS_THRESHOLD)?.toFloat()?.div(100)?.toBigDecimal()
        closedAt = pollEvent?.getTagInt(CLOSED_AT)

        refreshTallies()
    }

    fun refreshTallies() {
        totalZapped = totalZapped()
        wasZappedByLoggedInAccount = pollNote?.let { account?.calculateIfNoteWasZappedByAccount(it) } ?: false

        tallies = pollOptions?.keys?.map {
            val zappedInOption = zappedPollOptionAmount(it)

            val myTally = if (totalZapped.compareTo(BigDecimal.ZERO) > 0) {
                zappedInOption.divide(totalZapped, 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            val zappedByLoggedIn = account?.userProfile()?.let { it1 -> isPollOptionZappedBy(it, it1) } ?: false

            val consensus = consensusThreshold != null && myTally >= consensusThreshold!!

            PollOption(it, pollOptions?.get(it) ?: "", zappedInOption, myTally, consensus, zappedByLoggedIn)
        } ?: emptyList()
    }

    fun canZap(): Boolean {
        val account = account ?: return false
        val user = account.userProfile() ?: return false
        val note = pollNote ?: return false
        return user != note.author && !wasZappedByLoggedInAccount
    }

    fun isVoteAmountAtomic() = valueMaximum != null && valueMinimum != null && valueMinimum == valueMaximum

    fun isPollClosed(): Boolean = closedAt?.let { // allow 2 minute leeway for zap to propagate
        pollNote?.createdAt()?.plus(it * (86400 + 120))!! < Date().time / 1000
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

    fun isPollOptionZappedBy(option: Int, user: User): Boolean {
        return pollNote!!.zaps
            .any {
                val zapEvent = it.value?.event as? LnZapEvent
                val privateZapAuthor = account?.decryptZapContentAuthor(it.key)
                zapEvent?.zappedPollOption() == option && (it.key.author?.pubkeyHex == user.pubkeyHex || privateZapAuthor?.pubKey == user.pubkeyHex)
            }
    }

    private fun zappedPollOptionAmount(option: Int): BigDecimal {
        return pollNote?.zaps?.values?.sumOf {
            val event = it?.event as? LnZapEvent
            if (event?.zappedPollOption() == option) {
                event.amount ?: BigDecimal(0)
            } else {
                BigDecimal(0)
            }
        } ?: BigDecimal(0)
    }

    private fun totalZapped(): BigDecimal {
        return pollNote?.zaps?.values?.sumOf {
            val zapEvent = (it?.event as? LnZapEvent)

            if (zapEvent?.zappedPollOption() != null) {
                zapEvent.amount ?: BigDecimal(0)
            } else {
                BigDecimal(0)
            }
        } ?: BigDecimal(0)
    }
}
