package com.example.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.voice.VoiceStateHolder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RiyaTileService : TileService() {

  @Inject lateinit var voiceStateHolder: VoiceStateHolder

  override fun onStartListening() {
    super.onStartListening()
    val active = voiceStateHolder.state.value.isServiceRunning
    qsTile?.apply {
      state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
      label = "Riya"
      updateTile()
    }
  }

  override fun onClick() {
    super.onClick()
    val running = voiceStateHolder.state.value.isServiceRunning
    if (running) {
      RiyaVoiceService.start(this, RiyaVoiceService.ACTION_STOP)
      qsTile?.state = Tile.STATE_INACTIVE
    } else {
      RiyaVoiceService.start(this, RiyaVoiceService.ACTION_TAP_TO_TALK)
      qsTile?.state = Tile.STATE_ACTIVE
    }
    qsTile?.updateTile()
  }
}
