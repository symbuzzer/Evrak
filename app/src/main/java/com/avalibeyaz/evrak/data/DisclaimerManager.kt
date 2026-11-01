package com.avalibeyaz.evrak.data

import android.content.Context
import android.text.format.DateFormat
import com.avalibeyaz.evrak.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.core.content.edit

object DisclaimerManager {
    private const val PREFS_NAME = "evrak_prefs"
    private const val KEY_DISCLAIMER_VERSION = "disclaimer_accepted_version"
    private const val KEY_DISCLAIMER_TIMESTAMP = "disclaimer_accepted_timestamp"

    fun isDisclaimerAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_DISCLAIMER_TIMESTAMP, -1L) > 0L || prefs.getInt(KEY_DISCLAIMER_VERSION, -1) > 0
    }

    fun setDisclaimerAccepted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_DISCLAIMER_VERSION, BuildConfig.VERSION_CODE)
            putLong(KEY_DISCLAIMER_TIMESTAMP, System.currentTimeMillis())
        }
    }

    fun getDisclaimerAcceptedDateFormatted(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_DISCLAIMER_TIMESTAMP, -1L)
        if (timestamp <= 0L) return null

        val date = Date(timestamp)
        val dateFormat = DateFormat.getDateFormat(context)
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
        val timeFormat = SimpleDateFormat(timePattern, Locale.getDefault())

        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }
}
