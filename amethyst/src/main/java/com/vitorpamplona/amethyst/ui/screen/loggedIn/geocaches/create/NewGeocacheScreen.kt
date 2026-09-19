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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geocaches.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_new_description
import com.vitorpamplona.amethyst.commons.resources.geocache_new_difficulty
import com.vitorpamplona.amethyst.commons.resources.geocache_new_hint
import com.vitorpamplona.amethyst.commons.resources.geocache_new_hint_preview
import com.vitorpamplona.amethyst.commons.resources.geocache_new_hint_scramble
import com.vitorpamplona.amethyst.commons.resources.geocache_new_ladder
import com.vitorpamplona.amethyst.commons.resources.geocache_new_location_warning
import com.vitorpamplona.amethyst.commons.resources.geocache_new_mission
import com.vitorpamplona.amethyst.commons.resources.geocache_new_modifiers
import com.vitorpamplona.amethyst.commons.resources.geocache_new_name
import com.vitorpamplona.amethyst.commons.resources.geocache_new_pick_location
import com.vitorpamplona.amethyst.commons.resources.geocache_new_proof_ready
import com.vitorpamplona.amethyst.commons.resources.geocache_new_publish
import com.vitorpamplona.amethyst.commons.resources.geocache_new_require_proof
import com.vitorpamplona.amethyst.commons.resources.geocache_new_section_extras
import com.vitorpamplona.amethyst.commons.resources.geocache_new_section_proof
import com.vitorpamplona.amethyst.commons.resources.geocache_new_section_what
import com.vitorpamplona.amethyst.commons.resources.geocache_new_section_where
import com.vitorpamplona.amethyst.commons.resources.geocache_new_terrain
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_qr_warning
import com.vitorpamplona.amethyst.commons.ui.note.geocacheEmoji
import com.vitorpamplona.amethyst.commons.ui.note.geocacheLabelRes
import com.vitorpamplona.amethyst.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.creators.location.GeohashLocationPickerDialog
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationPreviewMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.tags.geohash.toGeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheGeohash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The cache composer, in five sections, and the screen with the most at stake.
 *
 * Publishing a cache means publishing a permanent, public, five-metre-accurate location of a
 * real place, signed by the user's own key. The location warning is therefore not a footnote: it
 * sits directly under the picker, un-dismissable, and says that the event cannot be recalled,
 * because that is the sentence an author needs before they tap Publish rather than after.
 *
 * The hint preview exists for the same reason at a smaller scale. Amethyst writes ROT13 — the
 * reference client's convention — and an author who cannot see the scrambled form has no way to
 * know whether their hint survives it.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NewGeocacheScreen(
    nav: INav,
    accountViewModel: AccountViewModel,
    prefillGeohash: String? = null,
    editKind: Int? = null,
    editPubKeyHex: String? = null,
    editDTag: String? = null,
) {
    val model: NewGeocacheViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    remember(editKind, editPubKeyHex, editDTag, prefillGeohash) {
        model.init(accountViewModel)
        if (editKind != null && editPubKeyHex != null && editDTag != null) {
            model.loadForEdit(editKind, editPubKeyHex, editDTag)
        } else {
            model.prefillGeohash(prefillGeohash)
        }
        true
    }

    var pickingLocation by remember { mutableStateOf(false) }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { model.uploadImage(uri, null, context) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringRes(if (model.isEditing) R.string.route_edit_geocache else R.string.route_new_geocache))
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = stringRes(R.string.back),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = model.isValid() && !model.isPublishing.value,
                        onClick = {
                            scope.launch { if (model.publish()) nav.popBack() }
                        },
                    ) { Text(stringResource(Res.string.geocache_new_publish)) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                .consumeWindowInsets(pad)
                .imePaddingSafe()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            ComposerSection(stringResource(Res.string.geocache_new_section_where))

            val point =
                remember(model.geohash.value) {
                    runCatching {
                        model.geohash.value
                            .takeIf { it.isNotEmpty() }
                            ?.toGeoHash()
                    }.getOrNull()
                }

            if (point != null) {
                LocationPreviewMap(
                    latitude = point.centerLat,
                    longitude = point.centerLon,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    aspectRatio = 16f / 9f,
                    pinColor = MaterialTheme.colorScheme.primary,
                    pinEmoji = model.type.value.geocacheEmoji(),
                )
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(onClick = { pickingLocation = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.geocache_new_pick_location))
            }

            Spacer(Modifier.height(8.dp))

            WarningBox(stringResource(Res.string.geocache_new_location_warning))

            if (model.hasLocation()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.geocache_new_ladder, model.geohash.value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!model.isPreciseEnough()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // NIP-CC asks for 8 characters, 9 for a micro. Below that a finder is
                        // searching a block rather than a hiding place.
                        text = "⚠️  ${GeocacheGeohash.minPublishPrecision(model.size.value)} characters or finer is what a finder needs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            ComposerSection(stringResource(Res.string.geocache_new_section_what))

            OutlinedTextField(
                value = model.name.value,
                onValueChange = { model.name.value = it },
                label = { Text(stringResource(Res.string.geocache_new_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = model.description.value,
                onValueChange = { model.description.value = it },
                label = { Text(stringResource(Res.string.geocache_new_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Spacer(Modifier.height(12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CacheType.entries.forEach { entry ->
                    FilterChip(
                        selected = model.type.value == entry,
                        onClick = { model.type.value = entry },
                        label = { Text(entry.geocacheEmoji() + "  " + entry.geocacheLabelRes()?.let { stringResource(it) }.orEmpty()) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CacheSize.entries.forEach { entry ->
                    FilterChip(
                        selected = model.size.value == entry,
                        onClick = { model.size.value = entry },
                        label = { Text(stringResource(entry.geocacheLabelRes())) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            StarRow(stringResource(Res.string.geocache_new_difficulty), model.difficulty.value) { model.difficulty.value = it }
            StarRow(stringResource(Res.string.geocache_new_terrain), model.terrain.value) { model.terrain.value = it }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = !model.isUploading.value,
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) {
                    if (model.isUploading.value) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("📷")
                    }
                }
                if (model.images.isNotEmpty()) {
                    Text(
                        text = "${model.images.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ComposerSection(stringResource(Res.string.geocache_new_section_extras))

            OutlinedTextField(
                value = model.hint.value,
                onValueChange = { model.hint.value = it },
                label = { Text(stringResource(Res.string.geocache_new_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (model.hint.value.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.geocache_new_hint_scramble),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = model.scrambleHint.value, onCheckedChange = { model.scrambleHint.value = it })
                }
                Text(
                    text = stringResource(Res.string.geocache_new_hint_preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = model.hintPreview(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = model.mission.value,
                onValueChange = { model.mission.value = it },
                label = { Text(stringResource(Res.string.geocache_new_mission)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.geocache_new_modifiers),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeModifier.entries.forEach { entry ->
                    FilterChip(
                        selected = model.modifiers.contains(entry),
                        onClick = { model.toggleModifier(entry) },
                        label = { Text(entry.code) },
                    )
                }
            }

            ComposerSection(stringResource(Res.string.geocache_new_section_proof))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.geocache_new_require_proof),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = model.requireProof.value, onCheckedChange = { model.setRequireProof(it) })
            }

            model.generatedPrivateKey.value?.let { secret ->
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCodeDrawer(secret)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(Res.string.geocache_new_proof_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    WarningBox(stringResource(Res.string.geocache_owner_qr_warning))
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (pickingLocation) {
        GeohashLocationPickerDialog(
            initialGeohash = model.geohash.value.ifBlank { null },
            onDismiss = { pickingLocation = false },
            onConfirm = {
                model.geohash.value = it
                pickingLocation = false
            },
        )
    }
}

@Composable
private fun ComposerSection(title: String) {
    Spacer(Modifier.height(22.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun WarningBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(10.dp),
        )
    }
}

/** A 1–5 rating as five taps, which is how every geocaching client on earth shows D and T. */
@Composable
private fun StarRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row {
            (1..5).forEach { star ->
                Text(
                    text = if (star <= value) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onChange(star) }.padding(horizontal = 2.dp),
                )
            }
        }
    }
}
