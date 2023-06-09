package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.service.Nip26
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DelegationTests {
    @Test
    fun validToken() {
        val delegation = "nostr:delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb959691396:kind=1&created_at>1674834236&created_at<1677426236"

        assertEquals(true, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun tokenWithoutNostrKey() {
        val delegation = "delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb959691396:kind=1&created_at>1674834236&created_at<1677426236"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun tokenWithoutDelegationKey() {
        val delegation = "nostr:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb959691396:kind=1&created_at>1674834236&created_at<1677426236"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun tokenWithoutKind() {
        val delegation = "nostr:delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb959691396:created_at>1674834236&created_at<1677426236"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun tokenWithoutCreatedAt() {
        val delegation = "nostr:delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb959691396:kind=1"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun invalidHexKey() {
        val delegation = "nostr:delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb9596913:kind=1&created_at>1674834236&created_at<1677426236"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun withoutParams() {
        val delegation = "nostr:delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb9596913"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun invalidCreatedAt() {
        val delegation = "nostr:delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb959691396:kind=1&created_at=1674834236&created_at<1677426236"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun invalidCreatedAt2() {
        val delegation = "nostr:delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb959691396:kind=1&created_at>=1674834236&created_at<1677426236"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }

    @Test
    fun unknownParam() {
        val delegation = "nostr:delegation:477318cfb5427b9cfc66a9fa376150c1ddbc62115ae27cef72417eb959691396:test=123&kind=1&created_at>=1674834236&created_at<1677426236"

        assertEquals(false, Nip26.isValidDelegation(delegation))
    }
}
