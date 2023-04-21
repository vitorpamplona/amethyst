package com.vitorpamplona.amethyst.ui.note

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

class PollNoteViewModel {
    var account: Account? = null
    private var pollNote: Note? = null

    var pollEvent: PollNoteEvent? = null
    private var pollOptions: Map<Int, String>? = null
    var valueMaximum: Int? = null
    var valueMinimum: Int? = null
    private var closedAt: Int? = null
    var consensusThreshold: BigDecimal? = null

    var totalZapped: BigDecimal = BigDecimal.ZERO

    fun load(note: Note?) {
        pollNote = note
        pollEvent = pollNote?.event as PollNoteEvent
        pollOptions = pollEvent?.pollOptions()
        valueMaximum = pollEvent?.getTagInt(VALUE_MAXIMUM)
        valueMinimum = pollEvent?.getTagInt(VALUE_MINIMUM)
        consensusThreshold = pollEvent?.getTagInt(CONSENSUS_THRESHOLD)?.toFloat()?.div(100)?.toBigDecimal()
        closedAt = pollEvent?.getTagInt(CLOSED_AT)

        totalZapped = totalZapped()
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

    fun optionVoteTally(op: Int): BigDecimal {
        return if (totalZapped.compareTo(BigDecimal.ZERO) > 0) {
            zappedPollOptionAmount(op).divide(totalZapped, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }

    fun isPollOptionZappedBy(option: Int, user: User): Boolean {
        if (pollNote?.zaps?.any { it.key.author === user } == true) {
            pollNote!!.zaps
                .any {
                    val event = it.value?.event as? LnZapEvent
                    event?.zappedPollOption() == option && event.zappedRequestAuthor() == user.pubkeyHex
                }
        }
        return false
    }

    fun zappedPollOptionAmount(option: Int): BigDecimal {
        return pollNote?.zaps?.values?.sumOf {
            val event = it?.event as? LnZapEvent
            if (event?.zappedPollOption() == option) {
                event.amount ?: BigDecimal(0)
            } else {
                BigDecimal(0)
            }
        } ?: BigDecimal(0)
    }

    fun totalZapped(): BigDecimal {
        return pollNote?.zaps?.values?.sumOf {
            (it?.event as? LnZapEvent)?.amount ?: BigDecimal(0)
        } ?: BigDecimal(0)
    }
}
