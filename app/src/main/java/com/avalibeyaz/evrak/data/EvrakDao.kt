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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvrak(evrak: Evrak)

    @Query("DELETE FROM evraklar WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @androidx.room.Delete
    suspend fun delete(evrak: Evrak)

    @Query("DELETE FROM evraklar")
    suspend fun deleteAll()
}
