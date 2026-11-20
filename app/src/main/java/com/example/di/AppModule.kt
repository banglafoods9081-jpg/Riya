package com.example.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.data.local.RiyaDao
import com.example.data.local.RiyaDatabase
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.PreferencesRepositoryImpl
import com.example.data.repository.SecretRepository
import com.example.data.repository.SecretRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

private val Context.riyaDataStore: DataStore<Preferences> by preferencesDataStore(name = "riya_preferences")

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun provideRiyaDatabase(@ApplicationContext context: Context): RiyaDatabase {
    return Room.databaseBuilder(context, RiyaDatabase::class.java, "riya_memory.db")
      .fallbackToDestructiveMigration(dropAllTables = true)
      .build()
  }

  @Provides
  @Singleton
  fun provideRiyaDao(db: RiyaDatabase): RiyaDao = db.riyaDao()

  @Provides
  @Singleton
  fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
    return context.riyaDataStore
  }

  @Provides
  @IoDispatcher
  fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

  @Provides
  @DefaultDispatcher
  fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient {
    val logging =
      HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("x-goog-api-key")
        redactHeader("Authorization")
      }
    return OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(45, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .addInterceptor(logging)
      .build()
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

  @Binds
  @Singleton
  abstract fun bindSecretRepository(impl: SecretRepositoryImpl): SecretRepository

  @Binds
  @Singleton
  abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository
}
