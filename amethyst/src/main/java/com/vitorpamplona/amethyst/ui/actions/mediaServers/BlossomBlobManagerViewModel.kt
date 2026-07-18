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
package com.vitorpamplona.amethyst.ui.actions.mediaServers

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomPaymentHandler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentProof
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.nipB7Blossom.BlossomReport
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One stored blob, plus which of the user's Blossom servers currently hold it —
 * the "seen files per server" view (BUD-01 HEAD / BUD-02 list). [serversPresent]
 * and [serversMissing] are server base URLs from the user's kind-10063 list.
 */
@Immutable
data class BlobRow(
    val hash: HexKey,
    val url: String?,
    val size: Long?,
    val type: String?,
    val serversPresent: List<String>,
    val serversMissing: List<String>,
)

/** A BUD-07 payment prompt raised while mirroring [row] to [target]. */
@Immutable
data class PendingMirrorPayment(
    val row: BlobRow,
    val target: String,
    val payment: BlossomPaymentRequired,
    val amountSats: Long?,
)

/**
 * Backs the Blossom blob-manager screen. For the active account it fans a
 * `GET /list/<pubkey>` (BUD-02) across every server in the user's kind-10063 list,
 * inverts the results into a per-blob presence matrix, and backfills servers that
 * don't implement `/list` with cheap `HEAD /<sha256>` probes (BUD-01). Exposes
 * delete (BUD-02), mirror-to-missing (BUD-04), and report (BUD-09) actions.
 */
@Stable
class BlossomBlobManagerViewModel : ViewModel() {
    private lateinit var account: Account

    private val _blobs = MutableStateFlow<List<BlobRow>>(emptyList())
    val blobs = _blobs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _pendingPayment = MutableStateFlow<PendingMirrorPayment?>(null)
    val pendingPayment = _pendingPayment.asStateFlow()

    fun init(accountViewModel: AccountViewModel) {
        this.account = accountViewModel.account
    }

    private fun clientFor(server: String) = BlossomClient(Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForUploads(server))

