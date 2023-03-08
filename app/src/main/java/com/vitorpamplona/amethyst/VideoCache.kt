package com.vitorpamplona.amethyst

import android.content.Context
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache

object VideoCache {

    var exoPlayerCacheSize: Long = 90 * 1024 * 1024 // 90MB

    var leastRecentlyUsedCacheEvictor = LeastRecentlyUsedCacheEvictor(exoPlayerCacheSize)

    lateinit var exoDatabaseProvider: StandaloneDatabaseProvider
    lateinit var simpleCache: SimpleCache

    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    fun get(context: Context): CacheDataSource.Factory {
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
                    DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }

        return cacheDataSourceFactory
    }
}
