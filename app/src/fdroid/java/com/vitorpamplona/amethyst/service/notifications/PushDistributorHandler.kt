package com.vitorpamplona.amethyst.service.notifications

import com.vitorpamplona.amethyst.Amethyst
import org.unifiedpush.android.connector.UnifiedPush

interface PushDistributorActions {
    fun getSavedDistributor(): String
    fun getInstalledDistributors(): List<String>
    fun saveDistributor(distributor: String)
    fun removeSavedDistributor()
}
class PushDistributorHandler() : PushDistributorActions {
    private val appContext = Amethyst.instance.applicationContext
    private val unifiedPush: UnifiedPush = UnifiedPush()

    private var endpointInternal = ""
    val endpoint = endpointInternal

    fun getEndpoint() = endpoint
    fun setEndpoint(newEndpoint: String) {
        endpointInternal = newEndpoint
    }

    override fun getSavedDistributor(): String {
        return unifiedPush.getDistributor(appContext)
    }

    fun savedDistributorExists(): Boolean = getSavedDistributor().isNotEmpty()

    override fun getInstalledDistributors(): List<String> {
        return unifiedPush.getDistributors(appContext)
    }

    override fun saveDistributor(distributor: String) {
        unifiedPush.saveDistributor(appContext, distributor)
    }

    override fun removeSavedDistributor() {
        unifiedPush.safeRemoveDistributor(appContext)
    }
}

fun UnifiedPush() = UnifiedPush
