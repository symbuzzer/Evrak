package com.avalibeyaz.evrak.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Evrak::class], version = 1, exportSchema = false)
abstract class EvrakDatabase : RoomDatabase() {
    abstract fun evrakDao(): EvrakDao

    companion object {
        @Volatile
        private var INSTANCE: EvrakDatabase? = null

        fun getDatabase(context: Context): EvrakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EvrakDatabase::class.java,
                    "evrak_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
