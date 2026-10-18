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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dvm_offline
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_encrypted_request
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_encrypted_result
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_heartbeat_title
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_content_discovery
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_content_search
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_event_count
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_image_generation
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_image_to_video
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_malware_scan
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_op_return
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_people_search
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_pow
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_publish_schedule
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_summarization
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_text_extraction
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_text_generation
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_text_to_speech
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_timestamping
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_translation
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_user_discovery
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_video_conversion
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_job_video_translation
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_amount
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_bid
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_count
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_customer
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_events
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_for
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_output
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_providers
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_result_relays
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_results
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_state
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_label_transaction
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_malware_clean
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_malware_flagged
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_online
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_group
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_length
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_max_results
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_max_tokens
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_model
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_negative_prompt
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_pow
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_range
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_ratio
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_relays
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_since
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_size
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_temperature
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_param_until
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_request_title
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_result_title
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_sats
import com.vitorpamplona.amethyst.commons.resources.kc_dvm_subtitle
import com.vitorpamplona.amethyst.commons.resources.language
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.DvmRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.DvmResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.relays
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.nip90Dvms.eventCount.DvmEventCountRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventCount.DvmEventCountResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPowDelegation.DvmEventPowDelegationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPowDelegation.DvmEventPowDelegationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.DvmEventTimestampingRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.DvmEventTimestampingResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.imageGeneration.DvmImageGenerationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.imageToVideo.DvmImageToVideoRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.malwareScanning.DvmMalwareScanRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.malwareScanning.DvmMalwareScanResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.opReturn.DvmOpReturnRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.opReturn.DvmOpReturnResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.DvmPeopleSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.DvmPeopleSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.firstAmount
import com.vitorpamplona.quartz.nip90Dvms.summarization.DvmSummarizationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmInputEventIds
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmParams
import com.vitorpamplona.quartz.nip90Dvms.tags.inputs
import com.vitorpamplona.quartz.nip90Dvms.tags.outputMimeType
import com.vitorpamplona.quartz.nip90Dvms.textExtraction.DvmTextExtractionRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textGeneration.DvmTextGenerationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textToSpeech.DvmTextToSpeechRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.translation.DvmTranslationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.DvmUserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.DvmUserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.videoConversion.DvmVideoConversionRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.videoTranslation.DvmVideoTranslationRequestEvent
import org.jetbrains.compose.resources.StringResource

/*
 * NIP-90 Data Vending Machines: one card for every job request (5000-5999), one for every job
 * result (6000-6999), and one for the DVM heartbeat. The request and result cards share a frame
 * and differ only in the job's name and icon, looked up by the request kind.
 *
 * Job feedback (7000, DvmStatusEvent) and content-discovery results (6300) keep their own
 * renderers, which NoteCompose dispatches before the base-class branches that land here.
 */

/** How many referenced events a card quotes at most, so one result cannot flood the feed. */
private const val MAX_QUOTED = 3

private const val MAX_RESULT_USERS = 5

private const val NIP90_RESULT_KIND_OFFSET = 1000

private val HEX64 = Regex("[0-9a-fA-F]{64}")

private class DvmJob(
    val name: StringResource,
    val icon: MaterialSymbol,
)

/** The name and icon of the job a request kind (5000-5999) asks for, or null for an unknown job. */
private fun dvmJob(requestKind: Int): DvmJob? =
    when (requestKind) {
        DvmTextExtractionRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_text_extraction, MaterialSymbols.Description)
        DvmSummarizationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_summarization, MaterialSymbols.AutoMirrored.Article)
        DvmTranslationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_translation, MaterialSymbols.Translate)
        DvmTextGenerationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_text_generation, MaterialSymbols.AutoAwesome)
        DvmImageGenerationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_image_generation, MaterialSymbols.Image)
        DvmVideoConversionRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_video_conversion, MaterialSymbols.VideoSettings)
        DvmVideoTranslationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_video_translation, MaterialSymbols.Movie)
        DvmImageToVideoRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_image_to_video, MaterialSymbols.VideoLibrary)
        DvmTextToSpeechRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_text_to_speech, MaterialSymbols.AutoMirrored.VolumeUp)
        DvmContentDiscoveryRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_content_discovery, MaterialSymbols.Explore)
        DvmUserDiscoveryRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_user_discovery, MaterialSymbols.People)
        DvmContentSearchRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_content_search, MaterialSymbols.Search)
        DvmPeopleSearchRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_people_search, MaterialSymbols.TravelExplore)
        DvmEventCountRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_event_count, MaterialSymbols.Numbers)
        DvmMalwareScanRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_malware_scan, MaterialSymbols.Security)
        DvmEventTimestampingRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_timestamping, MaterialSymbols.OpenTimestamps)
        DvmOpReturnRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_op_return, MaterialSymbols.CurrencyBitcoin)
        DvmEventPublishScheduleRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_publish_schedule, MaterialSymbols.Schedule)
        DvmEventPowDelegationRequestEvent.KIND -> DvmJob(Res.string.kc_dvm_job_pow, MaterialSymbols.Manufacturing)
        else -> null
    }

