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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet

import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransactionType

/**
 * What a transaction row has to say about its counterparty, resolved away from
 * Compose so the blank-handling is a plain unit test.
 *
 * The title is a [Title] rather than a resolved string because the fallback is a
 * string resource: keeping the choice here and the lookup in the composable means
 * this can be tested without a Context.
 */
data class TransactionRowLabels(
    val counterpartyPubkeyHex: String?,
    val title: Title,
    val subtitle: Subtitle?,
) {
    sealed interface Title {
        /** Render the counterparty's profile for this pubkey, falling back to [name]. */
        data class User(
            val pubkeyHex: String,
            val name: String?,
        ) : Title

        data class Literal(
            val text: String,
        ) : Title

        /** No name and no description: say "Received"/"Sent" for the direction. */
        data object Direction : Title
    }

    sealed interface Subtitle {
        data class Literal(
            val text: String,
        ) : Subtitle

        data object Direction : Subtitle
    }

    companion object {
        /**
         * WHY EVERY READ IS BLANK-GUARDED, not just null-checked. Wallets send
         * `"description": ""` for a payment with no memo — NIP-47 marks the field
         * optional, but omitting it is a choice and several wallets emit the empty
         * string instead. An elvis only catches null, so the row rendered an empty
         * Text: an invisible line with the height of a real one, which is why
         * outgoing rows looked like a bare arrow and a date.
         *
         * NwcPaymentNotifier already did this; the transactions screen did not.
         */
        fun resolve(tx: NwcTransaction): TransactionRowLabels {
            val isIncoming = tx.type == NwcTransactionType.INCOMING
            val parsed = tx.parsedMetadata()
            val description = tx.description?.ifBlank { null }

            // Incoming: who sent it. Outgoing: who received it — on a zap request the
            //  tag is the payee, which is what makes an outgoing row resolvable.
            val pubkeyHex = if (isIncoming) parsed?.senderPubkeyHex() else parsed?.recipientPubkeyHex()
            val displayName = if (isIncoming) parsed?.senderDisplayName() else parsed?.recipientIdentifier()

            // The zap comment, unless it merely repeats the description.
            val comment =
                parsed?.displayComment()?.takeIf { comment ->
                    description == null || !comment.equals(description, ignoreCase = true)
                }

            // Whether the title names a counterparty rather than describing the payment.
            // A named row wants a second line saying what the payment was; a row whose
            // title IS the description must not repeat it underneath.
            val isNamed = pubkeyHex != null || displayName != null

            val title =
                when {
                    pubkeyHex != null -> Title.User(pubkeyHex, displayName)
                    displayName != null -> Title.Literal(displayName)
                    description != null -> Title.Literal(description)
                    else -> Title.Direction
                }

            val subtitle =
                when {
                    comment != null -> Subtitle.Literal(comment)
                    isNamed -> description?.let { Subtitle.Literal(it) } ?: Subtitle.Direction
                    else -> null
                }

            return TransactionRowLabels(pubkeyHex, title, subtitle)
        }
    }
}
