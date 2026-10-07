package com.avalibeyaz.evrak.ui

import android.content.Context
import com.avalibeyaz.evrak.R
import com.avalibeyaz.evrak.data.IncompleteDownloadException
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.StreamCorruptedException
import java.util.zip.ZipException

object FileErrorUtils {
    fun getStandardizedErrorMessage(context: Context, throwable: Throwable?): String {
        if (throwable == null) {
            return context.getString(R.string.error_file_incomplete)
        }

        val cause = throwable.cause ?: throwable
        if (cause is IncompleteDownloadException) {
            return cause.localizedMessage ?: context.getString(R.string.error_file_incomplete)
        }

        if (cause is FileNotFoundException || cause is ZipException || cause is EOFException || cause is StreamCorruptedException) {
            return context.getString(R.string.error_file_incomplete)
        }

        val msg = cause.message ?: cause.localizedMessage ?: ""
        return getStandardizedErrorMessage(context, msg)
    }

    fun getStandardizedErrorMessage(context: Context, rawMessage: String?): String {
        if (rawMessage.isNullOrBlank()) {
            return context.getString(R.string.error_file_incomplete)
        }

        val lower = rawMessage.lowercase().replace("_", " ").replace("-", " ")
        return when {
            lower.contains("incomplete") ||
            lower.contains("tamamen indirilmedi") ||
            lower.contains("not found") ||
            lower.contains("bulunamadı") ||
            lower.contains("central directory") ||
            lower.contains("eof") ||
            lower.contains("corrupt") ||
            lower.contains("truncated") ||
            lower.contains("libreoffice") ||
            lower.contains("invalid zip") ||
            lower.contains("parse failed") ||
            lower.contains("read error") ||
            lower.contains("read failed") ||
            lower.contains("udf read") ||
            lower.contains("tiff open failed") ||
            lower.contains("conversion failed") ||
            lower.contains("invalid or corrupted") ||
            lower.contains("unexpected end") ||
            lower.contains("stream ended") -> {
                context.getString(R.string.error_file_incomplete)
            }
            else -> rawMessage
        }
    }
}
