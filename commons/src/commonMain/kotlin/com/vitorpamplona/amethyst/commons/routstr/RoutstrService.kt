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
package com.vitorpamplona.amethyst.commons.routstr

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.frontpage.rankModels
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditKind
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditState
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletWalletGateway
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverParams
import com.vitorpamplona.quartz.nip60Cashu.p2pk.anyP2pkLocked
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenB64Parser
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A Routstr key's balance, as the key card shows it. */
@Immutable
sealed interface RoutstrKeyBalance {
    data object Loading : RoutstrKeyBalance

    data class Known(
        val balance: RoutstrBalance,
        val at: Long,
    ) : RoutstrKeyBalance

    data class Failed(
        val error: RoutstrError,
    ) : RoutstrKeyBalance
}

/** The node list, as the picker shows it. */
@Immutable
sealed interface RoutstrNodeList {
    data object Loading : RoutstrNodeList

    /** Every announcement read, newest per address; the picker filters them ([RoutstrNodes.list]). */
    data class Loaded(
        val events: List<RoutstrNodeEvent>,
        val at: Long,
    ) : RoutstrNodeList

    data object Failed : RoutstrNodeList
}

/**
 * One account's Routstr: its node list (kind 38421, read from Routstr's discovery relays, no node
 * contacted), what each node said about itself, its keys' balances, and its purchases. Built and
 * held by [RoutstrService].
 */
