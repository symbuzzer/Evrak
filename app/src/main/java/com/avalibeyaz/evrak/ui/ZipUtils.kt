package com.avalibeyaz.evrak.ui

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import net.lingala.zip4j.ZipFile

object ZipUtils {
    fun determineCharset(filePath: String): Charset {
        val file = File(filePath)
        if (!file.exists()) return StandardCharsets.UTF_8
        
        val charsets = listOf(
            StandardCharsets.UTF_8,
            Charset.forName("Cp1254"),
            Charset.forName("IBM857")
        )
        
        var bestCharset = StandardCharsets.UTF_8
        var bestScore = -1
        
        for (cs in charsets) {
            try {
                ZipFile(file).use { zip ->
                    zip.charset = cs
                    val headers = zip.fileHeaders
                    var score = 0
                    var count = 0
                    for (header in headers) {
                        if (count > 50) break
                        count++
                        val name = header.fileName ?: ""
                        if (name.contains("\uFFFD")) {
                            score -= 1000
                        }
                        if (name.contains("ğ") || name.contains("ş") || name.contains("ç") || 
                            name.contains("ı") || name.contains("ö") || name.contains("ü") ||
                            name.contains("Ğ") || name.contains("Ş") || name.contains("Ç") || 
                            name.contains("İ") || name.contains("Ö") || name.contains("Ü")) {
                            score += 10
                        }
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestCharset = cs
                    }
                }
            } catch (_: Exception) {}
        }
        return bestCharset
    }

    fun extractZip(context: Context, filePath: String, treeUri: Uri, charset: Charset) {
        val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
        if (pickedDir != null && pickedDir.exists()) {
            ZipInputStream(File(filePath).inputStream(), charset).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val parts = entry.name.split("/")
                    var currentDir: DocumentFile? = pickedDir
                    
                    for (i in 0 until parts.size - 1) {
                        val part = parts[i]
                        if (part.isNotEmpty() && currentDir != null) {
                            val existing = currentDir.findFile(part)
                            currentDir = if (existing != null && existing.isDirectory) {
                                existing
                            } else {
                                currentDir.createDirectory(part)
                            }
                        }
                    }
                    
                    val fileName = parts.last()
                    if (fileName.isNotEmpty() && !entry.isDirectory && currentDir != null) {
                        val mimeType = getMimeType(fileName)
                        val newFile = currentDir.createFile(mimeType, fileName)
                        newFile?.uri?.let { fileUri ->
                            context.contentResolver.openOutputStream(fileUri)?.use { output ->
                                zis.copyTo(output)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } else {
            throw Exception("Picked directory does not exist")
        }
    }
}
