package com.example.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.safety.SafetyGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityBridge @Inject constructor() {

  @Volatile private var service: AccessibilityService? = null

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  private val _currentPackage = MutableStateFlow<String?>(null)
  val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

  fun attach(accessibilityService: AccessibilityService) {
    service = accessibilityService
    _isConnected.value = true
    Timber.i("RiyaAccessibilityService connected")
  }

  fun detach() {
    service = null
    _isConnected.value = false
    Timber.i("RiyaAccessibilityService disconnected")
  }

  fun updateCurrentPackage(packageName: String?) {
    _currentPackage.value = packageName
  }

  private fun checkSafeScreen(root: AccessibilityNodeInfo?): Boolean {
    val pkg = root?.packageName?.toString() ?: _currentPackage.value
    if (SafetyGuard.isSensitivePackage(pkg)) return false
    val snippet = collectNodeText(root, maxNodes = 40)
    return !SafetyGuard.containsSensitiveScreenText(snippet)
  }

  fun readScreenSummary(): Result<String> {
    val svc = service ?: return Result.failure(IllegalStateException("Accessibility Service is not enabled."))
    val root = svc.rootInActiveWindow ?: return Result.failure(IllegalStateException("No active window available."))
    if (!checkSafeScreen(root)) {
      return Result.failure(
        SecurityException("Protected banking/UPI/PIN/OTP screen detected. Riya never reads or automates these screens.")
      )
    }
    val lines = mutableListOf<String>()
    traverseForSummary(root, lines, depth = 0)
    val pkg = root.packageName?.toString().orEmpty()
    val header = "Active app package: $pkg"
    return Result.success("$header\n" + lines.take(80).joinToString("\n"))
  }

  suspend fun clickByTextOrId(query: String): Result<String> =
    withContext(Dispatchers.Main) {
      val svc = service ?: return@withContext Result.failure(IllegalStateException("Accessibility Service is not enabled."))
      val root = svc.rootInActiveWindow ?: return@withContext Result.failure(IllegalStateException("No active window."))
      if (!checkSafeScreen(root)) {
        return@withContext Result.failure(SecurityException("Blocked on protected banking/PIN/OTP screen."))
      }

      val target =
        findNodeById(root, query)
          ?: findNodeByText(root, query)
          ?: return@withContext Result.failure(NoSuchElementException("Could not find '$query' on screen."))

      val clicked = clickNodeOrParent(target)
      if (clicked) {
        Result.success("Tapped '$query'")
      } else {
        val rect = Rect()
        target.getBoundsInScreen(rect)
        if (!rect.isEmpty) {
          tapCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
        } else {
          Result.failure(IllegalStateException("Found '$query' but it is not clickable."))
        }
      }
    }

  suspend fun tapCoordinates(x: Float, y: Float): Result<String> =
    withContext(Dispatchers.Main) {
      val svc = service ?: return@withContext Result.failure(IllegalStateException("Accessibility Service is not enabled."))
      val root = svc.rootInActiveWindow
      if (root != null && !checkSafeScreen(root)) {
        return@withContext Result.failure(SecurityException("Blocked on protected banking/PIN/OTP screen."))
      }
      val path = Path().apply { moveTo(x, y) }
      val gesture =
        GestureDescription.Builder()
          .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
          .build()
      dispatchGestureSuspend(svc, gesture).map { "Tapped at (${x.toInt()}, ${y.toInt()})" }
    }

  suspend fun longPressCoordinates(x: Float, y: Float): Result<String> =
    withContext(Dispatchers.Main) {
      val svc = service ?: return@withContext Result.failure(IllegalStateException("Accessibility Service is not enabled."))
      val path = Path().apply { moveTo(x, y) }
      val gesture =
        GestureDescription.Builder()
          .addStroke(GestureDescription.StrokeDescription(path, 0L, 700L))
          .build()
      dispatchGestureSuspend(svc, gesture).map { "Long-pressed at (${x.toInt()}, ${y.toInt()})" }
    }

  suspend fun typeText(text: String): Result<String> =
    withContext(Dispatchers.Main) {
      val svc = service ?: return@withContext Result.failure(IllegalStateException("Accessibility Service is not enabled."))
      val root = svc.rootInActiveWindow ?: return@withContext Result.failure(IllegalStateException("No active window."))
      if (!checkSafeScreen(root)) {
        return@withContext Result.failure(SecurityException("Blocked typing on protected banking/PIN/password screen."))
      }
      val editable =
        findFocusedOrFirstEditable(root)
          ?: return@withContext Result.failure(NoSuchElementException("No editable text field found on screen."))
      if (editable.isPassword) {
        return@withContext Result.failure(SecurityException("Password fields are protected and never automated."))
      }
      editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
      val args = Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
      }
      val ok = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
      if (ok) Result.success("Typed text")
      else Result.failure(IllegalStateException("Failed to type text into the field."))
    }

  suspend fun scroll(direction: String): Result<String> =
    withContext(Dispatchers.Main) {
      val svc = service ?: return@withContext Result.failure(IllegalStateException("Accessibility Service is not enabled."))
      val metrics = svc.resources.displayMetrics
      val cx = metrics.widthPixels / 2f
      val cy = metrics.heightPixels / 2f
      val (sx, sy, ex, ey) =
        when (direction.lowercase()) {
          "up" -> listOf(cx, metrics.heightPixels * 0.3f, cx, metrics.heightPixels * 0.78f)
          "left" -> listOf(metrics.widthPixels * 0.8f, cy, metrics.widthPixels * 0.2f, cy)
          "right" -> listOf(metrics.widthPixels * 0.2f, cy, metrics.widthPixels * 0.8f, cy)
          else -> listOf(cx, metrics.heightPixels * 0.76f, cx, metrics.heightPixels * 0.26f) // down
        }
      swipe(sx, sy, ex, ey, durationMs = 340L).map { "Scrolled $direction" }
    }

  suspend fun swipe(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    durationMs: Long = 320L,
  ): Result<String> =
    withContext(Dispatchers.Main) {
      val svc = service ?: return@withContext Result.failure(IllegalStateException("Accessibility Service is not enabled."))
      val root = svc.rootInActiveWindow
      if (root != null && !checkSafeScreen(root)) {
        return@withContext Result.failure(SecurityException("Blocked on protected screen."))
      }
      val path = Path().apply {
        moveTo(startX, startY)
        lineTo(endX, endY)
      }
      val gesture =
        GestureDescription.Builder()
          .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
          .build()
      dispatchGestureSuspend(svc, gesture).map { "Swiped" }
    }

  fun performGlobal(action: String): Result<String> {
    val svc = service ?: return Result.failure(IllegalStateException("Accessibility Service is not enabled."))
    val code =
      when (action.lowercase()) {
        "back" -> AccessibilityService.GLOBAL_ACTION_BACK
        "home" -> AccessibilityService.GLOBAL_ACTION_HOME
        "recents", "recent" -> AccessibilityService.GLOBAL_ACTION_RECENTS
        "notifications", "notification" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        "quick_settings", "quick settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        "lock", "lock_screen" ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
          } else {
            return Result.failure(UnsupportedOperationException("Lock screen requires Android 9+."))
          }
        "screenshot" ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
          } else {
            return Result.failure(UnsupportedOperationException("Screenshot action requires Android 9+."))
          }
        else -> return Result.failure(IllegalArgumentException("Unknown global action: $action"))
      }
    val ok = svc.performGlobalAction(code)
    return if (ok) Result.success("Performed $action")
    else Result.failure(IllegalStateException("System rejected global action $action"))
  }

  suspend fun takeScreenshotBase64(): Result<String> {
    val svc = service ?: return Result.failure(IllegalStateException("Accessibility Service is not enabled."))
    val root = svc.rootInActiveWindow
    if (root != null && !checkSafeScreen(root)) {
      return Result.failure(SecurityException("Screenshots are blocked on banking/UPI/PIN/OTP screens."))
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return Result.failure(UnsupportedOperationException("Screen image capture requires Android 11+."))
    }
    val deferred = CompletableDeferred<Result<String>>()
    svc.takeScreenshot(
      Display.DEFAULT_DISPLAY,
      svc.mainExecutor,
      object : AccessibilityService.TakeScreenshotCallback {
        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
          try {
            val hardwareBitmap =
              Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
            val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
            screenshot.hardwareBuffer.close()
            if (softwareBitmap == null) {
              deferred.complete(Result.failure(IllegalStateException("Failed to decode screenshot.")))
              return
            }
            val scaled = Bitmap.createScaledBitmap(softwareBitmap, 720, (720f * softwareBitmap.height / softwareBitmap.width).toInt().coerceAtLeast(1), true)
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            softwareBitmap.recycle()
            scaled.recycle()
            deferred.complete(Result.success(base64))
          } catch (e: Exception) {
            deferred.complete(Result.failure(e))
          }
        }

        override fun onFailure(errorCode: Int) {
          deferred.complete(
            Result.failure(IllegalStateException("Screenshot failed (DRM or system block, code $errorCode)."))
          )
        }
      },
    )
    return withTimeoutOrNull(4500L) { deferred.await() }
      ?: Result.failure(IllegalStateException("Screenshot timed out."))
  }

  suspend fun automateYouTubeFirstResult(query: String): Result<String> {
    val svc = service ?: return Result.failure(IllegalStateException("Accessibility not active"))
    // Wait briefly for YouTube search results to settle
    delay(1600L)
    // Attempt 1: If search bar isn't filled, try clicking Search button and typing
    val root1 = svc.rootInActiveWindow
    if (root1 != null) {
      val searchBtn = findNodeByText(root1, "Search") ?: findNodeById(root1, "menu_search")
      if (searchBtn != null && findFocusedOrFirstEditable(root1) == null) {
        clickNodeOrParent(searchBtn)
        delay(600L)
        typeText(query)
        delay(500L)
        // Tap top suggestion or keyboard search area near bottom-right
        val metrics = svc.resources.displayMetrics
        tapCoordinates(metrics.widthPixels * 0.9f, metrics.heightPixels * 0.92f)
        delay(1600L)
      }
    }

    // Attempt 2: Tap first video card in YouTube results (usually middle-upper feed below search bar)
    repeat(3) {
      val root = svc.rootInActiveWindow
      if (root != null) {
        val candidate = findFirstYouTubeVideoNode(root)
        if (candidate != null && clickNodeOrParent(candidate)) {
          return Result.success("Playing '$query' on YouTube")
        }
      }
      delay(800L)
    }
    return Result.success("Opened YouTube search for '$query'")
  }

  private suspend fun dispatchGestureSuspend(
    svc: AccessibilityService,
    gesture: GestureDescription,
  ): Result<Unit> {
    val deferred = CompletableDeferred<Result<Unit>>()
    val dispatched =
      svc.dispatchGesture(
        gesture,
        object : AccessibilityService.GestureResultCallback() {
          override fun onCompleted(gestureDescription: GestureDescription?) {
            deferred.complete(Result.success(Unit))
          }

          override fun onCancelled(gestureDescription: GestureDescription?) {
            deferred.complete(Result.failure(IllegalStateException("Gesture was cancelled.")))
          }
        },
        null,
      )
    if (!dispatched) return Result.failure(IllegalStateException("Failed to dispatch gesture."))
    return withTimeoutOrNull(2500L) { deferred.await() }
      ?: Result.failure(IllegalStateException("Gesture timed out."))
  }

  private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
    var current: AccessibilityNodeInfo? = node
    var depth = 0
    while (current != null && depth < 6) {
      if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        return true
      }
      current = current.parent
      depth++
    }
    return false
  }

  private fun findNodeByText(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
    val q = query.trim().lowercase()
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    var bestPartial: AccessibilityNodeInfo? = null
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val text = node.text?.toString()?.trim()?.lowercase().orEmpty()
      val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
      if (text == q || desc == q) return node
      if (bestPartial == null && ((text.isNotEmpty() && text.contains(q)) || (desc.isNotEmpty() && desc.contains(q)))) {
        bestPartial = node
      }
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { queue.add(it) }
      }
    }
    return bestPartial
  }

  private fun findNodeById(root: AccessibilityNodeInfo, idQuery: String): AccessibilityNodeInfo? {
    val q = idQuery.trim().lowercase()
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val viewId = node.viewIdResourceName?.lowercase().orEmpty()
      if (viewId.isNotEmpty() && (viewId == q || viewId.endsWith(":id/$q"))) {
        return node
      }
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { queue.add(it) }
      }
    }
    return null
  }

  private fun findFocusedOrFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (it.isEditable) return it }
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      if (node.isEditable && node.isVisibleToUser) return node
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { queue.add(it) }
      }
    }
    return null
  }

  private fun findFirstYouTubeVideoNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
      val rect = Rect()
      node.getBoundsInScreen(rect)
      // Video cards typically have long descriptive accessibility labels (title + channel + views) and reside below the top bar
      if (node.isClickable && desc.length > 24 && rect.top > 240 && !desc.contains("navigate up") && !desc.contains("search")) {
        return node
      }
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { queue.add(it) }
      }
    }
    return null
  }

  private fun collectNodeText(root: AccessibilityNodeInfo?, maxNodes: Int): String {
    if (root == null) return ""
    val sb = StringBuilder()
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    var count = 0
    while (queue.isNotEmpty() && count < maxNodes) {
      val node = queue.removeFirst()
      count++
      if (!node.isPassword) {
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
      }
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { queue.add(it) }
      }
    }
    return sb.toString()
  }

  private fun traverseForSummary(
    node: AccessibilityNodeInfo,
    out: MutableList<String>,
    depth: Int,
  ) {
    if (out.size >= 90 || depth > 18) return
    if (!node.isPassword && node.isVisibleToUser) {
      val text = node.text?.toString()?.trim().orEmpty()
      val desc = node.contentDescription?.toString()?.trim().orEmpty()
      val label = text.ifEmpty { desc }
      if (label.isNotEmpty()) {
        val tag = if (node.isClickable) "[Tap] " else ""
        out.add("$tag$label")
      }
    }
    for (i in 0 until node.childCount) {
      node.getChild(i)?.let { traverseForSummary(it, out, depth + 1) }
    }
  }
}
