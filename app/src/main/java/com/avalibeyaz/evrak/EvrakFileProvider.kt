package com.avalibeyaz.evrak

import android.net.Uri
import androidx.core.content.FileProvider

class EvrakFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? {
        val path = uri.path ?: ""
        return when {
            path.endsWith(".udf", ignoreCase = true) -> "application/x-udf"
            path.endsWith(".eyp", ignoreCase = true) -> "application/eyp"
            else -> super.getType(uri)
        }
    }
}
