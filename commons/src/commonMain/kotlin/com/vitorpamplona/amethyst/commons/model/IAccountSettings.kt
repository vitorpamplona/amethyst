/*
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
package com.vitorpamplona.amethyst.commons.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for account settings needed by DAL feed filters in commons.
 * Abstracts Android-specific AccountSettings for use in KMP modules.
 *
 * DAL filters access settings.defaultXxxFollowList.value to determine
 * which follow list to use for each feed type.
 */
interface IAccountSettings {
    val defaultHomeFollowList: StateFlow<TopFilter>
    val defaultStoriesFollowList: StateFlow<TopFilter>
    val defaultNotificationFollowList: StateFlow<TopFilter>
    val defaultPollsFollowList: StateFlow<TopFilter>
    val defaultPicturesFollowList: StateFlow<TopFilter>
    val defaultShortsFollowList: StateFlow<TopFilter>
    val defaultLongsFollowList: StateFlow<TopFilter>
}
