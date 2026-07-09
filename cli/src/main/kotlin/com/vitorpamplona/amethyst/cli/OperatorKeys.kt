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
package com.vitorpamplona.amethyst.cli

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.cli.secrets.IdentitySecret
import com.vitorpamplona.amethyst.cli.secrets.SecretStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.io.File

/**
 * Operator-level signing keys for GrapeRank trusted-assertion publishing.
 *
 * A machine holds ONE operator master seed, **independent of any amy account**,
 * stored under `~/.amy/operator/` through the same [SecretStore] backend the
 * accounts use (OS keychain / NIP-49 ncryptsec / plaintext). From it we
 * deterministically derive ONE service key per observer:
 *
 * ```
 * serviceKey(observer) = sha256(masterPriv ‖ "graperank-provider:" ‖ observerHex ‖ counter)
 * ```
 *
 * That service key signs the observer's kind:30382 rank cards (and their kind:5
 * retractions). Deterministic derivation buys two things:
 *  - **Stable identity** — the same observer always maps to the same key, so
 *    re-signing a card *replaces* the prior one (kind:30382 is addressable)
 *    instead of orphaning it and spamming clients with duplicates.
 *  - **One-secret backup** — back up only the master seed; every service key is
 *    re-derivable even if the [providers] manifest is lost.
 *
 * The manifest (`~/.amy/operator/operator.json`) records the master pubkey, the
 * configured operator relay(s), and the observer → provider-pubkey mapping. Only
 * the master itself is a secret; it rides the [SecretStore] descriptor, so the
 * manifest holds public data.
 */
class OperatorKeys(
    amyHome: File,
    private val secrets: SecretStore,
) {
    private val dir = File(amyHome, DIR_NAME)
    private val configFile = File(dir, CONFIG_NAME)

    data class ProviderRecord(
        val providerPubKey: HexKey = "",
    )

    data class Config(
        val masterPubKey: HexKey = "",
        val master: IdentitySecret? = null,
        val relays: List<String> = emptyList(),
        val providers: MutableMap<HexKey, ProviderRecord> = mutableMapOf(),
    )

    private fun load(): Config? = if (configFile.exists()) Output.mapper.readValue<Config>(configFile.readText()) else null

    private fun save(cfg: Config) {
        SecureFileIO.secureMkdirs(dir)
        configFile.writeText(Output.mapper.writeValueAsString(cfg))
        SecureFileIO.tighten(configFile)
    }

    /** True once an operator master exists on this machine. */
    fun exists(): Boolean = load()?.master != null

    /** Load (or, on first use, create + persist) the operator master private key. */
    private fun masterPriv(): ByteArray {
        load()?.master?.let { return secrets.resolve(it).hexToByteArray() }
        val kp = KeyPair()
        val pub = kp.pubKey.toHexKey()
        val secret = secrets.store(pub, kp.privKey!!.toHexKey())
        save(Config(masterPubKey = pub, master = secret))
        System.err.println("[operator] created operator master ${pub.take(8)}… at ${configFile.path}")
        return kp.privKey!!
    }

    /** The operator master pubkey, creating the master on first use. */
    fun masterPubKey(): HexKey {
        masterPriv()
        return load()!!.masterPubKey
    }

    /**
     * The deterministic service key for [observerHex], recording the observer →
     * provider-pubkey mapping in the manifest. The counter loop only ever runs
     * once in practice — it's a guard for the ~2^-128 chance a sha256 output isn't
     * a valid secp256k1 scalar.
     */
    fun serviceKey(observerHex: HexKey): KeyPair {
        val master = masterPriv()
        var counter = 0
        while (true) {
            val material = master + "$DERIVATION_LABEL$observerHex:$counter".encodeToByteArray()
            val kp = runCatching { KeyPair(privKey = sha256(material)) }.getOrNull()
            if (kp?.privKey != null) {
                recordProvider(observerHex, kp.pubKey.toHexKey())
                return kp
            }
            counter++
        }
    }

    /**
     * The machine's dedicated NIP-66 relay-monitor identity, derived once from the
     * operator master (independent of any amy account). Unlike [serviceKey] this is
     * NOT per-observer — the machine publishes relay-reachability (kind:30166) under a
     * single, stable monitor pubkey, so a re-probe *replaces* the prior 30166 for a
     * relay instead of orphaning it. Re-derivable from the one master seed alone.
     */
    fun monitorKey(): KeyPair {
        val master = masterPriv()
        var counter = 0
        while (true) {
            val material = master + "$MONITOR_LABEL$counter".encodeToByteArray()
            val kp = runCatching { KeyPair(privKey = sha256(material)) }.getOrNull()
            if (kp?.privKey != null) return kp
            counter++
        }
    }

    private fun recordProvider(
        observerHex: HexKey,
        providerPubKey: HexKey,
    ) {
        val cfg = load() ?: return
        if (cfg.providers[observerHex]?.providerPubKey == providerPubKey) return
        cfg.providers[observerHex] = ProviderRecord(providerPubKey)
        save(cfg)
    }

    /** Relays the operator publishes all its 30382 cards + retractions to. */
    fun operatorRelays(): Set<NormalizedRelayUrl> =
        load()
            ?.relays
            .orEmpty()
            .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
            .toSet()

    fun setRelays(urls: List<String>) {
        masterPriv() // make sure the config (and master) exists first
        save(load()!!.copy(relays = urls))
    }

    fun providers(): Map<HexKey, ProviderRecord> = load()?.providers.orEmpty()

    companion object {
        private const val DIR_NAME = "operator"
        private const val CONFIG_NAME = "operator.json"
        private const val DERIVATION_LABEL = "graperank-provider:"
        private const val MONITOR_LABEL = "relay-monitor:"
    }
}
