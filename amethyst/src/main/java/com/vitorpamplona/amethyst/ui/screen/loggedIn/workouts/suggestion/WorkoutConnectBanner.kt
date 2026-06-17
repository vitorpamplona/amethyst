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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.launch

/**
 * Connect-only Health Connect prompt shown at the top of the Workouts feed. It
 * invites the user to grant Health Connect access; once granted (or dismissed,
 * or unavailable, or disabled in settings) it renders nothing. The actual list
 * of detected workouts lives only in the New Workout composer's carousel — this
 * banner never shows workouts.
 */
@Composable
fun WorkoutConnectBanner(
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val available = remember { HealthConnectManager.isAvailable(context) }
    if (!available) return

    val enabled by accountViewModel.settings.uiSettingsFlow.suggestWorkoutsFromHealthConnect
        .collectAsStateWithLifecycle()
    if (enabled == BooleanType.NEVER) return

    val manager = remember { HealthConnectManager(context) }
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf<Boolean?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            scope.launch { granted = manager.hasAllPermissions() }
        }

    LifecycleResumeEffect(Unit) {
        scope.launch { granted = manager.hasAllPermissions() }
        onPauseOrDispose {}
    }

    // Only when we know permission is missing (never during the check) and not dismissed.
    if (granted != false || dismissed) return

    ConnectHealthCard(
        modifier = modifier,
        onConnect = { permissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
        onDismiss = { dismissed = true },
    )
}

@Composable
private fun ConnectHealthCard(
    modifier: Modifier,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        symbol = MaterialSymbols.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                text = stringRes(R.string.workout_suggestion_connect_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(R.string.workout_suggestion_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringRes(R.string.workout_suggestion_connect_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onConnect) {
                Text(stringRes(R.string.workout_suggestion_connect_button))
            }
        }
    }
}
