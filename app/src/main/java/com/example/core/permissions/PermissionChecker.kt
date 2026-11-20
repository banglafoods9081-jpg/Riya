package com.example.core.permissions

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionChecker @Inject constructor(@ApplicationContext private val context: Context) {

  private val _statuses = MutableStateFlow<List<PermissionStatusItem>>(emptyList())
  val statuses: StateFlow<List<PermissionStatusItem>> = _statuses.asStateFlow()

  init {
    refresh()
  }

  fun refresh(): List<PermissionStatusItem> {
    val updated =
      RiyaPermission.entries.map { permission ->
        PermissionStatusItem(
          permission = permission,
          isGranted = isPermissionGranted(permission),
          isAvailableOnDevice = true,
        )
      }
    _statuses.value = updated
    return updated
  }

  fun isPermissionGranted(permission: RiyaPermission): Boolean {
    return when (permission) {
      RiyaPermission.MICROPHONE,
      RiyaPermission.CONTACTS,
      RiyaPermission.PHONE,
      RiyaPermission.SMS,
      RiyaPermission.CALENDAR -> {
        permission.runtimePermissions.all { perm ->
          ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
      }

      RiyaPermission.NOTIFICATIONS -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
          ) == PackageManager.PERMISSION_GRANTED
        } else {
          NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
      }

      RiyaPermission.ACCESSIBILITY -> isAccessibilityEnabled()

      RiyaPermission.OVERLAY -> Settings.canDrawOverlays(context)

      RiyaPermission.USAGE_ACCESS -> isUsageStatsGranted()

      RiyaPermission.NOTIFICATION_LISTENER -> {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
      }

      RiyaPermission.BATTERY_OPTIMIZATION -> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
      }

      RiyaPermission.EXACT_ALARM -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
          alarmManager?.canScheduleExactAlarms() == true
        } else {
          true
        }
      }
    }
  }

  private fun isAccessibilityEnabled(): Boolean {
    return try {
      val enabledServices =
        Settings.Secure.getString(
          context.contentResolver,
          Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
      enabledServices.split(':').any { component ->
        component.startsWith("${context.packageName}/", ignoreCase = true)
      }
    } catch (e: Exception) {
      Timber.w(e, "Unable to read enabled accessibility services")
      false
    }
  }

  private fun isUsageStatsGranted(): Boolean {
    return try {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
      val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          @Suppress("DEPRECATION")
          appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
          )
        } else {
          @Suppress("DEPRECATION")
          appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
          )
        }
      mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
      Timber.w(e, "Unable to check usage stats access")
      false
    }
  }

  fun createSettingsIntent(permission: RiyaPermission): Intent {
    val packageUri = Uri.parse("package:${context.packageName}")
    val primary =
      when (permission) {
        RiyaPermission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        RiyaPermission.OVERLAY ->
          Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
        RiyaPermission.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        RiyaPermission.NOTIFICATION_LISTENER ->
          Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        RiyaPermission.BATTERY_OPTIMIZATION ->
          Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        RiyaPermission.EXACT_ALARM ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
          } else {
            createAppDetailsIntent()
          }
        RiyaPermission.NOTIFICATIONS ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
              putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
          } else {
            createAppDetailsIntent()
          }
        else -> createAppDetailsIntent()
      }

    primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (primary.resolveActivity(context.packageManager) != null) {
      primary
    } else {
      createAppDetailsIntent()
    }
  }

  fun createAppDetailsIntent(): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.parse("package:${context.packageName}")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }
}
