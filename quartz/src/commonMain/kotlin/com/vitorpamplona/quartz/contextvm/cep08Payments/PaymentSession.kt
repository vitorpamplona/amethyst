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
package com.vitorpamplona.quartz.contextvm.cep08Payments

import com.vitorpamplona.quartz.nip01Core.core.Tag

/**
 * Client-side CEP-8 negotiation state for one session.
 *
 * The rule this exists to enforce: a server that will not honour
 * `explicit_gating` MUST NOT silently fall back to the transparent lifecycle,
 * and a client that *required* explicit gating MUST NOT auto-satisfy transparent
 * `payment_required` notifications it receives anyway. A payment handler that
 * simply pays whatever it is asked to pay violates the client half of that, so
 * [mayAutoPay] is the gate every handler has to pass through.
 */
class PaymentSession(
    /** The lifecycle this client wants. */
    private val requested: PaymentInteraction = PaymentInteraction.DEFAULT,
    /**
     * True when the application needs payment decisions to be visible — an agent
     * or a user must approve them.
     *
     * With this set, failing to negotiate [PaymentInteraction.EXPLICIT_GATING]
     * disables automatic payment entirely rather than degrading to silent
     * spending.
     */
    private val requiresVisiblePayments: Boolean = false,
    /** PMIs this client can actually settle. */
    private val supportedPmis: List<Pmi> = emptyList(),
) {
    private var negotiated = false
    private var effective = PaymentInteraction.DEFAULT

    /** The lifecycle in force. Before the first response this is the default. */
    val effectiveMode get() = effective

    /** True once the server has disclosed (or implied) the effective mode. */
    val isNegotiated get() = negotiated

    /**
     * True when negotiation did not deliver the mode this client asked for.
     *
     * Only meaningful after [observeServerTags].
     */
    val negotiationFailed get() = negotiated && effective != requested

    /** Tags to attach to the first direct message of the session. */
    fun negotiationTags(): List<Tag> =
        buildList {
            // transparent is the default, so advertising it is noise; only a
            // non-default request needs to be stated.
            if (requested != PaymentInteraction.DEFAULT) add(PaymentTags.paymentInteraction(requested))
            supportedPmis.forEach { add(PaymentTags.pmi(it)) }
        }

    /**
     * Applies the server's first direct response.
     *
     * Absence of the tag means transparent, per CEP-8's first-message semantics.
     */
    fun observeServerTags(tags: Array<Tag>) {
        effective = PaymentTags.parsePaymentInteraction(tags) ?: PaymentInteraction.DEFAULT
        negotiated = true
    }

    /**
     * Applies a mid-session upsert.
     *
     * CEP-8 treats a repeated `payment_interaction` as an upsert rather than a
     * one-shot handshake, because ContextVM messaging is connectionless: a
     * server cannot observe a client reconnecting, so first-message-only
     * negotiation could never be renegotiated after a transport reset. An absent
     * tag on a later message inherits the current mode.
     */
    fun observeLaterServerTags(tags: Array<Tag>) {
        PaymentTags.parsePaymentInteraction(tags)?.let {
            effective = it
            negotiated = true
        }
    }

    /**
     * Whether a handler may settle [request] without asking anyone.
     *
     * False when the client required visible payments but did not get explicit
     * gating, and false for a PMI this client cannot settle anyway.
     */
    fun mayAutoPay(request: PaymentRequest): Boolean {
        if (requiresVisiblePayments && effective != PaymentInteraction.EXPLICIT_GATING) return false
        return request.pmi in supportedPmis
    }

    /**
     * The first offered option this client can settle, or null.
     *
     * Order is the server's preference; a client picks the first it supports
     * rather than the cheapest, since price comparison across payment rails is
     * not something this layer can do.
     */
    fun selectPayable(options: List<PaymentRequest>): PaymentRequest? = options.firstOrNull { it.pmi in supportedPmis }

    /** PMIs both sides support, preserving the server's ordering. */
    fun intersectPmis(serverPmis: List<Pmi>): List<Pmi> = serverPmis.filter { it in supportedPmis }
}
