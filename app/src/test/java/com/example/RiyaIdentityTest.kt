package com.example

import com.example.core.config.RiyaIdentity
import com.example.domain.model.PreferredLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiyaIdentityTest {

  @Test
  fun `creator question triggers across English Banglish Bengali and Hindi`() {
    assertTrue(RiyaIdentity.isCreatorQuestion("Who made you?"))
    assertTrue(RiyaIdentity.isCreatorQuestion("Tomake ke banaiyeche?"))
    assertTrue(RiyaIdentity.isCreatorQuestion("Tumhe kisne banaya?"))
    assertTrue(RiyaIdentity.isCreatorQuestion("তোমাকে কে বানিয়েছে?"))
    assertFalse(RiyaIdentity.isCreatorQuestion("Play a romantic song on YouTube"))
  }

  @Test
  fun `system prompt embeds exact creator response, Rohan lifelong details, and rag rule`() {
    val prompt =
      RiyaIdentity.buildSystemPrompt(
        userName = "Rohan",
        preferredLanguage = PreferredLanguage.BANGLISH,
        personaIntensity = 0.85f,
        excessivePhoneUseDetected = true,
        heavyChattingAppDetected = "WhatsApp",
      )
    assertTrue(prompt.contains(RiyaIdentity.CREATOR_RESPONSE))
    assertTrue(prompt.contains("Rohan"))
    assertTrue(prompt.contains("Class 11"))
    assertTrue(prompt.contains("Saddam sir"))
    assertTrue(prompt.contains("Durlovepur, Eidgah More"))
    assertTrue(prompt.contains("Tisha"))
    assertTrue(prompt.contains("Ajija"))
    assertTrue(prompt.contains("single"))
    assertTrue(prompt.contains("WhatsApp"))
    assertEquals(
      "Amake baniyeche Rohan. Se Class 11-er student, ar Saddam sir-er student.",
      RiyaIdentity.CREATOR_RESPONSE,
    )
  }
}
