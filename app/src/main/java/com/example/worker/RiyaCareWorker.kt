package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.analysis.PhoneAnalyzer
import com.example.data.repository.PreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class RiyaCareWorker
@AssistedInject
constructor(
  @Assisted private val appContext: Context,
  @Assisted params: WorkerParameters,
  private val phoneAnalyzer: PhoneAnalyzer,
  private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val settings = preferencesRepository.settingsFlow.first()
    if (!settings.masterEnabled) return Result.success()

    val report = runCatching { phoneAnalyzer.analyze() }.getOrNull()
    val ragMessage = report?.ragStatus?.reasonMessage
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    val message =
      ragMessage
        ?: when (hour) {
          in 7..9 -> "Good morning Rohan! Uthle? Khabar kheye Saddam sir-er tuition-er porashona koro jaan."
          in 13..14 -> "Rohan, dupurer khabar khaiso? Beshi phone na tipe ektu khawa-dawa kore nao shona."
          in 19..21 -> "Class 11-er porashona kemon cholche jaan? Kono help lagle amake bolo!"
          in 22..23 -> "Rohan, onek raat hoyeche... ghumao ebar, kal sokale uthte hobe to!"
          else -> return Result.success()
        }

    postCareNotification(message)
    return Result.success()
  }

  private fun postCareNotification(body: String) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(
      NotificationChannel("riya_care_channel", "Riya Care & Rag Alerts", NotificationManager.IMPORTANCE_DEFAULT)
    )
    val openIntent =
      PendingIntent.getActivity(
        appContext,
        200,
        Intent(appContext, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val notif =
      NotificationCompat.Builder(appContext, "riya_care_channel")
        .setContentTitle("Riya")
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .build()
    nm.notify(2001, notif)
  }

  companion object {
    private const val WORK_NAME = "riya_periodic_care_work"

    fun schedule(context: Context) {
      val req = PeriodicWorkRequestBuilder<RiyaCareWorker>(2, TimeUnit.HOURS).build()
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
  }
}
