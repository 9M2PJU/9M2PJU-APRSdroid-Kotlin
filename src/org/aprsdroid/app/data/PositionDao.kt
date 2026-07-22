package org.aprsdroid.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `positions` table — the full position history
 * (one row per received position packet).
 *
 * Mirrors the position-related methods of the original Scala `StorageDatabase`.
 */
@Dao
interface PositionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(position: PositionEntity): Long

    /**
     * All positions newer than [sinceTs], ordered by call then _id
     * (matches Scala `getAllStaPositions`).
     */
    @Query("SELECT * FROM positions WHERE ts > :sinceTs ORDER BY call, _id")
    fun getAllStaPositions(sinceTs: Long): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE call = :call ORDER BY _id DESC")
    fun getPositionsForCall(call: String): Flow<List<PositionEntity>>

    @Query("DELETE FROM positions WHERE ts < :ts")
    suspend fun trimOlderThan(ts: Long)

    @Query("DELETE FROM positions")
    suspend fun deleteAll()
}
