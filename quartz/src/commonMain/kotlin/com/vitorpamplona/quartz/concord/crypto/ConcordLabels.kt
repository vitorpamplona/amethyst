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
package com.vitorpamplona.quartz.concord.crypto

/**
 * Frozen domain-separation labels used across the Concord protocol (CORD-01…07).
 *
 * These strings are part of the wire contract: every implementation must feed the
 * exact same UTF-8 bytes into HKDF for members to derive matching plane keys. They
 * are pinned to the Concord v2 reference client (Soapbox's Armada, `concord-v2`),
 * which is the interoperability target. Do not rename or re-case them.
 */
object ConcordLabels {
    /** Prefix for the SHA-256 community-id commitment (CORD-02). Not an HKDF label. */
    const val COMMUNITY = "concord/community"

    /** Per-Channel Chat Plane key (CORD-03). */
    const val CHANNEL = "concord/channel"

    /**
     * Control Plane *read* key (CORD-02 §5): community_root-derived, its conv_key
     * encrypts the wraps. Pre-split epochs used its pk/sk as the plane's address and
     * signer too — that use is retained for reading legacy epochs (CORD-06 §3).
     */
    const val CONTROL = "concord/control"

    /**
     * Control Plane signer (CORD-02 §5): control_root-derived, its pk is the plane's
     * address and its staff-only sk signs the wraps (CORD-01, Write-Restricted Streams).
     */
    const val CONTROL_SIGNER = "concord/control-signer"

    /** Guestbook Plane key (CORD-02). */
    const val GUESTBOOK = "concord/guestbook"

    /** Grant coordinate derivation (CORD-04). */
    const val GRANT = "concord/grant"

    /** Banlist coordinate derivation (CORD-04). */
    const val BANLIST = "concord/banlist"

    /** Invite-link coordinate derivation (CORD-05). */
    const val INVITE_LINKS = "concord/invite-links"

    /** Invite bundle decryption key from the unlock token (CORD-05). */
    const val INVITE_KEY = "concord/invite-key"

    /** Dissolution tombstone coordinate (CORD-02). */
    const val DISSOLVED = "concord/dissolved"

    /** Voice signer keypair — public key is the SFU room name (CORD-07). */
    const val VOICE_SIGNER = "concord/voice-signer"

    /** Voice media root key (CORD-07). */
    const val VOICE_MEDIA = "concord/voice-media"

    /** Per-sender voice frame key (CORD-07). No epoch field in the info. */
    const val VOICE_SENDER = "concord/voice-sender"

    /** Rekey recipient pseudonym / locator (CORD-06). */
    const val RECIPIENT_PSEUDONYM = "concord/recipient-pseudonym"

    /** Channel-scoped rekey pseudonym (CORD-06). */
    const val REKEY_PSEUDONYM = "concord/rekey-pseudonym"

    /** community_root-scoped rekey pseudonym for Refoundings (CORD-06). */
    const val BASE_REKEY_PSEUDONYM = "concord/base-rekey-pseudonym"

    /** Epoch-key commitment prefix for a rekey's `prevcommit` (CORD-02 §A.5). Not an HKDF label. */
    const val EPOCH_KEY_COMMITMENT = "concord/epoch-key-commitment"
}
