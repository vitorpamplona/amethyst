package com.vitorpamplona.quartz.encoders

import android.util.Log
import java.util.regex.Pattern

val lnurlpPattern = Pattern.compile("(?i:http|https):\\/\\/((.+)\\/)*\\.well-known\\/lnurlp\\/(.*)")

class Lud06 {
    fun toLud16(str: String): String? {
        return try {
            val url = toLnUrlp(str)

            val matcher = lnurlpPattern.matcher(url)
            matcher.find()
            val domain = matcher.group(2)
            val username = matcher.group(3)

            "$username@$domain"
        } catch (t: Throwable) {
            t.printStackTrace()
            Log.w("Lud06ToLud16","Fail to convert LUD06 to LUD16",t)
            null
        }
    }

    fun toLnUrlp(str: String): String? {
        return try {
            String(Bech32.decodeBytes(str, false).second)
        } catch (t: Throwable) {
            t.printStackTrace()
            Log.w("Lud06ToLud16","Fail to convert LUD06 to LUD16",t)
            null
        }
    }
}