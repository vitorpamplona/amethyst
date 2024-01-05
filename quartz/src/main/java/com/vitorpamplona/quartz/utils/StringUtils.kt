package com.vitorpamplona.quartz.utils

import kotlin.math.min

fun String.containsIgnoreCase(term: String): Boolean {
    if (term.isEmpty()) return true // Empty string is contained

    val whatUppercase = term.uppercase()
    val whatLowercase = term.lowercase()

    return containsIgnoreCase(whatLowercase, whatUppercase)
}

fun String.containsIgnoreCase(whatLowercase: String, whatUppercase: String): Boolean {
    var myOffset: Int
    var whatOffset: Int
    val termLength = min(whatUppercase.length, whatLowercase.length)

    for (i in 0 .. this.length - termLength) {
        if (this[i] != whatLowercase[0] && this[i] != whatUppercase[0]) continue

        myOffset = i+1
        whatOffset = 1
        while (whatOffset < termLength) {
            if (this[myOffset] != whatUppercase[whatOffset] && this[myOffset] != whatLowercase[whatOffset]) {
                break
            }
            myOffset++
            whatOffset++
        }
        if (whatOffset == termLength) return true
    }
    return false
}

fun String.containsAny(terms: List<DualCase>): Boolean {
    if (terms.isEmpty()) return true // Empty string is contained

    if (terms.size == 1) {
        return containsIgnoreCase(terms[0].lowercase, terms[0].uppercase)
    }

    return terms.any {
        containsIgnoreCase(it.lowercase, it.uppercase)
    }
}

class DualCase(val lowercase: String, val uppercase: String)