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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.favorites.BrowserIconRegistry
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * Turns a pending [NappletRequest] into the human-readable [NappletConsentInfo] the consent dialog
 * shows — the applet's title, the capability label, and a per-operation summary (e.g. a note preview
 * or a sat amount). Localized via app resources.
 *
 * Reads [account] only to diff a proposed replaceable list (follows, relays, mutes) against the copy
 * already cached there, so the dialog can say what a signature would actually change. It never signs,
 * mutates, or exposes account state — the values it reads are the user's own public lists.
 */
class NappletConsentSummary(
    private val context: Context,
    private val account: Account,
) {
    fun info(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): NappletConsentInfo {
        val untitled = context.getString(R.string.napplet_fallback_title, identity.authorPubKey.take(8))
        val (title, iconUrl) =
            if (identity.authorPubKey == "browser") {
                val host = OmniboxInput.hostOf(identity.identifier) ?: identity.identifier
                host to BrowserIconRegistry.iconModelFor(host)
            } else {
                resolveNappletMeta(identity.authorPubKey, identity.identifier, untitled)
            }
        val consequence = consequenceFor(request)
        return NappletConsentInfo(
            appletTitle = title,
            coordinate = identity.coordinate,
            capabilityLabel = context.getString(capability.labelRes()),
            operationSummary = listOfNotNull(summaryFor(request).ifBlank { null }, consequence?.text).joinToString("\n\n"),
            allowAlways = capability.canGrantAlways,
            iconUrl = iconUrl,
            rawData = rawEventFor(request),
            subject = consequence?.subject,
        )
    }

    /**
     * Pretty-prints the unsigned event behind the consent dialog's "Show Event" toggle. Only the
     * signing requests carry one; everything else has nothing to disclose.
     */
    private fun rawEventFor(request: NappletRequest): String =
        when (request) {
            is NappletRequest.Publish -> rawEvent(request.kind, request.tags, request.content, null)
            is NappletRequest.SignEvent -> rawEvent(request.kind, request.tags, request.content, request.createdAt)
            else -> ""
        }

    private fun rawEvent(
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        createdAt: Long?,
    ): String =
        buildString {
            append("kind: ").append(kind).append('\n')
            createdAt?.let { append("created_at: ").append(it).append('\n') }
            append("tags:")
            if (tags.isEmpty()) {
                append(" []\n")
            } else {
                append('\n')
                tags.fastForEach { tag -> append("  ").append(tag.joinToString(", ", "[", "]")).append('\n') }
            }
            append("content: ").append(content.ifEmpty { "(empty)" })
        }

    /** A consequence line, plus the single account it is about when the change names exactly one. */
    private data class Consequence(
        val text: String,
        val subject: ConsentSubject? = null,
    )

    /**
     * A plain-language warning for the kinds whose payload lives entirely in the tags. Without this
     * the dialog reads "publish a kind 3 event" while the user is actually about to replace their
     * whole social graph — the summary would be technically true and practically useless.
     */
    private fun consequenceFor(request: NappletRequest): Consequence? =
        when (request) {
            is NappletRequest.Publish -> consequenceFor(request.kind, request.tags)
            is NappletRequest.SignEvent -> consequenceFor(request.kind, request.tags)
            else -> null
        }

    private fun consequenceFor(
        kind: Int,
        tags: Array<Array<String>>,
    ): Consequence? =
        when (kind) {
            ContactListEvent.KIND ->
                diffOf(
                    current = account.kind3FollowList.getFollowListEvent()?.tags,
                    proposed = tags,
                    tagName = "p",
                    template = R.string.napplet_consent_diff_follows,
                    added = R.plurals.napplet_consent_diff_follow_added,
                    removed = R.plurals.napplet_consent_diff_follow_removed,
                    oneAdded = R.string.napplet_consent_diff_follow_one,
                    oneRemoved = R.string.napplet_consent_diff_unfollow_one,
                )
            AdvertisedRelayListEvent.KIND ->
                diffOf(
                    current = account.nip65RelayList.getNIP65RelayList()?.tags,
                    proposed = tags,
                    tagName = "r",
                    template = R.string.napplet_consent_diff_relays,
                    added = R.plurals.napplet_consent_diff_relay_added,
                    removed = R.plurals.napplet_consent_diff_relay_removed,
                )
            // Public entries only: a mute list also carries encrypted ones, which are not in `tags`
            // and so cannot be diffed here.
            MuteListEvent.KIND ->
                diffOf(
                    current = account.muteList.getMuteList()?.tags,
                    proposed = tags,
                    tagName = "p",
                    template = R.string.napplet_consent_diff_mutes,
                    added = R.plurals.napplet_consent_diff_mute_added,
                    removed = R.plurals.napplet_consent_diff_mute_removed,
                    oneAdded = R.string.napplet_consent_diff_mute_one,
                    oneRemoved = R.string.napplet_consent_diff_unmute_one,
                )
            // Deletions have no prior version to compare against — the tags are the whole request.
            DeletionEvent.KIND ->
                pluralFor(R.plurals.napplet_consent_effect_deletes, countTag(tags, "e") + countTag(tags, "a"))
                    ?.let { Consequence(it) }
            // Any other kind: at least tell the user tags exist and can be inspected, so an empty
            // content preview never reads as "there is nothing else here".
            else ->
                if (tags.isNotEmpty()) {
                    pluralFor(R.plurals.napplet_consent_effect_tags, tags.size)?.let { Consequence(it) }
                } else {
                    null
                }
        }

    /**
     * Describes what a proposed replaceable list changes relative to the copy already on the account.
     * A bare total ("a list of 12 accounts") hides the dangerous case: the alarming edit is a list
     * that silently drops 130 follows, and only a diff surfaces that. Falls back to the total when
     * nothing is cached to compare against.
     */
    private fun diffOf(
        current: Array<Array<String>>?,
        proposed: Array<Array<String>>,
        tagName: String,
        @StringRes template: Int,
        @PluralsRes added: Int,
        @PluralsRes removed: Int,
        @StringRes oneAdded: Int? = null,
        @StringRes oneRemoved: Int? = null,
    ): Consequence {
        val next = valuesOf(proposed, tagName)
        val previous =
            current?.let { valuesOf(it, tagName) }
                ?: return Consequence(pluralStringRes(context, R.plurals.napplet_consent_diff_no_baseline, next.size, next.size))

        val addedKeys = next.filter { it !in previous }
        val removedKeys = previous.filter { it !in next }
        if (addedKeys.isEmpty() && removedKeys.isEmpty()) {
            return Consequence(context.getString(R.string.napplet_consent_diff_none))
        }

        // The overwhelmingly common edit is a single follow/unfollow. Naming and picturing that one
        // account is far more use than "follows 1 new account" — the user can tell at a glance
        // whether it is who they expected.
        if (oneAdded != null && addedKeys.size == 1 && removedKeys.isEmpty()) {
            subjectOf(addedKeys.first())?.let { return Consequence(context.getString(oneAdded, it.name), it) }
        }
        if (oneRemoved != null && removedKeys.size == 1 && addedKeys.isEmpty()) {
            subjectOf(removedKeys.first())?.let { return Consequence(context.getString(oneRemoved, it.name), it) }
        }

        val parts = listOfNotNull(pluralFor(added, addedKeys.size), pluralFor(removed, removedKeys.size))
        val summary =
            if (parts.size == 2) {
                context.getString(R.string.napplet_consent_diff_joiner, parts[0], parts[1])
            } else {
                parts.first()
            }
        return Consequence(context.getString(template, summary))
    }

    /** Resolves a pubkey to a name + picture for the dialog, or null when the user isn't cached. */
    private fun subjectOf(pubKey: String): ConsentSubject? {
        val user = account.cache.getUserIfExists(pubKey) ?: return null
        return ConsentSubject(
            pubKey = pubKey,
            name = user.toBestDisplayName(),
            pictureUrl = user.profilePicture(),
        )
    }

    /** The distinct values of every `[tagName, value, …]` tag. */
    private fun valuesOf(
        tags: Array<Array<String>>,
        tagName: String,
    ): Set<String> {
        val out = mutableSetOf<String>()
        tags.fastForEach { if (it.size > 1 && it[0] == tagName) out.add(it[1]) }
        return out
    }

    private fun pluralFor(
        resId: Int,
        count: Int,
    ): String? = if (count <= 0) null else pluralStringRes(context, resId, count, count)

    private fun countTag(
        tags: Array<Array<String>>,
        name: String,
    ): Int = tags.count { it.isNotEmpty() && it[0] == name }

    private fun summaryFor(request: NappletRequest): String =
        when (request) {
            is NappletRequest.GetPublicKey -> context.getString(R.string.napplet_consent_get_pubkey)
            is NappletRequest.IdentityRead -> context.getString(R.string.napplet_consent_identity_read)
            is NappletRequest.Publish -> {
                val preview = request.content.take(160).trim()
                if (preview.isEmpty()) {
                    context.getString(R.string.napplet_consent_publish, request.kind)
                } else {
                    context.getString(R.string.napplet_consent_publish_preview, request.kind) + "\n“$preview”"
                }
            }
            is NappletRequest.SignEvent -> {
                val preview = request.content.take(160).trim()
                if (preview.isEmpty()) {
                    context.getString(R.string.napplet_consent_sign, request.kind)
                } else {
                    context.getString(R.string.napplet_consent_sign, request.kind) + "\n“$preview”"
                }
            }
            is NappletRequest.PublishEncrypted -> context.getString(R.string.napplet_consent_publish_encrypted)
            is NappletRequest.QueryEvents, is NappletRequest.Subscribe -> context.getString(R.string.napplet_consent_query)
            is NappletRequest.StorageGet, is NappletRequest.StorageSet, is NappletRequest.StorageRemove, is NappletRequest.StorageKeys ->
                context.getString(R.string.napplet_consent_storage)
            is NappletRequest.NotifyCreate -> {
                val preview = request.title.take(80).trim()
                if (preview.isEmpty()) context.getString(R.string.napplet_consent_notify) else context.getString(R.string.napplet_consent_notify) + "\n“$preview”"
            }
            is NappletRequest.NotifyList, is NappletRequest.NotifyDismiss -> context.getString(R.string.napplet_consent_notify)
            is NappletRequest.PayInvoice -> {
                // getAmountInSats returns ZERO (not null, not a throw) for an amountless BOLT11, so a
                // naive read renders "pay 0 sats" — telling the user a payment is free when the amount
                // is in fact unspecified and decided by the payee. Treat non-positive as "no amount".
                val sats = runCatching { LnInvoiceUtil.getAmountInSats(request.invoice).toLong() }.getOrNull()
                if (sats != null && sats > 0) {
                    pluralStringRes(context, R.plurals.napplet_consent_pay_amount, sats.toInt(), sats)
                } else {
                    context.getString(R.string.napplet_consent_pay_no_amount)
                }
            }
            is NappletRequest.ResourceBytes -> context.getString(R.string.napplet_consent_resource)
            is NappletRequest.UploadBlob -> context.getString(R.string.napplet_consent_upload)
            // Resolved in the broker before consent (negotiation / shell-mediated / cosmetic); never shown.
            is NappletRequest.ShellSupports, is NappletRequest.RegisterAction, is NappletRequest.UnregisterAction, is NappletRequest.ThemeGet -> ""
        }
}
