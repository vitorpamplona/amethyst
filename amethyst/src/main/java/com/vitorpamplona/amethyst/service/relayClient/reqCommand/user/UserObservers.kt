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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata

@Composable
fun observeUser(user: User): State<UserState?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user.live().metadata.observeAsState()
}

@Composable
fun observeUserName(user: User): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .live()
        .metadata
        .map { it.user.toBestDisplayName() }
        .distinctUntilChanged()
        .observeAsState(user.toBestDisplayName())
}

@Composable
fun observeUserNip05(user: User): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .live()
        .metadata
        .map { it.user.info?.nip05 }
        .distinctUntilChanged()
        .observeAsState(user.info?.nip05)
}

@Composable
fun observeUserAboutMe(user: User): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .live()
        .metadata
        .map { it.user.info?.about ?: "" }
        .distinctUntilChanged()
        .observeAsState(user.info?.about ?: "")
}

@Composable
fun observeUserInfo(user: User): State<UserMetadata?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .live()
        .metadata
        .map { it.user.info }
        .distinctUntilChanged()
        .observeAsState(user.info)
}

@Composable
fun observeUserBanner(user: User): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .live()
        .metadata
        .map { it.user.info?.banner ?: "" }
        .distinctUntilChanged()
        .observeAsState(user.info?.banner ?: "")
}

@Composable
fun observeUserPicture(user: User): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .live()
        .metadata
        .map { it.user.info?.picture }
        .distinctUntilChanged()
        .observeAsState(user.info?.picture)
}

@Composable
fun observeUserShortName(user: User): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .live()
        .metadata
        .map { it.user.toBestShortFirstName() }
        .distinctUntilChanged()
        .observeAsState(user.toBestShortFirstName())
}
