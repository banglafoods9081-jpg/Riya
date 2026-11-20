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

  @Test
  fun `gemini 404 parser extracts suggested replacement model and clean error`() {
    val body =
      """
      {
        "error": {
          "code": 404,
          "message": "This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use models/gemini-3.8-flash for this task."
        }
      }
      """.trimIndent()
    org.junit.Assert.assertEquals(
      "gemini-3.8-flash",
      com.example.domain.agent.RiyaBrain.extractSuggestedModel(body),
    )
    org.junit.Assert.assertTrue(
      com.example.domain.agent.RiyaBrain.extractGeminiErrorMessage(body)
        ?.contains("models/gemini-3.8-flash") == true
    )
  }
}
