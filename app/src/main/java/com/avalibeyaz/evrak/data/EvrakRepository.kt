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

class IncompleteDownloadException(message: String) : Exception(message)

class EvrakRepository(private val context: Context, private val evrakDao: EvrakDao) {
    val allEvraklar: Flow<List<Evrak>> = evrakDao.getAllEvraklar()

    suspend fun getAllPaths(): List<String> = evrakDao.getAllPaths()

    private val supportedExtensions = setOf(
        ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt", ".tiff", ".tif", ".png", ".jpg", ".jpeg", ".gif", ".udf", ".html", ".htm", ".txt", ".zip", ".eyp",
        ".webp", ".bmp", ".heic", ".avif"
    )

    suspend fun addEvrakFromUri(uri: Uri, resolver: ContentResolver? = null): Evrak? {
        val cr = resolver ?: context.contentResolver
        
        val mimeType = try { cr.getType(uri) } catch (_: Exception) { null }
        var extension = getExtensionFromMime(mimeType, uri)
        
        val fileName = getFileName(uri, cr) ?: context.getString(R.string.unknown_document)

        if (extension == null || extension == ".bin") {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot != -1) {
                val ext = fileName.substring(lastDot).lowercase()
                extension = ext
            }
        }
        
        val isExistingSupported = extension != null && supportedExtensions.any { it.equals(extension, ignoreCase = true) }

