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
package com.vitorpamplona.quartz.nip01Core.tags.geohash

enum class GeohashPrecision(
    val digits: Int,
) {
    KM_5000_X_5000(1), // 5,000km	×	5,000km
    KM_1250_X_625(2), // 1,250km	×	625km
    KM_156_X_156(3), //   156km	×	156km
    KM_39_X_19(4), //  39.1km	×	19.5km
    KM_5_X_5(5), //  4.89km	×	4.89km
    M_1000_X_600(6), //  1.22km	×	0.61km
    M_153_X_153(7), //    153m	×	153m
    M_38_X_19(8), //   38.2m	×	19.1m
    M_5_X_5(9), //   4.77m	×	4.77m
    MM_1000_X_1000(10), //   1.19m	×	0.596m
    MM_149_X_149(11), //   149mm	×	149mm
    MM_37_X_18(12), //  37.2mm	×	18.6mm
}
