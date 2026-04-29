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
package com.vitorpamplona.amethyst.commons.ui.signing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

sealed class SigningOpState {
    data object Idle : SigningOpState()

    data object Pending : SigningOpState()

    data class Error(
        val message: String,
    ) : SigningOpState()
}

/**
 * Global signing status — any [SigningState] instance updates this when signing starts/ends.
 * Observe [globalState] from a screen-level composable to show a persistent status bar.
 */
object GlobalSigningStatus {
    var globalState by mutableStateOf<SigningOpState>(SigningOpState.Idle)
        private set

    private val activeCount = AtomicInteger(0)

    fun onPending() {
        activeCount.incrementAndGet()
        globalState = SigningOpState.Pending
    }

    fun onIdle() {
        if (activeCount.decrementAndGet() <= 0) {
            activeCount.set(0)
            globalState = SigningOpState.Idle
        }
    }

    fun onError(message: String) {
        activeCount.decrementAndGet()
        globalState = SigningOpState.Error(message)
    }
}

/**
 * Per-component state holder for remote signer operations.
 * Tracks Idle/Pending/Error — no Success state (existing UI handles success).
 * Error resets to Idle on next execute() call (tap = retry).
 * Also updates [GlobalSigningStatus] for screen-level status bar.
 */
@Stable
class SigningState {
    var state by mutableStateOf<SigningOpState>(SigningOpState.Idle)
        private set

    var errorMessage: String? = null
        private set

    suspend fun <T> execute(block: suspend () -> T): T? {
        if (state is SigningOpState.Pending) return null
        state = SigningOpState.Pending
        GlobalSigningStatus.onPending()
        errorMessage = null
        return try {
            val result = withContext(Dispatchers.IO) { block() }
            state = SigningOpState.Idle
            GlobalSigningStatus.onIdle()
            result
        } catch (e: CancellationException) {
            state = SigningOpState.Idle
            GlobalSigningStatus.onIdle()
            throw e
        } catch (e: SignerExceptions.TimedOutException) {
            setError("Signer timed out")
            null
        } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
            setError("Signing rejected")
            null
        } catch (e: SignerExceptions) {
            setError(e.message ?: "Signing failed")
            null
        } catch (e: Exception) {
            setError(e.message ?: "Operation failed")
            null
        }
    }

    private fun setError(message: String) {
        errorMessage = message
        state = SigningOpState.Error(message)
        GlobalSigningStatus.onError(message)
    }
}
