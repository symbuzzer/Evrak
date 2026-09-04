package com.avalibeyaz.evrak.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.avalibeyaz.evrak.R
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile

class EvrakRepository(private val context: Context, private val evrakDao: EvrakDao) {
    val allEvraklar: Flow<List<Evrak>> = evrakDao.getAllEvraklar()

    private val supportedExtensions = setOf(
        ".pdf", ".docx", ".doc", ".tiff", ".tif", ".png", ".jpg", ".jpeg", ".gif", ".udf", ".html", ".htm"
    )

    suspend fun addEvrakFromUri(uri: Uri, resolver: ContentResolver? = null): Evrak? {
        val cr = resolver ?: context.contentResolver
        
        val mimeType = try { cr.getType(uri) } catch (_: Exception) { null }
        var extension = getExtensionFromMime(mimeType, uri)
        
        val fileName = getFileName(uri, cr) ?: context.getString(R.string.unknown_document)

        if (extension == null) {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot != -1) {
                val ext = fileName.substring(lastDot).lowercase()
                if (supportedExtensions.contains(ext)) {
                    extension = ext
                }
            }
        }
        
        val cacheFile = copyUriToInternalStorageWithSniffing(uri, cr) { sniffedExt ->
            if (sniffedExt != null) {
                val isExistingUdf = extension?.equals(".udf", ignoreCase = true) == true
                val isSniffedZip = sniffedExt.equals(".docx", ignoreCase = true) || sniffedExt.equals(".zip", ignoreCase = true)
                
                if (isExistingUdf && isSniffedZip) return@copyUriToInternalStorageWithSniffing
                
                val imageExtensions = setOf(".png", ".jpg", ".jpeg", ".gif")
                val isExistingImage = extension?.lowercase() in imageExtensions
                val isSniffedImage = sniffedExt.lowercase() in imageExtensions
                
                if (isExistingImage && isSniffedImage) {
                } else {
                    extension = sniffedExt
                }
            }
        } ?: return null
        
        if (extension == ".docx" || extension == ".zip") {
            val deepExt = deepSniffZip(cacheFile)
            if (deepExt != null) {
                extension = deepExt
            }
        }
        
        var finalName = fileName
        if (extension != null && !finalName.endsWith(extension, ignoreCase = true)) {
            val nameWithoutExt = if (finalName.contains(".")) finalName.substringBeforeLast(".") else finalName
            finalName = "$nameWithoutExt$extension"
        }
        
        val finalCacheFile = File(cacheFile.parent, "${System.currentTimeMillis()}_$finalName")
        cacheFile.renameTo(finalCacheFile)
        
        val isSupported = supportedExtensions.any { finalName.endsWith(it, ignoreCase = true) }
        
        val evrak = Evrak(name = finalName, path = finalCacheFile.absolutePath)
        
        if (isSupported) {
            evrakDao.insertEvrak(evrak)
        }
        
        return evrak
    }

    private fun getExtensionFromMime(mimeType: String?, uri: Uri): String? {
        if (mimeType != null) {
            if (mimeType == "application/x-udf") return ".udf"
            if (mimeType == "image/png") return ".png"
            if (mimeType == "image/jpeg") return ".jpg"
            if (mimeType == "image/gif") return ".gif"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (ext != null) {
                return if (ext == "jpeg") ".jpg" else ".$ext"
            }
        }
        val path = uri.path ?: return null
        val lastDot = path.lastIndexOf('.')
        if (lastDot != -1) {
            val ext = path.substring(lastDot).lowercase()
            if (supportedExtensions.contains(ext)) return ext
        }
        return null
    }

    suspend fun deleteEvrak(evrak: Evrak) {
        evrakDao.delete(evrak)
        try {
            val file = File(evrak.path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun renameEvrak(evrak: Evrak, newName: String) {
        val extension = if (evrak.path.contains(".")) {
            evrak.path.substring(evrak.path.lastIndexOf('.'))
        } else ""
        
        var finalName = newName.trim()
        if (extension.isNotEmpty() && !finalName.endsWith(extension, ignoreCase = true)) {
            finalName += extension
        }

        val oldFile = File(evrak.path)
        val parentDir = oldFile.parentFile
        var newPath = evrak.path

        if (parentDir != null && oldFile.exists()) {
            val newFile = File(parentDir, "${System.currentTimeMillis()}_$finalName")
            if (oldFile.renameTo(newFile)) {
                newPath = newFile.absolutePath
            }
        }
        
        val updatedEvrak = evrak.copy(name = finalName, path = newPath)
        evrakDao.insertEvrak(updatedEvrak)
    }

    suspend fun deleteAllEvrak() {
        evrakDao.deleteAll()
        try {
            val cacheDir = File(context.filesDir, "evrak_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFileName(uri: Uri, cr: ContentResolver): String? {
        var name: String? = null
        try {
            if (uri.scheme == "content") {
                val documentFile = DocumentFile.fromSingleUri(context, uri)
                name = documentFile?.name
                
                if (name == null) {
                    cr.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    private fun copyUriToInternalStorageWithSniffing(
        uri: Uri, 
        cr: ContentResolver,
        onSniffed: (String?) -> Unit
    ): File? {
        return try {
            val cacheDir = File(context.filesDir, "evrak_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val tempFile = File(cacheDir, "temp_${System.currentTimeMillis()}")
            
            var sniffedExtension: String? = null

            val success = try {
                cr.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        val header = ByteArray(8)
                        val read = input.read(header)
                        if (read >= 4) {
                            sniffedExtension = sniffFileType(header)
                        }
                        onSniffed(sniffedExtension)

                        FileOutputStream(tempFile).use { output ->
                            output.write(header, 0, read)
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                try {
                    cr.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }

            if (success && tempFile.exists() && tempFile.length() > 0) {
                tempFile
            } else {
                if (tempFile.exists()) tempFile.delete()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sniffFileType(header: ByteArray): String? {
        if (header.size >= 4 && 
            header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && 
            header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) {
            return ".pdf"
        }
        
        if (header.size >= 4 && 
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && 
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
            return ".zip"
        }

        if (header.size >= 4 && 
            header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() && 
            header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()) {
            return ".doc"
        }

        if (header.size >= 8 &&
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()) {
            return ".png"
        }

        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()) {
            return ".jpg"
        }

        if (header.size >= 4 &&
            header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x38.toByte()) {
            return ".gif"
        }

        if (header.size >= 4) {
            if (header[0] == 0x49.toByte() && header[1] == 0x49.toByte() && header[2] == 0x2A.toByte() && header[3] == 0x00.toByte()) {
                return ".tiff"
            }
            if (header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() && header[2] == 0x00.toByte() && header[3] == 0x2A.toByte()) {
                return ".tiff"
            }
        }

        if (header.size >= 4 && header[0] == 0x3C.toByte()) {
            val s = String(header, 0, 4).lowercase()
            if (s == "<!do" || s == "<htm") {
                return ".html"
            }
        }

        return null
    }

    private fun deepSniffZip(file: File): String? {
        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                
                if (entries.any { it.equals("content.xml", ignoreCase = true) }) {
                    return ".udf"
                }
                
                if (entries.any { it.contains("word/") } || entries.any { it == "[Content_Types].xml" }) {
                    return ".docx"
                }
                
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
