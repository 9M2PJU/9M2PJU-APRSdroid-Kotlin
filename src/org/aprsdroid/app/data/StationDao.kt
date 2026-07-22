package org.aprsdroid.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `stations` table — the most recently heard
 * position / status of each station.
 *
 * Mirrors the station-related methods of the original Scala `StorageDatabase`.
 *
 * Note: latitude/longitude are stored as `Int` in
 * "degrees × 1_000_000" (the legacy APRSdroid convention). Distance
 * calculations use the same cosine-correction approximation as the
 * Scala code (see [neighbors]).
 */
@Dao
interface StationDao {

    // ---- inserts ----

    /** Replace the full station row (UNIQUE on `call`) — matches Scala `replaceOrThrow`. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: StationEntity): Long

    // ---- queries ----

    /** All stations ordered by call (matches Scala `getStations` with no filter). */
    @Query("SELECT * FROM stations ORDER BY call LIMIT :limit")
    fun getStations(limit: String): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE :sel ORDER BY call LIMIT :limit")
    fun getStationsFiltered(sel: String, limit: String): Flow<List<StationEntity>>

    /**
     * Stations within a lat/lon rectangle — matches Scala `getRectStations`.
     * Handles the +180°/-180° wrap-around: when `lon1 > lon2` the query
     * uses `LON <= lon1 OR LON >= lon2` instead of `BETWEEN`.
     */
    @Query(
        """
        SELECT * FROM stations
        WHERE lat >= :lat1 AND lat <= :lat2
          AND lon >= :lon1 AND lon <= :lon2
        ORDER BY call LIMIT :limit
        """
    )
    fun getRectStations(lat1: Int, lon1: Int, lat2: Int, lon2: Int, limit: String): Flow<List<StationEntity>>

    @Query(
        """
        SELECT * FROM stations
        WHERE lat >= :lat1 AND lat <= :lat2
          AND (lon <= :lon1 OR lon >= :lon2)
        ORDER BY call LIMIT :limit
        """
    )
    fun getRectStationsWrapped(lat1: Int, lon1: Int, lat2: Int, lon2: Int, limit: String): Flow<List<StationEntity>>

    /** Most recent position for a call (matches Scala `getStaPosition`). */
    @Query("SELECT * FROM stations WHERE call LIKE :call ORDER BY _id DESC LIMIT 1")
    suspend fun getStaPosition(call: String): StationEntity?

    /** All SSIDs / objects for a base call (matches Scala `getAllSsids`). */
    @Query(
        """
        SELECT * FROM stations
        WHERE call = :barecall OR call LIKE :wildcard
           OR origin = :barecall OR origin LIKE :wildcard
        """
    )
    fun getAllSsids(barecall: String, wildcard: String): Flow<List<StationEntity>>

    // ---- distance / neighbors ----

    /**
     * Nearest stations to (lat, lon) heard since [ts], plus [mycall] itself.
     *
     * Mirrors Scala `getNeighbors`. The distance column is computed in
     * Kotlin using the same cosine-correction approximation as the
     * original Scala code:
     *
     *   corr = (cos(lat) ^ 2) * 100
     *   dist = (lat - myLat)^2 + (lon - myLon)^2 * corr / 100
     *
     * Returns the list sorted by distance ascending.
     */
    @Query(
        """
        SELECT * FROM stations
        WHERE ts > :ts OR call = :mycall
        """
    )
    suspend fun neighborsRaw(mycall: String, ts: Long): List<StationEntity>

    // ---- maintenance ----

    @Query("DELETE FROM stations WHERE ts < :ts")
    suspend fun trimOlderThan(ts: Long)

    @Query("DELETE FROM stations")
    suspend fun deleteAll()
}

/**
 * Compute the cosine-corrected squared distance used by the original
 * Scala `getNeighbors` query, using the same approximation:
 *
 *   corr = (cos(pi*lat/180e6))^2 * 100   (lat in degrees×1e6)
 *   dist = (lat - myLat)^2 + (lon - myLon)^2 * corr / 100
 *
 * Result is an integer "distance" suitable only for relative ordering,
 * not for display in real units.
 */
fun stationDistance(myLat: Int, myLon: Int, lat: Int, lon: Int): Int {
    val rad = Math.PI * myLat / 180_000_000.0
    val corr = (Math.cos(rad) * Math.cos(rad) * 100).toInt()
    val dlat = lat - myLat
    val dlon = lon - myLon
    return dlat * dlat + dlon * dlon * corr / 100
}
