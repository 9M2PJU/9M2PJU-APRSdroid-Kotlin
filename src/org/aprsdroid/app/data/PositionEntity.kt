package org.aprsdroid.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `positions` table — the full position history
 * (one row per received position packet).
 *
 * Mirrors the original Scala `StorageDatabase.Position` companion object.
 * Indexed on `ts`.
 */
@Entity(
    tableName = "positions",
    indices = [Index(value = ["ts"])],
)
data class PositionEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,

    @ColumnInfo(name = "ts")
    val ts: Long,

    @ColumnInfo(name = "call")
    val call: String,

    @ColumnInfo(name = "lat")
    val lat: Int,

    @ColumnInfo(name = "lon")
    val lon: Int,
)
