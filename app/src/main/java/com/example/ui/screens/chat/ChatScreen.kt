package com.example.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.PlumCard
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()

  LaunchedEffect(state.messages.size) {
    if (state.messages.isNotEmpty()) {
      listState.animateScrollToItem(state.messages.lastIndex)
    }
  }

  Box(
    modifier = Modifier.fillMaxSize().background(RiyaGradients.BackgroundAura),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Chat with Riya", style = MaterialTheme.typography.headlineMedium, color = RoseGoldWarm)
        IconButton(
          onClick = viewModel::clearChat,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_chat_button"),
        ) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear chat", tint = TextSecondary)
        }
      }

      if (state.messages.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = SakuraRose, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Kotha bolo jaan... ami ekhane achi", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
          }
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(state.messages, key = { it.id }) { msg ->
            val isUser = msg.role == "user"
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
              Surface(
                color = if (isUser) RoseContainer else PlumCard,
                shape =
                  RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp,
                  ),
                modifier = Modifier.widthIn(max = 310.dp),
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text(text = msg.text, style = MaterialTheme.typography.bodyLarge)
                  Spacer(modifier = Modifier.height(4.dp))
                  val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
                  Text(
                    text = if (isUser) timeStr else "${msg.mood} • $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                  )
                }
              }
            }
          }
        }
      }

      if (state.isThinking) {
        Row(
          modifier = Modifier.padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SakuraRose)
          Text("Riya likhche...", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = state.draft,
          onValueChange = viewModel::onDraftChange,
          placeholder = { Text("Type in Banglish, Bengali, Hindi, or English...") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage() }),
          trailingIcon = {
            IconButton(
              onClick = { viewModel.sendMessage() },
              modifier = Modifier.testTag("chat_send_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = SakuraRose)
            }
          },
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SakuraRose),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth().testTag("chat_input_field"),
        )
      }
    }
  }
}
