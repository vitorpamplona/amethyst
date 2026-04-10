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
package com.vitorpamplona.amethyst.ios.ui.labels

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip32Labeling.selfReportLabels
import com.vitorpamplona.quartz.nip32Labeling.selfReportNamespaces
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelTag

/**
 * Self-reported label data extracted from any event's NIP-32 `l`/`L` tags.
 */
data class LabelDisplayData(
    val labels: List<LabelTag>,
    val namespaces: List<String>,
) {
    val isEmpty: Boolean get() = labels.isEmpty()
}

/**
 * Extract self-reported NIP-32 labels from any event.
 * Returns null if the event has no labels.
 */
fun Event.toLabelDisplayData(): LabelDisplayData? {
    val labels = selfReportLabels()
    val namespaces = selfReportNamespaces().map { it.namespace }
    if (labels.isEmpty()) return null
    return LabelDisplayData(labels = labels, namespaces = namespaces)
}
