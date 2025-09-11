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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import kotlinx.collections.immutable.ImmutableList

@Composable
private fun GalleryImage(
    image: MediaUrlImage,
    allImages: ImmutableList<MediaUrlImage>,
    modifier: Modifier,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) {
    Box(modifier = modifier) {
        ZoomableContentView(
            content = image,
            images = allImages,
            roundedCorner = roundedCorner,
            contentScale = contentScale,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun ImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    roundedCorner: Boolean = true,
) {
    // Add vertical padding around the entire gallery for better text separation
    Column(modifier = modifier.padding(vertical = Size10dp)) {
        when {
            images.isEmpty() -> {
                // No images to display
            }
            images.size == 1 -> {
                // Single image - display full width
                GalleryImage(
                    image = images.first(),
                    allImages = images,
                    modifier = Modifier.fillMaxWidth(),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }
            images.size == 2 -> {
                // Two images - side by side in 4:3 ratio
                TwoImageGallery(
                    images = images,
                    accountViewModel = accountViewModel,
                    roundedCorner = roundedCorner,
                    modifier = Modifier,
                )
            }
            images.size == 3 -> {
                // Three images - one large, two small
                ThreeImageGallery(
                    images = images,
                    accountViewModel = accountViewModel,
                    roundedCorner = roundedCorner,
                    modifier = Modifier,
                )
            }
            images.size == 4 -> {
                // Four images - 2x2 grid
                FourImageGallery(
                    images = images,
                    accountViewModel = accountViewModel,
                    roundedCorner = roundedCorner,
                    modifier = Modifier,
                )
            }
            else -> {
                // Many images - use staggered grid with 4:3 ratio
                ManyImageGallery(
                    images = images,
                    accountViewModel = accountViewModel,
                    roundedCorner = roundedCorner,
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
private fun TwoImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.aspectRatio(4f / 3f),
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        repeat(2) { index ->
            GalleryImage(
                image = images[index],
                allImages = images,
                modifier = Modifier.weight(1f).fillMaxSize(),
                roundedCorner = roundedCorner,
                contentScale = ContentScale.Crop,
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
private fun ThreeImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.aspectRatio(4f / 3f),
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        // Large image on the left
        GalleryImage(
            image = images[0],
            allImages = images,
            modifier = Modifier.weight(2f).fillMaxSize(),
            roundedCorner = roundedCorner,
            contentScale = ContentScale.Crop,
            accountViewModel = accountViewModel,
        )

        // Two smaller images on the right
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            repeat(2) { index ->
                GalleryImage(
                    image = images[index + 1],
                    allImages = images,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
private fun FourImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.aspectRatio(4f / 3f),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        repeat(2) { rowIndex ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Size5dp),
            ) {
                repeat(2) { colIndex ->
                    val imageIndex = rowIndex * 2 + colIndex
                    GalleryImage(
                        image = images[imageIndex],
                        allImages = images,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManyImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
    modifier: Modifier,
) {
    // Calculate optimal grid layout for many images
    val columns =
        when {
            images.size <= 6 -> 3 // 3 columns for 5-6 images
            images.size <= 9 -> 3 // 3 columns for 7-9 images
            else -> 4 // 4 columns for 10+ images
        }

    if (images.size <= 20) {
        // For smaller sets, use non-lazy Column/Row approach (simpler, no constraint issues)
        val rows = (images.size + columns - 1) / columns // Ceiling division

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            repeat(rows) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    repeat(columns) { colIndex ->
                        val imageIndex = rowIndex * columns + colIndex
                        if (imageIndex < images.size) {
                            GalleryImage(
                                image = images[imageIndex],
                                allImages = images,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                roundedCorner = roundedCorner,
                                contentScale = ContentScale.Crop,
                                accountViewModel = accountViewModel,
                            )
                        } else {
                            // Empty space for incomplete rows
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    } else {
        // For larger sets, use LazyVerticalGrid with explicit height constraint
        val rows = (images.size + columns - 1) / columns
        // Calculate height: (image height + spacing) * rows - last spacing
        // Assume square images with 5dp spacing
        val gridHeight = (100 * rows + 5 * (rows - 1)).dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.height(gridHeight), // Explicit height constraint
            verticalArrangement = Arrangement.spacedBy(Size5dp),
            horizontalArrangement = Arrangement.spacedBy(Size5dp),
            userScrollEnabled = false,
        ) {
            items(images) { image ->
                GalleryImage(
                    image = image,
                    allImages = images,
                    modifier = Modifier.aspectRatio(1f),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}