/** NIP-90: a bare `["encrypted"]` tag means the inputs/params (or the result) are NIP-04 ciphertext. */
private fun TagArray.isDvmEncrypted(): Boolean = any { it.isNotEmpty() && it[0] == "encrypted" }

/** NIP-90 `["bid", <msats>]`: the most the customer will pay, in sats. */
private fun TagArray.dvmBidSats(): Long? = firstOrNull { it.size > 1 && it[0] == "bid" }?.get(1)?.toLongOrNull()?.div(1000)

/** Everything a request card shows, parsed once per event. */
private class DvmRequestInfo(
    val texts: List<String>,
    val urls: List<String>,
    val eventIds: List<HexKey>,
    val eventJsons: List<String>,
    val filterJson: String?,
    val params: List<Pair<String, String>>,
    val users: List<HexKey>,
    val output: String?,
    val bidSats: Long?,
    val resultRelays: String?,
    val providers: List<HexKey>,
)

private fun parseRequest(event: DvmRequestEvent): DvmRequestInfo {
    val inputs = event.tags.inputs()
    // These two kinds carry whole signed-event JSONs as their `text` inputs, not human text.
    val eventJsons =
        when (event) {
            is DvmEventPowDelegationRequestEvent -> event.eventJsons()
            is DvmEventPublishScheduleRequestEvent -> event.eventJsons()
            else -> emptyList()
        }
    val texts =
        if (eventJsons.isEmpty()) {
            inputs.filter { it.type == "text" || it.type == "prompt" }.map { it.value }
        } else {
            emptyList()
        }

    val users = mutableListOf<HexKey>()
    val params = LinkedHashMap<String, MutableList<String>>()
    event.tags.dvmParams().forEach { (key, values) ->
        when (key) {
            "user" -> values.filterTo(users) { it.isValid() }
            "users" -> values.forEach { value -> HEX64.findAll(value).mapTo(users) { it.value.lowercase() } }
            else -> {
                val value =
                    when (key) {
                        "since", "until" -> values.firstOrNull()?.toLongOrNull()?.let { formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT) }
                        "range" -> values.joinToString(" – ")
                        else -> values.joinToString(" ")
                    }
                if (!value.isNullOrBlank()) params.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
    }

    return DvmRequestInfo(
        texts = texts,
        urls = inputs.filter { it.type == "url" }.map { it.value },
        eventIds = event.tags.dvmInputEventIds().distinct(),
        eventJsons = eventJsons,
        filterJson = (event as? DvmEventCountRequestEvent)?.filterJson()?.ifBlank { null },
        params = params.map { (key, values) -> key to values.joinToString(", ") },
        users = users.distinct(),
        output = event.tags.outputMimeType(),
        bidSats = event.tags.dvmBidSats(),
        resultRelays =
            event.tags
                .relays()
                ?.relays
                ?.joinToString(", ") { it.displayUrl() },
        providers = event.serviceProviders(),
    )
}

@Composable
private fun dvmParamLabel(key: String): String =
    when (key) {
        "language" -> stringRes(Res.string.language)
        "model" -> stringRes(Res.string.kc_dvm_param_model)
        "max_results" -> stringRes(Res.string.kc_dvm_param_max_results)
        "max_tokens" -> stringRes(Res.string.kc_dvm_param_max_tokens)
        "temperature" -> stringRes(Res.string.kc_dvm_param_temperature)
        "length" -> stringRes(Res.string.kc_dvm_param_length)
        "size" -> stringRes(Res.string.kc_dvm_param_size)
        "ratio" -> stringRes(Res.string.kc_dvm_param_ratio)
        "negative_prompt" -> stringRes(Res.string.kc_dvm_param_negative_prompt)
        "pow" -> stringRes(Res.string.kc_dvm_param_pow)
        "range" -> stringRes(Res.string.kc_dvm_param_range)
        "since" -> stringRes(Res.string.kc_dvm_param_since)
        "until" -> stringRes(Res.string.kc_dvm_param_until)
        "relay", "relays" -> stringRes(Res.string.kc_dvm_param_relays)
        "group" -> stringRes(Res.string.kc_dvm_param_group)
        // Any other param is a DVM-specific protocol word, shown as the DVM wrote it.
        else -> key
    }

/** A labelled list of people (the providers asked, the customer, the user a job is for). */
@Composable
private fun DvmPeople(
    label: StringResource,
    pubKeys: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (pubKeys.isEmpty()) return
    Text(
        text = stringRes(label),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    KindCardUsers(pubKeys, accountViewModel, nav)
}

/** A NIP-90 job request (5000-5999): the job, its inputs and params, the bid, and the DVMs asked to run it. */
@Composable
fun RenderDvmRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DvmRequestEvent ?: return
    val job = remember(event) { dvmJob(event.kind) }
    val title = job?.let { stringRes(Res.string.kc_dvm_request_title, stringRes(it.name)) } ?: kindName(event.kind)
    val encrypted = remember(event) { event.tags.isDvmEncrypted() }

    if (encrypted) {
        // Inputs and params are ciphertext for the chosen DVM; only who was asked is public.
        val providers = remember(event) { event.serviceProviders() }
        KindCard(
            icon = MaterialSymbols.Lock,
            title = title,
            subtitle = stringRes(Res.string.kc_dvm_encrypted_request),
        ) {
            DvmPeople(Res.string.kc_dvm_label_providers, providers, accountViewModel, nav)
        }
        return
    }

    val info = remember(event) { parseRequest(event) }

    KindCard(
        icon = job?.icon ?: MaterialSymbols.Assistant,
        title = title,
        subtitle = stringRes(Res.string.kc_dvm_subtitle),
    ) {
        info.texts.forEach { KindCardText(baseNote, it, backgroundColor, accountViewModel, nav, quotesLeft) }
        // Image and media URLs render as previews through the rich text viewer.
        info.urls.forEach { KindCardText(baseNote, it, backgroundColor, accountViewModel, nav, quotesLeft) }
        if (info.eventJsons.isNotEmpty()) {
            KindCardField(stringRes(Res.string.kc_dvm_label_events), info.eventJsons.size.toString())
            KindCardCode(info.eventJsons.first(), maxLines = 4)
        }
        info.filterJson?.let { KindCardCode(it, maxLines = 6) }
        info.eventIds.take(MAX_QUOTED).forEach { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }

        info.params.forEach { (key, value) -> KindCardField(dvmParamLabel(key), value) }
        KindCardField(stringRes(Res.string.kc_dvm_label_output), info.output)
        info.bidSats?.let { KindCardField(stringRes(Res.string.kc_dvm_label_bid), stringRes(Res.string.kc_dvm_sats, it.toString())) }
        KindCardField(stringRes(Res.string.kc_dvm_label_result_relays), info.resultRelays, maxLines = 2)

        DvmPeople(Res.string.kc_dvm_label_for, info.users, accountViewModel, nav)
        DvmPeople(Res.string.kc_dvm_label_providers, info.providers, accountViewModel, nav)
    }
}

/**
 * A NIP-90 job result (6000-6999): what the DVM produced (text, media links, found events or
 * people, a count, a verdict), the price it asks, and the request it answers, quoted.
 */
@Composable
fun RenderDvmResult(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DvmResponseEvent ?: return
    val job = remember(event) { dvmJob(event.kind - NIP90_RESULT_KIND_OFFSET) }
    val title = job?.let { stringRes(Res.string.kc_dvm_result_title, stringRes(it.name)) } ?: kindName(event.kind)
    val encrypted = remember(event) { event.tags.isDvmEncrypted() }
    val requestId = remember(event) { event.jobRequestId() }
    val customer = remember(event) { listOfNotNull(event.customer()) }

    if (encrypted) {
        KindCard(
            icon = MaterialSymbols.Lock,
            title = title,
            subtitle = stringRes(Res.string.kc_dvm_encrypted_result),
        ) {
            if (requestId == null) DvmPeople(Res.string.kc_dvm_label_customer, customer, accountViewModel, nav)
            requestId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
        }
        return
    }

    val amountSats =
        remember(event) {
            event.tags
                .firstAmount()
                ?.amount
                ?.div(1000)
        }

    if (event is DvmMalwareScanResponseEvent) {
        val clean = remember(event) { event.isClean() }
        KindCard(
            icon = if (clean) MaterialSymbols.CheckCircle else MaterialSymbols.Warning,
            title = title,
            subtitle = if (clean) stringRes(Res.string.kc_dvm_malware_clean) else stringRes(Res.string.kc_dvm_malware_flagged, event.content),
            iconTint = if (clean) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        ) {
            amountSats?.let { KindCardField(stringRes(Res.string.kc_dvm_label_amount), stringRes(Res.string.kc_dvm_sats, it.toString())) }
            requestId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
        }
        return
    }

    KindCard(
        icon = job?.icon ?: MaterialSymbols.Assistant,
        title = title,
        subtitle = stringRes(Res.string.kc_dvm_subtitle),
    ) {
        DvmResultPayload(baseNote, event, quotesLeft, backgroundColor, accountViewModel, nav)
        amountSats?.let { KindCardField(stringRes(Res.string.kc_dvm_label_amount), stringRes(Res.string.kc_dvm_sats, it.toString())) }
        if (requestId == null) DvmPeople(Res.string.kc_dvm_label_customer, customer, accountViewModel, nav)
        requestId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** What the DVM produced, drawn by what the result kind's `content` holds. */
@Composable
private fun DvmResultPayload(
    baseNote: Note,
    event: DvmResponseEvent,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (event) {
        is DvmContentSearchResponseEvent -> {
            val found = remember(event) { event.resultTags() }
            KindCardField(stringRes(Res.string.kc_dvm_label_results), found.size.toString())
            found.take(MAX_QUOTED).forEach { tag ->
                ETag.parseId(tag)?.let {
                    KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav)
                } ?: ATag.parseAddress(tag)?.let {
                    KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }
        }

        is DvmPeopleSearchResponseEvent -> {
            val people = remember(event) { event.resultTags().mapNotNull(PTag::parseKey).distinct() }
            DvmFoundPeople(people, accountViewModel, nav)
        }

        is DvmUserDiscoveryResponseEvent -> {
            val people = remember(event) { event.resultTags().mapNotNull(PTag::parseKey).distinct() }
            DvmFoundPeople(people, accountViewModel, nav)
        }

        is DvmEventCountResponseEvent -> {
            KindCardField(stringRes(Res.string.kc_dvm_label_count), event.count()?.toString() ?: event.content)
        }

        is DvmEventTimestampingResponseEvent -> {
            event.otsEventId()?.takeIf { it.isValid() }?.let {
                KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }

        is DvmEventPublishScheduleResponseEvent -> {
            event.publishedEventId()?.takeIf { it.isValid() }?.let {
                KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }

        is DvmOpReturnResponseEvent -> {
            KindCardField(stringRes(Res.string.kc_dvm_label_transaction), event.transactionId(), monospace = true)
        }

        // The mined event itself, as signed JSON: that is the whole product of the job.
        is DvmEventPowDelegationResponseEvent -> {
            KindCardCode(event.content, maxLines = 6)
        }

        // Text and media results (summaries, translations, generated text, image/video/audio URLs).
        else -> {
            KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        }
    }
}

@Composable
private fun DvmFoundPeople(
    people: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    KindCardField(stringRes(Res.string.kc_dvm_label_results), people.size.toString())
    KindCardUsers(people, accountViewModel, nav, max = MAX_RESULT_USERS)
}

/** A DVM heartbeat (11998): the DVM's status line, whether the beat is still fresh, and its NIP-89 announcement. */
@Composable
fun RenderDvmHeartbeat(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DvmHeartbeatEvent ?: return
    val fresh = remember(event) { event.isFreshAt() }
    val announcement =
        remember(event) {
            event.dTag().ifBlank { null }?.let { Address(AppDefinitionEvent.KIND, event.pubKey, it) }
        }

    KindCard(
        icon = MaterialSymbols.Sensors,
        title = stringRes(Res.string.kc_dvm_heartbeat_title),
        subtitle = event.status(),
        iconTint = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        KindCardField(
            stringRes(Res.string.kc_dvm_label_state),
            stringRes(if (fresh) Res.string.kc_dvm_online else Res.string.dvm_offline),
        )
        announcement?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}