class RoutstrAccount(
    val account: Account,
    val api: RoutstrApi,
    store: RoutstrPurchaseStore,
    val torEnabled: () -> Boolean,
    private val scope: CoroutineScope,
) {
    val pubKey: HexKey get() = account.signer.pubKey

    val purchases =
        RoutstrPurchases(
            account = account.signer.pubKey,
            api = api,
            store = store,
            saveKey = ::saveKey,
            wallet = { cashuWallet() },
            torEnabled = torEnabled,
        )

    /**
     * Into the AI keys (kind 12000) through their editor, which reads the write relays first and
     * keeps the majority rules: waits for an edit already on its way, then saves. True once a relay
     * took it.
     */
    private suspend fun saveKey(key: AgentKeyTag): Boolean {
        val editor = account.agentKeyEditor
        repeat(3) {
            withTimeoutOrNull(EDITOR_WAIT_MS) { editor.state.first { !it.isBusy } }
            when (editor.run(AgentKeyEditKind.SAVE, key)) {
                is AgentKeyEditState.Done -> return true
                null -> {} // Another edit took the editor first: wait for it again.
                else -> return false
            }
        }
        return false
    }

    // --- Nodes ---------------------------------------------------------------------

    private val _nodes = MutableStateFlow<RoutstrNodeList>(RoutstrNodeList.Loading)
    val nodes: StateFlow<RoutstrNodeList> = _nodes.asStateFlow()
    private var nodesJob: Job? = null

    /** Reads the announcements again, unless a read is on its way or one finished within [maxAgeSeconds]. */
    fun refreshNodes(maxAgeSeconds: Long = 600) {
        val current = _nodes.value
        if (nodesJob?.isActive == true) return
        if (current is RoutstrNodeList.Loaded && TimeUtils.now() - current.at < maxAgeSeconds) return
        nodesJob =
            scope.launch {
                if (current !is RoutstrNodeList.Loaded) _nodes.value = RoutstrNodeList.Loading
                _nodes.value =
                    try {
                        val events = account.client.fetchRoutstrNodes(maxAge = RoutstrNodes.LISTED_MAX_AGE)
                        // Kept in the cache too, so a note that quotes one finds it.
                        events.forEach { account.cache.justConsume(it, null, false) }
                        val merged = (events.filterIsInstance<RoutstrNodeEvent>() + (current as? RoutstrNodeList.Loaded)?.events.orEmpty())
                        RoutstrNodeList.Loaded(newestPerAddress(merged), TimeUtils.now())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't read the Routstr nodes", e)
                        current as? RoutstrNodeList.Loaded ?: RoutstrNodeList.Failed
                    }
            }
    }

    /** The node announced at [address] (`38421:<pubkey>:<d>`) among those read, or in the cache. */
    fun node(address: String): RoutstrNode? {
        (_nodes.value as? RoutstrNodeList.Loaded)?.events?.firstOrNull { "${it.kind}:${it.pubKey}:${it.dTag()}" == address }?.let { return RoutstrNode.from(it) }
        val event = account.cache.getAddressableNoteIfExists(address)?.event as? RoutstrNodeEvent ?: return null
        return RoutstrNode.from(event)
    }

    /** The node a key URL names, among those read. */
    fun nodeForKey(keyUrl: String): RoutstrNode? =
        (_nodes.value as? RoutstrNodeList.Loaded)
            ?.events
            ?.asSequence()
            ?.map(RoutstrNode::from)
            ?.firstOrNull { it.owns(keyUrl) }

    /** The name of the node a key URL names: from its announcement, else its `/v1/info`. */
    fun nodeName(keyUrl: String): String? = nodeForKey(keyUrl)?.name ?: _info.value[RoutstrNodes.root(keyUrl).lowercase()]?.name

    // --- What a node says about itself -------------------------------------------------

    private val _info = MutableStateFlow<Map<String, RoutstrInfo?>>(emptyMap())

    /** `/v1/info` per node root: null where the node answered as something else. */
    val info: StateFlow<Map<String, RoutstrInfo?>> = _info.asStateFlow()

    /** `/v1/info` of [root], asked once per run; null when it isn't a Routstr node. */
    suspend fun info(
        root: String,
        onionRoot: String? = null,
    ): RoutstrInfo? {
        val key = RoutstrNodes.root(root).lowercase()
        if (_info.value.containsKey(key)) return _info.value[key]
        val base = if (torEnabled() && onionRoot != null) onionRoot else root
        val answer =
            try {
                api.info(base).takeIf { it.isRoutstr }
            } catch (e: RoutstrException) {
                // No answer says nothing about what it is: ask again next time.
                if (e.failure == RoutstrFailure.UNREACHABLE || e.failure == RoutstrFailure.UNSUPPORTED) return null
                null
            }
        _info.update { it + (key to answer) }
        return answer
    }

    /** Whether [key] is a Routstr key: a node read from the announcements, or one whose `/v1/info` said so. */
    fun isRoutstrKey(key: AgentKeyTag): Boolean {
        if (!key.hasKey()) return false
        if (nodeForKey(key.url) != null) return true
        if (_info.value[RoutstrNodes.root(key.url).lowercase()] != null) return true
        return AgentProviderPresets.forUrl(key.url)?.id == ROUTSTR_PRESET
    }

    /** Whether to ask [key]'s endpoint whether it is a Routstr node: a custom endpoint or Routstr's own, https, `/v1`. */
    fun mayBeRoutstr(key: AgentKeyTag): Boolean {
        if (!key.hasKey() || !key.isUrlAllowed() || AgentKeyTag.isLoopback(key.url)) return false
        val preset = AgentProviderPresets.forUrl(key.url)
        if (preset != null && preset.id != ROUTSTR_PRESET) return false
        return key.url
            .trim()
            .trimEnd('/')
            .endsWith("/v1")
    }

    // --- Balances ------------------------------------------------------------------------

    private val _balances = MutableStateFlow<Map<AgentKeyTag, RoutstrKeyBalance>>(emptyMap())
    val balances: StateFlow<Map<AgentKeyTag, RoutstrKeyBalance>> = _balances.asStateFlow()

    /** Where to call [key]'s node: its onion when Tor is on and the node has one. */
    private fun baseFor(key: AgentKeyTag): String {
        val root = RoutstrNodes.root(key.url)
        if (!torEnabled()) return root
        val onion = nodeForKey(key.url)?.onionUrl ?: _info.value[root.lowercase()]?.onionUrl?.takeIf { RoutstrNodes.root(it).endsWith(".onion") }
        return onion?.let(RoutstrNodes::root) ?: root
    }

    /** Asks the node for [key]'s balance (`/v1/balance/info`), for the key card and the print card. */
    suspend fun refreshBalance(key: AgentKeyTag): RoutstrKeyBalance {
        if (!key.hasKey()) return RoutstrKeyBalance.Failed(RoutstrError(null, RoutstrFailure.INVALID_KEY))
        _balances.update { if (it[key] is RoutstrKeyBalance.Known) it else it + (key to RoutstrKeyBalance.Loading) }
        val next =
            try {
                RoutstrKeyBalance.Known(api.balance(baseFor(key), key.key), TimeUtils.now())
            } catch (e: RoutstrException) {
                RoutstrKeyBalance.Failed(e.error)
            }
        _balances.update { it + (key to next) }
        return next
    }

    /** The models [key]'s node lists, with sats prices. */
    suspend fun models(key: AgentKeyTag): List<RoutstrModel> = api.models(baseFor(key), key.key.ifEmpty { null })

    private val modelCache = MutableStateFlow<Map<AgentKeyTag, List<RoutstrModel>>>(emptyMap())

    /** [models], asked once per run per key; null when the node didn't answer. */
    suspend fun cachedModels(key: AgentKeyTag): List<RoutstrModel>? {
        modelCache.value[key]?.let { return it }
        return try {
            models(key).also { list -> modelCache.update { it + (key to list) } }
        } catch (e: RoutstrException) {
            null
        }
    }

    /** The Observer's model (the schedule's `model` param), when this device has read the schedule. */
    fun observerModel(): String? = (account.observerSchedule.flow.value as? SyncedSchedule.Readable)?.task?.let(ObserverParams::model)

    /**
     * Whether [balance] may not cover one edition with the Observer's model (the schedule's, else
     * the best-ranked text model, which the print picks by default) at [key]'s node: under
     * its `max_cost` reserve or its edition estimate. Unknown model, unknown answer: false.
     */
    suspend fun isLow(
        key: AgentKeyTag,
        balance: RoutstrBalance,
    ): Boolean {
        val models = cachedModels(key) ?: return false
        // The schedule's model, else the one the print picks by default.
        val wanted = observerModel() ?: rankModels(models.filter { it.textOutput && it.enabled }.map { it.id }).firstOrNull()
        val model = models.firstOrNull { it.id == wanted } ?: return false
        return RoutstrPricing.isLow(balance.balanceMsats, model.sats, RoutstrSetupViewModel.editionSats(model))
    }

    // --- Wallets -------------------------------------------------------------------------

    /** Whether the account has a Lightning wallet connected (NWC, the one zaps use). */
    val hasLightningWallet: StateFlow<Boolean> =
        account.nip47SignerState.defaultWalletUri
            .map { it != null }
            .stateIn(scope, SharingStarted.Eagerly, account.nip47SignerState.defaultWalletUri.value != null)

    /** Pays [bolt11] from the account's NWC wallet; throws when it can't, never pretends it paid. */
    suspend fun payWithLightningWallet(bolt11: String) {
        AccountNappletWalletGateway(account).payInvoice(bolt11)
    }

    /** The account's NIP-60 Cashu wallet, when it has one. */
    fun cashuWallet(): RoutstrCashuWallet? = if (account.cashuWalletState.hasWallet()) AccountCashuWallet(account) else null

    companion object {
        private const val TAG = "RoutstrAccount"
        const val ROUTSTR_PRESET = "routstr"
        private const val EDITOR_WAIT_MS = 60_000L

        fun newestPerAddress(events: List<RoutstrNodeEvent>): List<RoutstrNodeEvent> =
            events
                .groupBy { it.pubKey + ":" + it.dTag() }
                .values
                .map { group -> group.maxBy { it.createdAt } }
    }
}

