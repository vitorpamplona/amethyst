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
package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.service.MoneroValidator
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneroTest {
    val validMoneroAddress = "48nyqvKwXsNKcryVsMVLmtDgC3BdaSW4UBgS3Nwj2GBfR8BsjsUArgsTfsj7aHyWDWFPTFBug3c7PJZi4kU6XHBp7DMbESE"
    val validMoneroSubAddress = "888tNkZrPN6JsEgekjMnABU4TBzc2Dt29EPAvkRxbANsAnjyPbb3iQ1YBRk1UXcdRsiKc9dhwMVgN5S9cQUiyoogDavup3H"
    val validMoneroIntegratedAddress = "4LL9oSLmtpccfufTMvppY6JwXNouMBzSkbLYfpAV5Usx3skxNgYeYTRj5UzqtReoS44qo9mtmXCqY45DJ852K5Jv2bYXZKKQePHES9khPK"
    val invalidMoneroAddress = "  "
    val blankAddress = ""
    val addressWithSpaces = " 888tNkZrPN6JsEgekjMnABU4TBzc2Dt29EPAvkRxbANsAnjyPbb3iQ1YBRk1UXcdRsiKc9dhwMVgN5S9cQUiyoogDavup3H "
    val addressWithSpacesEnd = "888tNkZrPN6JsEgekjMnABU4TBzc2Dt29EPAvkRxbANsAnjyPbb3iQ1YBRk1UXcdRsiKc9dhwMVgN5S9cQUiyoogDavup3H "
    val invalidAddress = "48nyqvKwXsNKcryVsMVLmtDgC3BdaSW4UBgS3Nwj2GBfR8BsjsUArgsTfsj7aHyWDWFPTFBug3c7PJZi4kU6XHBp"

    @Test
    fun validMoneroAddress() {
        assert(MoneroValidator.isValidAddress(validMoneroAddress))
    }

    @Test
    fun validMoneroSubAddress() {
        assert(MoneroValidator.isValidAddress(validMoneroSubAddress))
    }

    @Test
    fun validMoneroIntegratedAddress() {
        assert(MoneroValidator.isValidAddress(validMoneroIntegratedAddress))
    }

    @Test
    fun invalidMoneroAddresses() {
        assert(MoneroValidator.isValidAddress(invalidMoneroAddress) == false)
        assert(MoneroValidator.isValidAddress(addressWithSpaces) == false)
        assert(MoneroValidator.isValidAddress(invalidAddress) == false)
        assert(MoneroValidator.isValidAddress(addressWithSpacesEnd) == false)
        assert(MoneroValidator.isValidAddress(blankAddress) == false)
    }
}
