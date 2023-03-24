package com.vitorpamplona.amethyst.ui.note

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.*
import java.util.*

class PollNoteViewModel : ViewModel() {
    var account: Account? = null
    var pollNote: Note? = null

    var pollEvent: PollNoteEvent? = null
    var valueMaximum: Int? = null
    var valueMinimum: Int? = null
    var closedAt: Int? = null
    var consensusThreshold: Int? = null

    fun load(note: Note?) {
        pollNote = note
        pollEvent = pollNote?.event as PollNoteEvent
        valueMaximum = pollEvent?.getTagInt(VALUE_MAXIMUM)
        valueMinimum = pollEvent?.getTagInt(VALUE_MINIMUM)
        consensusThreshold = pollEvent?.getTagInt(CONSENSUS_THRESHOLD)
        closedAt = pollEvent?.getTagInt(CLOSED_AT)
    }

    val isPollClosed: Boolean = closedAt?.let { // allow 2 minute leeway for zap to propagate
        pollNote?.createdAt()?.plus(it * (86400 + 120))!! > Date().time / 1000
    } == true

    val isVoteAmountAtomic = valueMaximum != null && valueMinimum != null && valueMinimum == valueMaximum

    fun voteAmountPlaceHolderText(ctx: Context): String = if (valueMinimum == null && valueMaximum == null) {
        ctx.getString(R.string.sats)
    } else if (valueMinimum == null) {
        "1—$valueMaximum " + ctx.getString(R.string.sats)
    } else if (valueMaximum == null) {
        ">$valueMinimum " + ctx.getString(R.string.sats)
    } else {
        "$valueMinimum—$valueMaximum " + ctx.getString(R.string.sats)
    }

    fun amount(textAmount: String) = if (textAmount.isEmpty()) { null } else {
        try {
            textAmount.toLong()
        } catch (e: Exception) { null }
    }

    fun isValidAmount(amount: Long?): Boolean {
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
}
