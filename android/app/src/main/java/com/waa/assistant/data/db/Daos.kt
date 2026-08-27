package com.waa.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.waa.assistant.data.model.ConversationEntity
import com.waa.assistant.data.model.LogEntity
import com.waa.assistant.data.model.MessageEntity
import com.waa.assistant.data.model.ReplyJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity)

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MessageEntity): Long

    @Query("SELECT COUNT(*) FROM messages WHERE fingerprint = :fp")
    suspend fun countByFingerprint(fp: String): Int

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun latest(conversationId: String, limit: Int): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC"
    )
    fun observeConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Query(
        "SELECT COUNT(*) FROM messages WHERE timestamp >= :startOfDay AND isSelf = 0"
    )
    suspend fun countReceivedToday(startOfDay: Long): Int
}

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReplyJobEntity)

    @Update
    suspend fun update(entity: ReplyJobEntity)

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): ReplyJobEntity?

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<ReplyJobEntity>>

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<ReplyJobEntity>>

    @Query("SELECT COUNT(*) FROM jobs WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query(
        "SELECT COUNT(*) FROM jobs WHERE createdAt >= :startOfDay AND status IN ('SENT','REVIEW','GENERATING')"
    )
    suspend fun countAiToday(startOfDay: Long): Int

    @Query(
        "SELECT COUNT(*) FROM jobs WHERE repliedAt >= :startOfDay AND status = 'SENT'"
    )
    suspend fun countSentToday(startOfDay: Long): Int

    @Query(
        "SELECT COUNT(*) FROM jobs WHERE repliedAt >= :sinceMs AND status = 'SENT'"
    )
    suspend fun countSentSince(sinceMs: Long): Int
}

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LogEntity)

    @Query("SELECT * FROM logs ORDER BY ts DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<LogEntity>>

    @Query("DELETE FROM logs WHERE ts < :before")
    suspend fun purgeBefore(before: Long)
}
