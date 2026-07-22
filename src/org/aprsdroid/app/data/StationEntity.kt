package org.aprsdroid.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `stations` table — the most recently heard
 * position / status of each station (or object / item).
 *
 * Mirrors the original Scala `StorageDatabase.Station` companion object.
 * `call` is UNIQUE (the Scala schema declares it `TEXT UNIQUE`) and is
 * indexed on `lat` / `lon` / `ts`.
 */
@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["call"], unique = true),
        Index(value = ["lat"]),
        Index(value = ["lon"]),
        Index(value = ["ts"]),
    ],
)
data class StationEntity(

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

    @ColumnInfo(name = "speed")
    val speed: Int? = null,

    @ColumnInfo(name = "course")
    val course: Int? = null,

    @ColumnInfo(name = "alt")
    val alt: Int? = null,

    @ColumnInfo(name = "symbol")
    val symbol: String? = null,

    @ColumnInfo(name = "comment")
    val comment: String? = null,

    /** originator call for object/item. */
    @ColumnInfo(name = "origin")
    val origin: String? = null,

    /** voice frequency. */
    @ColumnInfo(name = "qrg")
    val qrg: String? = null,

    /** bitmask: MSGCAPABLE | OBJECT | MOVING. */
    @ColumnInfo(name = "flags")
    val flags: Int = 0,
) {
    companion object {
        // Binary flags used for symbol coloring — keep in sync with
        // StorageDatabase.Station
        const val FLAG_MSGCAPABLE = 1
        const val FLAG_OBJECT = 2
        const val FLAG_MOVING = 4
    }
}
