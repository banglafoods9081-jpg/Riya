package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RiyaDao {
  // Chat History
  @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
  fun observeRecentMessages(limit: Int = 100): Flow<List<ChatMessageEntity>>

  @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getRecentMessagesSync(limit: Int = 18): List<ChatMessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: ChatMessageEntity): Long

  @Query("DELETE FROM chat_messages")
  suspend fun clearChatHistory()

  // Memory Facts
  @Query("SELECT * FROM memory_facts ORDER BY isPermanent DESC, timestamp DESC")
  fun observeAllFacts(): Flow<List<MemoryFactEntity>>

  @Query("SELECT * FROM memory_facts ORDER BY isPermanent DESC, timestamp DESC LIMIT :limit")
  suspend fun getFactsSync(limit: Int = 40): List<MemoryFactEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFact(fact: MemoryFactEntity): Long

  @Query("SELECT COUNT(*) FROM memory_facts WHERE isPermanent = 1")
  suspend fun countPermanentFacts(): Int

  @Query("DELETE FROM memory_facts WHERE id = :id AND isPermanent = 0")
  suspend fun deleteDynamicFact(id: Long)

  @Query("DELETE FROM memory_facts WHERE isPermanent = 0")
  suspend fun clearDynamicFacts()

  // Action Logs
  @Query("SELECT * FROM action_logs ORDER BY timestamp DESC LIMIT :limit")
  fun observeActionLogs(limit: Int = 150): Flow<List<ActionLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertActionLog(log: ActionLogEntity): Long

  @Query("DELETE FROM action_logs")
  suspend fun clearActionLogs()

  // Notification Records
  @Query("SELECT * FROM notification_records ORDER BY timestamp DESC LIMIT :limit")
  fun observeNotifications(limit: Int = 60): Flow<List<NotificationRecordEntity>>

  @Query("SELECT * FROM notification_records WHERE isUnread = 1 ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getUnreadNotificationsSync(limit: Int = 25): List<NotificationRecordEntity>

  @Query("SELECT COUNT(*) FROM notification_records WHERE isChatApp = 1 AND timestamp >= :sinceMillis")
  suspend fun countRecentChatNotifications(sinceMillis: Long): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNotification(record: NotificationRecordEntity): Long

  @Query("UPDATE notification_records SET isUnread = 0")
  suspend fun markAllNotificationsRead()

  @Query("DELETE FROM notification_records")
  suspend fun clearNotifications()
}
