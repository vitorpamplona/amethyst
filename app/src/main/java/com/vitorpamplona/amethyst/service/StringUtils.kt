package com.vitorpamplona.amethyst.service

fun String.isUTF16Char(pos: Int): Boolean {
    return Character.charCount(this.codePointAt(pos)) == 2
}

fun String.firstFullChar(): String {
    return when (this.length) {
        0, 1 -> return this
        2, 3 -> return if (isUTF16Char(0)) this.take(2) else this.take(1)
        else -> {
            val first = isUTF16Char(0)
            val second = isUTF16Char(2)
            if (first && second) {
                this.take(4)
            } else if (first) {
                this.take(2)
            } else {
                this.take(1)
            }
        }
    }
}
