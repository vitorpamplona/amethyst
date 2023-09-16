package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

class SplitItem<T>(val key: T) {
    var percentage by mutableStateOf(0f)
}

class Split<T>() {
    var items: List<SplitItem<T>> by mutableStateOf(emptyList())

    fun addItem(key: T): Int {
        val wasEqualSplit = isEqualSplit()
        val newItem = SplitItem(key)
        items = items.plus(newItem)

        if (wasEqualSplit) {
            forceEqualSplit()
        } else {
            updatePercentage(items.lastIndex, equalSplit())
        }

        return items.lastIndex
    }

    fun equalSplit() = 1f / items.size

    fun isEqualSplit(): Boolean {
        val expectedPercentage = equalSplit()
        return items.all { (it.percentage - expectedPercentage) < 0.01 }
    }

    fun forceEqualSplit() {
        val correctPercentage = equalSplit()
        items.forEach {
            it.percentage = correctPercentage
        }
    }

    fun updatePercentage(index: Int, percentage: Float) {
        if (items.isEmpty()) return

        val splitItem = items.getOrNull(index) ?: return

        if (items.size == 1) {
            splitItem.percentage = 1f
        } else {
            splitItem.percentage = percentage

            println("Update ${items[index].key} to $percentage")

            val othersMustShare = 1.0f - splitItem.percentage

            val othersHave = items.sumOf {
                if (it == splitItem) 0.0 else it.percentage.toDouble()
            }.toFloat()

            if (abs(othersHave - othersMustShare) < 0.01) return // nothing to do

            println("Others Must Share $othersMustShare but have $othersHave")

            bottomUpAdjustment(othersMustShare, othersHave, index)
        }
    }

    private fun bottomUpAdjustment(othersMustShare: Float, othersHave: Float, exceptForIndex: Int) {
        var needToRemove = othersHave - othersMustShare
        if (needToRemove > 0) {
            for (i in items.indices.reversed()) {
                if (i == exceptForIndex) continue // do not update the current item

                if (needToRemove < items[i].percentage) {
                    val oldValue = items[i].percentage
                    items[i].percentage -= needToRemove
                    needToRemove = 0f
                    println("- Updating ${items[i].key} from $oldValue to ${items[i].percentage - needToRemove}. $needToRemove left")
                } else {
                    val oldValue = items[i].percentage
                    needToRemove -= items[i].percentage
                    items[i].percentage = 0f
                    println("- Updating ${items[i].key} from $oldValue to ${0}. $needToRemove left")
                }

                if (needToRemove < 0.01) {
                    break
                }
            }
        } else if (needToRemove < 0) {
            if (items.lastIndex == exceptForIndex) {
                items[items.lastIndex - 1].percentage += -needToRemove
            } else {
                items.last().percentage += -needToRemove
            }
        }
    }
}
