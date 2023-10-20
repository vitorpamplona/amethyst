package com.vitorpamplona.amethyst.service.playback

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File

@SuppressLint("UnsafeOptInUsageError")
class VideoCache {

    var exoPlayerCacheSize: Long = 150 * 1024 * 1024 // 90MB

    var leastRecentlyUsedCacheEvictor = LeastRecentlyUsedCacheEvictor(exoPlayerCacheSize)

    lateinit var exoDatabaseProvider: StandaloneDatabaseProvider
    lateinit var simpleCache: SimpleCache

    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    @Synchronized
    fun initFileCache(context: Context) {
        exoDatabaseProvider = StandaloneDatabaseProvider(context)

        simpleCache = SimpleCache(
            File(context.cacheDir, "exoplayer"),
            leastRecentlyUsedCacheEvictor,
            exoDatabaseProvider
        )
    }

    // This method should be called when proxy setting changes.
    fun renewCacheFactory(client: OkHttpClient) {
        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(
                OkHttpDataSource.Factory(client)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun get(client: OkHttpClient): CacheDataSource.Factory {
        // Renews the factory because OkHttpMight have changed.
        renewCacheFactory(client)

        return cacheDataSourceFactory
    }
}
