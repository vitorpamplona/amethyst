package com.vitorpamplona.amethyst.service.model.zaps

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.LnZapEventInterface
import java.math.BigDecimal

object UserZaps {
    fun forProfileFeed(zaps: Map<Note, Note?>?): List<Pair<Note, Note>> {
        if (zaps == null) return emptyList()

        val notesGroupedByAuthor = groupNotesByAuthor(zaps.filter { it.value != null })
        val aggregateZapAmounts = aggregateZapAmounts(notesGroupedByAuthor)

        val sortedZaps = aggregateZapAmounts
            .toList()
            .sortedBy { (it.second?.event as? LnZapEventInterface)?.amount()?.total() }
            .reversed()

        return (sortedZaps) as List<Pair<Note, Note>>
    }

    private fun groupNotesByAuthor(zaps: Map<Note, Note?>): Map<String, Map<Note, Note?>> {
        val authorPubKeyToZapMap = mutableMapOf<String, MutableMap<Note, Note?>>()

        for ((k, v) in zaps) {
            val authorKey = k.author?.pubkeyHex.toString()
            if (!authorPubKeyToZapMap.contains(authorKey)) {
                authorPubKeyToZapMap[authorKey] = mutableMapOf()
            }
            authorPubKeyToZapMap[k.author?.pubkeyHex.toString()]?.put(k, v)
        }

        return authorPubKeyToZapMap
    }

    private fun aggregateZapAmounts(notesGroupedByAuthor: Map<String, Map<Note, Note?>>): Map<Note, Note?> {
        val authorToZap = mutableMapOf<Note, Note?>()

        for ((_, notes) in notesGroupedByAuthor) {
            val firstZapNote = notes.values.first()

            ((firstZapNote?.event as LnZapEventInterface).amount() as ZapAmount).amount =
                calculateTotalAmount(notes)

            authorToZap[notes.keys.first()] = firstZapNote
        }
        return authorToZap
    }

    private fun calculateTotalAmount(notes: Map<Note, Note?>): BigDecimal {
        var totalAmount = BigDecimal(0)
        for ((_, note) in notes) {
            val t = (note?.event as LnZapEventInterface).amount().total()
            if (t !== null) {
                totalAmount = totalAmount.add(t)
            }
        }
        return totalAmount
    }
}
