package com.example.core.permissions

import android.Manifest
import android.os.Build

enum class PermissionKind {
  RUNTIME,
  SPECIAL_SETTINGS,
}

enum class RiyaPermission(
  val title: String,
  val bengaliHint: String,
  val description: String,
  val kind: PermissionKind,
  val isEssential: Boolean,
  val runtimePermissions: List<String> = emptyList(),
  val minApi: Int = Build.VERSION_CODES.O,
) {
  MICROPHONE(
    title = "Microphone",
    bengaliHint = "Tomar kotha shunte ei permission ta lagbe",
    description = "Required for voice conversation, wake word detection, and barge-in interruption.",
    kind = PermissionKind.RUNTIME,
    isEssential = true,
    runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO),
  ),
  NOTIFICATIONS(
    title = "Notifications",
    bengaliHint = "Riya active thakle notification-e Stop/Mute button dekhabe",
    description = "Shows the persistent listening bar, quick Mute/Stop controls, and caring reminders.",
    kind = PermissionKind.RUNTIME,
    isEssential = true,
    runtimePermissions =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
      } else {
        emptyList()
      },
    minApi = Build.VERSION_CODES.TIRAMISU,
  ),
  BATTERY_OPTIMIZATION(
    title = "Unrestricted Battery",
    bengaliHint = "Background-e Riya jeno bondho na hoye jay",
    description = "Prevents the system from killing Riya's voice service when the screen is off or app is minimized.",
    kind = PermissionKind.SPECIAL_SETTINGS,
    isEssential = true,
  ),
  ACCESSIBILITY(
    title = "Accessibility Service",
    bengaliHint = "App open kora, YouTube chalano, ar tap/scroll korar jonno",
    description = "Powers hands-free phone control, YouTube playback, and screen reading. Never reads banking/UPI/PIN screens.",
    kind = PermissionKind.SPECIAL_SETTINGS,
    isEssential = false,
  ),
  OVERLAY(
    title = "Display Over Other Apps",
    bengaliHint = "Jekono app theke Riya bubble-e tap kore kotha bolte parbe",
    description = "Enables the floating glowing Riya orb bubble while using YouTube, Chrome, or WhatsApp.",
    kind = PermissionKind.SPECIAL_SETTINGS,
    isEssential = false,
  ),
  USAGE_ACCESS(
    title = "Usage Stats Access",
    bengaliHint = "Phone analysis report ar screen-time dekhar jonno",
    description = "Allows Riya to analyze screen time, heavy/unused apps, and give phone health tips.",
    kind = PermissionKind.SPECIAL_SETTINGS,
    isEssential = false,
  ),
  NOTIFICATION_LISTENER(
    title = "Notification Access",
    bengaliHint = "Unread message gulo summarize kore shonate parbe",
    description = "Lets Riya read and summarize incoming notifications and suggest smart replies.",
    kind = PermissionKind.SPECIAL_SETTINGS,
    isEssential = false,
  ),
  CONTACTS(
    title = "Contacts",
    bengaliHint = "Nam bole call ba message korar jonno",
    description = "Matches spoken contact names (e.g., 'Maake call koro') accurately before placing calls.",
    kind = PermissionKind.RUNTIME,
    isEssential = false,
    runtimePermissions = listOf(Manifest.permission.READ_CONTACTS),
  ),
  PHONE(
    title = "Phone Calls",
    bengaliHint = "Voice command diye call laganor jonno (age confirm korbe)",
    description = "Places phone calls when you confirm a voice call request.",
    kind = PermissionKind.RUNTIME,
    isEssential = false,
    runtimePermissions = listOf(Manifest.permission.CALL_PHONE),
  ),
  SMS(
    title = "Send SMS",
    bengaliHint = "Voice confirmation-er por SMS pathanor jonno",
    description = "Sends SMS messages only after you explicitly confirm the recipient and text.",
    kind = PermissionKind.RUNTIME,
    isEssential = false,
    runtimePermissions = listOf(Manifest.permission.SEND_SMS),
  ),
  CALENDAR(
    title = "Calendar",
    bengaliHint = "Routine, study reminder, ar event add korar jonno",
    description = "Reads upcoming events and saves study, medicine, or birthday reminders.",
    kind = PermissionKind.RUNTIME,
    isEssential = false,
    runtimePermissions =
      listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
  ),
  EXACT_ALARM(
    title = "Alarms & Reminders",
    bengaliHint = "Thik somoye alarm ar morning/night reminder-er jonno",
    description = "Schedules exact alarms and caring daily wake-up or study reminders.",
    kind = PermissionKind.SPECIAL_SETTINGS,
    isEssential = false,
    minApi = Build.VERSION_CODES.S,
  ),
}

data class PermissionStatusItem(
  val permission: RiyaPermission,
  val isGranted: Boolean,
  val isAvailableOnDevice: Boolean = true,
)
