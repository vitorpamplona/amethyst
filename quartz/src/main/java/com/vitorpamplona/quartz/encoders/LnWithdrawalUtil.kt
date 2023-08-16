package com.vitorpamplona.quartz.encoders

import java.util.regex.Pattern

object LnWithdrawalUtil {
    private val withdrawalPattern = Pattern.compile(
        "lnurl.+",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Finds LN withdrawal in the provided input string and returns it.
     * For example for input = "aaa bbb lnbc1xxx ccc" it will return "lnbc1xxx"
     * It will only return the first withdrawal found in the input.
     *
     * @return the invoice if it was found. null for null input or if no invoice is found
     */
    fun findWithdrawal(input: String?): String? {
        if (input == null) {
            return null
        }
        val matcher = withdrawalPattern.matcher(input)
        return if (matcher.find()) {
            matcher.group()
        } else {
            null
        }
    }
}