/** The NIP-60 wallet, for Routstr: spends from a mint the node takes, and takes the node's change back. */
class AccountCashuWallet(
    private val account: Account,
) : RoutstrCashuWallet {
    private val state get() = account.cashuWalletState

    private fun norm(mint: String) = mint.trim().trimEnd('/').lowercase()

    override fun balances(mints: List<String>): Map<String, Long> {
        val wanted = mints.map(::norm).toSet()
        return state.peekMintBalances().filter { (mint, sats) -> sats > 0 && norm(mint) in wanted }
    }

    override suspend fun mintToken(
        mint: String,
        amountSats: Long,
    ): String = state.sendAsToken(mint, amountSats, memo = "Routstr").cashuToken

    override suspend fun receive(token: String): Boolean {
        val groups = CashuTokenB64Parser.parse(token.trim())?.takeIf { it.isNotEmpty() } ?: return false
        val known =
            state.mints.value
                .map(::norm)
                .toSet()
        // All or nothing: a token from a mint this wallet doesn't hold stays with the purchase.
        if (groups.any { norm(it.mint) !in known }) return false
        val (walletKey, identityKey) = if (groups.any { it.proofs.anyP2pkLocked() }) state.redeemSigningKeys() else null to null
        groups.forEach { group ->
            state.ops.redeemToken(
                cashuToken = token.trim(),
                proofs = group.proofs,
                mintUrl = group.mint,
                walletP2pkPrivkeyHex = walletKey,
                identityPrivkeyHex = identityKey,
            )
        }
        return true
    }
}

