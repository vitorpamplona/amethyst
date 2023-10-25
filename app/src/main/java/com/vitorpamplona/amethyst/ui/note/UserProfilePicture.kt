package com.vitorpamplona.amethyst.ui.note

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.ExternalSignerUtils
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ReportNoteDialog
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toHexKey
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    NoteAuthorPicture(baseNote, size, accountViewModel, pictureModifier) {
        nav("User/${it.pubkeyHex}")
    }
}

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null
) {
    val author by baseNote.live().authorChanges.observeAsState(baseNote.author)

    Crossfade(targetState = author) {
        if (it == null) {
            DisplayBlankAuthor(size, modifier)
        } else {
            ClickableUserPicture(it, size, accountViewModel, modifier, onClick)
        }
    }
}

@Composable
fun DisplayBlankAuthor(size: Dp, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.background

    val nullModifier = remember {
        modifier
            .size(size)
            .clip(shape = CircleShape)
            .background(backgroundColor)
    }

    RobohashAsyncImage(
        robot = "authornotfound",
        contentDescription = stringResource(R.string.unknown_author),
        modifier = nullModifier
    )
}

@Composable
fun UserPicture(
    userHex: String,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    LoadUser(baseUserHex = userHex, accountViewModel) {
        if (it != null) {
            UserPicture(
                user = it,
                size = size,
                pictureModifier = pictureModifier,
                accountViewModel = accountViewModel,
                nav = nav
            )
        } else {
            DisplayBlankAuthor(
                size,
                pictureModifier
            )
        }
    }
}

@Composable
fun UserPicture(
    user: User,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val route by remember(user) {
        derivedStateOf {
            "User/${user.pubkeyHex}"
        }
    }

    ClickableUserPicture(
        baseUser = user,
        size = size,
        accountViewModel = accountViewModel,
        modifier = pictureModifier,
        onClick = {
            nav(route)
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null,
    onLongClick: ((User) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val ripple = rememberRipple(bounded = false, radius = size)

    // BaseUser is the same reference as accountState.user
    val myModifier = remember {
        if (onClick != null && onLongClick != null) {
            Modifier
                .size(size)
                .combinedClickable(
                    onClick = { onClick(baseUser) },
                    onLongClick = { onLongClick(baseUser) },
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = ripple
                )
        } else if (onClick != null) {
            Modifier
                .size(size)
                .clickable(
                    onClick = { onClick(baseUser) },
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = ripple
                )
        } else {
            Modifier.size(size)
        }
    }

    Box(modifier = myModifier, contentAlignment = Alignment.TopEnd) {
        BaseUserPicture(baseUser, size, accountViewModel, modifier)
    }
}

@Composable
fun NonClickableUserPictures(
    users: ImmutableSet<HexKey>,
    size: Dp,
    accountViewModel: AccountViewModel
) {
    val myBoxModifier = remember {
        Modifier.size(size)
    }

    Box(myBoxModifier, contentAlignment = Alignment.TopEnd) {
        val userList = remember(users) {
            users.toList()
        }

        when (userList.size) {
            0 -> {}
            1 -> LoadUser(baseUserHex = userList[0], accountViewModel) {
                it?.let {
                    BaseUserPicture(it, size, accountViewModel, outerModifier = Modifier)
                }
            }
            2 -> {
                LoadUser(baseUserHex = userList[0], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(1.5f), accountViewModel, outerModifier = Modifier.align(Alignment.CenterStart))
                    }
                }
                LoadUser(baseUserHex = userList[1], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(1.5f), accountViewModel, outerModifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
            }
            3 -> {
                LoadUser(baseUserHex = userList[0], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(1.8f), accountViewModel, outerModifier = Modifier.align(Alignment.BottomStart))
                    }
                }
                LoadUser(baseUserHex = userList[1], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(1.8f), accountViewModel, outerModifier = Modifier.align(Alignment.TopCenter))
                    }
                }
                LoadUser(baseUserHex = userList[2], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(1.8f), accountViewModel, outerModifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
            else -> {
                LoadUser(baseUserHex = userList[0], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(2f), accountViewModel, outerModifier = Modifier.align(Alignment.BottomStart))
                    }
                }
                LoadUser(baseUserHex = userList[1], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(2f), accountViewModel, outerModifier = Modifier.align(Alignment.TopStart))
                    }
                }
                LoadUser(baseUserHex = userList[2], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(2f), accountViewModel, outerModifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
                LoadUser(baseUserHex = userList[3], accountViewModel) {
                    it?.let {
                        BaseUserPicture(it, size.div(2f), accountViewModel, outerModifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }
}

@Composable
fun BaseUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    innerModifier: Modifier = Modifier,
    outerModifier: Modifier = remember { Modifier.size(size) }
) {
    Box(outerModifier, contentAlignment = Alignment.TopEnd) {
        InnerBaseUserPicture(baseUser, size, accountViewModel, innerModifier)
    }
}

@Composable
fun InnerBaseUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier
) {
    val userProfile by baseUser.live().profilePictureChanges.observeAsState(baseUser.profilePicture())

    PictureAndFollowingMark(
        userHex = baseUser.pubkeyHex,
        userPicture = userProfile,
        size = size,
        modifier = modifier,
        accountViewModel = accountViewModel
    )
}

