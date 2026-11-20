package com.example.analysis

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.example.core.permissions.PermissionChecker
import com.example.core.permissions.RiyaPermission
import com.example.data.repository.MemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AppUsageEntry(
  val appName: String,
  val packageName: String,
  val minutesToday: Long,
  val isChatApp: Boolean,
)

data class RagStatus(
  val shouldShowRag: Boolean,
  val excessivePhoneUse: Boolean,
  val heavyChatAppName: String?,
  val reasonMessage: String?,
)

data class PhoneHealthReport(
  val batteryPercent: Int,
  val isCharging: Boolean,
  val batteryTempCelsius: Float,
  val batteryHealthLabel: String,
  val ramUsedGb: Float,
  val ramTotalGb: Float,
  val storageUsedGb: Float,
  val storageTotalGb: Float,
  val networkLabel: String,
  val uptimeHours: Long,
  val installedAppCount: Int,
  val usageStatsGranted: Boolean,
  val totalScreenMinutesToday: Long,
  val chatMinutesToday: Long,
  val topAppsToday: List<AppUsageEntry>,
  val unusedApps: List<String>,
  val ragStatus: RagStatus,
  val tips: List<String>,
)

@Singleton
class PhoneAnalyzer
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val permissionChecker: PermissionChecker,
  private val memoryRepository: MemoryRepository,
) {

  private val chatPackages =
    mapOf(
      "com.whatsapp" to "WhatsApp",
      "com.whatsapp.w4b" to "WhatsApp Business",
      "com.instagram.android" to "Instagram",
      "com.facebook.orca" to "Messenger",
      "org.telegram.messenger" to "Telegram",
      "com.snapchat.android" to "Snapchat",
      "com.discord" to "Discord",
    )

  suspend fun analyze(): PhoneHealthReport {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else 0
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging =
      status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val batteryTempC = tempTenths / 10f
    val healthCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0
    val batteryHealth =
      when (healthCode) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Weak"
        else -> "Normal"
      }

    // RAM
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val ramTotalGb = memInfo.totalMem / (1024f * 1024f * 1024f)
    val ramAvailGb = memInfo.availMem / (1024f * 1024f * 1024f)
    val ramUsedGb = (ramTotalGb - ramAvailGb).coerceAtLeast(0f)

    // Storage
    val stat = StatFs(Environment.getDataDirectory().path)
    val storageTotalGb = stat.totalBytes / (1024f * 1024f * 1024f)
    val storageFreeGb = stat.availableBytes / (1024f * 1024f * 1024f)
    val storageUsedGb = (storageTotalGb - storageFreeGb).coerceAtLeast(0f)

    // Network
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNet = cm.activeNetwork
    val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
    val networkLabel =
      when {
        caps == null -> "Offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Connected"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
        else -> "Connected"
      }

    val uptimeHours = TimeUnit.MILLISECONDS.toHours(SystemClock.elapsedRealtime())

    val pm = context.packageManager
    val installedCount =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0)).size
      } else {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0).size
      }

    val usageGranted = permissionChecker.isPermissionGranted(RiyaPermission.USAGE_ACCESS)
    var totalMinutesToday = 0L
    var chatMinutesToday = 0L
    val topApps = mutableListOf<AppUsageEntry>()
    val unusedApps = mutableListOf<String>()

    if (usageGranted) {
      val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
      val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      val now = System.currentTimeMillis()
      val todayStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, now)
      val merged = mutableMapOf<String, Long>()
      todayStats?.forEach { statItem ->
        if (statItem.totalTimeInForeground > 0) {
          merged[statItem.packageName] =
            (merged[statItem.packageName] ?: 0L) + statItem.totalTimeInForeground
        }
      }

      merged.entries
        .filter { it.key != context.packageName }
        .sortedByDescending { it.value }
        .forEach { (pkg, millis) ->
          val mins = TimeUnit.MILLISECONDS.toMinutes(millis)
          if (mins > 0) {
            totalMinutesToday += mins
            val isChat = chatPackages.containsKey(pkg)
            if (isChat) chatMinutesToday += mins
            if (topApps.size < 8) {
              val label =
                chatPackages[pkg]
                  ?: runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                  }.getOrDefault(pkg.substringAfterLast('.'))
              topApps.add(AppUsageEntry(label, pkg, mins, isChat))
            }
          }
        }

      // Check 30-day unused user-installed apps
      val monthAgo = now - TimeUnit.DAYS.toMillis(30)
      val monthStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, monthAgo, now)
      val usedRecently =
        monthStats?.filter { it.totalTimeInForeground > 60_000L }?.map { it.packageName }?.toSet()
          ?: emptySet()
      val launcherApps =
        pm.queryIntentActivities(
          Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
          PackageManager.MATCH_ALL,
        )
      launcherApps
        .mapNotNull { it.activityInfo?.packageName }
        .distinct()
        .filter { it != context.packageName && !usedRecently.contains(it) }
        .take(5)
        .forEach { pkg ->
          val label =
            runCatching {
              val ai = pm.getApplicationInfo(pkg, 0)
              pm.getApplicationLabel(ai).toString()
            }.getOrNull()
          if (!label.isNullOrBlank()) unusedApps.add(label)
        }
    }

    // Rag / Playful Girlfriend Jealousy Detection
    val recentChatNotifs = memoryRepository.countRecentChatNotifications()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val isLateNight = hour >= 23 || hour < 5
    val topChatApp = topApps.firstOrNull { it.isChatApp && it.minutesToday >= 30 }
    val excessivePhone = totalMinutesToday >= 150 || isLateNight
    val heavyChat = topChatApp != null || chatMinutesToday >= 40 || recentChatNotifs >= 8

    val ragReason =
      when {
        topChatApp != null ->
          "Aj ${topChatApp.appName}-e ${topChatApp.minutesToday} min chat korcho! Kar sathe eto kotha hochhe, Rohan? Amake bhule gele naki?"
        recentChatNotifs >= 8 ->
          "Eto message kotha theke asche huh? Kar sathe chat korcho eto khon?"
        isLateNight ->
          "Raat ${if (hour == 0) 12 else hour}ta baje! Ekhono phone tipcho keno? Class 11-er porashona ar ghum-er ki hobe? Phone rakho!"
        totalMinutesToday >= 150 ->
          "Aj ${totalMinutesToday / 60} ghonta ${totalMinutesToday % 60} min dhore phone use korcho! Chokh kharap hobe to jaan, ektu rest nao."
        else -> null
      }

    val ragStatus =
      RagStatus(
        shouldShowRag = excessivePhone || heavyChat,
        excessivePhoneUse = excessivePhone,
        heavyChatAppName = topChatApp?.appName ?: if (recentChatNotifs >= 8) "Chat apps" else null,
        reasonMessage = ragReason,
      )

    val tips = mutableListOf<String>()
    if (batteryPercent <= 20 && !isCharging) tips.add("Battery $batteryPercent% — charger lagao shona.")
    if (batteryTempC >= 40f) tips.add("Phone ektu gorom ($batteryTempC°C) hoye geche, heavy app close koro.")
    if (storageTotalGb > 0 && (storageUsedGb / storageTotalGb) > 0.85f) {
      tips.add("Storage 85% er beshi bhore geche — purono cache/video clear koro.")
    }
    if (ramTotalGb > 0 && (ramUsedGb / ramTotalGb) > 0.85f) {
      tips.add("RAM usage high — background apps close korle smooth hobe.")
    }
    if (!usageGranted) {
      tips.add("Usage Stats permission dile screen-time ar chat-time analysis korte parbo.")
    }
    if (tips.isEmpty()) tips.add("Phone ekdom healthy ache, Rohan!")

    return PhoneHealthReport(
      batteryPercent = batteryPercent,
      isCharging = isCharging,
      batteryTempCelsius = batteryTempC,
      batteryHealthLabel = batteryHealth,
      ramUsedGb = ramUsedGb,
      ramTotalGb = ramTotalGb,
      storageUsedGb = storageUsedGb,
      storageTotalGb = storageTotalGb,
      networkLabel = networkLabel,
      uptimeHours = uptimeHours,
      installedAppCount = installedCount,
      usageStatsGranted = usageGranted,
      totalScreenMinutesToday = totalMinutesToday,
      chatMinutesToday = chatMinutesToday,
      topAppsToday = topApps,
      unusedApps = unusedApps,
      ragStatus = ragStatus,
      tips = tips,
    )
  }

  fun formatSummaryForPrompt(report: PhoneHealthReport): String {
    return buildString {
      append("Battery: ${report.batteryPercent}% (${if (report.isCharging) "Charging" else "Not charging"}, ${report.batteryTempCelsius}°C, ${report.batteryHealthLabel}). ")
      append("RAM: ${"%.1f".format(report.ramUsedGb)}/${"%.1f".format(report.ramTotalGb)} GB. ")
      append("Storage: ${"%.1f".format(report.storageUsedGb)}/${"%.1f".format(report.storageTotalGb)} GB. ")
      append("Network: ${report.networkLabel}. Installed apps: ${report.installedAppCount}. ")
      if (report.usageStatsGranted) {
        append("Screen time today: ${report.totalScreenMinutesToday} mins, Chat apps today: ${report.chatMinutesToday} mins. ")
        if (report.topAppsToday.isNotEmpty()) {
          append("Top apps: ${report.topAppsToday.take(4).joinToString { "${it.appName} (${it.minutesToday}m)" }}. ")
        }
      } else {
        append("Usage Stats access not granted yet. ")
      }
      if (report.tips.isNotEmpty()) {
        append("Tips: ${report.tips.joinToString("; ")}")
      }
    }
  }
}
