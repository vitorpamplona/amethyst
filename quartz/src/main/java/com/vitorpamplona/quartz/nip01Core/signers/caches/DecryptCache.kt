/**
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
package com.vitorpamplona.quartz.nip01Core.signers.caches

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import com.vitorpamplona.quartz.utils.TimeUtils

abstract class DecryptCache<I : Any, T : Any>(
    val signer: NostrSigner,
) {
    var cache: CacheResults<T> = CacheResults.CanTryAgain(0)

    fun preload(result: T) {
        cache = CacheResults.Success(result)
    }

    abstract suspend fun decryptAndParse(
        event: I,
        signer: NostrSigner,
    ): T

    private suspend fun performDecrypt(input: I): T? {
        try {
            val response = decryptAndParse(input, signer)
            cache = CacheResults.Success(response)
            return response
        } catch (e: SignerExceptions.NothingToDecrypt) {
            Log.w("DecryptCache", "Nothing to decrypt", e)
            // ciphertext is blank. Cancels everything.
            cache = CacheResults.DontTryAgain()
        } catch (e: SignerExceptions.AutomaticallyUnauthorizedException) {
            Log.w("DecryptCache", "NothAutomaticallyUnauthorizedException", e)
            // User has rejected this permission. Don't try again.
            cache = CacheResults.DontTryAgain<T>()
        } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
            Log.w("DecryptCache", "ManuallyUnauthorizedException", e)
            // User has rejected this permission. Don't try again.
            cache = CacheResults.CanTryAgain<T>(TimeUtils.tenSecondsFromNow())
        } catch (e: SignerExceptions.TimedOutException) {
            Log.w("DecryptCache", "TimedOutException", e)
            // User has did not reply to the approval request. Ignore until later time.
            cache = CacheResults.CanTryAgain<T>(TimeUtils.tenSecondsFromNow())
        } catch (e: SignerExceptions.CouldNotPerformException) {
            // Log.w("DecryptCache", "CouldNotPerformException", e)
            // Decryption failed. This key might not be able to decrypt anything. Don't try again.
            cache = CacheResults.DontTryAgain<T>()
        } catch (e: SignerExceptions.SignerNotFoundException) {
            Log.w("DecryptCache", "SignerNotFoundException", e)
            // Signer app was deleted. Not sure what to to. It should probably log off.
            cache = CacheResults.DontTryAgain<T>()
        } catch (e: SignerExceptions.RunningOnBackgroundWithoutAutomaticPermissionException) {
            Log.w("DecryptCache", "RunningOnBackgroundWithoutAutomaticPermissionException", e)
            // App received a notifications, asked the signer to decrypt but the permission was not automatic.
            // It needs the interface but does not have it. It needs to wait for an activity.
            cache = CacheResults.NeedsForegroundActivityToTryAgain<T>(TimeUtils.tenSecondsFromNow())
        } catch (e: com.fasterxml.jackson.core.JsonParseException) {
            Log.w("DecryptCache", "JsonParseException", e)
            // Decryption failed. This key might not be able to decrypt anything. Don't try again.
            cache = CacheResults.DontTryAgain()
        } catch (e: IllegalStateException) {
            Log.w("DecryptCache", "IllegalStateException", e)
            cache = CacheResults.DontTryAgain()
        } catch (e: IllegalArgumentException) {
            Log.w("DecryptCache", "IllegalArgumentException", e)
            cache = CacheResults.DontTryAgain()
        }
        return null
    }

    fun cached(): T? {
        val cachedResult = cache
        return if (cachedResult is CacheResults.Success<T>) {
            cachedResult.value
        } else {
            null
        }
    }

    suspend fun decrypt(input: I): T? {
        val cachedResult = cache
        return when (cachedResult) {
            is CacheResults.Success<T> -> cachedResult.value
            is CacheResults.DontTryAgain -> null
            is CacheResults.CanTryAgain -> {
                if (TimeUtils.now() > cachedResult.after) {
                    performDecrypt(input)
                } else {
                    null
                }
            }
            is CacheResults.NeedsForegroundActivityToTryAgain<*> -> {
                if (TimeUtils.now() > cachedResult.after && (signer !is NostrSignerExternal || signer.hasForegroundActivity())) {
                    performDecrypt(input)
                } else {
                    null
                }
            }
        }
    }
}