/**
 * Routstr for every account on this device: one [RoutstrAccount] per account, and the pending
 * purchases each one kept, followed again from the moment the account is loaded ([start]).
 */
class RoutstrService(
    http: IRoleBasedHttpClientBuilder,
    private val store: RoutstrPurchaseStore,
    /** Whether calls to an onion go through Tor: the app's Tor is on and routes onions. */
    private val torEnabled: () -> Boolean,
    private val scope: CoroutineScope,
    val api: RoutstrApi = http.routstrApi(),
) {
    private val accounts = MutableStateFlow<Map<HexKey, RoutstrAccount>>(emptyMap())

    fun of(account: Account): RoutstrAccount {
        accounts.value[account.signer.pubKey]?.takeIf { it.account === account }?.let { return it }
        val made = RoutstrAccount(account, api, store, torEnabled, account.scope)
        accounts.update { it + (account.signer.pubKey to made) }
        return made
    }

    fun existing(pubKey: HexKey): RoutstrAccount? = accounts.value[pubKey]

    /** Follows each loaded account's pending purchases: resumed on launch, recovered by bolt11 when the id is lost. */
    fun start(loaded: StateFlow<Map<HexKey, Account>>) {
        scope.launch {
            val started = HashSet<Account>()
            loaded.collect { map ->
                map.values.forEach { account ->
                    if (started.add(account)) {
                        account.scope.launch {
                            try {
                                of(account).purchases.resume(account.scope)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w("RoutstrService", "Couldn't resume the Routstr purchases", e)
                            }
                        }
                    }
                }
            }
        }
    }

    /** A removed account's pending purchases stay on this device: one may hold money, and the next login resumes it. */
    fun forget(pubKey: HexKey) {
        accounts.update { it - pubKey }
    }

    companion object {
        /** Whether the app sends `.onion` calls through Tor: Tor is on and onions go through it. */
        fun onionsViaTor(tor: TorSettingsFlow): Boolean = tor.torType.value != TorType.OFF && tor.onionRelaysViaTor.value
    }
}

/** Kind 38421 events among [events], typed. */
fun List<Event>.routstrNodes(): List<RoutstrNodeEvent> = filterIsInstance<RoutstrNodeEvent>()
