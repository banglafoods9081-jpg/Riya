package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class RiyaAppWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    for (widgetId in appWidgetIds) {
      val launchIntent =
        Intent(context, MainActivity::class.java).apply {
          putExtra(EXTRA_START_TALK, true)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
      val pendingIntent =
        PendingIntent.getActivity(
          context,
          widgetId,
          launchIntent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

      val views = RemoteViews(context.packageName, R.layout.widget_riya)
      views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
      views.setOnClickPendingIntent(R.id.widget_talk_button, pendingIntent)
      appWidgetManager.updateAppWidget(widgetId, views)
    }
  }

  companion object {
    const val EXTRA_START_TALK = "extra_start_talk"
  }
}
