package com.avalibeyaz.evrak.data

import android.content.Context
import java.io.File

const val EVRAK_CACHE_DIR_NAME = "evrak_cache"

fun Context.evrakCacheDir(): File = File(filesDir, EVRAK_CACHE_DIR_NAME)