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
package com.vitorpamplona.amethyst.commons.nip64Chess

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vitorpamplona.amethyst.commons.util.appDataDir
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * The desktop dismissed-games store, in the shared app data directory.
 *
 * Built here rather than in desktopApp so a front end does not need DataStore
 * on its own classpath to get one. Replaces a `java.util.prefs` node without
 * carrying it over — the dismissed list is a convenience, and chess has few
 * enough users that a migration is not worth the code.
 */
fun desktopChessDismissedGamesStore(): ChessDismissedGamesStore {
    val file = File(appDataDir, "chess_dismissed_games.preferences_pb")
    file.parentFile?.mkdirs()
    return ChessDismissedGamesStore(PreferenceDataStoreFactory.createWithPath(produceFile = { file.toOkioPath() }))
}
