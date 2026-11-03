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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemLabelIndex
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.quartz.nip01Core.core.Event

/** The title [item] shows, its latest authorised rename applied ([GitItemTitles.titleOf]). */
@Composable
fun observeGitItemTitle(item: Event): String? {
    val labels by GitItemLabelIndex.byItem.collectAsStateWithLifecycle()
    val authority = observeGitAuthority()
    return remember(item, labels, authority) { GitItemTitles.titleOf(item, labels?.get(item.id).orEmpty(), authority) }
}

/** The labels [item] shows: its own plus authorised NIP-32 `#t` labels ([GitItemTitles.labelsOf]). */
@Composable
fun observeGitItemLabels(item: Event): List<String> {
    val labels by GitItemLabelIndex.byItem.collectAsStateWithLifecycle()
    val authority = observeGitAuthority()
    return remember(item, labels, authority) { GitItemTitles.labelsOf(item, labels?.get(item.id).orEmpty(), authority) }
}
