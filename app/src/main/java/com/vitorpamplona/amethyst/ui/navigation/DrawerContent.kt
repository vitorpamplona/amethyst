package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.W500
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.components.ZoomableAsyncImage
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch
import nostr.postr.toNpub

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.google.zxing.qrcode.encoder.ByteMatrix

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.RoboHashCache

@Composable
fun DrawerContent(navController: NavHostController,
                  scaffoldState: ScaffoldState,
                  accountViewModel: AccountViewModel,
                  accountStateViewModel: AccountStateViewModel) {

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.background
    ) {
        Column() {
            ProfileContent(
                account.userProfile(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp)
                    .padding(top = 100.dp),
                scaffoldState,
                navController
            )
            Divider(
                thickness = 0.25.dp,
                modifier = Modifier.padding(top = 20.dp)
            )
            ListContent(
                account.userProfile(),
                navController,
                scaffoldState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1F),
                accountStateViewModel
            )

            BottomContent(account.userProfile(), scaffoldState, navController)
        }
    }
}

@Composable
fun ProfileContent(baseAccountUser: User, modifier: Modifier = Modifier, scaffoldState: ScaffoldState, navController: NavController) {
    val coroutineScope = rememberCoroutineScope()

    val accountUserState by baseAccountUser.liveMetadata.observeAsState()
    val accountUser = accountUserState?.user ?: return

    val accountUserFollowsState by baseAccountUser.liveFollows.observeAsState()
    val accountUserFollows = accountUserFollowsState?.user ?: return

    val ctx = LocalContext.current.applicationContext

    Box {
        val banner = accountUser.info.banner
        if (banner != null && banner.isNotBlank()) {
            AsyncImage(
                model = banner,
                contentDescription = "Profile Image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.profile_banner),
                contentDescription = "Profile Banner",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
        }

        Column(modifier = modifier) {
            AsyncImage(
                model = accountUser.profilePicture(),
                contentDescription = "Profile Image",
                placeholder = rememberAsyncImagePainter(RoboHashCache.get(ctx, accountUser.pubkeyHex)),
                fallback = rememberAsyncImagePainter(RoboHashCache.get(ctx, accountUser.pubkeyHex)),
                error = rememberAsyncImagePainter(RoboHashCache.get(ctx, accountUser.pubkeyHex)),
                modifier = Modifier
                    .width(100.dp)
                    .height(100.dp)
                    .clip(shape = CircleShape)
                    .border(3.dp, MaterialTheme.colors.background, CircleShape)
                    .background(MaterialTheme.colors.background)
                    .clickable(onClick = {
                        accountUser.let {
                            navController.navigate("User/${it.pubkeyHex}")
                        }
                        coroutineScope.launch {
                            scaffoldState.drawerState.close()
                        }
                    })
            )
            Text(
                accountUser.bestDisplayName() ?: "",
                modifier = Modifier.padding(top = 7.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(" @${accountUser.bestUsername()}", color = Color.LightGray)
            Row(modifier = Modifier.padding(top = 15.dp)) {
                Row() {
                    Text("${accountUserFollows.follows?.size ?: "--"}", fontWeight = FontWeight.Bold)
                    Text(" Following")
                }
                Row(modifier = Modifier.padding(start = 10.dp)) {
                    Text("${accountUserFollows.followers?.size ?: "--"}", fontWeight = FontWeight.Bold)
                    Text(" Followers")
                }
            }
        }
    }
}

@Composable
fun ListContent(
    accountUser: User?,
    navController: NavHostController,
    scaffoldState: ScaffoldState,
    modifier: Modifier,
    accountViewModel: AccountStateViewModel
) {
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier) {
        LazyColumn() {
            item {
                if (accountUser != null)
                    NavigationRow(navController,
                        scaffoldState,
                        "User/${accountUser.pubkeyHex}",
                        Route.Profile.icon,
                        "Profile"
                    )

                Divider(
                    modifier = Modifier.padding(bottom = 15.dp),
                    thickness = 0.25.dp
                )
                Column(modifier = modifier.padding(horizontal = 25.dp)) {
                    Row(modifier = Modifier.clickable(onClick = {
                        navController.navigate(Route.Filters.route)
                        coroutineScope.launch {
                            scaffoldState.drawerState.close()
                        }
                    })) {
                        Text(
                            text = "Security Filters",
                            fontSize = 18.sp,
                            fontWeight = W500
                        )
                    }
                    Row(modifier = Modifier.clickable(onClick = { accountViewModel.logOff() })) {
                        Text(
                            text = "Log out",
                            modifier = Modifier.padding(vertical = 15.dp),
                            fontSize = 18.sp,
                            fontWeight = W500
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationRow(navController: NavHostController, scaffoldState: ScaffoldState, route: String, icon: Int, title: String) {
    val coroutineScope = rememberCoroutineScope()
    val currentRoute = currentRoute(navController)
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = {
            if (currentRoute != route) {
                navController.navigate(route)
            }
            coroutineScope.launch {
                scaffoldState.drawerState.close()
            }
        })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp, horizontal = 25.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon), null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colors.primary
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = title,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
fun BottomContent(user: User, scaffoldState: ScaffoldState, navController: NavController) {
    val coroutineScope = rememberCoroutineScope()

    // store the dialog open or close state
    var dialogOpen by remember {
        mutableStateOf(false)
    }

    Column(modifier = Modifier) {
        Divider(
            modifier = Modifier.padding(top = 15.dp),
            thickness = 0.25.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
        ) {
            /*
            IconButton(
                onClick = {
                    when (AppCompatDelegate.getDefaultNightMode()) {
                        AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_theme),
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colors.primary
                )
            }*/
            Box(modifier = Modifier.weight(1F))
            IconButton(onClick = {
                dialogOpen = true
                coroutineScope.launch {
                    scaffoldState.drawerState.close()
                }
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_qrcode),
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colors.primary
                )
            }
        }
    }

    if (dialogOpen) {
        ShowQRDialog(user,
            onScan = {
                dialogOpen = false
                coroutineScope.launch {
                    scaffoldState.drawerState.close()
                }
                navController.navigate(it)
            },
            onClose = { dialogOpen = false }
        )
    }
}