    // The user's explicitly configured kind-10063 servers (empty if they never set
    // a list) — not the DEFAULT_MEDIA_SERVERS fallback, so the matrix reflects the
    // servers the user actually chose.
    private fun servers(): List<String> =
        account.blossomServers.flow.value
            .distinct()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                _blobs.value = loadMatrix()
            } catch (e: Exception) {
                Log.w("BlossomBlobManager", "Failed to load blob list", e)
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadMatrix(): List<BlobRow> {
        val pubkey = account.signer.pubKey
        val servers = servers()
        if (servers.isEmpty()) return emptyList()

        // Per-server /list results, keyed by hash. Servers that don't implement
        // /list (or error) contribute an empty holding and get HEAD-backfilled below.
        val presence = mutableMapOf<HexKey, MutableSet<String>>()
        val meta = mutableMapOf<HexKey, BlobMeta>()
        val listCapable = mutableSetOf<String>()

        servers.forEach { server ->
            try {
                val auth = account.createBlossomListAuth("List blobs", listOf(server)).toAuthorizationHeader()
                val blobs = clientFor(server).list(server, pubkey, auth)
                listCapable.add(server)
                blobs.forEach { d ->
                    val hash = d.sha256 ?: return@forEach
                    presence.getOrPut(hash) { mutableSetOf() }.add(server)
                    meta.putIfAbsent(hash, BlobMeta(d.url, d.size, d.type))
                }
            } catch (e: Exception) {
                Log.w("BlossomBlobManager", "list failed on $server", e)
            }
        }

        // BUD-01 backfill: for every known hash, HEAD-probe the servers that
        // didn't (or couldn't) list it, so the presence matrix is complete.
        val allHashes = presence.keys.toList()
        servers.forEach { server ->
            allHashes.forEach { hash ->
                if (server !in presence[hash].orEmpty()) {
                    if (clientFor(server).has(hash, server)) {
                        presence.getOrPut(hash) { mutableSetOf() }.add(server)
                    }
                }
            }
        }

        return presence
            .map { (hash, present) ->
                val m = meta[hash]
                BlobRow(
                    hash = hash,
                    url = m?.url,
                    size = m?.size,
                    type = m?.type,
                    serversPresent = servers.filter { it in present },
                    serversMissing = servers.filter { it !in present },
                )
            }.sortedByDescending { it.serversPresent.size }
    }

    /** BUD-02 delete: remove [hash] from a single [server]. */
    fun delete(
        hash: HexKey,
        server: String,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok =
                try {
                    val auth = account.createBlossomDeleteAuth(hash, "Delete blob", listOf(server)).toAuthorizationHeader()
                    clientFor(server).delete(hash, server, auth)
                } catch (e: Exception) {
                    Log.w("BlossomBlobManager", "delete failed on $server", e)
                    false
                }
            if (ok) refresh()
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    /** BUD-04: mirror a blob to every server in the user's list that doesn't have it yet. */
    fun mirrorToMissing(row: BlobRow) {
        val source = row.url ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var mirrored = 0
            for (target in row.serversMissing) {
                try {
                    mirrorOne(source, row, target, null)
                    mirrored++
                } catch (e: BlossomPaymentException) {
                    // BUD-07: this server wants payment. Pause and ask the user to confirm;
                    // the rest of the servers are retried after they decide.
                    if (BlossomPaymentHandler.canPay(account, e.payment)) {
                        _pendingPayment.value = PendingMirrorPayment(row, target, e.payment, BlossomPaymentHandler.amountSats(e.payment))
                        return@launch
                    }
                    Log.w("BlossomBlobManager", "mirror to $target needs unsupported payment", e)
                } catch (e: Exception) {
                    Log.w("BlossomBlobManager", "mirror to $target failed", e)
                }
            }
            if (mirrored > 0) refresh()
        }
    }

    private suspend fun mirrorOne(
        source: String,
        row: BlobRow,
        target: String,
        proof: BlossomPaymentProof?,
    ) {
        val auth = account.createBlossomUploadAuth(row.hash, row.size ?: 0L, "Mirror ${row.hash}", listOf(target)).toAuthorizationHeader()
        clientFor(target).mirror(source, target, auth, proof)
    }

    /** User confirmed the BUD-07 prompt: pay via the wallet, retry, then continue with the rest. */
    fun confirmPendingPayment() {
        val pending = _pendingPayment.value ?: return
        _pendingPayment.value = null
        val source = pending.row.url ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val proof = BlossomPaymentHandler.pay(account, pending.payment)
            if (proof == null) {
                _error.value = "Payment failed or was not confirmed by the wallet."
                return@launch
            }
            try {
                mirrorOne(source, pending.row, pending.target, proof)
            } catch (e: Exception) {
                Log.w("BlossomBlobManager", "paid mirror to ${pending.target} failed", e)
            }
            // Continue mirroring to any remaining servers (which may prompt again).
            refresh()
            mirrorToMissing(pending.row.copy(serversMissing = pending.row.serversMissing.filter { it != pending.target }))
        }
    }

    fun cancelPendingPayment() {
        _pendingPayment.value = null
    }

    /** BUD-09: report a blob to a server as problematic content. */
    fun report(
        hash: HexKey,
        server: String,
        type: ReportType,
        comment: String,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok =
                try {
                    val event = account.signer.sign(BlossomReport.build(hash, type, account.signer.pubKey, comment))
                    clientFor(server).report(server, event.toJson())
                } catch (e: Exception) {
                    Log.w("BlossomBlobManager", "report failed on $server", e)
                    false
                }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    fun hostOf(serverBaseUrl: String): String = BlossomServerUrl.domain(serverBaseUrl)

    private data class BlobMeta(
        val url: String?,
        val size: Long?,
        val type: String?,
    )
}
