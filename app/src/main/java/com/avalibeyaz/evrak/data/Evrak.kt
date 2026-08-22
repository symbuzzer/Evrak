package com.avalibeyaz.evrak.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evraklar")
data class Evrak(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String,
    val dateOpened: Long = System.currentTimeMillis()
)
