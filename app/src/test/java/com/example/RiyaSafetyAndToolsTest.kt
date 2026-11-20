package com.example

import com.example.core.safety.SafetyGuard
import com.example.data.remote.GrokToolDeclarations
import com.example.domain.agent.RiyaBrain
import org.junit.Assert.assertEquals
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
  fun `grok tool declarations use openai function schema and stay within 20 tools`() {
    val array = GrokToolDeclarations.buildToolsJsonArray()
    assertEquals(20, array.length())
    val json = array.toString()
    assertTrue(json.contains("\"type\":\"function\""))
    assertTrue(json.contains("open_app"))
    assertTrue(json.contains("play_youtube"))
    assertTrue(json.contains("call_contact"))
    assertTrue(json.contains("send_message"))
    assertTrue(json.contains("get_phone_status"))
    assertTrue(json.contains("describe_screen"))
    assertTrue(json.contains("remember_fact"))
  }

  @Test
  fun `provider config and live model parser route xai grok and groq keys cleanly`() {
    val grok = RiyaBrain.resolveProviderConfig("xai-example-secret-key-1234567890")
    assertFalse(grok.isGroq)
    assertEquals("https://api.x.ai/v1", grok.baseUrl)

    val groq = RiyaBrain.resolveProviderConfig("gsk_example_secret_key_1234567890")
    assertTrue(groq.isGroq)
    assertEquals("https://api.groq.com/openai/v1", groq.baseUrl)

    val modelsJson =
      """{"data":[{"id":"grok-imagine-image"},{"id":"grok-4.7"},{"id":"grok-4.20"}]}"""
    val parsed = RiyaBrain.parsePreferredChatModels(modelsJson, isGroq = false)
    assertEquals(listOf("grok-4.20", "grok-4.7"), parsed)

    val errJson = """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""
    assertEquals("Incorrect API key provided", RiyaBrain.extractGrokErrorMessage(errJson))
  }
}
