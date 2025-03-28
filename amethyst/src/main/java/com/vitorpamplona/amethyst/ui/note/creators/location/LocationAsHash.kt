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
package com.vitorpamplona.amethyst.ui.note.creators.location

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationAsHash(
    postViewModel: ILocationGrabber,
    innerContent: @Composable () -> Unit = {},
) {
    val locationPermissionState =
        rememberPermissionState(
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

    LaunchedEffect(locationPermissionState.status.isGranted) {
        postViewModel.locationManager().setLocationPermission(locationPermissionState.status.isGranted)
    }

    if (locationPermissionState.status.isGranted) {
        DisplayLocationBox(postViewModel, innerContent)
    } else {
        LaunchedEffect(locationPermissionState) { locationPermissionState.launchPermissionRequest() }
    }
}

@Composable
fun DisplayLocationBox(
    model: ILocationGrabber,
    innerContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .height(20.dp)
                    .width(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = stringRes(R.string.geohash_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp),
            )

            DisplayLocationObserver(model)
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringRes(R.string.geohash_explainer),
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        innerContent()
    }
}
