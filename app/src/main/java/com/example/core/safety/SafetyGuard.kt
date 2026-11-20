package com.example.core.safety

object SafetyGuard {

  private val sensitivePackages =
    setOf(
      "com.google.android.apps.nbu.paisa.user", // Google Pay
      "com.phonepe.app",
      "net.one97.paytm",
      "in.org.npci.upiapp", // BHIM
      "com.amazon.mShop.android.shopping",
      "com.dreamplug.androidapp", // CRED
      "com.sbi.lotusintouch",
      "com.csam.icici.bank.imobile",
      "com.snapwork.hdfc",
      "com.axis.mobile",
      "com.kotak.mobile.banking",
      "com.android.settings.password",
    )

  private val sensitiveKeywords =
    listOf(
      "upi pin",
      "enter pin",
      "mpin",
      "atm pin",
      "cvv",
      "card number",
      "netbanking",
      "net banking",
      "enter otp",
      "one time password",
      "password",
      "passcode",
      "biometric prompt",
      "confirm payment",
      "pay ₹",
      "bank account",
    )

  private val stopPhrases =
    listOf(
      "riya stop",
      "stop riya",
      "stop listening",
      "emergency stop",
      "riya chup",
      "riya bondho",
      "রিয়া থামো",
      "रिया रुक",
    )

  fun isSensitivePackage(packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    val pkg = packageName.lowercase()
    return sensitivePackages.contains(pkg) ||
      pkg.contains("bank") ||
      pkg.contains("upi") ||
      pkg.contains("wallet") ||
      pkg.contains("pay") && (pkg.contains("gpay") || pkg.contains("phonepe") || pkg.contains("paytm"))
  }

  fun containsSensitiveScreenText(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val lower = text.lowercase()
    return sensitiveKeywords.any { lower.contains(it) }
  }

  fun isEmergencyStopCommand(spokenText: String): Boolean {
    val lower = spokenText.trim().lowercase()
    return stopPhrases.any { lower.contains(it) }
  }

  fun requiresUserConfirmation(toolName: String): Boolean {
    return toolName in setOf("call_contact", "send_message")
  }
}
