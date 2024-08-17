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
package androidx.compose.material3.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeRect
import androidx.window.layout.FoldingFeature

/**
 * Calculates the [Posture] for a given list of [FoldingFeature]s. This methods converts framework
 * folding info into the Material-opinionated posture info.
 */
fun calculatePosture(foldingFeatures: List<FoldingFeature>): Posture {
    var isTableTop = false
    val hingeList = mutableListOf<HingeInfo>()
    @Suppress("ListIterator")
    foldingFeatures.forEach {
        if (it.orientation == FoldingFeature.Orientation.HORIZONTAL &&
            it.state == FoldingFeature.State.HALF_OPENED
        ) {
            isTableTop = true
        }
        hingeList.add(
            HingeInfo(
                bounds = it.bounds.toComposeRect(),
                isFlat = it.state == FoldingFeature.State.FLAT,
                isVertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
                isSeparating = it.isSeparating,
                isOccluding = it.occlusionType == FoldingFeature.OcclusionType.FULL,
            ),
        )
    }
    return Posture(isTableTop, hingeList)
}

/**
 * Posture info that can help make layout adaptation decisions. For example when
 * [Posture.separatingVerticalHingeBounds] is not empty, the layout may want to avoid putting any
 * content over those hinge area. We suggest to use [calculatePosture] to retrieve instances of this
 * class in applications, unless you have a strong need of customization that cannot be fulfilled by
 * the default implementation.
 *
 * Note that the hinge bounds will be represent as [Rect] with window coordinates, instead of layout
 * coordinate.
 *
 * @constructor create an instance of [Posture]
 * @property isTabletop `true` if the current window is considered as in the table top mode, i.e.
 *           there is one half-opened horizontal hinge in the middle of the current window. When
 *           this is `true` it usually means it's hard for users to interact with the window area
 *           around the hinge and developers may consider separating the layout along the hinge and
 *           show software keyboard or other controls in the bottom half of the window.
 * @property hingeList a list of all hinges that are relevant to the posture.
 */
@Immutable
class Posture(
    val isTabletop: Boolean = false,
    val hingeList: List<HingeInfo> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Posture) return false
        if (isTabletop != other.isTabletop) return false
        if (hingeList != other.hingeList) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isTabletop.hashCode()
        result = 31 * result + hingeList.hashCode()
        return result
    }

    override fun toString(): String {
        @Suppress("ListIterator")
        return "Posture(isTabletop=$isTabletop, " +
            "hinges=[${hingeList.joinToString(", ")}])"
    }
}

/**
 * Returns the list of vertical hinge bounds that are separating.
 */
val Posture.separatingVerticalHingeBounds get() = hingeList.getBounds { isVertical && isSeparating }

/**
 *  Returns the list of vertical hinge bounds that are occluding.
 */
val Posture.occludingVerticalHingeBounds get() = hingeList.getBounds { isVertical && isOccluding }

/**
 *  Returns the list of all vertical hinge bounds.
 */
val Posture.allVerticalHingeBounds get() = hingeList.getBounds { isVertical }

/**
 * Returns the list of horizontal hinge bounds that are separating.
 */
val Posture.separatingHorizontalHingeBounds
    get() = hingeList.getBounds { !isVertical && isSeparating }

/**
 * Returns the list of horizontal hinge bounds that are occluding.
 */
val Posture.occludingHorizontalHingeBounds
    get() = hingeList.getBounds { !isVertical && isOccluding }

/**
 *  Returns the list of all horizontal hinge bounds.
 */
val Posture.allHorizontalHingeBounds
    get() = hingeList.getBounds { !isVertical }

/**
 * A class that contains the info of a hinge relevant to a [Posture].
 *
 * @param bounds the bounds of the hinge in the relevant viewport.
 * @param isFlat `true` if the hinge is fully open and the relevant window space presented to the
 *        user is flat.
 * @param isVertical `true` if the hinge is a vertical one, i.e., it separates the viewport into
 *        left and right; `false` if the hinge is horizontal, i.e., it separates the viewport
 *        into top and bottom.
 * @param isSeparating `true` if the hinge creates two logical display areas.
 * @param isOccluding `true` if the hinge conceals part of the display.
 */
@Immutable
class HingeInfo(
    val bounds: Rect,
    val isFlat: Boolean,
    val isVertical: Boolean,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HingeInfo) return false
        if (bounds != other.bounds) return false
        if (isFlat != other.isFlat) return false
        if (isVertical != other.isVertical) return false
        if (isSeparating != other.isSeparating) return false
        if (isOccluding != other.isOccluding) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bounds.hashCode()
        result = 31 * result + isFlat.hashCode()
        result = 31 * result + isVertical.hashCode()
        result = 31 * result + isSeparating.hashCode()
        result = 31 * result + isOccluding.hashCode()
        return result
    }

    override fun toString(): String =
        "HingeInfo(bounds=$bounds, " +
            "isFlat=$isFlat, " +
            "isVertical=$isVertical, " +
            "isSeparating=$isSeparating, " +
            "isOccluding=$isOccluding)"
}

private inline fun List<HingeInfo>.getBounds(predicate: HingeInfo.() -> Boolean): List<Rect> =
    @Suppress("ListIterator")
    mapNotNull { if (it.predicate()) it.bounds else null }