@Composable
fun PictureAndFollowingMark(
    userHex: String,
    userPicture: String?,
    size: Dp,
    modifier: Modifier,
    accountViewModel: AccountViewModel
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val myImageModifier = remember {
        modifier
            .size(size)
            .clip(shape = CircleShape)
            .background(backgroundColor)
    }

    val automaticallyShowProfilePicture = remember {
        accountViewModel.settings.showProfilePictures.value
    }

    RobohashAsyncImageProxy(
        robot = userHex,
        model = userPicture,
        contentDescription = stringResource(id = R.string.profile_image),
        modifier = myImageModifier,
        contentScale = ContentScale.Crop,
        loadProfilePicture = automaticallyShowProfilePicture
    )
    val myIconSize by remember(size) {
        derivedStateOf {
            size.div(3.5f)
        }
    }
    ObserveAndDisplayFollowingMark(userHex, myIconSize, accountViewModel)
}

@Composable
fun ObserveAndDisplayFollowingMark(userHex: String, iconSize: Dp, accountViewModel: AccountViewModel) {
    WatchUserFollows(userHex, accountViewModel) { newFollowingState ->
        AnimatedVisibility(
            visible = newFollowingState,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                FollowingIcon(iconSize)
            }
        }
    }
}

@Composable
fun WatchUserFollows(userHex: String, accountViewModel: AccountViewModel, onFollowChanges: @Composable (Boolean) -> Unit) {
    val showFollowingMark by remember {
        accountViewModel.userFollows.map {
            it.user.isFollowingCached(userHex) || (userHex == accountViewModel.account.userProfile().pubkeyHex)
        }.distinctUntilChanged()
    }.observeAsState(false)

    onFollowChanges(showFollowingMark)
}

@Immutable
data class DropDownParams(
    val isFollowingAuthor: Boolean,
    val isPrivateBookmarkNote: Boolean,
    val isPublicBookmarkNote: Boolean,
    val isLoggedUser: Boolean,
    val isSensitive: Boolean,
    val showSensitiveContent: Boolean?
)

