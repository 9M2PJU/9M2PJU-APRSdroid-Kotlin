package org.aprsdroid.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `messages` table — APRS text messages
 * (incoming and outgoing, with retry state).
 *
 * Mirrors the original Scala `StorageDatabase.Message` companion object.
 * Indexed on `call` and `type`.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["call"]),
        Index(value = ["type"]),
    ],
)
data class MessageEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,

    /** timestamp of RX or first TX. */
    @ColumnInfo(name = "ts")
    val ts: Long,

    /** attempt number for sending msg. */
    @ColumnInfo(name = "retrycnt")
    val retrycnt: Int = 0,

    /** callsign of comms partner. */
    @ColumnInfo(name = "call")
    val call: String,

    /** message id (up to 5 alphanumeric symbols). */
    @ColumnInfo(name = "msgid")
    val msgid: String,

    /** incoming / out-new / out-acked / ... — see TYPE_* constants. */
    @ColumnInfo(name = "type")
    val type: Int,

    @ColumnInfo(name = "text")
    val text: String? = null,
) {
    companion object {
        // Message types — keep in sync with StorageDatabase.Message
        const val TYPE_INCOMING = 1
        const val TYPE_OUT_NEW = 2
        const val TYPE_OUT_ACKED = 3
        const val TYPE_OUT_REJECTED = 4
        const val TYPE_OUT_ABORTED = 5
    }
}
