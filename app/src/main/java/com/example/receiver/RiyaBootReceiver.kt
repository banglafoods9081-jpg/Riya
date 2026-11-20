package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.worker.RiyaCareWorker

class RiyaBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
      RiyaCareWorker.schedule(context)
    }
  }
}
