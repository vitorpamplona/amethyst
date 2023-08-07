package com.vitorpamplona.amethyst.service

import android.content.Context

object PackageUtils {
    fun isPackageInstalled(context: Context, target: String): Boolean {
        return context.packageManager.getInstalledApplications(0).find { info -> info.packageName == target } != null
    }
}
