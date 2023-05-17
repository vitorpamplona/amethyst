package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UserReactionsRow(model: UserReactionsViewModel, accountViewModel: AccountViewModel, navController: NavController, onClick: () -> Unit) {
    Row(verticalAlignment = CenterVertically, modifier = Modifier.clickable(onClick = onClick).padding(10.dp)) {
        Text(
            text = "Today",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.width(65.dp)
        )

        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1f)) {
            UserReplyReaction(model.replies[model.today])
        }

        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1f)) {
            UserBoostReaction(model.boosts[model.today])
        }

        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1f)) {
            UserLikeReaction(model.replies[model.today])
        }

        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1f)) {
            UserZapReaction(model.zaps[model.today])
        }
    }
}

class UserReactionsViewModel : ViewModel() {
    var user: User? = null

    var reactions by mutableStateOf<Map<String, Int>>(emptyMap())
    var boosts by mutableStateOf<Map<String, Int>>(emptyMap())
    var zaps by mutableStateOf<Map<String, BigDecimal>>(emptyMap())
    var replies by mutableStateOf<Map<String, Int>>(emptyMap())

    val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd") // SimpleDateFormat()
    val today = sdf.format(LocalDateTime.now())

    fun load(baseUser: User) {
        user = baseUser
        reactions = emptyMap()
        boosts = emptyMap()
        zaps = emptyMap()
        replies = emptyMap()
    }

    fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    fun formatDate(createAt: Long): String {
        return sdf.format(
            Instant.ofEpochSecond(createAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        )
    }

    fun refreshSuspended() {
        val currentUser = user?.pubkeyHex ?: return

        val reactions = mutableMapOf<String, Int>()
        val boosts = mutableMapOf<String, Int>()
        val zaps = mutableMapOf<String, BigDecimal>()
        val replies = mutableMapOf<String, Int>()

        LocalCache.notes.values.forEach {
            val noteEvent = it.event
            if (noteEvent is ReactionEvent) {
                if (noteEvent.isTaggedUser(currentUser)) {
                    val netDate = formatDate(noteEvent.createdAt)
                    reactions[netDate] = (reactions[netDate] ?: 0) + 1
                }
            }
            if (noteEvent is RepostEvent) {
                if (noteEvent.isTaggedUser(currentUser)) {
                    val netDate = formatDate(noteEvent.createdAt)
                    boosts[netDate] = (boosts[netDate] ?: 0) + 1
                }
            }
            if (noteEvent is LnZapEvent) {
                if (noteEvent.isTaggedUser(currentUser)) {
                    val netDate = formatDate(noteEvent.createdAt)
                    zaps[netDate] = (zaps[netDate] ?: BigDecimal.ZERO) + (noteEvent.amount ?: BigDecimal.ZERO)
                }
            }
            if (noteEvent is TextNoteEvent) {
                if (noteEvent.isTaggedUser(currentUser)) {
                    val netDate = formatDate(noteEvent.createdAt)
                    replies[netDate] = (replies[netDate] ?: 0) + 1
                }
            }
        }

        this.reactions = reactions
        this.replies = replies
        this.zaps = zaps
        this.boosts = boosts
    }

    var collectorJob: Job? = null

    init {
        collectorJob = viewModelScope.launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect { newNotes ->
                refresh()
            }
        }
    }

    override fun onCleared() {
        collectorJob?.cancel()
        super.onCleared()
    }
}

@Composable
fun UserReplyReaction(
    replyCount: Int?
) {
    Icon(
        painter = painterResource(R.drawable.ic_comment),
        null,
        modifier = Modifier.size(20.dp),
        tint = Color.Cyan
    )

    Spacer(modifier = Modifier.width(10.dp))

    Text(
        showCount(replyCount),
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}

@Composable
fun UserBoostReaction(
    boostCount: Int?
) {
    Icon(
        painter = painterResource(R.drawable.ic_retweeted),
        null,
        modifier = Modifier.size(20.dp),
        tint = Color.Unspecified
    )

    Spacer(modifier = Modifier.width(10.dp))

    Text(
        showCount(boostCount),
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}

@Composable
fun UserLikeReaction(
    likeCount: Int?
) {
    Icon(
        painter = painterResource(R.drawable.ic_liked),
        null,
        modifier = Modifier.size(20.dp),
        tint = Color.Unspecified
    )

    Spacer(modifier = Modifier.width(10.dp))

    Text(
        showCount(likeCount),
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}

@Composable
fun UserZapReaction(
    amount: BigDecimal?
) {
    Icon(
        imageVector = Icons.Default.Bolt,
        contentDescription = stringResource(R.string.zaps),
        modifier = Modifier.size(20.dp),
        tint = BitcoinOrange
    )

    Spacer(modifier = Modifier.width(8.dp))

    Text(
        showAmount(amount),
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}
