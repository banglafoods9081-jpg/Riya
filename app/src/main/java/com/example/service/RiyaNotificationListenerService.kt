package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.core.safety.SafetyGuard
import com.example.data.repository.MemoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RiyaNotificationListenerService : NotificationListenerService() {

  @Inject lateinit var memoryRepository: MemoryRepository

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val chatPackages =
    setOf(
      "com.whatsapp",
      "com.whatsapp.w4b",
      "com.instagram.android",
      "com.facebook.orca",
      "org.telegram.messenger",
      "com.snapchat.android",
      "com.google.android.apps.messaging",
    )

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    val pkg = sbn.packageName ?: return
    if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return
    if (SafetyGuard.isSensitivePackage(pkg)) return

    val extras = sbn.notification?.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    if (title.isEmpty() && body.isEmpty()) return
    if (SafetyGuard.containsSensitiveScreenText("$title $body")) return

    val appLabel =
      runCatching {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
      }.getOrDefault(pkg.substringAfterLast('.'))

    val isChat = chatPackages.contains(pkg)
    scope.launch {
      runCatching {
        memoryRepository.recordNotification(
          packageName = pkg,
          appName = appLabel,
          title = title,
          body = body,
          isChatApp = isChat,
        )
      }.onFailure { Timber.w(it, "Failed to store notification") }
    }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }
}
