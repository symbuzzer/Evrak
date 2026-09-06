package com.avalibeyaz.evrak.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CleanupManager {
    private const val TAG = "CleanupManager"
    private const val CLEANUP_THRESHOLD_MS = 24 * 60 * 60 * 1000L

    suspend fun performCleanup(context: Context, repository: EvrakRepository) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting cleanup process...")

                cleanupDirectory(context.cacheDir)

                val lokCacheDir = File(context.filesDir, "lok_cache")
                if (lokCacheDir.exists()) {
                    cleanupDirectory(lokCacheDir)
                }

                val loInputDir = File(context.filesDir, "in")
                if (loInputDir.exists()) {
                    cleanupDirectory(loInputDir)
                }

                cleanupOrphanFiles(context, repository)

                Log.d(TAG, "Cleanup process finished.")
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup failed", e)
            }
        }
    }

    private fun cleanupDirectory(directory: File) {
        val now = System.currentTimeMillis()
        directory.listFiles()?.forEach { file ->
            if (file.isFile && (now - file.lastModified() > CLEANUP_THRESHOLD_MS)) {
                if (file.delete()) {
                    Log.d(TAG, "Deleted old cache file: ${file.name}")
                }
            } else if (file.isDirectory) {
                cleanupDirectory(file)
                if (file.listFiles()?.isEmpty() == true) {
                    file.delete()
                }
            }
        }
    }

    private suspend fun cleanupOrphanFiles(context: Context, repository: EvrakRepository) {
        val evrakCacheDir = File(context.filesDir, "evrak_cache")
        if (!evrakCacheDir.exists()) return

        val databasePaths = repository.getAllPaths().toSet()
        evrakCacheDir.listFiles()?.forEach { file ->
            if (file.isFile && !databasePaths.contains(file.absolutePath)) {
                if (file.delete()) {
                    Log.d(TAG, "Deleted orphan file: ${file.name}")
                }
            }
        }
    }
}
