package com.vitorpamplona.amethyst.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface EventDao {
    @Query("SELECT * FROM EventEntity WHERE id = :id")
    @Transaction
    fun getById(id: String): EventWithTags?

    @Query("SELECT * FROM EventEntity WHERE id IN (:ids)")
    @Transaction
    fun getByIds(ids: List<String>): List<EventWithTags>

    @Query("SELECT * FROM EventEntity ORDER BY createdAt DESC")
    @Transaction
    fun getAll(): List<EventWithTags>

    @Query("SELECT * FROM EventEntity WHERE pubkey = :pubkey ORDER BY createdAt DESC")
    @Transaction
    fun getByAuthor(pubkey: String): List<EventWithTags>

    @Query("SELECT COUNT(*) FROM EventEntity WHERE pubkey = :pubkey")
    fun getRowCountByAuthor(pubkey: String): Int

    @Query(
        "SELECT * FROM EventEntity " +
            "WHERE pubkey = :pubkey " +
            "AND kind = :kind " +
            "ORDER BY createdAt DESC"
    )
    @Transaction
    fun getByAuthorAndKind(pubkey: String, kind: Int): List<EventWithTags>

    @Query(
        "SELECT * FROM EventEntity " +
            "WHERE kind = :kind " +
            "  AND pubkey in (:pubkeys) " +
            "ORDER BY createdAt DESC"
    )
    @Transaction
    fun getByAuthorsAndKind(pubkeys: List<String>, kind: Int): List<EventWithTags>

    @Query(
        "SELECT EventEntity.pk, EventEntity.id, EventEntity.pubkey, EventEntity.createdAt, EventEntity.kind, EventEntity.content, EventEntity.sig " +
            "FROM EventEntity " +
            "INNER JOIN TagEntity ON EventEntity.pk = TagEntity.pkEvent " +
            "WHERE EventEntity.kind = :kind " +
            "  AND TagEntity.col0Name = 'p' " +
            "  AND TagEntity.col1Value in (:pubkeys) " +
            "ORDER BY createdAt DESC"
    )
    @Transaction
    fun getByPTagsAndKind(pubkeys: List<String>, kind: Int): List<EventWithTags>

    @Query(
        "SELECT EventEntity.pk, EventEntity.id, EventEntity.pubkey, EventEntity.createdAt, EventEntity.kind, EventEntity.content, EventEntity.sig " +
            "FROM EventEntity " +
            "INNER JOIN TagEntity ON EventEntity.pk = TagEntity.pkEvent " +
            "WHERE TagEntity.col0Name = :col0 " +
            "AND TagEntity.col1Value = :col1 " +
            "ORDER BY createdAt DESC"
    )
    @Transaction
    fun getByTag(col0: String, col1: String): List<EventWithTags>

    @Query(
        "SELECT COUNT(DISTINCT(EventEntity.id)) " +
            "FROM EventEntity " +
            "INNER JOIN TagEntity ON EventEntity.pk = TagEntity.pkEvent " +
            "WHERE TagEntity.col0Name = :col0 " +
            "AND TagEntity.col1Value = :col1 " +
            "ORDER BY createdAt DESC"
    )
    fun getRowCountByTag(col0: String, col1: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEvent(event: EventEntity): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTag(vararg tags: TagEntity): List<Long>?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTags(tags: List<TagEntity>): List<Long>?

    @Delete
    fun delete(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    fun insertEventWithTags(dbEvent: EventEntity, dbTags: List<TagEntity>) {
        insertEvent(dbEvent)?.let { eventPK ->
            if (eventPK >= 0) {
                dbTags.forEach {
                    it.pkEvent = eventPK
                }

                insertTags(dbTags)
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    fun insertEventWithTags(dbEvent: EventWithTags) {
        insertEvent(dbEvent.event)?.let { eventPK ->
            if (eventPK >= 0) {
                dbEvent.tags.forEach {
                    it.pkEvent = eventPK
                }

                insertTags(dbEvent.tags)
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    fun insertListOfEventWithTags(dbEvent: List<EventWithTags>) {
        dbEvent.forEach {
            insertEvent(it.event)?.let { eventPK ->
                if (eventPK >= 0) {
                    it.tags.forEach {
                        it.pkEvent = eventPK
                    }

                    insertTags(it.tags)
                }
            }
        }
    }
}
