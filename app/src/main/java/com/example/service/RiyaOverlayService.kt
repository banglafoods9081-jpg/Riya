package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.math.abs

class RiyaOverlayService : Service() {

  private var windowManager: WindowManager? = null
  private var bubbleView: ImageView? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_HIDE) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return START_NOT_STICKY
    }
    startForegroundBubble()
    showBubble()
    return START_STICKY
  }

  private fun startForegroundBubble() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel =
      NotificationChannel(
        "riya_overlay_channel",
        "Riya Floating Bubble",
        NotificationManager.IMPORTANCE_MIN,
      )
    nm.createNotificationChannel(channel)
    val notif =
      NotificationCompat.Builder(this, "riya_overlay_channel")
        .setContentTitle("Riya Floating Orb")
        .setContentText("Tap the floating bubble anytime to talk")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(1002, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(1002, notif)
    }
  }

  private fun showBubble() {
    if (bubbleView != null) return
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager = wm

    val sizePx = (58 * resources.displayMetrics.density).toInt()
    val view =
      ImageView(this).apply {
        setImageResource(R.mipmap.ic_launcher_round)
        background =
          GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF150B24.toInt())
            setStroke(4, 0xFFFF5C93.toInt())
          }
        setPadding(8, 8, 8, 8)
        contentDescription = "Tap to talk with Riya"
      }

    val params =
      WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 48
        y = 320
      }

    var startX = 0
    var startY = 0
    var touchX = 0f
    var touchY = 0f

    view.setOnTouchListener { _: View, event: MotionEvent ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          startX = params.x
          startY = params.y
          touchX = event.rawX
          touchY = event.rawY
          true
        }
        MotionEvent.ACTION_MOVE -> {
          params.x = startX + (event.rawX - touchX).toInt()
          params.y = startY + (event.rawY - touchY).toInt()
          runCatching { wm.updateViewLayout(view, params) }
          true
        }
        MotionEvent.ACTION_UP -> {
          val moved = abs(event.rawX - touchX) > 15 || abs(event.rawY - touchY) > 15
          if (!moved) {
            RiyaVoiceService.start(this@RiyaOverlayService, RiyaVoiceService.ACTION_TAP_TO_TALK)
          }
          true
        }
        else -> false
      }
    }

    runCatching {
      wm.addView(view, params)
      bubbleView = view
      _isShowing.value = true
    }.onFailure { Timber.e(it, "Could not add overlay bubble") }
  }

  override fun onDestroy() {
    bubbleView?.let { runCatching { windowManager?.removeView(it) } }
    bubbleView = null
    _isShowing.value = false
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val ACTION_SHOW = "com.example.action.SHOW_OVERLAY"
    const val ACTION_HIDE = "com.example.action.HIDE_OVERLAY"

    private val _isShowing = MutableStateFlow(false)
    val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

    fun toggle(context: Context, show: Boolean) {
      val intent =
        Intent(context, RiyaOverlayService::class.java)
          .setAction(if (show) ACTION_SHOW else ACTION_HIDE)
      if (show) {
        ContextCompat.startForegroundService(context, intent)
      } else {
        _isShowing.value = false
        context.startService(intent)
      }
    }
  }
}
