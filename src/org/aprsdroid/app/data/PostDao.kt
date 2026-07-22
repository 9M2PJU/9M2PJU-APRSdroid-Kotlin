package org.aprsdroid.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `posts` table — the packet log / activity feed.
 *
 * Mirrors the post-related methods of the original Scala `StorageDatabase`.
 */
@Dao
interface PostDao {

    // ---- inserts ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: PostEntity): Long

    /** Insert a post from raw fields (matches Scala `addPost`). */
    @Query(
        """
        INSERT INTO posts (ts, type, status, message)
        VALUES (:ts, :type, :status, :message)
        """
    )
    suspend fun addPost(ts: Long, type: Int, status: String?, message: String?): Long

    // ---- queries ----

    /** All posts, newest first (matches Scala `getPosts(limit)`). */
    @Query("SELECT * FROM posts ORDER BY _id DESC LIMIT :limit")
    fun getPosts(limit: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts ORDER BY _id DESC")
    fun getAllPosts(): Flow<List<PostEntity>>

    /** Posts matching a free-text filter on `message` (matches Scala `getPostFilter`). */
    @Query("SELECT * FROM posts WHERE message LIKE '%' || :filter || '%' ORDER BY _id DESC LIMIT :limit")
    fun getPostsFiltered(filter: String, limit: String): Flow<List<PostEntity>>

    /**
     * Posts associated with a station call — matches Scala `getStaPosts`:
     *   - call-originated messages   (`call%`)
     *   - object definitions         (`%;call%`)
     *   - item definitions           (`%)call%`)
     */
    @Query(
        """
        SELECT * FROM posts
        WHERE message LIKE :callPrefix
           OR message LIKE :objPattern
           OR message LIKE :itemPattern
        ORDER BY _id DESC LIMIT :limit
        """
    )
    fun getStaPosts(
        callPrefix: String,
        objPattern: String,
        itemPattern: String,
        limit: String,
    ): Flow<List<PostEntity>>

    /** Convenience wrapper for [getStaPosts] building the LIKE patterns from a call. */
    fun getStaPosts(call: String, limit: String): Flow<List<PostEntity>> =
        getStaPosts("$call%", "%;$call%", "%)$call%", limit)

    /**
     * Posts suitable for export — incoming/tx/digi/igate packets
     * (Scala `getExportPosts`: type IN (0, 3, 5, 6)).
     */
    @Query("SELECT * FROM posts WHERE type IN (0, 3, 5, 6) AND message LIKE :callPrefix ORDER BY _id")
    fun getExportPosts(callPrefix: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE type IN (0, 3, 5, 6) ORDER BY _id")
    fun getExportPostsAll(): Flow<List<PostEntity>>

    // ---- maintenance ----

    /** Delete posts older than [ts] (matches Scala `trimPosts(ts)`). */
    @Query("DELETE FROM posts WHERE ts < :ts")
    suspend fun trimOlderThan(ts: Long)

    @Query("DELETE FROM posts")
    suspend fun deleteAll()
}
