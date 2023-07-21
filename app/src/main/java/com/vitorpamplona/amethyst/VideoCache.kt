package com.vitorpamplona.amethyst

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

@UnstableApi object VideoCache {

    var exoPlayerCacheSize: Long = 90 * 1024 * 1024 // 90MB

    var leastRecentlyUsedCacheEvictor = LeastRecentlyUsedCacheEvictor(exoPlayerCacheSize)

    lateinit var exoDatabaseProvider: StandaloneDatabaseProvider
    lateinit var simpleCache: SimpleCache

    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    @Synchronized
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun init(context: Context, client: OkHttpClient) {
        exoDatabaseProvider = StandaloneDatabaseProvider(context)

        simpleCache = SimpleCache(
            context.cacheDir,
            leastRecentlyUsedCacheEvictor,
            exoDatabaseProvider
        )

        renewCacheFactory(client)
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

    fun get(context: Context, client: OkHttpClient): CacheDataSource.Factory {
        if (!this::simpleCache.isInitialized) {
            init(context, client)
        }

        return cacheDataSourceFactory
    }
}
