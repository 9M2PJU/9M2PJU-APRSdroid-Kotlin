package org.aprsdroid.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the `posts` table — the packet log / activity feed.
 *
 * Mirrors the original Scala `StorageDatabase.Post` companion object.
 */
@Entity(tableName = "posts")
data class PostEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,

    @ColumnInfo(name = "ts")
    val ts: Long,

    @ColumnInfo(name = "type")
    val type: Int,

    @ColumnInfo(name = "status")
    val status: String? = null,

    @ColumnInfo(name = "message")
    val message: String? = null,
) {
    companion object {
        // Post types — keep in sync with StorageDatabase.Post
        const val TYPE_POST = 0
        const val TYPE_INFO = 1
        const val TYPE_ERROR = 2
        const val TYPE_INCMG = 3
        const val TYPE_TX = 4
        const val TYPE_DIGI = 5
        const val TYPE_IG = 6
    }
}
