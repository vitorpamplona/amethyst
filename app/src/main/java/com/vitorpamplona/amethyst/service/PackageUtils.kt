package com.vitorpamplona.amethyst.service

import android.content.Context

object PackageUtils {
    private fun isPackageInstalled(context: Context, target: String): Boolean {
        return context.packageManager.getInstalledApplications(0).find { info -> info.packageName == target } != null
    }

    fun isOrbotInstalled(context: Context): Boolean {
        return isPackageInstalled(context, "org.torproject.android")
    }

    fun isAmberInstalled(context: Context): Boolean {
        return isPackageInstalled(context, "com.greenart7c3.nostrsigner")
    }
}
