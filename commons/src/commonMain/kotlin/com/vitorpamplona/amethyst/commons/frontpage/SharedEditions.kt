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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask

/**
 * One paper a device of the account printed, as it records it in the schedule's private tags, so
 * the account's other devices show it, open it, and don't print that morning again
 * (`commons/plans/2026-11-20-observer-synced-settings-and-editions.md`):
 *
 * `["edition", day, printedAt, sha256, code, by, blob, key, nonce, headline, weather]`
 *
 * [sha256] is the page's hash as kept (the one a public site would name). [blob] is the hash of its
 * AES-256-GCM ciphertext on the reader's own Blossom servers, opened with [key] and [nonce]; the
 * three are empty when no copy went up, and the record still says the day is printed. [weather] is
 * the forecast kept beside the paper ([WeatherReport], as JSON), which the page itself never holds.
 */
@Immutable
data class EditionRecord(
    /** The printing device's local day, `yyyy-MM-dd`, as it keeps the paper. */
    val day: String,
    val printedAt: Long,
    val sha256: HexKey,
    val code: String,
    /** The install that printed it, as it names itself in a claim ("Pixel 8", "Desktop (Linux)"). */
    val by: String? = null,
    val blob: HexKey? = null,
    val key: HexKey? = null,
    val nonce: HexKey? = null,
    val headline: String? = null,
    val weather: WeatherReport? = null,
) {
    /** Whether an encrypted copy is on the reader's servers to open. */
    val hasCopy: Boolean get() = blob != null && key != null && nonce != null

    fun toTag(): Array<String> =
        arrayOf(
            TAG_NAME,
            day,
            printedAt.toString(),
            sha256,
            code,
            by.orEmpty(),
            blob.orEmpty(),
            key.orEmpty(),
            nonce.orEmpty(),
            headline?.take(HEADLINE_MAX).orEmpty(),
            ObserverWeather.encode(weather).orEmpty(),
        )

    /** The record as a kept edition, for the history: printed on another device, not published. */
    fun asSaved() =
        SavedEdition(
            day = day,
            code = code,
            printedAt = printedAt,
            model = "",
            headline = headline,
            sha256 = sha256,
            printedBy = by,
            weather = weather,
        )

    companion object {
        const val TAG_NAME = "edition"

        /** One record per day, the newest; this many days at most, as many as a device keeps. */
        const val KEEP = FrontPageEditionStore.KEEP

        const val HEADLINE_MAX = 200

        private val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun isTag(tag: Array<String>) = tag.isNotEmpty() && tag[0] == TAG_NAME

        /** [tag] as a record; null when it isn't one, or its day, time or hash can't be read. */
        fun parse(tag: Array<String>): EditionRecord? {
            if (!isTag(tag) || tag.size < 4) return null
            val day = tag[1].takeIf { DAY.matches(it) } ?: return null
            val printedAt = tag[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val sha256 = tag[3].lowercase().takeIf(::isHash) ?: return null

            fun at(i: Int) = tag.getOrNull(i)?.takeIf { it.isNotBlank() }
            val blob = at(6)?.lowercase()?.takeIf(::isHash)
            val key = at(7)?.lowercase()?.takeIf { isHex(it) && it.length == 64 }
            val nonce = at(8)?.lowercase()?.takeIf { isHex(it) && it.length in 24..32 }
            val copy = blob != null && key != null && nonce != null
            return EditionRecord(
                day = day,
                printedAt = printedAt,
                sha256 = sha256,
                code = at(4).orEmpty(),
                by = at(5),
                blob = blob.takeIf { copy },
                key = key.takeIf { copy },
                nonce = nonce.takeIf { copy },
                headline = at(9)?.take(HEADLINE_MAX),
                weather = ObserverWeather.decode(at(10)),
            )
        }

        private fun isHash(value: String) = value.length == 64 && isHex(value)

        private fun isHex(value: String) = value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

/** A page sealed for the reader's servers: the ciphertext to upload, and the record's blob, key and nonce. */
class SealedPage(
    val bytes: ByteArray,
    val blob: HexKey,
    val key: HexKey,
    val nonce: HexKey,
)

/** The account's papers in its schedule: reading and writing the records, and sealing and opening the pages. */
object SharedEditions {
    /** Every record [task] holds, one per day (the newest print of it), newest first. Private tags first, as NIP-FB reads them. */
    fun records(task: ScheduledTask?): List<EditionRecord> {
        if (task == null) return emptyList()
        return (task.privateTags + task.publicTags)
            .mapNotNull(EditionRecord::parse)
            .groupBy { it.day }
            .map { (_, sameDay) -> sameDay.maxBy { it.printedAt } }
            .sortedByDescending { it.printedAt }
    }

    /**
     * [task] holding [record]: it replaces that day's record wherever it was, joins the private
     * side, and only the newest [EditionRecord.KEEP] days stay. Every other tag stays where it was.
     * A record older than every kept day, or than the one its day already holds, changes nothing.
     */
    fun withRecord(
        task: ScheduledTask,
        record: EditionRecord,
    ): ScheduledTask {
        val current = records(task)
        if (current.any { it.day == record.day && it.printedAt > record.printedAt }) return task
        val kept = (current.filter { it.day != record.day } + record).sortedByDescending { it.printedAt }.take(EditionRecord.KEEP)
        if (record !in kept) return task
        // Only the records read here are rewritten: an `edition` tag this code can't read (another
        // app's, a later version's) stays where it was, as NIP-FB asks of every writer.
        val public = task.publicTags.filterNot(::isReadRecord)
        val private = task.privateTags.filterNot(::isReadRecord) + kept.map { it.toTag() }
        return ScheduledTask(public.toTypedArray(), private.toTypedArray())
    }

    private fun isReadRecord(tag: Array<String>) = EditionRecord.parse(tag) != null

    /** [page] (the bytes kept, with the paper's CSP) under a fresh AES-256-GCM key, for the reader's servers. */
    fun seal(page: ByteArray): SealedPage {
        val cipher = AESGCM()
        val bytes = cipher.encrypt(page)
        return SealedPage(bytes, ObserverPublisher.sha256Hex(bytes), cipher.keyBytes.toHexKey(), cipher.nonce.toHexKey())
    }

    /** The page [record] names, from [bytes] downloaded by its blob hash; null unless it decrypts to the page's own hash. */
    fun open(
        record: EditionRecord,
        bytes: ByteArray,
    ): String? {
        val key = record.key ?: return null
        val nonce = record.nonce ?: return null
        if (!ObserverPublisher.sha256Hex(bytes).equals(record.blob, ignoreCase = true)) return null
        val page = AESGCM(key.hexToByteArray(), nonce.hexToByteArray()).decryptOrNull(bytes) ?: return null
        if (!ObserverPublisher.sha256Hex(page).equals(record.sha256, ignoreCase = true)) return null
        return page.decodeToString()
    }

    /**
     * The history with the account's other devices' papers: [history] (this device's kept editions
     * and the Observer sites'), plus each record of a paper none of them has, by hash or by day,
     * that can be opened here. A record without a copy still counts as printed ("already
     * printed?", the status line), but never takes today's place with a paper nobody can open.
     */
    fun mergeRecords(
        history: List<SavedEdition>,
        records: List<EditionRecord>,
    ): List<SavedEdition> {
        val shas = history.mapNotNull { it.sha256?.lowercase() }.toSet()
        val days = history.map { it.day }.toSet()
        val others = records.filter { it.hasCopy && it.sha256 !in shas && it.day !in days }.map { it.asSaved() }
        return if (others.isEmpty()) history else (history + others).sortedByDescending { it.day }
    }
}
