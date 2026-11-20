package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val role: String, // "user", "riya", "system"
  val text: String,
  val mood: String = "warm",
  val language: String = "banglish",
  val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val category: String,
  val fact: String,
  val isPermanent: Boolean = false,
  val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "action_logs")
data class ActionLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val toolName: String,
  val argumentsSummary: String,
  val status: String, // "SUCCESS", "FAILED", "BLOCKED", "PENDING_CONFIRM"
  val resultMessage: String,
  val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notification_records")
data class NotificationRecordEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val packageName: String,
  val appName: String,
  val title: String,
  val body: String,
  val isChatApp: Boolean,
  val isUnread: Boolean = true,
  val timestamp: Long = System.currentTimeMillis(),
)
