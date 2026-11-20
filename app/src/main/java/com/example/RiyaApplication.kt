package com.example

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.worker.RiyaCareWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class RiyaApplication : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    RiyaCareWorker.schedule(this)
    Timber.d("RiyaApplication initialized")
  }

  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
        .build()
}
