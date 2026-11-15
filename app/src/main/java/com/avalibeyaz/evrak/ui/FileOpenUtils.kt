package com.avalibeyaz.evrak.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.avalibeyaz.evrak.R
import com.avalibeyaz.evrak.data.Evrak
import java.io.File

fun openFileWithSystemChooser(context: Context, filePath: String, displayName: String? = null): Boolean {
    val file = File(filePath)
    if (!file.exists()) return false

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val mimeType = getMimeType(filePath)
    val ext = filePath.substringAfterLast('.', "").lowercase()
    val name = displayName ?: file.name

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra("from_open_with", true)
        putExtra("file_path", filePath)
        putExtra("display_name", name)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    val packageManager = context.packageManager
    val activities = packageManager.queryIntentActivities(intent, 0)
    val otherActivities = activities.filter { it.activityInfo.packageName != context.packageName }

    if (otherActivities.isNotEmpty()) {
        for (resolveInfo in otherActivities) {
            val pkg = resolveInfo.activityInfo.packageName
            try {
                context.grantUriPermission(
                    pkg,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        if (ext == "apk" || mimeType == "application/vnd.android.package-archive") {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val chooserIntent = Intent.createChooser(intent, context.getString(R.string.open_with)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(chooserIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return false
}

fun openFileWith(context: Context, filePath: String, displayName: String? = null) {
    val handled = openFileWithSystemChooser(context, filePath, displayName)
    if (!handled) {
        Toast.makeText(context, context.getString(R.string.no_other_apps), Toast.LENGTH_SHORT).show()
    }
}

fun openFileWith(context: Context, evrak: Evrak) {
    openFileWith(context, evrak.path, evrak.name)
}
