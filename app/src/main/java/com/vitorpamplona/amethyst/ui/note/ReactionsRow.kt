package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReactionsRow(baseNote: Note, accountViewModel: AccountViewModel) {
  val accountState by accountViewModel.accountLiveData.observeAsState()
  val account = accountState?.account ?: return

  val reactionsState by baseNote.live().reactions.observeAsState()
  val reactedNote = reactionsState?.note

  val boostsState by baseNote.live().boosts.observeAsState()
  val boostedNote = boostsState?.note

  val zapsState by baseNote.live().zaps.observeAsState()
  val zappedNote = zapsState?.note

  val repliesState by baseNote.live().replies.observeAsState()
  val replies = repliesState?.note?.replies ?: emptySet()

  val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
  val uri = LocalUriHandler.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var wantsToReplyTo by remember {
    mutableStateOf<Note?>(null)
  }

  if (wantsToReplyTo != null)
    NewPostView({ wantsToReplyTo = null }, wantsToReplyTo, account)

  var wantsToZap by remember { mutableStateOf(false) }
  var wantsToChangeZapAmount by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier
      .padding(top = 8.dp)
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    IconButton(
      modifier = Modifier.then(Modifier.size(20.dp)),
      onClick = {
        if (account.isWriteable())
          wantsToReplyTo = baseNote
        else
          scope.launch {
            Toast.makeText(
              context,
              "Login with a Private key to be able to reply",
              Toast.LENGTH_SHORT
            ).show()
          }
      }
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_comment),
        null,
        modifier = Modifier.size(15.dp),
        tint = grayTint,
      )
    }

    Text(
      "  ${showCount(replies.size)}",
      fontSize = 14.sp,
      color = grayTint,
      modifier = Modifier.weight(1f)
    )

    IconButton(
      modifier = Modifier.then(Modifier.size(20.dp)),
      onClick = {
        if (account.isWriteable())
          accountViewModel.boost(baseNote)
        else
          scope.launch {
            Toast.makeText(
              context,
              "Login with a Private key to be able to boost posts",
              Toast.LENGTH_SHORT
            ).show()
          }
      }
    ) {
      if (boostedNote?.isBoostedBy(account.userProfile()) == true) {
        Icon(
          painter = painterResource(R.drawable.ic_retweeted),
          null,
          modifier = Modifier.size(20.dp),
          tint = Color.Unspecified
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.ic_retweet),
          null,
          modifier = Modifier.size(20.dp),
          tint = grayTint
        )
      }
    }

    Text(
      "  ${showCount(boostedNote?.boosts?.size)}",
      fontSize = 14.sp,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
      modifier = Modifier.weight(1f)
    )

    IconButton(
      modifier = Modifier.then(Modifier.size(20.dp)),
      onClick = {
        if (account.isWriteable())
          accountViewModel.reactTo(baseNote)
        else
          scope.launch {
            Toast.makeText(
              context,
              "Login with a Private key to like Posts",
              Toast.LENGTH_SHORT
            ).show()
          }
      }
    ) {
      if (reactedNote?.isReactedBy(account.userProfile()) == true) {
        Icon(
          painter = painterResource(R.drawable.ic_liked),
          null,
          modifier = Modifier.size(16.dp),
          tint = Color.Unspecified
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.ic_like),
          null,
          modifier = Modifier.size(16.dp),
          tint = grayTint
        )
      }
    }

    Text(
      "  ${showCount(reactedNote?.reactions?.size)}",
      fontSize = 14.sp,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
      modifier = Modifier.weight(1f)
    )


    Row(
      modifier = Modifier
        .then(Modifier.size(20.dp))
        .combinedClickable(
          role = Role.Button,
          interactionSource = remember { MutableInteractionSource() },
          indication = rememberRipple(bounded = false, radius = 24.dp),
          onClick = {
            if (account.zapAmountChoices.isEmpty()) {
              scope.launch {
                Toast
                  .makeText(
                    context,
                    "No Zap Amount Setup. Long Press to change",
                    Toast.LENGTH_SHORT
                  )
                  .show()
              }
            } else if (!account.isWriteable()) {
              scope.launch {
                Toast
                  .makeText(
                    context,
                    "Login with a Private key to be able to send Zaps",
                    Toast.LENGTH_SHORT
                  )
                  .show()
              }
            } else if (account.zapAmountChoices.size == 1) {
              accountViewModel.zap(baseNote, account.zapAmountChoices.first() * 1000, "", context) {
                scope.launch {
                  Toast
                    .makeText(context, it, Toast.LENGTH_SHORT)
                    .show()
                }
              }
            } else if (account.zapAmountChoices.size > 1) {
              wantsToZap = true
            }
          },
          onLongClick = {
            wantsToChangeZapAmount = true
          }
        )
    ) {
      if (wantsToZap) {
        ZapAmountChoicePopup(
          baseNote,
          accountViewModel,
          onDismiss = {
            wantsToZap = false
          },
          onChangeAmount = {
            wantsToZap = false
            wantsToChangeZapAmount = true
          }
        )
      }
      if (wantsToChangeZapAmount) {
        UpdateZapAmountDialog({ wantsToChangeZapAmount = false }, account = account)
      }

      if (zappedNote?.isZappedBy(account.userProfile()) == true) {
        Icon(
          imageVector = Icons.Default.Bolt,
          contentDescription = "Zaps",
          modifier = Modifier.size(20.dp),
          tint = BitcoinOrange
        )
      } else {
        Icon(
          imageVector = Icons.Outlined.Bolt,
          contentDescription = "Zaps",
          modifier = Modifier.size(20.dp),
          tint = grayTint
        )
      }
    }

    Text(
      showAmount(zappedNote?.zappedAmount()),
      fontSize = 14.sp,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
      modifier = Modifier.weight(1f)
    )

    IconButton(
      modifier = Modifier.then(Modifier.size(20.dp)),
      onClick = { uri.openUri("https://counter.amethyst.social/${baseNote.idHex}/") }
    ) {
      Icon(
        imageVector = Icons.Outlined.BarChart,
        null,
        modifier = Modifier.size(19.dp),
        tint = grayTint
      )
    }

    Row(modifier = Modifier.weight(1f)) {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data("https://counter.amethyst.social/${baseNote.idHex}.svg?label=+&color=00000000")
          .crossfade(true)
          .diskCachePolicy(CachePolicy.DISABLED)
          .memoryCachePolicy(CachePolicy.ENABLED)
          .build(),
        contentDescription = "View count",
        modifier = Modifier.height(24.dp),
        colorFilter = ColorFilter.tint(grayTint)
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ZapAmountChoicePopup(baseNote: Note, accountViewModel: AccountViewModel, onDismiss: () -> Unit, onChangeAmount: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val accountState by accountViewModel.accountLiveData.observeAsState()
  val account = accountState?.account ?: return

  Popup(
    alignment = Alignment.BottomCenter,
    offset = IntOffset(0, -50),
    onDismissRequest = { onDismiss() }
  ) {

    FlowRow() {

      account.zapAmountChoices.forEach { amountInSats ->
        Button(
          modifier = Modifier.padding(horizontal = 3.dp),
          onClick = {
            accountViewModel.zap(baseNote, amountInSats * 1000, "", context) {
              scope.launch {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
              }
            }
            onDismiss()
          },
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults
            .buttonColors(
              backgroundColor = MaterialTheme.colors.primary
            )
        ) {
          Text("⚡ ${showAmount(amountInSats.toBigDecimal())}",
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.combinedClickable(
              onClick = {
                accountViewModel.zap(baseNote, amountInSats * 1000, "", context) {
                  scope.launch {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                  }
                }
                onDismiss()
              },
              onLongClick = {
                onChangeAmount()
              },
            )
          )
        }
      }

    }
  }
}

class UpdateZapAmountViewModel: ViewModel() {
  private var account: Account? = null

  var amounts by mutableStateOf(TextFieldValue(""))

  fun load(account: Account) {
    this.account = account
    this.amounts = TextFieldValue(account.zapAmountChoices.joinToString(", "))
  }

  fun toListOfAmounts(commaSeparatedAmounts: String): List<Long> {
    return commaSeparatedAmounts.split(",").map { it.trim().toLongOrNull() ?: 0 }
  }

  fun updateAmounts(commaSeparatedAmounts: TextFieldValue) {
    val correctedText = toListOfAmounts(commaSeparatedAmounts.text).joinToString(", ")
    amounts = TextFieldValue(correctedText, commaSeparatedAmounts.selection, commaSeparatedAmounts.composition)
  }

  fun sendPost() {
    account?.changeZapAmounts(toListOfAmounts(amounts.text))
    amounts = TextFieldValue("")
  }

  fun cancel() {
    amounts = TextFieldValue("")
  }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UpdateZapAmountDialog(onClose: () -> Unit, account: Account) {
  val postViewModel: UpdateZapAmountViewModel = viewModel()

  val ctx = LocalContext.current.applicationContext

  // initialize focus reference to be able to request focus programmatically
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(account) {
    postViewModel.load(account)
    delay(100)
    focusRequester.requestFocus()
  }

  Dialog(
    onDismissRequest = { onClose() },
    properties = DialogProperties(
      dismissOnClickOutside = false
    )
  ) {
    Surface() {
      Column(modifier = Modifier.padding(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CloseButton(onCancel = {
            postViewModel.cancel()
            onClose()
          })

          SaveButton(
            onPost = {
              postViewModel.sendPost()
              LocalPreferences(ctx).saveToEncryptedStorage(account)
              onClose()
            },
            isActive = postViewModel.amounts.text.isNotBlank()
          )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
          OutlinedTextField(
            label = { Text(text = "Comma-separated Zap amounts in sats") },
            value = postViewModel.amounts,
            onValueChange = {
              postViewModel.updateAmounts(it)
            },
            keyboardOptions = KeyboardOptions.Default.copy(
              capitalization = KeyboardCapitalization.None,
              keyboardType = KeyboardType.Number
            ),
            placeholder = {
              Text(
                text = "100, 1000, 5000",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
              )
            },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .focusRequester(focusRequester)
              .onFocusChanged {
                if (it.isFocused) {
                  keyboardController?.show()
                }
              }
          )
        }
      }
    }
  }
}

fun showCount(count: Int?): String {
  if (count == null) return " "
  if (count == 0) return " "

  return when {
    count >= 1000000000 -> "${Math.round(count / 1000000000f)}G"
    count >= 1000000 -> "${Math.round(count / 1000000f)}M"
    count >= 1000 -> "${Math.round(count / 1000f)}k"
    else -> "$count"
  }
}

val OneGiga = BigDecimal(1000000000)
val OneMega = BigDecimal(1000000)
val OneKilo = BigDecimal(1000)

fun showAmount(amount: BigDecimal?): String {
  if (amount == null) return " "
  if (amount.abs() < BigDecimal(0.01)) return " "

  return when {
    amount >= OneGiga -> "%.1fG".format(amount.div(OneGiga).setScale(1, RoundingMode.HALF_UP))
    amount >= OneMega -> "%.1fM".format(amount.div(OneMega).setScale(1, RoundingMode.HALF_UP))
    amount >= OneKilo -> "%.1fk".format(amount.div(OneKilo).setScale(1, RoundingMode.HALF_UP))
    else -> "%.0f".format(amount)
  }
}