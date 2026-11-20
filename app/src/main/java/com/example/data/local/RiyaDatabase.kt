package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [
    ChatMessageEntity::class,
    MemoryFactEntity::class,
    ActionLogEntity::class,
    NotificationRecordEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class RiyaDatabase : RoomDatabase() {
  abstract fun riyaDao(): RiyaDao
}
