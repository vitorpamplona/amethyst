/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.lightning

import android.util.Log
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import java.util.regex.Pattern

class Lud06 {
    companion object {
        val LNURLP_PATTERN = Pattern.compile("(?i:http|https):\\/\\/((.+)\\/)*\\.well-known\\/lnurlp\\/(.*)")
    }

    fun toLud16(str: String): String? {
        return try {
            val url = toLnUrlp(str) ?: return null

            val matcher = LNURLP_PATTERN.matcher(url)
            if (matcher.find()) {
                val domain = matcher.group(2)
                val username = matcher.group(3)

                "$username@$domain"
            } else {
                null
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Log.w("Lud06ToLud16", "Fail to convert LUD06 to LUD16", t)
            null
        }
    }

    fun toLnUrlp(str: String): String? =
        try {
            String(Bech32.decodeBytes(str, false).second)
        } catch (t: Throwable) {
            t.printStackTrace()
            Log.w("Lud06ToLud16", "Fail to convert LUD06 to LUD16", t)
            null
        }
}
