package org.aprsdroid.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `messages` table — APRS text messages
 * (incoming and outgoing, with retry state).
 *
 * Mirrors the message-related methods of the original Scala `StorageDatabase`.
 */
@Dao
interface MessageDao {

    // ---- inserts / updates ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET type = :type WHERE _id = :id")
    suspend fun updateMessageType(id: Long, type: Int)

    /**
     * Mark all OUT_NEW messages to [call] with the given [msgid] as
     * [type] (typically OUT_ACKED or OUT_REJECTED).
     * Matches Scala `updateMessageAcked`.
     */
    @Query(
        """
        UPDATE messages SET type = :type
        WHERE type = 2 AND call = :call AND msgid = :msgid
        """
    )
    suspend fun updateMessageAcked(call: String, msgid: String, type: Int)

    // ---- queries ----

    /** All messages with a comms partner, ordered by _id (matches Scala `getMessages`). */
    @Query("SELECT * FROM messages WHERE call = :call ORDER BY _id")
    fun getMessages(call: String): Flow<List<MessageEntity>>

    /** Pending outgoing messages with retrycnt <= [retries] (matches Scala `getPendingMessages`). */
    @Query("SELECT * FROM messages WHERE type = 2 AND retrycnt <= :retries ORDER BY _id")
    fun getPendingMessages(retries: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type = 2 AND retrycnt <= :retries ORDER BY _id")
    suspend fun getPendingMessagesList(retries: Int): List<MessageEntity>

    /**
     * One row per conversation partner — the latest message per `call`.
     * Mirrors Scala `getConversations`, which selects from a sub-query
     * ordered by `_id DESC`, grouped by `call`, ordered by `_id DESC`.
     */
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages ORDER BY _id DESC
        )
        GROUP BY call
        ORDER BY _id DESC
        """
    )
    fun getConversations(): Flow<List<MessageEntity>>

    /**
     * Duplicate-detection query used by the incoming-message path
     * (matches Scala `isMessageDuplicate`).
     *
     * If [timeThreshold] is non-null, only messages with `ts >= timeThreshold`
     * are considered (configurable dupe window). A null threshold means
     * "check all history" — the Scala code only adds the time condition
     * when `p.msgdupe` is enabled.
     */
    @Query(
        """
        SELECT 1 FROM messages
        WHERE type = 1 AND call = :call AND msgid = :msgid AND text = :text
              AND (:timeThreshold IS NULL OR ts >= :timeThreshold)
        LIMIT 1
        """
    )
    suspend fun isDuplicate(call: String, msgid: String, text: String, timeThreshold: Long?): Int?

    /**
     * Next outgoing message id for [call] — matches Scala `createMsgId`:
     * `MAX(CAST(msgid AS INTEGER))` over non-incoming messages, +1.
     * Returns 0 if there are no prior outgoing messages.
     */
    @Query(
        """
        SELECT COALESCE(MAX(CAST(msgid AS INTEGER)), -1) + 1
        FROM messages WHERE call = :call AND type != :incomingType
        """
    )
    suspend fun createMsgId(call: String, incomingType: Int = MessageEntity.TYPE_INCOMING): Int

    // ---- deletion ----

    @Query("DELETE FROM messages WHERE call = :call")
    suspend fun deleteMessages(call: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
