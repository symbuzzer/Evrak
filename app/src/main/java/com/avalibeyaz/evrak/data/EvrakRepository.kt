package com.avalibeyaz.evrak.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

class EvrakRepository(private val context: Context, private val evrakDao: EvrakDao) {
    val allEvraklar: Flow<List<Evrak>> = evrakDao.getAllEvraklar()

    private val supportedExtensions = setOf(
        ".pdf", ".docx", ".doc", ".tiff", ".tif", ".png", ".jpg", ".jpeg", ".gif", ".udf"
    )

    suspend fun addEvrakFromUri(uri: Uri, resolver: ContentResolver? = null): Evrak? {
        val cr = resolver ?: context.contentResolver
        
        // 1. Identification: Try MIME first, then sniffer
        val mimeType = try { cr.getType(uri) } catch (e: Exception) { null }
        var extension = getExtensionFromMime(mimeType, uri)
        
        // 2. Initial name
        var fileName = getFileName(uri, cr) ?: "Bilinmeyen Belge"

        // 2.1 Fallback extension from filename if MIME failed
        if (extension == null) {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot != -1) {
                val ext = fileName.substring(lastDot).lowercase()
                if (supportedExtensions.contains(ext)) {
                    extension = ext
                }
            }
        }
        
        // 3. Robust Copy and Sniffing Fallback
        val cacheFile = copyUriToInternalStorageWithSniffing(uri, cr) { sniffedExt ->
            // If sniffer found a better extension, use it
            // FIX: If we already have .udf and sniffer finds .docx (ZIP), keep .udf
            if (sniffedExt != null) {
                val isExistingUdf = extension?.equals(".udf", ignoreCase = true) == true
                val isSniffedZip = sniffedExt.equals(".docx", ignoreCase = true)
                
                if (!(isExistingUdf && isSniffedZip)) {
                    extension = sniffedExt
                }
            }
        } ?: return null
        
        // 3.1 Deep Sniffing if it's a ZIP-based format (detected as .docx)
        if (extension == ".docx") {
            val deepExt = deepSniffZip(cacheFile)
            if (deepExt != null) {
                extension = deepExt
            }
        }
        
        // 4. Final filename correction
        var finalName = fileName
        if (extension != null && !finalName.endsWith(extension!!, ignoreCase = true)) {
            val nameWithoutExt = if (finalName.contains(".")) finalName.substringBeforeLast(".") else finalName
            finalName = "$nameWithoutExt$extension"
        }
        
        // Rename the actual cache file to match final identification
        val finalCacheFile = File(cacheFile.parent, "${System.currentTimeMillis()}_$finalName")
        cacheFile.renameTo(finalCacheFile)
        
        // Check if supported before adding to history
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
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (ext != null) return ".$ext"
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
            
            // Temporary file for sniffing and copying
            val tempFile = File(cacheDir, "temp_${System.currentTimeMillis()}")
            
            var sniffedExtension: String? = null

            // FIX: Keep handle open for the ENTIRE duration of copy
            val success = try {
                cr.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        // Sniff the first few bytes
                        val header = ByteArray(8)
                        val read = input.read(header)
                        if (read >= 4) {
                            sniffedExtension = sniffFileType(header)
                        }
                        onSniffed(sniffedExtension)

                        // Resume copying the rest
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
                // Final fallback if File Descriptor fails
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
                } catch (e2: Exception) {
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
        // PDF: %PDF (25 50 44 46)
        if (header.size >= 4 && 
            header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && 
            header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) {
            return ".pdf"
        }
        
        // DOCX/ZIP: PK.. (50 4B 03 04)
        if (header.size >= 4 && 
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && 
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
            return ".docx"
        }

        // DOC (Legacy): D0 CF 11 E0
        if (header.size >= 4 && 
            header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() && 
            header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()) {
            return ".doc"
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header.size >= 8 &&
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()) {
            return ".png"
        }

        // JPG: FF D8 FF
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()) {
            return ".jpg"
        }

        // GIF: GIF8 (47 49 46 38)
        if (header.size >= 4 &&
            header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x38.toByte()) {
            return ".gif"
        }

        // TIFF: II* (49 49 2A 00) or MM (4D 4D 00 2A)
        if (header.size >= 4) {
            if (header[0] == 0x49.toByte() && header[1] == 0x49.toByte() && header[2] == 0x2A.toByte() && header[3] == 0x00.toByte()) {
                return ".tiff"
            }
            if (header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() && header[2] == 0x00.toByte() && header[3] == 0x2A.toByte()) {
                return ".tiff"
            }
        }

        return null
    }

    private fun deepSniffZip(file: File): String? {
        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                
                // UDF: content.xml at root (or sometimes nested, but root is standard)
                if (entries.any { it.equals("content.xml", ignoreCase = true) }) {
                    return ".udf"
                }
                
                // DOCX: [Content_Types].xml and word/ directory
                if (entries.any { it.contains("word/") } || entries.any { it == "[Content_Types].xml" }) {
                    return ".docx"
                }
                
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isVirtualFile(uri: Uri, cr: ContentResolver): Boolean {
        if (!DocumentsContract.isDocumentUri(context, uri)) return false
        return try {
            cr.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val flags = cursor.getInt(0)
                    (flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0
                } else false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
