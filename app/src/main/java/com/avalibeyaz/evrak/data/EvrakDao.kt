package com.avalibeyaz.evrak.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvrakDao {
    @Query("SELECT * FROM evraklar ORDER BY dateOpened DESC")
    fun getAllEvraklar(): Flow<List<Evrak>>

    @Query("SELECT path FROM evraklar")
    suspend fun getAllPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvrak(evrak: Evrak)

    @androidx.room.Update
    suspend fun updateEvrak(evrak: Evrak)

    @Query("SELECT * FROM evraklar WHERE name = :name AND size = :size LIMIT 1")
    suspend fun getEvrakByNameAndSize(name: String, size: Long): Evrak?

    @Query("DELETE FROM evraklar WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @androidx.room.Delete
    suspend fun delete(evrak: Evrak)

    @Query("DELETE FROM evraklar")
    suspend fun deleteAll()
}
