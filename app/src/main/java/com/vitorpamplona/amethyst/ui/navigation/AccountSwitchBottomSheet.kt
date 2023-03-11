package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.RoboHashCache
import com.vitorpamplona.amethyst.ui.components.AsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AccountSwitchBottomSheet(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
    sheetState: ModalBottomSheetState
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val accounts = LocalPreferences.findAllLocalAccounts()

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val accountUserState by account.userProfile().live().metadata.observeAsState()
    val accountUser = accountUserState?.user ?: return

    var popupExpanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Select Account", fontWeight = FontWeight.Bold)
        }
        accounts.forEach { acc ->
            val current = accountUser.pubkeyNpub() == acc.npub

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp, 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImageProxy(
                    model = ResizeImage(acc.profilePicture, 64.dp),
                    placeholder = BitmapPainter(RoboHashCache.get(context, acc.npub)),
                    fallback = BitmapPainter(RoboHashCache.get(context, acc.npub)),
                    error = BitmapPainter(RoboHashCache.get(context, acc.npub)),
                    contentDescription = stringResource(id = R.string.profile_image),
                    modifier = Modifier
                        .width(64.dp)
                        .height(64.dp)
                        .clip(shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    acc.displayName?.let {
                        Text(it)
                    }
                    Text(acc.npub.toShortenHex())
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (current) {
                    Text("✓")
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { /*TODO*/ }) {
                    Icon(imageVector = Icons.Default.Logout, "Logout")
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { popupExpanded = true }) {
                Text("Add New Account")
            }
        }
    }

    if (popupExpanded) {
        Dialog(
            onDismissRequest = { popupExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(MaterialTheme.colors.surface),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val key = remember { mutableStateOf(TextFieldValue("")) }
                    var errorMessage by remember { mutableStateOf("") }
                    var showPassword by remember {
                        mutableStateOf(false)
                    }
                    val autofillNode = AutofillNode(
                        autofillTypes = listOf(AutofillType.Password),
                        onFill = { key.value = TextFieldValue(it) }
                    )
                    val autofill = LocalAutofill.current
                    LocalAutofillTree.current += autofillNode

                    OutlinedTextField(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                autofillNode.boundingBox = coordinates.boundsInWindow()
                            }
                            .onFocusChanged { focusState ->
                                autofill?.run {
                                    if (focusState.isFocused) {
                                        requestAutofillForNode(autofillNode)
                                    } else {
                                        cancelAutofillForNode(autofillNode)
                                    }
                                }
                            },
                        value = key.value,
                        onValueChange = { key.value = it },
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go
                        ),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.nsec_npub_hex_private_key),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) {
                                        stringResource(R.string.show_password)
                                    } else {
                                        stringResource(
                                            R.string.hide_password
                                        )
                                    }
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                try {
                                    accountStateViewModel.login(key.value.text)
                                } catch (e: Exception) {
                                    errorMessage = context.getString(R.string.invalid_key)
                                }
                            }
                        )
                    )
                    if (errorMessage.isNotBlank()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
        }
    }
}
