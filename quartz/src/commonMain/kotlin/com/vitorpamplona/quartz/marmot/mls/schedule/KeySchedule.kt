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
package com.vitorpamplona.quartz.marmot.mls.schedule

import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider

/**
 * MLS Key Schedule (RFC 9420 Section 8).
 *
 * Implements the deterministic key derivation chain that produces all
 * epoch secrets from the commit_secret and init_secret.
 *
 * ```
 *                init_secret (from previous epoch or initial)
 *                     |
 *                commit_secret (from TreeKEM root)
 *                     |
 *              ┌──────┴──────┐
 *              |             |
 *         joiner_secret  epoch_secret
 *              |             |
 *         (for Welcome)  ┌───┼───┬────────┬──────────┬──────────┐
 *                        |   |   |        |          |          |
 *                  sender_ encryp- export- epoch_    confirm-  membership_
 *                  data_   tion_   er_     authenti- ation_    _key
 *                  secret  secret  secret  cator     key
 * ```
 *
 * The MLS-Exporter function (which Marmot uses for outer encryption keys)
 * derives from exporter_secret.
 */
class KeySchedule(
    private val groupContext: ByteArray,
) {
    /**
     * Derive a complete set of epoch secrets from commit_secret and init_secret.
     *
     * @param commitSecret from TreeKEM (root path secret after Commit)
     * @param initSecret from previous epoch (or zeros for first epoch)
     * @param pskSecret pre-shared key secret (zeros if no PSK)
     * @return all derived epoch keys
     */
    fun deriveEpochSecrets(
        commitSecret: ByteArray,
        initSecret: ByteArray,
        pskSecret: ByteArray = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH),
    ): EpochSecrets {
        // joiner_secret = ExpandWithLabel(
        //     HKDF-Extract(init_secret, commit_secret),
        //     "joiner", GroupContext, Nh)
        val prk = MlsCryptoProvider.hkdfExtract(initSecret, commitSecret)
        val joinerSecret = MlsCryptoProvider.expandWithLabel(prk, "joiner", groupContext, MlsCryptoProvider.HASH_OUTPUT_LENGTH)

        // member_secret = HKDF-Extract(joiner_secret, psk_secret)
        val memberSecret = MlsCryptoProvider.hkdfExtract(joinerSecret, pskSecret)

        // welcome_secret = DeriveSecret(member_secret, "welcome")
        val welcomeSecret = MlsCryptoProvider.deriveSecret(memberSecret, "welcome")

        // epoch_secret = ExpandWithLabel(member_secret, "epoch", GroupContext, Nh)
        val epochSecret = MlsCryptoProvider.expandWithLabel(memberSecret, "epoch", groupContext, MlsCryptoProvider.HASH_OUTPUT_LENGTH)

        // Derive individual secrets from epoch_secret
        val senderDataSecret = MlsCryptoProvider.deriveSecret(epochSecret, "sender data")
        val encryptionSecret = MlsCryptoProvider.deriveSecret(epochSecret, "encryption")
        val exporterSecret = MlsCryptoProvider.deriveSecret(epochSecret, "exporter")
        val epochAuthenticator = MlsCryptoProvider.deriveSecret(epochSecret, "authentication")
        val externalSecret = MlsCryptoProvider.deriveSecret(epochSecret, "external")
        val confirmationKey = MlsCryptoProvider.deriveSecret(epochSecret, "confirm")
        val membershipKey = MlsCryptoProvider.deriveSecret(epochSecret, "membership")
        val resumptionPsk = MlsCryptoProvider.deriveSecret(epochSecret, "resumption")

        // init_secret for next epoch
        val nextInitSecret = MlsCryptoProvider.deriveSecret(epochSecret, "init")

        return EpochSecrets(
            joinerSecret = joinerSecret,
            welcomeSecret = welcomeSecret,
            epochSecret = epochSecret,
            senderDataSecret = senderDataSecret,
            encryptionSecret = encryptionSecret,
            exporterSecret = exporterSecret,
            epochAuthenticator = epochAuthenticator,
            externalSecret = externalSecret,
            confirmationKey = confirmationKey,
            membershipKey = membershipKey,
            resumptionPsk = resumptionPsk,
            initSecret = nextInitSecret,
        )
    }

    companion object {
        /**
         * MLS-Exporter function (RFC 9420 Section 8.5):
         *
         * ```
         * MLS-Exporter(Label, Context, Length) =
         *     ExpandWithLabel(DeriveSecret(exporter_secret, Label),
         *                     "exported", Hash(Context), Length)
         * ```
         *
         * This is what Marmot uses to derive the outer ChaCha20-Poly1305
         * encryption key: MLS-Exporter("marmot", "group-event", 32)
         */
        fun mlsExporter(
            exporterSecret: ByteArray,
            label: String,
            context: ByteArray,
            length: Int,
        ): ByteArray = mlsExporter(exporterSecret, label.encodeToByteArray(), context, length)

        /**
         * MLS-Exporter with raw byte label for non-UTF-8 labels.
         */
        fun mlsExporter(
            exporterSecret: ByteArray,
            label: ByteArray,
            context: ByteArray,
            length: Int,
        ): ByteArray {
            val derivedSecret = MlsCryptoProvider.expandWithLabelRaw(exporterSecret, label, ByteArray(0), MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            val contextHash = MlsCryptoProvider.hash(context)
            return MlsCryptoProvider.expandWithLabel(derivedSecret, "exported", contextHash, length)
        }
    }
}

/**
 * All secrets derived from a single epoch's key schedule.
 */
data class EpochSecrets(
    /** Used in Welcome messages to encrypt GroupInfo */
    val joinerSecret: ByteArray,
    /** Key for encrypting Welcome message GroupInfo */
    val welcomeSecret: ByteArray,
    /** Master secret for the epoch (all other secrets derive from this) */
    val epochSecret: ByteArray,
    /** Secret for sender data encryption in PrivateMessage */
    val senderDataSecret: ByteArray,
    /** Secret for application message encryption ratchets */
    val encryptionSecret: ByteArray,
    /** Secret for MLS-Exporter function */
    val exporterSecret: ByteArray,
    /** Epoch authenticator provided to application layer */
    val epochAuthenticator: ByteArray,
    /** Secret for external Commit/join */
    val externalSecret: ByteArray,
    /** Key for Commit confirmation MAC */
    val confirmationKey: ByteArray,
    /** Key for membership MAC in PublicMessage */
    val membershipKey: ByteArray,
    /** Pre-shared key for epoch resumption */
    val resumptionPsk: ByteArray,
    /** Init secret for the NEXT epoch */
    val initSecret: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EpochSecrets) return false
        return epochSecret.contentEquals(other.epochSecret)
    }

    override fun hashCode(): Int = epochSecret.contentHashCode()
}
