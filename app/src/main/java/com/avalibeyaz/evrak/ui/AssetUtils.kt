package com.avalibeyaz.evrak.ui

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetUtils {
    private const val TAG = "AssetUtils"

    fun copyAsset(context: Context, assetPath: String, destDir: File): Boolean {
        return try {
            val children = context.assets.list(assetPath)
            if (children.isNullOrEmpty()) {
                val fileName = assetPath.substringAfterLast('/')
                val destFile = File(destDir, fileName)
                copyFile(context, assetPath, destFile)
            } else {
                val isUnpack = assetPath == "unpack"
                val targetDir = if (isUnpack || assetPath.isEmpty()) {
                    destDir
                } else {
                    val dirName = assetPath.substringAfterLast('/')
                    File(destDir, dirName)
                }

                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                var allSuccess = true
                for (child in children) {
                    val childPath = if (assetPath.isEmpty()) child else "$assetPath/$child"
                    if (!copyAsset(context, childPath, targetDir)) {
                        allSuccess = false
                    }
                }
                allSuccess
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copying asset: $assetPath", e)
            false
        }
    }

    private fun copyFile(context: Context, assetPath: String, destFile: File): Boolean {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Skipped copying file: $assetPath")
            false
        }
    }
}
