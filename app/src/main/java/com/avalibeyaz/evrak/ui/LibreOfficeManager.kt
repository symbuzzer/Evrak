package com.avalibeyaz.evrak.ui

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.avalibeyaz.evrak.R
import org.libreoffice.kit.Document
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

object LibreOfficeManager {
    private const val TAG = "LibreOfficeManager"
    private var isInitialized = false
    private var office: Office? = null
    private val mutex = Mutex()

    suspend fun init(activity: Activity): Boolean {
        if (isInitialized) return true

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                if (isInitialized) return@withLock true
                
                try {
                    val context = activity.applicationContext
                    val internalFilesDir = context.filesDir
                    val filesDirPath = internalFilesDir.absolutePath
                    val nativeLibDir = activity.applicationInfo.nativeLibraryDir
                    
                    val lokCacheDir = File(internalFilesDir, "lok_cache").apply { if (!exists()) mkdirs() }
                    val profileDir = File(internalFilesDir, "user_profile").apply { if (!exists()) mkdirs() }
                    
                    Log.d(TAG, context.getString(R.string.msg_copying_assets))
                    
                    AssetUtils.copyAsset(context, "program", internalFilesDir)
                    AssetUtils.copyAsset(context, "share", internalFilesDir)
                    AssetUtils.copyAsset(context, "unpack", internalFilesDir)

                    patchFontConfig(internalFilesDir, lokCacheDir)

                    val unorcFile = File(internalFilesDir, "program/unorc")
                    unorcFile.parentFile?.mkdirs()
                    unorcFile.writeText(
                        "[Bootstrap]\n" +
                        "URE_INTERNAL_LIB_EXTERNAL_TYPEPATH=\n" +
                        "URE_INTERNAL_JAVA_DIR=file://$filesDirPath/program/classes\n" +
                        "URE_INTERNAL_JAVA_CLASSPATH=\n" +
                        "UNO_TYPES=file://$filesDirPath/program/udkapi.rdb file://$filesDirPath/program/offapi.rdb\n" +
                        "UNO_SERVICES=file://$filesDirPath/program/services.rdb file://$filesDirPath/program/services/services.rdb\n"
                    )

                    val fundamentalrcFile = File(internalFilesDir, "program/fundamentalrc")
                    fundamentalrcFile.writeText(
                        "[Bootstrap]\n" +
                        "BRAND_BASE_DIR=file://$filesDirPath/\n" +
                        "BRAND_SHARE_SUBDIR=share\n" +
                        "CONFIGURATION_LAYERS=xcsxcu:\${BRAND_BASE_DIR}\${BRAND_SHARE_SUBDIR}/registry res:\${BRAND_BASE_DIR}\${BRAND_SHARE_SUBDIR}/registry\n" +
                        "UserInstallation=file://${profileDir.absolutePath}/\n" +
                        "LO_LIB_DIR=file://$nativeLibDir/\n" +
                        "URE_BIN_DIR=file://$filesDirPath/program/\n"
                    )
                    
                    LibreOfficeKit.putenv("HOME=$filesDirPath")
                    LibreOfficeKit.putenv("TMPDIR=${lokCacheDir.absolutePath}")
                    LibreOfficeKit.putenv("LO_LIB_DIR=$nativeLibDir")
                    LibreOfficeKit.putenv("BRAND_BASE_DIR=file://$filesDirPath/")
                    LibreOfficeKit.putenv("URE_BOOTSTRAP=file://$filesDirPath/program/fundamentalrc")
                    LibreOfficeKit.putenv("FONTCONFIG_FILE=$filesDirPath/etc/fonts/fonts.conf")
                    LibreOfficeKit.putenv("FONTCONFIG_PATH=$filesDirPath/etc/fonts")
                    
                    val types = "file://$filesDirPath/program/udkapi.rdb file://$filesDirPath/program/offapi.rdb"
                    val services = "file://$filesDirPath/program/services.rdb file://$filesDirPath/program/services/services.rdb"
                    LibreOfficeKit.putenv("UNO_TYPES=$types")
                    LibreOfficeKit.putenv("UNO_SERVICES=$services")
                    
                    LibreOfficeKit.putenv("LC_ALL=en_US.UTF-8")
                    LibreOfficeKit.putenv("LANG=en_US.UTF-8")

                    val mainXcd = File(internalFilesDir, "share/registry/main.xcd")
                    if (!mainXcd.exists()) {
                        Log.e(TAG, "CRITICAL MISSING: main.xcd not found at ${mainXcd.absolutePath}")
                    }

                    val success = LibreOfficeKit.init(activity)
                    if (success) {
                        office = Office.get()
                        isInitialized = (office != null)
                        if (isInitialized) {
                            Log.d(TAG, context.getString(R.string.msg_libreoffice_ready))
                            delay(1000)
                        } else {
                            Log.e(TAG, "Office object could not be retrieved.")
                        }
                    } else {
                        Log.e(TAG, "LibreOfficeKit.init returned false!")
                    }
                    isInitialized
                } catch (e: Exception) {
                    Log.e(TAG, "Initialization error", e)
                    false
                }
            }
        }
    }

    private fun patchFontConfig(baseDir: File, cacheDir: File) {
        val fontsConf = File(baseDir, "etc/fonts/fonts.conf")
        if (fontsConf.exists()) {
            try {
                var content = fontsConf.readText()
                val oldCache = "/data/data/org.documentfoundation.libreoffice/fontconfig"
                val newCache = cacheDir.absolutePath
                
                if (content.contains(oldCache)) {
                    content = content.replace(oldCache, newCache)
                    fontsConf.writeText(content)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fonts.conf patch error", e)
            }
        }
    }

    suspend fun convertToPdf(inputFile: File, outputFile: File, activity: Activity): Boolean {
        if (!isInitialized) {
            init(activity)
        }
        
        if (!isInitialized) {
            Log.e(TAG, activity.getString(R.string.error_libreoffice_not_initialized))
            return false
        }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                var doc: Document? = null
                var tempInputFile: File? = null
                try {
                    val inputDir = File(activity.filesDir, "in").apply { if (!exists()) mkdirs() }
                    val safeExtension = inputFile.extension.let { if (it.isEmpty()) "docx" else it }
                    tempInputFile = File(inputDir, "lo_input_${System.currentTimeMillis()}.$safeExtension")
                    inputFile.copyTo(tempInputFile, overwrite = true)

                    val inputUri = Uri.fromFile(tempInputFile).toString()
                    val outputUri = Uri.fromFile(outputFile).toString()
                    
                    doc = office?.documentLoad(inputUri)
                    
                    if (doc == null) {
                        val error = try { office?.getError() ?: "Unknown native error" } catch (e: Exception) { "Error message could not be retrieved" }
                        Log.e(TAG, "Document load failed. Native error: $error")
                        return@withLock false
                    }

                    outputFile.parentFile?.mkdirs()
                    doc.saveAs(outputUri, "pdf", "")
                    
                    val success = outputFile.exists() && outputFile.length() > 0
                    if (!success) {
                        Log.e(TAG, activity.getString(R.string.error_pdf_not_created))
                    }
                    success
                } catch (e: Exception) {
                    Log.e(TAG, activity.getString(R.string.error_during_conversion), e)
                    false
                } finally {
                    try { doc?.destroy() } catch (e: Exception) {}
                    try { tempInputFile?.delete() } catch (e: Exception) {}
                }
            }
        }
    }
}
