package com.example.control

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemController
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val appLauncher: AppLauncher,
) {

  private var torchEnabled = false

  fun setFlashlight(enable: Boolean): Result<String> {
    return try {
      val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      val cameraId =
        camManager.cameraIdList.firstOrNull { id ->
          camManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return Result.failure(UnsupportedOperationException("No flashlight available on this device."))
      camManager.setTorchMode(cameraId, enable)
      torchEnabled = enable
      Result.success(if (enable) "Flashlight turned ON" else "Flashlight turned OFF")
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun toggleFlashlight(): Result<String> = setFlashlight(!torchEnabled)

  fun adjustVolume(action: String): Result<String> {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return when (action.lowercase()) {
      "volume_up", "up" -> {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        Result.success("Volume increased")
      }
      "volume_down", "down" -> {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        Result.success("Volume decreased")
      }
      "mute" -> {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        Result.success("Media muted")
      }
      "unmute" -> {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
        Result.success("Media unmuted")
      }
      else -> Result.failure(IllegalArgumentException("Unknown volume action: $action"))
    }
  }

  fun dispatchMediaKey(action: String): Result<String> {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val keyCode =
      when (action.lowercase()) {
        "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
        "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
        "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
        "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
        else -> return adjustVolume(action)
      }
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    return Result.success("Media command '$action' sent")
  }

  fun openSetting(setting: String): Result<String> {
    val intent =
      when (setting.lowercase()) {
        "wifi", "wi-fi" ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
          } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
          }
        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        "brightness", "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        "sound", "volume" -> Intent(Settings.ACTION_SOUND_SETTINGS)
        "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        "dnd", "do_not_disturb" -> {
          val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          if (nm.isNotificationPolicyAccessGranted) {
            val next =
              if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
              } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
              }
            nm.setInterruptionFilter(next)
            return Result.success(
              if (next == NotificationManager.INTERRUPTION_FILTER_PRIORITY) "DND turned ON"
              else "DND turned OFF"
            )
          }
          Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
        else -> Intent(Settings.ACTION_SETTINGS)
      }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
      context.startActivity(intent)
      "Opened $setting settings"
    }
  }

  fun setAlarm(hour: Int, minute: Int, label: String?): Result<String> {
    val intent =
      Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
        putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
        putExtra(AlarmClock.EXTRA_MESSAGE, label ?: "Riya Reminder")
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      context.startActivity(intent)
      "Alarm set for %02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }
  }

  fun setTimer(seconds: Int, label: String?): Result<String> {
    val intent =
      Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
        putExtra(AlarmClock.EXTRA_MESSAGE, label ?: "Riya Timer")
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      context.startActivity(intent)
      "Timer set for $seconds seconds"
    }
  }

  fun openYouTubeSearch(query: String): Result<String> {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val ytIntent =
      Intent(Intent.ACTION_SEARCH).apply {
        setPackage("com.google.android.youtube")
        putExtra("query", query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    val webFallback =
      Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded"))
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    return runCatching {
      if (ytIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(ytIntent)
      } else {
        context.startActivity(webFallback)
      }
      "Opened YouTube for '$query'"
    }
  }

  fun searchWeb(query: String): Result<String> {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      context.startActivity(intent)
      "Searching web for '$query'"
    }
  }

  fun openMapsNavigation(destination: String): Result<String> {
    val encoded = URLEncoder.encode(destination, "UTF-8")
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      context.startActivity(intent)
      "Starting navigation to $destination"
    }
  }

  fun openCamera(): Result<String> {
    val intent =
      Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      context.startActivity(intent)
      "Opened Camera"
    }.recoverCatching {
      appLauncher.openAppByName("camera").getOrThrow()
    }
  }

  fun resolveContactNumber(nameOrNumber: String): Pair<String, String?> {
    val trimmed = nameOrNumber.trim()
    if (trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } && trimmed.count { it.isDigit() } >= 4) {
      return trimmed to trimmed
    }
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
      return trimmed to null
    }
    try {
      context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
          ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
          ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
        arrayOf("%$trimmed%"),
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val foundName = cursor.getString(0) ?: trimmed
          val foundNumber = cursor.getString(1)
          return foundName to foundNumber
        }
      }
    } catch (e: Exception) {
      Timber.w(e, "Contact query failed")
    }
    return trimmed to null
  }

  fun executeCall(number: String): Result<String> {
    val hasCallPerm =
      ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val intent =
      Intent(if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:${Uri.encode(number)}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      context.startActivity(intent)
      if (hasCallPerm) "Calling $number" else "Opened dialer for $number"
    }
  }

  fun executeSms(number: String, message: String): Result<String> {
    val hasSmsPerm =
      ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    if (hasSmsPerm) {
      return runCatching {
        val smsManager =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
          } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
          }
        smsManager.sendTextMessage(number, null, message, null, null)
        "SMS sent to $number"
      }
    }
    val intent =
      Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
        putExtra("sms_body", message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      context.startActivity(intent)
      "Opened SMS app for $number"
    }
  }

  fun openWhatsAppMessage(phoneOrEmpty: String?, message: String): Result<String> {
    val encodedText = URLEncoder.encode(message, "UTF-8")
    val digits = phoneOrEmpty?.filter { it.isDigit() }.orEmpty()
    val url =
      if (digits.length >= 10) {
        "https://wa.me/$digits?text=$encodedText"
      } else {
        "https://api.whatsapp.com/send?text=$encodedText"
      }
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      context.startActivity(intent)
      "Opened WhatsApp with prepared message"
    }
  }
}
