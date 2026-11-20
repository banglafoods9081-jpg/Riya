package com.example.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.control.AccessibilityBridge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RiyaAccessibilityService : AccessibilityService() {

  @Inject lateinit var bridge: AccessibilityBridge

  override fun onServiceConnected() {
    super.onServiceConnected()
    bridge.attach(this)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val pkg = event?.packageName?.toString()
    if (!pkg.isNullOrBlank()) {
      bridge.updateCurrentPackage(pkg)
    }
  }

  override fun onInterrupt() {
    bridge.detach()
  }

  override fun onDestroy() {
    bridge.detach()
    super.onDestroy()
  }
}
