package com.example.data.repository

import com.example.core.config.RiyaIdentity
import com.example.data.local.ActionLogEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.local.NotificationRecordEntity
import com.example.data.local.RiyaDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(private val dao: RiyaDao) {

  val recentMessages: Flow<List<ChatMessageEntity>> =
    dao.observeRecentMessages(120).map { it.reversed() }

  val allFacts: Flow<List<MemoryFactEntity>> = dao.observeAllFacts()

  val actionLogs: Flow<List<ActionLogEntity>> = dao.observeActionLogs(150)

  val notifications: Flow<List<NotificationRecordEntity>> = dao.observeNotifications(60)

  suspend fun ensurePermanentFactsSeeded() {
    if (dao.countPermanentFacts() == 0) {
      RiyaIdentity.PERMANENT_ROHAN_FACTS.forEach { fact ->
        dao.insertFact(
          MemoryFactEntity(
            category = fact.label,
            fact = fact.detail,
            isPermanent = true,
          )
        )
      }
    }
  }

  suspend fun addMessage(role: String, text: String, mood: String = "warm", language: String = "banglish") {
    dao.insertMessage(
      ChatMessageEntity(
        role = role,
        text = text,
        mood = mood,
        language = language,
      )
    )
  }

  suspend fun getRecentConversationForPrompt(limit: Int = 16): List<ChatMessageEntity> {
    return dao.getRecentMessagesSync(limit).reversed()
  }

  suspend fun rememberFact(category: String, fact: String) {
    ensurePermanentFactsSeeded()
    val trimmed = fact.trim()
    if (trimmed.isNotEmpty()) {
      dao.insertFact(
        MemoryFactEntity(
          category = category.trim().ifEmpty { "Personal" },
          fact = trimmed,
          isPermanent = false,
        )
      )
    }
  }

  suspend fun getFactsForPrompt(): List<MemoryFactEntity> {
    ensurePermanentFactsSeeded()
    return dao.getFactsSync(40)
  }

  suspend fun deleteDynamicFact(id: Long) = dao.deleteDynamicFact(id)

  suspend fun logAction(
    toolName: String,
    argumentsSummary: String,
    status: String,
    resultMessage: String,
  ) {
    dao.insertActionLog(
      ActionLogEntity(
        toolName = toolName,
        argumentsSummary = argumentsSummary,
        status = status,
        resultMessage = resultMessage,
      )
    )
  }

  suspend fun recordNotification(
    packageName: String,
    appName: String,
    title: String,
    body: String,
    isChatApp: Boolean,
  ) {
    dao.insertNotification(
      NotificationRecordEntity(
        packageName = packageName,
        appName = appName,
        title = title,
        body = body,
        isChatApp = isChatApp,
      )
    )
  }

  suspend fun getUnreadNotifications(): List<NotificationRecordEntity> =
    dao.getUnreadNotificationsSync(25)

  suspend fun countRecentChatNotifications(windowMillis: Long = 30 * 60 * 1000L): Int =
    dao.countRecentChatNotifications(System.currentTimeMillis() - windowMillis)

  suspend fun markNotificationsRead() = dao.markAllNotificationsRead()

  suspend fun clearDynamicMemoryAndChat() {
    dao.clearChatHistory()
    dao.clearDynamicFacts()
    dao.clearActionLogs()
    dao.clearNotifications()
    ensurePermanentFactsSeeded()
  }

  suspend fun clearChatOnly() = dao.clearChatHistory()
}