@Composable
fun NoteDropDownMenu(note: Note, popupExpanded: MutableState<Boolean>, accountViewModel: AccountViewModel) {
    var reportDialogShowing by remember { mutableStateOf(false) }

    var state by remember {
        mutableStateOf<DropDownParams>(
            DropDownParams(
                isFollowingAuthor = false,
                isPrivateBookmarkNote = false,
                isPublicBookmarkNote = false,
                isLoggedUser = false,
                isSensitive = false,
                showSensitiveContent = null
            )
        )
    }

    val onDismiss = remember(popupExpanded) {
        { popupExpanded.value = false }
    }

    DropdownMenu(
        expanded = popupExpanded.value,
        onDismissRequest = onDismiss
    ) {
        val clipboardManager = LocalClipboardManager.current
        val appContext = LocalContext.current.applicationContext
        val actContext = LocalContext.current

        WatchBookmarksFollowsAndAccount(note, accountViewModel) { newState ->
            if (state != newState) {
                state = newState
            }
        }

        val scope = rememberCoroutineScope()

        if (!state.isFollowingAuthor) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.follow))
                },
                onClick = {
                    val author = note.author ?: return@DropdownMenuItem
                    accountViewModel.follow(author)
                    onDismiss()
                }
            )
            Divider()
        }
        DropdownMenuItem(
            text = {
                Text(stringResource(R.string.copy_text))
            },
            onClick = {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(AnnotatedString(accountViewModel.decrypt(note) ?: ""))
                    onDismiss()
                }
            }
        )
        DropdownMenuItem(
            text = {
                Text(stringResource(R.string.copy_user_pubkey))
            },
            onClick = {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(AnnotatedString("nostr:${note.author?.pubkeyNpub()}"))
                    onDismiss()
                }
            }
        )
        DropdownMenuItem(
            text = {
                Text(stringResource(R.string.copy_note_id))
            },
            onClick = {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(AnnotatedString("nostr:" + note.toNEvent()))
                    onDismiss()
                }
            }
        )
        DropdownMenuItem(
            text = {
                Text(stringResource(R.string.quick_action_share))
            },
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        externalLinkForNote(note)
                    )
                    putExtra(Intent.EXTRA_TITLE, actContext.getString(R.string.quick_action_share_browser_link))
                }

                val shareIntent = Intent.createChooser(sendIntent, appContext.getString(R.string.quick_action_share))
                ContextCompat.startActivity(actContext, shareIntent, null)
                onDismiss()
            }
        )
        Divider()
        DropdownMenuItem(
            text = {
                Text(stringResource(R.string.broadcast))
            },
            onClick = {
                scope.launch(Dispatchers.IO) { accountViewModel.broadcast(note); onDismiss() }
            }
        )
        Divider()
        if (state.isPrivateBookmarkNote) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.remove_from_private_bookmarks))
                },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (accountViewModel.loggedInWithExternalSigner()) {
                            val bookmarks = accountViewModel.userProfile().latestBookmarkList
                            ExternalSignerUtils.decrypt(
                                bookmarks?.content ?: "",
                                accountViewModel.account.keyPair.pubKey.toHexKey(),
                                bookmarks?.id ?: ""
                            )
                            bookmarks?.decryptedContent = ExternalSignerUtils.cachedDecryptedContent[bookmarks?.id ?: ""] ?: ""
                            accountViewModel.removePrivateBookmark(note, bookmarks?.decryptedContent ?: "")
                        } else {
                            accountViewModel.removePrivateBookmark(note)
                            onDismiss()
                        }
                    }
                }
            )
        } else {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.add_to_private_bookmarks))
                },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (accountViewModel.loggedInWithExternalSigner()) {
                            val bookmarks = accountViewModel.userProfile().latestBookmarkList
                            ExternalSignerUtils.decrypt(
                                bookmarks?.content ?: "",
                                accountViewModel.account.keyPair.pubKey.toHexKey(),
                                bookmarks?.id ?: ""
                            )
                            bookmarks?.decryptedContent = ExternalSignerUtils.cachedDecryptedContent[bookmarks?.id ?: ""] ?: ""
                            accountViewModel.addPrivateBookmark(note, bookmarks?.decryptedContent ?: "")
                        } else {
                            accountViewModel.addPrivateBookmark(note)
                            onDismiss()
                        }
                    }
                }
            )
        }
        if (state.isPublicBookmarkNote) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.remove_from_public_bookmarks))
                },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (accountViewModel.loggedInWithExternalSigner()) {
                            val bookmarks = accountViewModel.userProfile().latestBookmarkList
                            ExternalSignerUtils.decrypt(
                                bookmarks?.content ?: "",
                                accountViewModel.account.keyPair.pubKey.toHexKey(),
                                bookmarks?.id ?: ""
                            )
                            bookmarks?.decryptedContent = ExternalSignerUtils.cachedDecryptedContent[bookmarks?.id ?: ""] ?: ""
                            accountViewModel.removePublicBookmark(
                                note,
                                bookmarks?.decryptedContent ?: ""
                            )
                        } else {
                            accountViewModel.removePublicBookmark(note)
                            onDismiss()
                        }
                    }
                }
            )
        } else {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.add_to_public_bookmarks))
                },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (accountViewModel.loggedInWithExternalSigner()) {
                            val bookmarks = accountViewModel.userProfile().latestBookmarkList
                            ExternalSignerUtils.decrypt(
                                bookmarks?.content ?: "",
                                accountViewModel.account.keyPair.pubKey.toHexKey(),
                                bookmarks?.id ?: ""
                            )
                            bookmarks?.decryptedContent = ExternalSignerUtils.cachedDecryptedContent[bookmarks?.id ?: ""] ?: ""
                            accountViewModel.addPublicBookmark(
                                note,
                                bookmarks?.decryptedContent ?: ""
                            )
                        } else {
                            accountViewModel.addPublicBookmark(note)
                            onDismiss()
                        }
                    }
                }
            )
        }
        Divider()
        if (state.showSensitiveContent == null || state.showSensitiveContent == true) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.content_warning_hide_all_sensitive_content))
                },
                onClick = { scope.launch(Dispatchers.IO) { accountViewModel.hideSensitiveContent(); onDismiss() } }
            )
        }
        if (state.showSensitiveContent == null || state.showSensitiveContent == false) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.content_warning_show_all_sensitive_content))
                },
                onClick = { scope.launch(Dispatchers.IO) { accountViewModel.disableContentWarnings(); onDismiss() } }
            )
        }
        if (state.showSensitiveContent != null) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.content_warning_see_warnings))
                },
                onClick = { scope.launch(Dispatchers.IO) { accountViewModel.seeContentWarnings(); onDismiss() } }
            )
        }
        Divider()
        if (state.isLoggedUser) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.request_deletion))
                },
                onClick = { scope.launch(Dispatchers.IO) { accountViewModel.delete(note); onDismiss() } }
            )
        } else {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.block_report))
                },
                onClick = { reportDialogShowing = true }
            )
        }
    }

    if (reportDialogShowing) {
        ReportNoteDialog(note = note, accountViewModel = accountViewModel) {
            reportDialogShowing = false
            onDismiss()
        }
    }
}

@Composable
fun WatchBookmarksFollowsAndAccount(note: Note, accountViewModel: AccountViewModel, onNew: (DropDownParams) -> Unit) {
    val followState by accountViewModel.userProfile().live().follows.observeAsState()
    val bookmarkState by accountViewModel.userProfile().live().bookmarks.observeAsState()
    val showSensitiveContent by accountViewModel.showSensitiveContentChanges.observeAsState(accountViewModel.account.showSensitiveContent)

    LaunchedEffect(key1 = followState, key2 = bookmarkState, key3 = showSensitiveContent) {
        launch(Dispatchers.IO) {
            val newState = DropDownParams(
                isFollowingAuthor = accountViewModel.isFollowing(note.author),
                isPrivateBookmarkNote = accountViewModel.isInPrivateBookmarks(note),
                isPublicBookmarkNote = accountViewModel.isInPublicBookmarks(note),
                isLoggedUser = accountViewModel.isLoggedUser(note.author),
                isSensitive = note.event?.isSensitive() ?: false,
                showSensitiveContent = showSensitiveContent
            )

            launch(Dispatchers.Main) {
                onNew(
                    newState
                )
            }
        }
    }
}
