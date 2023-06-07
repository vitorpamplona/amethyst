package com.vitorpamplona.amethyst.ui.screen.loggedOff

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.format.DateFormat.getDateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.navigation.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.qrcode.SimpleQrCodeScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nostr.postr.Persona
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Date

class Kind(val kind: Int, val description: String, val checked: MutableState<Boolean>)

enum class DateType {
    INITIAL,
    END
}

class Delegation(
    var delegatee: Account,
    var delegator: String,
    var kinds: List<Kind>,
    var signature: String,
    var validStarting: Long,
    var validUntil: Long
) {
    override fun toString(): String {
        val selectedKinds = kinds.filter { it.checked.value }.joinToString(separator = "&") { "kind=${it.kind}" }
        val delegateeHex = delegatee.userProfile().pubkeyHex
        return "nostr:delegation:$delegateeHex:$selectedKinds&created_at>${validStarting.toString().dropLast(3)}&created_at<${validUntil.toString().dropLast(3)}"
    }
}

fun toTags(token: String, signature: String, nPubKey: String): List<String> {
    val keys = token.split(":")
    val parsed = Nip19.uriToRoute(nPubKey)

    return listOf(keys[1], parsed?.hex!!, keys[3], signature)
}

const val PAGES = 6

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DelegatedKeyDialog(
    onClose: () -> Unit,
    useProxy: Boolean,
    proxyPort: Int,
    onSuccess: (it: Delegation) -> Unit
) {
    val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, 1)
    val delegation = Delegation(
        Account(Persona(), proxy = proxy, proxyPort = proxyPort, delegatorNPubKey = "", delegationToken = "", delegationSignature = ""),
        "",
        listOf(
            Kind(1, "TextNote", remember { mutableStateOf(true) }),
            Kind(3, "Contacts", remember { mutableStateOf(true) }),
            Kind(7, "Reaction", remember { mutableStateOf(true) })
        ),
        "",
        Calendar.getInstance().timeInMillis,
        calendar.timeInMillis
    )
    val pagerState = rememberPagerState()
    val delegator = remember { mutableStateOf(TextFieldValue("")) }
    val signature = remember { mutableStateOf(TextFieldValue("")) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface {
            HorizontalPager(
                pageCount = PAGES,
                state = pagerState,
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> KindsPage(
                        onClose,
                        scope,
                        pagerState,
                        delegation.kinds,
                        context
                    )
                    1 -> DatePage(
                        onClose,
                        scope,
                        pagerState,
                        delegation,
                        DateType.INITIAL
                    )
                    2 -> DatePage(
                        onClose,
                        scope,
                        pagerState,
                        delegation,
                        DateType.END
                    )
                    3 -> SelectNPubPage(
                        delegation = delegation,
                        scope = scope,
                        context = context,
                        delegator = delegator,
                        pagerState = pagerState,
                        onClose = onClose
                    )
                    4 -> GenerateDelegationPage(
                        onClose,
                        scope,
                        pagerState,
                        delegation,
                        delegator,
                        context
                    )
                    5 -> SelectSignaturePage(
                        scope,
                        context,
                        delegation,
                        signature,
                        onClose,
                        pagerState,
                        onSuccess
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenerateDelegationPage(
    onClose: () -> Unit,
    scope: CoroutineScope,
    pagerState: PagerState,
    delegation: Delegation,
    delegator: MutableState<TextFieldValue>,
    context: Context
) {
    var openFolderDialog by remember {
        mutableStateOf(false)
    }
    var showQrCode by remember {
        mutableStateOf(false)
    }

    CustomPage(
        onClose,
        scope,
        pagerState
    ) {
        Text("Generate delegation string")
        println(delegation.delegatee.userProfile().pubkeyNpub())
        println(delegation.toString())
        Button(
            modifier = Modifier.padding(10.dp),
            onClick = {
                delegation.delegator = delegator.value.text
                showQrCode = true
            },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults
                .buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
        ) {
            Text(
                text = "Generate delegation",
                color = Color.White
            )
        }

        if (openFolderDialog) {
            LaunchFolderSelectionDialog(
                {
                    scope.launch {
                        saveStringToFolder(
                            context,
                            text = delegation.toString(),
                            folderUri = it,
                            fileName = "delegation_string.txt"
                        )
                        openFolderDialog = false
                    }
                },
                { openFolderDialog = false }
            )
        }

        if (showQrCode) {
            QrCodeDrawer(delegation.toString(), followTheme = false)
            Button(
                modifier = Modifier.padding(10.dp),
                onClick = {
                    openFolderDialog = true
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults
                    .buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
            ) {
                Text(
                    text = "Save to text file",
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DatePage(
    onClose: () -> Unit,
    scope: CoroutineScope,
    pagerState: PagerState,
    delegation: Delegation,
    type: DateType
) {
    val label = if (type == DateType.INITIAL) "Valid starting" else "Valid until"
    val context = LocalContext.current
    val currentDate = Date(if (type == DateType.INITIAL) delegation.validStarting else delegation.validUntil)
    val calendar = Calendar.getInstance()
    calendar.time = currentDate
    val dateFormat = getDateFormat(context)
    var datePicked by remember { mutableStateOf(dateFormat.format(Date(calendar.timeInMillis))) }

    val mDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            calendar.set(year, month, day)
            datePicked = dateFormat.format(Date(calendar.timeInMillis))
            if (type == DateType.INITIAL) {
                delegation.validStarting = calendar.timeInMillis
            } else {
                delegation.validUntil = calendar.timeInMillis
            }
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    CustomPage(
        onClose,
        scope,
        pagerState,
        {
            val isValidDate = delegation.validUntil > delegation.validStarting || type == DateType.INITIAL
            if (!isValidDate) {
                scope.launch {
                    Toast.makeText(context, "Valid until must be greater than Valid starting", Toast.LENGTH_SHORT).show()
                }
            }
            isValidDate
        }
    ) {
        Text(label)

        OutlinedTextField(
            readOnly = true,
            modifier = Modifier
                .padding(30.dp),
            value = datePicked,
            onValueChange = {
                datePicked = it
            },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go
            ),
            placeholder = {
                Text(
                    text = label,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            trailingIcon = {
                Row {
                    IconButton(onClick = { mDatePickerDialog.show() }) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "Show Date picker"
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun SelectSignaturePage(
    scope: CoroutineScope,
    context: Context,
    delegation: Delegation,
    signature: MutableState<TextFieldValue>,
    onClose: () -> Unit,
    pagerState: PagerState,
    onSuccess: (it: Delegation) -> Unit
) {
    var dialogOpen by remember {
        mutableStateOf(false)
    }
    var openFileDialog by remember {
        mutableStateOf(false)
    }
    var showPassword by remember {
        mutableStateOf(false)
    }
    val autofillNode = AutofillNode(
        autofillTypes = listOf(AutofillType.Password),
        onFill = {
            signature.value = TextFieldValue(it)
            delegation.signature = it
        }
    )
    val autofill = LocalAutofill.current
    LocalAutofillTree.current += autofillNode

    CustomPage(
        onClose,
        scope,
        pagerState,
        {
            delegation.signature = signature.value.text
            var isValidSig = true
            if (delegation.signature.isBlank()) {
                scope.launch {
                    Toast.makeText(context, "Signature is required", Toast.LENGTH_SHORT).show()
                }
                isValidSig = false
            }
//            if (isValidSig) {
//                try {
//                    val sig = Hex.decode(delegation.signature)
//                    val token = Event.sha256.digest(delegation.toString().toByteArray())
//                    val parsed = Nip19.uriToRoute(delegation.delegator)
//                    val pubKeyParsed = parsed?.hex?.hexToByteArray()
//                    if (!Secp256k1.get().verifySchnorr(sig, token, pubKeyParsed!!)) {
//                        scope.launch {
//                            Toast.makeText(context, "Invalid signature", Toast.LENGTH_SHORT).show()
//                        }
//                        isValidSig = false
//                    }
//                } catch (e: Exception) {
//                    scope.launch {
//                        Toast.makeText(context, "Invalid signature", Toast.LENGTH_SHORT).show()
//                    }
//                    isValidSig = false
//                }
//            }
            if (isValidSig) {
                onClose()
                onSuccess(delegation)
            }
            isValidSig
        }
    ) {
        Text("Scan signature from hardware wallet")

        if (openFileDialog) {
            LaunchFileSelectionDialog(
                {
                    scope.launch {
                        readTextFile(
                            context,
                            folderUri = it,
                            textField = signature
                        )
                        openFileDialog = false
                    }
                },
                { openFileDialog = false }
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .padding(30.dp)
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
            value = signature.value,
            onValueChange = {
                signature.value = it
                delegation.signature = it.text
            },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go
            ),
            placeholder = {
                Text(
                    text = "Signature",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            trailingIcon = {
                Row {
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
                }
            },
            leadingIcon = {
                if (dialogOpen) {
                    SimpleQrCodeScanner {
                        dialogOpen = false
                        if (!it.isNullOrEmpty()) {
                            signature.value = TextFieldValue(it)
                            delegation.signature = it
                        }
                    }
                }
                IconButton(onClick = { dialogOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_qrcode),
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colors.primary
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
        )
        Button(
            modifier = Modifier.padding(10.dp),
            onClick = {
                openFileDialog = true
            },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults
                .buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
        ) {
            Text(
                text = "Read from file",
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun SelectNPubPage(
    scope: CoroutineScope,
    context: Context,
    delegation: Delegation,
    delegator: MutableState<TextFieldValue>,
    onClose: () -> Unit,
    pagerState: PagerState
) {
    var dialogOpen by remember {
        mutableStateOf(false)
    }
    var openFileDialog by remember {
        mutableStateOf(false)
    }
    var showPassword by remember {
        mutableStateOf(false)
    }
    val autofillNode = AutofillNode(
        autofillTypes = listOf(AutofillType.Password),
        onFill = { delegator.value = TextFieldValue(it) }
    )
    val autofill = LocalAutofill.current
    LocalAutofillTree.current += autofillNode

    CustomPage(
        onClose,
        scope,
        pagerState,
        {
            delegation.delegator = delegator.value.text
            var returnValue = true
            if (delegator.value.text.isBlank()) {
                returnValue = false
                scope.launch {
                    Toast.makeText(context, context.getString(R.string.key_is_required), Toast.LENGTH_SHORT).show()
                }
            }
            if (returnValue) {
                try {
                    val parsed = Nip19.uriToRoute(delegator.value.text)
                    val pubKeyParsed = parsed?.hex?.hexToByteArray()
                    if (pubKeyParsed == null) {
                        scope.launch {
                            Toast.makeText(context, context.getString(R.string.invalid_key), Toast.LENGTH_SHORT).show()
                        }
                        returnValue = false
                    }
                } catch (e: Exception) {
                    returnValue = false
                    scope.launch {
                        Toast.makeText(context, context.getString(R.string.invalid_key), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            returnValue
        }
    ) {
        Text("Scan npub from hardware wallet")

        if (openFileDialog) {
            LaunchFileSelectionDialog(
                {
                    scope.launch {
                        readTextFile(
                            context,
                            folderUri = it,
                            textField = delegator
                        )
                        openFileDialog = false
                    }
                },
                { openFileDialog = false }
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .padding(30.dp)
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
            value = delegator.value,
            onValueChange = {
                delegator.value = it
                delegation.delegator = it.text
            },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go
            ),
            placeholder = {
                Text(
                    text = "npub",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            trailingIcon = {
                Row {
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
                }
            },
            leadingIcon = {
                if (dialogOpen) {
                    SimpleQrCodeScanner {
                        dialogOpen = false
                        if (!it.isNullOrEmpty()) {
                            delegator.value = TextFieldValue(it)
                            delegation.delegator = it
                        }
                    }
                }
                IconButton(onClick = { dialogOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_qrcode),
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colors.primary
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
        )
        Button(
            modifier = Modifier.padding(10.dp),
            onClick = {
                openFileDialog = true
            },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults
                .buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
        ) {
            Text(
                text = "Read from file",
                color = Color.White
            )
        }
    }
}

fun readTextFile(
    context: Context,
    folderUri: Uri,
    textField: MutableState<TextFieldValue>
) {
    try {
        val file = DocumentFile.fromSingleUri(context, folderUri)
        val input = context.contentResolver.openInputStream(file!!.uri)
        val reader = BufferedReader(InputStreamReader(input))
        textField.value = TextFieldValue(reader.readText())
        input?.close()
        reader.close()
    } catch (e: IOException) {
        Log.e("File", "Error reading file", e)
    }
}

@Composable
fun LaunchFolderSelectionDialog(
    onFolderSelected: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            onCancel()
        }
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data?.data
            intent?.let { uri ->
                onFolderSelected(uri)
            }
        }
    }

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    val rootUri = Uri.fromFile(Environment.getExternalStorageDirectory())
    intent.putExtra("android.provider.extra.INITIAL_URI", rootUri)
    SideEffect {
        launcher.launch(intent)
    }
}

@Composable
fun LaunchFileSelectionDialog(
    onFolderSelected: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            onCancel()
        }
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data?.data
            intent?.let { uri ->
                onFolderSelected(uri)
            }
        }
    }

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    val rootUri = Uri.fromFile(Environment.getExternalStorageDirectory())
    intent.putExtra("android.provider.extra.INITIAL_URI", rootUri)
    intent.type = "text/plain"
    SideEffect {
        launcher.launch(intent)
    }
}

fun saveStringToFolder(context: Context, text: String, folderUri: Uri, fileName: String) {
    try {
        val dir = DocumentFile.fromTreeUri(context, folderUri)
        dir?.findFile(fileName)?.delete()
        val file = dir!!.createFile("*/txt", fileName)
        val out = context.contentResolver.openOutputStream(file!!.uri)
        out?.write(text.toByteArray())
        out?.close()
    } catch (e: IOException) {
        Log.e("File", "Error saving file", e)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomPage(
    onClose: () -> Unit,
    scope: CoroutineScope,
    pagerState: PagerState,
    onValidation: () -> Boolean = { true },
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(10.dp)
            .fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CloseButton {
                    onClose()
                }
            }
            content()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, false)
        ) {
            Button(
                modifier = Modifier.padding(10.dp),
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults
                    .buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
            ) {
                Text(
                    text = "Prior",
                    color = Color.White
                )
            }
            Button(
                modifier = Modifier.padding(10.dp),
                onClick = {
                    if (!onValidation()) {
                        return@Button
                    }

                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults
                    .buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
            ) {
                Text(
                    text = "Next",
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KindsPage(
    onClose: () -> Unit,
    scope: CoroutineScope,
    pagerState: PagerState,
    kinds: List<Kind>,
    context: Context
) {
    CustomPage(
        onClose,
        scope,
        pagerState,
        {
            val isAnySelected = kinds.any { it.checked.value }
            if (!isAnySelected) {
                scope.launch {
                    Toast.makeText(context, "You must select a kind", Toast.LENGTH_SHORT).show()
                }
            }
            isAnySelected
        }
    ) {
        Text("Allowed kinds")
        Column(
            modifier = Modifier
                .padding(30.dp)
                .fillMaxWidth()
        ) {
            kinds.forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = kind.checked.value,
                        onCheckedChange = { checked ->
                            kind.checked.value = checked
                        }
                    )

                    Text(
                        modifier = Modifier.padding(start = 2.dp),
                        text = "[${kind.kind}] ${kind.description}"
                    )
                }
            }
        }
    }
}
