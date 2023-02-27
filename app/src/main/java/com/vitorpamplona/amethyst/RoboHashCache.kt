package com.vitorpamplona.amethyst

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.UUID
import name.neuhalfen.projects.android.robohash.RoboHash

object RoboHashCache {

  lateinit var robots: RoboHash

  lateinit var defaultAvatar: ImageBitmap

  @Synchronized
  fun get(context: Context, hash: String): ImageBitmap {
    if (!this::robots.isInitialized) {
      robots = RoboHash(context)
      //robots.useCache(LruCache(100));

      defaultAvatar = robots.imageForHandle(robots.calculateHandleFromUUID(UUID.nameUUIDFromBytes("aaaa".toByteArray()))).asImageBitmap()
    }

    return defaultAvatar
  }

}