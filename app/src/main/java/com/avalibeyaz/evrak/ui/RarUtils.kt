package com.avalibeyaz.evrak.ui

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object RarUtils {
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
                Archive(file).use { archive ->
                    val headers = archive.fileHeaders
                    var score = 0
                    var count = 0
                    if (headers != null) {
                        for (header in headers) {
                            if (count > 50) break
                            count++
                            val name = header.fileName as? String ?: ""
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

    fun extractRar(context: Context, filePath: String, treeUri: Uri, charset: Charset) {
        val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
        if (pickedDir != null && pickedDir.exists()) {
            Archive(File(filePath)).use { archive ->
                val headers = archive.fileHeaders
                if (headers != null) {
                    for (header in headers) {
                        val entryName = header.fileName
                        if (entryName.isNullOrEmpty()) continue
                        val parts = entryName.split(Regex("[/\\\\]"))
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
                        if (fileName.isNotEmpty() && !header.isDirectory && currentDir != null) {
                            val mimeType = getMimeType(fileName)
                            val newFile = currentDir.createFile(mimeType, fileName)
                            newFile?.uri?.let { fileUri ->
                                context.contentResolver.openOutputStream(fileUri)?.use { output ->
                                    archive.extractFile(header, output)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            throw Exception("Picked directory does not exist")
        }
    }
}