        val cacheFile = copyUriToInternalStorageWithSniffing(uri, cr) { sniffedExt ->
            if (sniffedExt != null) {
                if (isExistingSupported || extension.isNullOrEmpty() || extension == ".bin") {
                    val isExistingUdf = extension?.equals(".udf", ignoreCase = true) == true
                    val isExistingEyp = extension?.equals(".eyp", ignoreCase = true) == true
                    val isSniffedZip = sniffedExt.equals(".docx", ignoreCase = true) || sniffedExt.equals(".zip", ignoreCase = true)
                    
                    if (isExistingUdf && isSniffedZip) return@copyUriToInternalStorageWithSniffing
                    if (isExistingEyp && sniffedExt.equals(".eyp", ignoreCase = true)) return@copyUriToInternalStorageWithSniffing
                    
                    val imageExtensions = setOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".heic", ".avif")
                    val isExistingImage = extension?.lowercase() in imageExtensions
                    val isSniffedImage = sniffedExt.lowercase() in imageExtensions
                    
                    if (!(isExistingImage && isSniffedImage)) {
                        extension = sniffedExt
                    }
                }
            }
        } ?: return null

        if (extension == ".ole") {
            val deepOleExt = deepSniffOle(cacheFile)
            if (deepOleExt != null) {
                extension = deepOleExt
            }
        }
        
        if (extension == ".docx" || extension == ".zip" || extension.isNullOrEmpty() || extension == ".bin" || !isExistingSupported) {
            val deepExt = deepSniffZip(cacheFile)
            if (deepExt != null) {
                extension = deepExt
            }
        }

        if (fileName.endsWith(".eyp", ignoreCase = true) && extension == ".zip") {
            extension = ".eyp"
            }
        
        var finalName = fileName
        if (extension != null && !finalName.endsWith(extension, ignoreCase = true)) {
            val nameWithoutExt = if (finalName.contains(".")) finalName.substringBeforeLast(".") else finalName
            finalName = "$nameWithoutExt$extension"
        }
        
        val finalCacheFile = File(cacheFile.parent, "${System.currentTimeMillis()}_$finalName")
        try {
            cacheFile.copyTo(finalCacheFile, overwrite = true)
            if (finalCacheFile.exists() && finalCacheFile.length() > 0) {
                if (cacheFile.absolutePath != finalCacheFile.absolutePath) {
                    cacheFile.delete()
                }
            } else {
                cacheFile.renameTo(finalCacheFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cacheFile.renameTo(finalCacheFile)
        }

        val actualCacheFile = if (finalCacheFile.exists() && finalCacheFile.length() > 0) {
            finalCacheFile
        } else if (cacheFile.exists() && cacheFile.length() > 0) {
            cacheFile
        } else {
            return null
        }

        if (actualCacheFile.length() <= 0L) {
            try {
                if (actualCacheFile.exists()) actualCacheFile.delete()
                if (cacheFile.exists() && cacheFile.absolutePath != actualCacheFile.absolutePath) cacheFile.delete()
            } catch (_: Exception) {}
            throw IncompleteDownloadException(context.getString(R.string.error_file_incomplete))
        }

        val isSupported = supportedExtensions.any { finalName.endsWith(it, ignoreCase = true) }
        
        val fileSize = actualCacheFile.length()
        val existingEvrak = evrakDao.getEvrakByNameAndSize(finalName, fileSize)

        val evrak = if (existingEvrak != null) {
            val oldFile = File(existingEvrak.path)
            if (oldFile.exists() && oldFile.length() == fileSize && existingEvrak.path != actualCacheFile.absolutePath) {
                try {
                    actualCacheFile.delete()
                } catch (_: Exception) {}
                val updated = existingEvrak.copy(dateOpened = System.currentTimeMillis())
                if (isSupported) {
                    evrakDao.updateEvrak(updated)
                }
                updated
            } else {
                if (existingEvrak.path != actualCacheFile.absolutePath) {
                    try {
                        if (oldFile.exists()) oldFile.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val updated = existingEvrak.copy(
                    path = actualCacheFile.absolutePath,
                    dateOpened = System.currentTimeMillis()
                )
                if (isSupported) {
                    evrakDao.updateEvrak(updated)
                }
                updated
            }
        } else {
            val newEvrak = Evrak(name = finalName, path = actualCacheFile.absolutePath, size = fileSize)
            if (isSupported) {
                evrakDao.insertEvrak(newEvrak)
            }
            newEvrak
        }
        
        return evrak
    }

    suspend fun ensureEvrak(path: String, name: String): Evrak {
        val isSupported = supportedExtensions.any { name.endsWith(it, ignoreCase = true) || path.endsWith(it, ignoreCase = true) }
        val existing = evrakDao.getEvrakByPath(path) ?: evrakDao.getEvrakByNameAndSize(name, File(path).length())
        if (existing != null) {
            val updated = existing.copy(dateOpened = System.currentTimeMillis(), path = path, name = name)
            if (isSupported) {
                evrakDao.updateEvrak(updated)
            }
            return updated
        } else {
            val newEvrak = Evrak(name = name, path = path, size = File(path).length())
            if (isSupported) {
                evrakDao.insertEvrak(newEvrak)
                return evrakDao.getEvrakByPath(path) ?: newEvrak
            }
            return newEvrak
        }
    }

    suspend fun updateEvrakTimestamp(evrak: Evrak) {
        val updated = evrak.copy(dateOpened = System.currentTimeMillis())
        evrakDao.updateEvrak(updated)
    }

    private fun getExtensionFromMime(mimeType: String?, uri: Uri): String? {
        var result: String? = null
        if (mimeType != null) {
            result = when (mimeType) {
                "application/eyp" -> ".eyp"
                "application/x-udf" -> ".udf"
                "text/plain" -> ".txt"
                "image/png" -> ".png"
                "image/jpeg" -> ".jpg"
                "image/gif" -> ".gif"
                "image/webp" -> ".webp"
                "image/bmp", "image/x-ms-bmp", "image/x-bmp" -> ".bmp"
                "image/heic", "image/heic-sequence" -> ".heic"
                "image/avif" -> ".avif"
                else -> {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (ext != null) {
                        if (ext == "jpeg") ".jpg" else ".$ext"
                    } else null
                }
            }
        }

        if (result == null || result == ".bin") {
            val path = uri.path
            if (path != null) {
                val lastDot = path.lastIndexOf('.')
                if (lastDot != -1) {
                    val ext = path.substring(lastDot).lowercase()
                    if (ext.length in 2..5) {
                        return ext
                    }
                }
            }
        }
        
        return result
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

    suspend fun renameEvrak(evrak: Evrak, newName: String): Evrak {
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
            try {
                oldFile.copyTo(newFile, overwrite = true)
                if (newFile.exists() && newFile.length() > 0) {
                    newPath = newFile.absolutePath
                    try {
                        if (oldFile.absolutePath != newPath) {
                            oldFile.delete()
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (oldFile.renameTo(newFile)) {
                    newPath = newFile.absolutePath
                }
            }
        }
        
        val realEvrak = if (evrak.id != 0) {
            evrak
        } else {
            evrakDao.getEvrakByPath(evrak.path) ?: evrakDao.getEvrakByNameAndSize(evrak.name, evrak.size) ?: evrak
        }

        val updatedEvrak = realEvrak.copy(
            name = finalName,
            path = newPath,
            dateOpened = System.currentTimeMillis()
        )

        if (updatedEvrak.id != 0) {
            evrakDao.updateEvrak(updatedEvrak)
        } else {
            evrakDao.insertEvrak(updatedEvrak)
        }

        return evrakDao.getEvrakByPath(newPath) ?: updatedEvrak
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

            var success = false
            var attempt = 0
            val maxAttempts = 3

            while (attempt < maxAttempts && !success) {
                attempt++
                try {
                    if (tempFile.exists()) tempFile.delete()
                    cr.openInputStream(uri)?.use { input ->
                        val header = ByteArray(12)
                        val read = input.read(header)
                        if (read >= 4) {
                            sniffedExtension = sniffFileType(header)
                        }
                        onSniffed(sniffedExtension)

                        FileOutputStream(tempFile).use { output ->
                            if (read > 0) {
                                output.write(header, 0, read)
                            }
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        success = true
                    } else {
                        if (attempt < maxAttempts) {
                            Thread.sleep(300)
                        }
                    }
                } catch (e: IncompleteDownloadException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (attempt < maxAttempts) {
                        try { Thread.sleep(300) } catch (_: InterruptedException) {}
                    }
                }
            }

            if (success && tempFile.exists() && tempFile.length() > 0) {
                tempFile
            } else {
                if (tempFile.exists()) tempFile.delete()
                throw IncompleteDownloadException(context.getString(R.string.error_file_incomplete))
            }
        } catch (e: IncompleteDownloadException) {
            throw e
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
            return ".ole"
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

        if (header.size >= 12 &&
            header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
            header[8] == 0x57.toByte() && header[9] == 0x45.toByte() && header[10] == 0x42.toByte() && header[11] == 0x50.toByte()) {
            return ".webp"
        }

        if (header.size >= 2 && header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()) {
            return ".bmp"
        }

        if (header.size >= 12 &&
            header[4] == 0x66.toByte() && header[5] == 0x74.toByte() && header[6] == 0x79.toByte() && header[7] == 0x70.toByte()) {
            val ftyp = String(header, 8, 4)
            if (ftyp == "heic" || ftyp == "heix" || ftyp == "hevc") {
                return ".heic"
            }
            if (ftyp == "avif" || ftyp == "avis") {
                return ".avif"
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
                
                if (entries.any { it == "word/document.xml" }) {
                    return ".docx"
                }
                if (entries.any { it == "ppt/presentation.xml" }) {
                    return ".pptx"
                }
                if (entries.any { it == "xl/workbook.xml" }) {
                    return ".xlsx"
                }
                
                if (entries.any { it.equals("AndroidManifest.xml", ignoreCase = true) || it.startsWith("classes", ignoreCase = true) }) {
                    return ".apk"
                }
                
                if (entries.any { it == "[Content_Types].xml" }) {
                    return ".zip"
                }
                
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun deepSniffOle(file: File): String? {
        return try {
            val bytes = file.readBytes()
            val content = String(bytes, Charsets.UTF_16LE)
            
            when {
                content.contains("WordDocument") -> ".doc"
                content.contains("Workbook") || content.contains("Book") -> ".xls"
                content.contains("PowerPoint Document") -> ".ppt"
                else -> {
                    val asciiContent = String(bytes, Charsets.US_ASCII)
                    when {
                        asciiContent.contains("WordDocument") -> ".doc"
                        asciiContent.contains("Workbook") || asciiContent.contains("Book") -> ".xls"
                        asciiContent.contains("PowerPoint Document") -> ".ppt"
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun validateFileIntegrity(file: File, extension: String? = null): Boolean {
        if (!file.exists() || file.length() <= 0L) {
            return false
        }

        val ext = (extension ?: file.name.substringAfterLast('.', "")).lowercase().removePrefix(".")
        val zipExtensions = setOf("zip", "udf", "docx", "xlsx", "pptx", "eyp")

        if (ext in zipExtensions || file.name.endsWith(".udf", true) || file.name.endsWith(".zip", true) || file.name.endsWith(".eyp", true)) {
            return try {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    if (!entries.hasMoreElements()) return false
                    if (ext == "udf" || file.name.endsWith(".udf", ignoreCase = true)) {
                        zip.entries().asSequence().any { it.name.equals("content.xml", ignoreCase = true) }
                    } else {
                        true
                    }
                }
            } catch (_: Exception) {
                false
            }
        }

        if (ext == "pdf" || file.name.endsWith(".pdf", ignoreCase = true)) {
            val fileLength = file.length()
            if (fileLength < 10) return false
            return try {
                val headerBuffer = ByteArray(1024)
                val readHeader = FileInputStream(file).use { fis -> fis.read(headerBuffer) }
                if (readHeader < 4) return false
                val headerStr = String(headerBuffer, 0, readHeader, Charsets.ISO_8859_1)
                if (!headerStr.contains("%PDF")) return false

                val readFooterLength = 65536.coerceAtMost(fileLength.toInt())
                val footerBuffer = ByteArray(readFooterLength)
                java.io.RandomAccessFile(file, "r").use { raf ->
                    raf.seek(fileLength - readFooterLength)
                    raf.readFully(footerBuffer)
                }
                val footerStr = String(footerBuffer, Charsets.ISO_8859_1)
                footerStr.contains("%%EOF") || fileLength > 512
            } catch (_: Exception) {
                false
            }
        }

        return true
    }
}
