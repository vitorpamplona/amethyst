package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import org.unifiedpush.android.connector.UnifiedPush

interface PushDistributorActions {
    fun getSavedDistributor(): String
    fun getInstalledDistributors(): List<String>
    fun saveDistributor(distributor: String)
    fun removeSavedDistributor()
}
object PushDistributorHandler : PushDistributorActions {
    private val appContext = Amethyst.instance.applicationContext
    private val unifiedPush: UnifiedPush = UnifiedPush

    private var endpointInternal = ""
    val endpoint = endpointInternal

    fun getSavedEndpoint() = endpoint
    fun setEndpoint(newEndpoint: String) {
        endpointInternal = newEndpoint
        Log.d("PushHandler", "New endpoint saved : $endpointInternal")
    }

    fun removeEndpoint() {
        endpointInternal = ""
    }

    override fun getSavedDistributor(): String {
        return unifiedPush.getDistributor(appContext)
    }

    fun savedDistributorExists(): Boolean = getSavedDistributor().isNotEmpty()

    override fun getInstalledDistributors(): List<String> {
        return unifiedPush.getDistributors(appContext)
    }

    fun formattedDistributorNames(): List<String> {
        val distributorsArray = getInstalledDistributors().toTypedArray()
        val distributorsNameArray = distributorsArray.map {
            try {
                val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.packageManager.getApplicationInfo(
                        it,
                        PackageManager.ApplicationInfoFlags.of(
                            PackageManager.GET_META_DATA.toLong()
                        )
                    )
                } else {
                    appContext.packageManager.getApplicationInfo(it, 0)
                }
                appContext.packageManager.getApplicationLabel(ai)
            } catch (e: PackageManager.NameNotFoundException) {
                it
            } as String
        }.toTypedArray()
        return distributorsNameArray.toList()
    }

    override fun saveDistributor(distributor: String) {
        unifiedPush.saveDistributor(appContext, distributor)
        unifiedPush.registerApp(appContext)
    }

    override fun removeSavedDistributor() {
        unifiedPush.safeRemoveDistributor(appContext)
    }
    fun forceRemoveDistributor(context: Context) {
        unifiedPush.forceRemoveDistributor(context)
    }
}
