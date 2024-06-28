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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.R

class HttpStatusMessages {
    companion object {
        fun resourceIdFor(statusCode: Int = 0): Int? =
            when (statusCode) {
                400 -> R.string.http_status_400
                401 -> R.string.http_status_401
                402 -> R.string.http_status_402
                403 -> R.string.http_status_403
                404 -> R.string.http_status_404
                405 -> R.string.http_status_405
                406 -> R.string.http_status_406
                407 -> R.string.http_status_407
                408 -> R.string.http_status_408
                409 -> R.string.http_status_409
                410 -> R.string.http_status_410
                411 -> R.string.http_status_411
                412 -> R.string.http_status_412
                413 -> R.string.http_status_413
                414 -> R.string.http_status_414
                415 -> R.string.http_status_415
                416 -> R.string.http_status_416
                417 -> R.string.http_status_417
                426 -> R.string.http_status_426

                500 -> R.string.http_status_500
                501 -> R.string.http_status_501
                502 -> R.string.http_status_502
                503 -> R.string.http_status_503
                504 -> R.string.http_status_504
                505 -> R.string.http_status_505
                506 -> R.string.http_status_506
                507 -> R.string.http_status_507
                508 -> R.string.http_status_508
                511 -> R.string.http_status_511

                else -> null
            }
    }
}
