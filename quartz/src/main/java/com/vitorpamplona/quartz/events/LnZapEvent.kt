package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil

@Immutable
class LnZapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : LnZapEventInterface, Event(id, pubKey, createdAt, kind, tags, content, sig) {
    // This event is also kept in LocalCache (same object)
    @Transient val zapRequest: LnZapRequestEvent?
    override fun containedPost(): LnZapRequestEvent? = try {
        description()?.ifBlank { null }?.let {
            fromJson(it)
        } as? LnZapRequestEvent
    } catch (e: Exception) {
        Log.w("LnZapEvent", "Failed to Parse Contained Post ${description()} in event ${id}", e)
        null
    }

    init {
        zapRequest = containedPost()
    }

    override fun zappedPost() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    override fun zappedAuthor() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }

    override fun zappedPollOption(): Int? = try {
        zapRequest?.tags?.firstOrNull { it.size > 1 && it[0] == POLL_OPTION }?.get(1)?.toInt()
    } catch (e: Exception) {
        Log.e("LnZapEvent", "ZappedPollOption failed to parse", e)
        null
    }

    override fun zappedRequestAuthor(): String? = zapRequest?.pubKey()

    override fun amount() = amount

    // Keeps this as a field because it's a heavier function used everywhere.
    val amount by lazy {
        try {
            lnInvoice()?.let { LnInvoiceUtil.getAmountInSats(it) }
        } catch (e: Exception) {
            Log.e("LnZapEvent", "Failed to Parse LnInvoice ${lnInvoice()}", e)
            null
        }
    }
    override fun content(): String {
        return content
    }

    fun lnInvoice() = tags.firstOrNull { it.size > 1 && it[0] == "bolt11" }?.get(1)

    private fun description() = tags.firstOrNull { it.size > 1 && it[0] == "description" }?.get(1)

    companion object {
        const val kind = 9735
    }

    enum class ZapType() {
        PUBLIC,
        PRIVATE,
        ANONYMOUS,
        NONZAP
    }
}
