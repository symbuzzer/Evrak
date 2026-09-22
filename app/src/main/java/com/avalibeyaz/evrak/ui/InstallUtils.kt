package com.avalibeyaz.evrak.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object InstallUtils {
    fun isPackageSideloaded(context: Context, packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val installSourceInfo = packageManager.getInstallSourceInfo(packageName)
                val installerPackageName = installSourceInfo.installingPackageName
                installerPackageName != "com.android.vending"
            } else {
                @Suppress("DEPRECATION")
                val installerPackageName = packageManager.getInstallerPackageName(packageName)
                installerPackageName != "com.android.vending"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

