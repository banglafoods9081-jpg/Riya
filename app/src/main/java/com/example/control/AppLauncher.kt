package com.example.control

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledAppInfo(
  val label: String,
  val packageName: String,
)

@Singleton
class AppLauncher @Inject constructor(@ApplicationContext private val context: Context) {

  private val aliases: Map<String, List<String>> =
    mapOf(
      "youtube" to listOf("youtube", "yt", "ইউটিউব", "यूट्यूब"),
      "whatsapp" to listOf("whatsapp", "whats app", "wp", "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ", "व्हाट्सएप"),
      "spotify" to listOf("spotify", "gaan", "music", "স্পটিফাই"),
      "chrome" to listOf("chrome", "browser", "google chrome", "ক্রোম"),
      "instagram" to listOf("instagram", "insta", "ig", "ইনস্টাগ্রাম", "इंस्टाग्राम"),
      "facebook" to listOf("facebook", "fb", "ফেসবুক", "फेसबुक"),
      "telegram" to listOf("telegram", "tg", "টেলিগ্রাম", "टेलीग्राम"),
      "camera" to listOf("camera", "cam", "chobi", "ক্যামেরা", "कैमरा"),
      "gallery" to listOf("gallery", "photos", "google photos", "গ্যালারি", "गैलरी"),
      "calculator" to listOf("calculator", "calc", "hisab", "ক্যালকুলেটর"),
      "clock" to listOf("clock", "alarm", "ghori", "timer", "ঘড়ি"),
      "maps" to listOf("maps", "google maps", "map", "rasta", "ম্যাপ"),
      "play store" to listOf("play store", "playstore", "google play", "প্লে স্টোর"),
      "settings" to listOf("settings", "setting", "সেটিংস"),
      "phone" to listOf("phone", "dialer", "call", "contacts", "ফোন"),
      "messages" to listOf("messages", "sms", "messaging", "মেসেজ"),
    )

  fun getInstalledLauncherApps(): List<InstalledAppInfo> {
    val pm = context.packageManager
    val launcherIntent =
      Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    val resolved =
      pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
    return resolved
      .mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
        if (label.isEmpty()) null else InstalledAppInfo(label = label, packageName = pkg)
      }
      .distinctBy { it.packageName }
      .sortedBy { it.label.lowercase() }
  }

  fun openAppByName(spokenName: String): Result<String> {
    val query = normalizeQuery(spokenName)
    if (query.isEmpty()) return Result.failure(IllegalArgumentException("App name is empty"))

    val installed = getInstalledLauncherApps()
    val canonicalQuery = resolveAlias(query)

    // 1. Exact label match
    val exact =
      installed.firstOrNull { it.label.lowercase() == canonicalQuery || it.label.lowercase() == query }
    // 2. Starts-with / contains match
    val contains =
      exact
        ?: installed.firstOrNull {
          it.label.lowercase().contains(canonicalQuery) ||
            canonicalQuery.contains(it.label.lowercase())
        }
    // 3. Fuzzy Levenshtein match
    val bestFuzzy =
      contains
        ?: installed
          .map { app -> app to similarityScore(canonicalQuery, app.label.lowercase()) }
          .filter { it.second >= 0.58 }
          .maxByOrNull { it.second }
          ?.first

    val target =
      bestFuzzy
        ?: return Result.failure(
          NoSuchElementException("App '$spokenName' installed list-e khuje pelam na.")
        )

    val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
      ?: return Result.failure(IllegalStateException("${target.label} launch kora jacche na."))

    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
      context.startActivity(launchIntent)
      Timber.i("Launched ${target.label} (${target.packageName})")
      Result.success("Opened ${target.label}")
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun normalizeQuery(raw: String): String {
    return raw
      .lowercase()
      .replace("open", "")
      .replace("launch", "")
      .replace("kholo", "")
      .replace("khola", "")
      .replace("khol", "")
      .replace("khule", "")
      .replace("chalao", "")
      .replace("chala", "")
      .replace("koro", "")
      .replace("karo", "")
      .replace("kro", "")
      .replace("খুলে দাও", "")
      .replace("চালু করো", "")
      .replace("চালু কর", "")
      .replace("ওপেন", "")
      .replace("খোলো", "")
      .replace("খোলা", "")
      .replace("খোল", "")
      .replace("চালাও", "")
      .replace("চালা", "")
      .replace("করো", "")
      .replace("কর", "")
      .replace("দাও", "")
      .replace("অ্যাপ", "")
      .replace("app", "")
      .replace("the", "")
      .trim()
  }

  private fun resolveAlias(query: String): String {
    for ((canonical, keys) in aliases) {
      if (keys.any { query.contains(it) }) return canonical
    }
    return query
  }

  private fun similarityScore(a: String, b: String): Double {
    if (a == b) return 1.0
    val maxLen = maxOf(a.length, b.length)
    if (maxLen == 0) return 0.0
    val dist = levenshtein(a, b)
    return 1.0 - (dist.toDouble() / maxLen.toDouble())
  }

  private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    val lhsLen = lhs.length
    val rhsLen = rhs.length
    var costs = IntArray(rhsLen + 1) { it }
    for (i in 1..lhsLen) {
      val newCosts = IntArray(rhsLen + 1)
      newCosts[0] = i
      for (j in 1..rhsLen) {
        val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
        newCosts[j] = minOf(costs[j] + 1, newCosts[j - 1] + 1, costs[j - 1] + match)
      }
      costs = newCosts
    }
    return costs[rhsLen]
  }
}
