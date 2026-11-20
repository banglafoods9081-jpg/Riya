package com.example

import com.example.core.safety.SafetyGuard
import com.example.data.remote.GeminiToolDeclarations
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class RiyaSafetyAndToolsTest {

  @Test
  fun `safety guard blocks banking packages and sensitive text`() {
    assertTrue(SafetyGuard.isSensitivePackage("com.google.android.apps.nbu.paisa.user"))
    assertTrue(SafetyGuard.isSensitivePackage("com.phonepe.app"))
    assertFalse(SafetyGuard.isSensitivePackage("com.google.android.youtube"))

    assertTrue(SafetyGuard.containsSensitiveScreenText("Please enter your UPI PIN to continue"))
    assertTrue(SafetyGuard.containsSensitiveScreenText("Enter OTP sent to mobile"))
    assertFalse(SafetyGuard.containsSensitiveScreenText("Search romantic Bengali songs"))

    assertTrue(SafetyGuard.isEmergencyStopCommand("Riya stop now"))
    assertTrue(SafetyGuard.requiresUserConfirmation("call_contact"))
    assertTrue(SafetyGuard.requiresUserConfirmation("send_message"))
    assertFalse(SafetyGuard.requiresUserConfirmation("open_app"))
  }

  @Test
  fun `gemini tool declarations include core phone control tools`() {
    val json = GeminiToolDeclarations.buildToolsJsonArray().toString()
    assertTrue(json.contains("open_app"))
    assertTrue(json.contains("play_youtube"))
    assertTrue(json.contains("call_contact"))
    assertTrue(json.contains("send_message"))
    assertTrue(json.contains("get_phone_status"))
    assertTrue(json.contains("describe_screen"))
    assertTrue(json.contains("remember_fact"))
  }
}
