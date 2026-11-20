package com.example.data.remote

import org.json.JSONArray
import org.json.JSONObject

object GeminiToolDeclarations {

  fun buildToolsJsonArray(): JSONArray {
    val declarations =
      JSONArray()
        .put(
          tool(
            name = "open_app",
            description = "Open any installed Android app by name (supports English, Bengali, or Hindi names like YouTube, WhatsApp, Chrome, Camera, Spotify, Calculator, Settings, Maps).",
            props = mapOf("app_name" to prop("string", "Spoken or common app name")),
            required = listOf("app_name"),
          )
        )
        .put(
          tool(
            name = "play_youtube",
            description = "Open YouTube, search for a song or video, and automatically tap and play the first result when Accessibility is enabled.",
            props = mapOf("query" to prop("string", "Song, artist, or video query")),
            required = listOf("query"),
          )
        )
        .put(
          tool(
            name = "media_control",
            description = "Control media playback or volume: play, pause, play_pause, next, previous, stop, volume_up, volume_down, mute, unmute, fullscreen, like, subscribe.",
            props = mapOf("action" to prop("string", "One of play, pause, play_pause, next, previous, stop, volume_up, volume_down, mute, unmute, fullscreen, like, subscribe")),
            required = listOf("action"),
          )
        )
        .put(
          tool(
            name = "tap",
            description = "Tap an element on the current screen by visible text/ID or screen coordinates.",
            props =
              mapOf(
                "target" to prop("string", "Visible button text, label, or view id"),
                "x" to prop("number", "Optional X coordinate in pixels"),
                "y" to prop("number", "Optional Y coordinate in pixels"),
              ),
          )
        )
        .put(
          tool(
            name = "type_text",
            description = "Type text into the currently focused or first editable input field on screen.",
            props = mapOf("text" to prop("string", "Text to type")),
            required = listOf("text"),
          )
        )
        .put(
          tool(
            name = "scroll",
            description = "Scroll the current screen up, down, left, or right.",
            props = mapOf("direction" to prop("string", "up, down, left, or right")),
            required = listOf("direction"),
          )
        )
        .put(
          tool(
            name = "swipe",
            description = "Swipe across the screen using pixel coordinates.",
            props =
              mapOf(
                "start_x" to prop("number", "Start X pixel"),
                "start_y" to prop("number", "Start Y pixel"),
                "end_x" to prop("number", "End X pixel"),
                "end_y" to prop("number", "End Y pixel"),
              ),
            required = listOf("start_x", "start_y", "end_x", "end_y"),
          )
        )
        .put(
          tool(
            name = "press_global",
            description = "Perform a global Android navigation action: back, home, recents, notifications, quick_settings, lock_screen, screenshot.",
            props = mapOf("action" to prop("string", "back, home, recents, notifications, quick_settings, lock_screen, or screenshot")),
            required = listOf("action"),
          )
        )
        .put(
          tool(
            name = "read_screen",
            description = "Read the text and clickable elements currently visible on screen (blocked on banking/UPI/PIN/OTP screens).",
            props = emptyMap(),
          )
        )
        .put(
          tool(
            name = "describe_screen",
            description = "Inspect what is currently on screen using Accessibility tree and optional screenshot vision.",
            props = emptyMap(),
          )
        )
        .put(
          tool(
            name = "take_screenshot",
            description = "Take a system screenshot using Accessibility global action.",
            props = emptyMap(),
          )
        )
        .put(
          tool(
            name = "call_contact",
            description = "Prepare a phone call to a contact name or phone number. Always asks user confirmation before dialing.",
            props = mapOf("contact" to prop("string", "Contact name or phone number")),
            required = listOf("contact"),
          )
        )
        .put(
          tool(
            name = "send_message",
            description = "Prepare an SMS or WhatsApp message to a recipient. Always asks user confirmation before sending.",
            props =
              mapOf(
                "channel" to prop("string", "sms or whatsapp"),
                "recipient" to prop("string", "Contact name or phone number"),
                "message" to prop("string", "Message text to send"),
              ),
            required = listOf("channel", "recipient", "message"),
          )
        )
        .put(
          tool(
            name = "set_alarm",
            description = "Set an alarm on the Clock app.",
            props =
              mapOf(
                "hour" to prop("integer", "24-hour clock hour (0-23)"),
                "minute" to prop("integer", "Minute (0-59)"),
                "label" to prop("string", "Optional alarm label"),
              ),
            required = listOf("hour", "minute"),
          )
        )
        .put(
          tool(
            name = "set_timer",
            description = "Start a countdown timer.",
            props =
              mapOf(
                "seconds" to prop("integer", "Duration in seconds"),
                "label" to prop("string", "Optional timer label"),
              ),
            required = listOf("seconds"),
          )
        )
        .put(
          tool(
            name = "toggle_setting",
            description = "Control device settings: flashlight_on, flashlight_off, flashlight_toggle, wifi, bluetooth, dnd, brightness, sound, battery, camera, maps, settings.",
            props =
              mapOf(
                "setting" to prop("string", "Setting name"),
                "destination" to prop("string", "Optional destination if setting is maps"),
              ),
            required = listOf("setting"),
          )
        )
        .put(
          tool(
            name = "search_web",
            description = "Search Google in the browser.",
            props = mapOf("query" to prop("string", "Search query")),
            required = listOf("query"),
          )
        )
        .put(
          tool(
            name = "get_phone_status",
            description = "Analyze phone battery, temperature, RAM, storage, network, screen-time, heavy chat apps, and health tips.",
            props = emptyMap(),
          )
        )
        .put(
          tool(
            name = "read_notifications",
            description = "Read recent unread notifications captured by Riya's Notification Listener.",
            props = emptyMap(),
          )
        )
        .put(
          tool(
            name = "remember_fact",
            description = "Save a new personal fact, preference, or reminder about Rohan permanently in Riya's Room memory.",
            props =
              mapOf(
                "category" to prop("string", "Category such as Likes, Study, Routine, Mood, People"),
                "fact" to prop("string", "Fact to remember"),
              ),
            required = listOf("category", "fact"),
          )
        )

    return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
  }

  private fun tool(
    name: String,
    description: String,
    props: Map<String, JSONObject>,
    required: List<String> = emptyList(),
  ): JSONObject {
    val propertiesJson = JSONObject()
    props.forEach { (k, v) -> propertiesJson.put(k, v) }
    val schema =
      JSONObject()
        .put("type", "object")
        .put("properties", propertiesJson)
    if (required.isNotEmpty()) {
      schema.put("required", JSONArray(required))
    }
    return JSONObject()
      .put("name", name)
      .put("description", description)
      .put("parameters", schema)
  }

  private fun prop(type: String, description: String): JSONObject {
    return JSONObject().put("type", type).put("description", description)
  }
}
