package com.vitorpamplona.amethyst

import android.content.Context
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.vitorpamplona.amethyst.service.HttpClient

object VideoCache {

    var exoPlayerCacheSize: Long = 90 * 1024 * 1024 // 90MB

    var leastRecentlyUsedCacheEvictor = LeastRecentlyUsedCacheEvictor(exoPlayerCacheSize)

    lateinit var exoDatabaseProvider: StandaloneDatabaseProvider
    lateinit var simpleCache: SimpleCache

    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    fun init(context: Context) {
        if (!this::simpleCache.isInitialized) {
            exoDatabaseProvider = StandaloneDatabaseProvider(context)

            simpleCache = SimpleCache(
                context.cacheDir,
                leastRecentlyUsedCacheEvictor,
                exoDatabaseProvider
            )

            cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(
                    OkHttpDataSource.Factory(HttpClient.getHttpClient())
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(
                    OkHttpDataSource.Factory(HttpClient.getHttpClient())
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }

    fun get(): CacheDataSource.Factory {
        return cacheDataSourceFactory
    }
}
