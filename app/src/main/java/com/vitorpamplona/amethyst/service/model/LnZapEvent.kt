package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.service.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.service.relays.Client
import java.math.BigDecimal

class LnZapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : LnZapEventInterface, Event(id, pubKey, createdAt, kind, tags, content, sig) {

    override fun zappedPost() = tags
        .filter { it.firstOrNull() == "e" }
        .mapNotNull { it.getOrNull(1) }

    override fun zappedPollOption(): Int? = containedPost()?.tags
        ?.filter { it.firstOrNull() == POLL_OPTION }
        ?.getOrNull(0)?.getOrNull(1)?.toInt()

    override fun zappedAuthor() = tags
        .filter { it.firstOrNull() == "p" }
        .mapNotNull { it.getOrNull(1) }

    override fun zappedRequestAuthor(): String? = containedPost()?.pubKey()

    override fun amount(): BigDecimal? {
        return amount
    }

    // Keeps this as a field because it's a heavier function used everywhere.
    val amount by lazy {
        try {
            lnInvoice()?.let { LnInvoiceUtil.getAmountInSats(it) }
        } catch (e: Exception) {
            Log.e("LnZapEvent", "Failed to Parse LnInvoice ${description()}", e)
            null
        }
    }
    override fun message(): String {
        return message
    }
    val message = content

    override fun containedPost(): Event? = try {
        description()?.ifBlank { null }?.let {
            fromJson(it, Client.lenient)
        }
    } catch (e: Exception) {
        Log.e("LnZapEvent", "Failed to Parse Contained Post ${description()}", e)
        null
    }

    private fun lnInvoice(): String? = tags
        .filter { it.firstOrNull() == "bolt11" }
        .mapNotNull { it.getOrNull(1) }
        .firstOrNull()

    private fun description(): String? = tags
        .filter { it.firstOrNull() == "description" }
        .mapNotNull { it.getOrNull(1) }
        .firstOrNull()

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
