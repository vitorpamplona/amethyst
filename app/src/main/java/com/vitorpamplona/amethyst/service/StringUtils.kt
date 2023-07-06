package com.vitorpamplona.amethyst.service

fun String.isUTF16Char(pos: Int): Boolean {
    println("Test $pos ${Character.charCount(this.codePointAt(pos))}")
    return Character.charCount(this.codePointAt(pos)) == 2
}

fun String.firstFullCharOld(): String {
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

fun String.firstFullChar(): String {
    var isInJoin = false
    var start = 0
    var previousCharLength = 0
    var next: Int
    var codePoint: Int

    var i = 0

    while (i < this.length) {
        codePoint = codePointAt(i)

        // Skips if it starts with the join char 0x200D
        if (codePoint == 0x200D && previousCharLength == 0) {
            next = offsetByCodePoints(i, 1)
            start = next
        } else {
            // If join, searches for the next char
            if (codePoint == 0x200D) {
                isInJoin = true
            } else {
                // stops when two chars are not joined together
                if ((previousCharLength > 0) && (!isInJoin) && Character.charCount(codePoint) == 1) {
                    break
                }

                isInJoin = false
            }

            // next char to evaluate
            next = offsetByCodePoints(i, 1)
            previousCharLength += (next - i)
        }

        i = next
    }

    // if ends in join, then seachers backwards until a char is found.
    if (isInJoin) {
        i = previousCharLength - 1
        while (i > 0) {
            if (this[i].code == 0x200D) {
                previousCharLength -= 1
            } else {
                break
            }

            i -= 1
        }
    }

    return substring(start, start + previousCharLength)
}
