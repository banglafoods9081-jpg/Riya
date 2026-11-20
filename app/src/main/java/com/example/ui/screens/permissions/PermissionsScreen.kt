package com.example.ui.screens.permissions

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.permissions.PermissionKind
import com.example.core.permissions.PermissionStatusItem
import com.example.core.permissions.RiyaPermission
import com.example.service.RiyaOverlayService
import com.example.ui.components.RiyaGlassCard
import com.example.ui.theme.AmethystGlow
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.PlumSurfaceElevated
import com.example.ui.theme.RiyaGradients
import com.example.ui.theme.RoseGoldWarm
import com.example.ui.theme.SakuraRose
import com.example.ui.theme.SuccessMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
  isOnboardingFlow: Boolean,
  onBack: (() -> Unit)?,
  onDone: () -> Unit,
  viewModel: PermissionsViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

  val runtimePermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
      viewModel.refreshPermissions()
      val allGranted = results.values.all { it }
      if (allGranted && !state.isChecklistMode) {
        if (state.currentStepIndex < state.items.lastIndex) {
          viewModel.nextStep()
        }
      } else if (!allGranted) {
        scope.launch {
          snackbarHostState.showSnackbar(
            "Permission denied. You can also enable it anytime from App Info."
          )
        }
      }
    }

  val launchPermissionRequest: (RiyaPermission) -> Unit = { permission ->
    if (RiyaOverlayService.isShowing.value) {
      RiyaOverlayService.toggle(context, false)
    }
    if (
      permission.kind == PermissionKind.RUNTIME && permission.runtimePermissions.isNotEmpty()
    ) {
      runtimePermissionLauncher.launch(permission.runtimePermissions.toTypedArray())
    } else {
      val intent = viewModel.createSettingsIntent(permission)
      runCatching { context.startActivity(intent) }
        .onFailure {
          scope.launch {
            snackbarHostState.showSnackbar("Could not open Settings screen automatically.")
          }
        }
    }
  }

  val openAppInfo: () -> Unit = {
    val intent: Intent = viewModel.createAppInfoIntent()
    runCatching { context.startActivity(intent) }
      .onFailure {
        scope.launch { snackbarHostState.showSnackbar("Unable to open App Info.") }
      }
  }

  Scaffold(
    containerColor = ObsidianNight,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          if (onBack != null) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("permissions_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      )
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(RiyaGradients.BackgroundAura)
          .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
      ) {
        Text(
          text = stringResource(R.string.permissions_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Bar + Mode Toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text =
              stringResource(
                R.string.permissions_progress_format,
                state.grantedCount,
                state.totalCount,
              ),
            style = MaterialTheme.typography.labelLarge,
            color = RoseGoldWarm,
          )

          SingleChoiceSegmentedButtonRow {
            SegmentedButton(
              selected = !state.isChecklistMode,
              onClick = { viewModel.setChecklistMode(false) },
              shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
              modifier = Modifier.testTag("mode_wizard_button"),
            ) {
              Text(stringResource(R.string.permissions_wizard_mode))
            }
            SegmentedButton(
              selected = state.isChecklistMode,
              onClick = { viewModel.setChecklistMode(true) },
              shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
              modifier = Modifier.testTag("mode_checklist_button"),
            ) {
              Text(stringResource(R.string.permissions_checklist_mode))
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val progress =
          if (state.totalCount == 0) 0f else state.grantedCount.toFloat() / state.totalCount.toFloat()
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
          color = SakuraRose,
          trackColor = PlumSurfaceElevated,
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (state.isChecklistMode) {
          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            itemsIndexed(state.items, key = { _, item -> item.permission.name }) { index, item ->
              PermissionChecklistRow(
                item = item,
                onGrantClick = { launchPermissionRequest(item.permission) },
                onCardClick = { viewModel.selectStep(index) },
              )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
          }
        } else {
          val current = state.currentItem
          Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            if (current != null) {
              AnimatedContent(targetState = current, label = "permission_step") { item ->
                PermissionWizardCard(
                  stepNumber = state.currentStepIndex + 1,
                  totalSteps = state.totalCount,
                  item = item,
                  onGrantClick = { launchPermissionRequest(item.permission) },
                )
              }

              if (itemNeedsRestrictedHint(current)) {
                RiyaGlassCard {
                  Row(verticalAlignment = Alignment.Top) {
                    Icon(
                      imageVector = Icons.Rounded.Info,
                      contentDescription = null,
                      tint = WarningAmber,
                      modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = stringResource(R.string.permissions_restricted_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                      )
                      Spacer(modifier = Modifier.height(10.dp))
                      OutlinedButton(
                        onClick = openAppInfo,
                        modifier = Modifier.testTag("open_app_info_button"),
                      ) {
                        Text(stringResource(R.string.permissions_open_app_info))
                      }
                    }
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              OutlinedButton(
                onClick = viewModel::previousStep,
                enabled = state.currentStepIndex > 0,
                modifier = Modifier.weight(1f).height(50.dp).testTag("permission_prev_button"),
              ) {
                Text(stringResource(R.string.permissions_previous))
              }
              FilledTonalButton(
                onClick = {
                  if (state.currentStepIndex < state.items.lastIndex) {
                    viewModel.nextStep()
                  } else {
                    onDone()
                  }
                },
                modifier = Modifier.weight(1f).height(50.dp).testTag("permission_next_button"),
              ) {
                Text(
                  if (state.currentStepIndex < state.items.lastIndex) {
                    stringResource(R.string.permissions_skip)
                  } else {
                    stringResource(R.string.permissions_next)
                  }
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
          onClick = onDone,
          shape = MaterialTheme.shapes.large,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = SakuraRose,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier =
            Modifier
              .fillMaxWidth()
              .height(54.dp)
              .padding(bottom = 8.dp)
              .testTag("permissions_done_button"),
        ) {
          Text(
            text =
              if (isOnboardingFlow) {
                stringResource(R.string.permissions_done)
              } else {
                "Done"
              },
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))
      }
    }
  }
}

private fun itemNeedsRestrictedHint(item: PermissionStatusItem): Boolean {
  return !item.isGranted &&
    (item.permission == RiyaPermission.ACCESSIBILITY ||
      item.permission == RiyaPermission.NOTIFICATION_LISTENER)
}

@Composable
private fun PermissionWizardCard(
  stepNumber: Int,
  totalSteps: Int,
  item: PermissionStatusItem,
  onGrantClick: () -> Unit,
) {
  RiyaGlassCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "STEP $stepNumber OF $totalSteps",
        style = MaterialTheme.typography.labelMedium,
        color = AmethystGlow,
      )
      AssistChip(
        onClick = {},
        label = {
          Text(
            text = if (item.permission.isEssential) "Essential" else "Optional",
            style = MaterialTheme.typography.labelSmall,
          )
        },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Box(
      modifier =
        Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(
            if (item.isGranted) SuccessMint.copy(alpha = 0.16f)
            else SakuraRose.copy(alpha = 0.16f)
          ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = permissionIcon(item.permission),
        contentDescription = item.permission.title,
        tint = if (item.isGranted) SuccessMint else SakuraRose,
        modifier = Modifier.size(32.dp),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = item.permission.title,
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = "\"${item.permission.bengaliHint}\"",
      style = MaterialTheme.typography.titleMedium,
      color = RoseGoldWarm,
    )

    Spacer(modifier = Modifier.height(10.dp))

    Text(
      text = item.permission.description,
      style = MaterialTheme.typography.bodyMedium,
      color = TextSecondary,
    )

    Spacer(modifier = Modifier.height(22.dp))

    if (item.isGranted) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(
          imageVector = Icons.Rounded.CheckCircle,
          contentDescription = "Granted",
          tint = SuccessMint,
          modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.permissions_granted),
          style = MaterialTheme.typography.titleMedium,
          color = SuccessMint,
        )
      }
    } else {
      Button(
        onClick = onGrantClick,
        shape = MaterialTheme.shapes.medium,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = AmethystGlow,
            contentColor = MaterialTheme.colorScheme.onSecondary,
          ),
        modifier =
          Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("grant_permission_${item.permission.name.lowercase()}"),
      ) {
        Text(
          text = stringResource(R.string.permissions_grant),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun PermissionChecklistRow(
  item: PermissionStatusItem,
  onGrantClick: () -> Unit,
  onCardClick: () -> Unit,
) {
  RiyaGlassCard(onClick = onCardClick) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = permissionIcon(item.permission),
        contentDescription = item.permission.title,
        tint = if (item.isGranted) SuccessMint else SakuraRose,
        modifier = Modifier.size(26.dp),
      )
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.permission.title,
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = item.permission.bengaliHint,
          style = MaterialTheme.typography.bodyMedium,
          color = TextMuted,
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      if (item.isGranted) {
        Icon(
          imageVector = Icons.Rounded.CheckCircle,
          contentDescription = "Granted",
          tint = SuccessMint,
          modifier = Modifier.size(24.dp),
        )
      } else {
        FilledTonalButton(
          onClick = onGrantClick,
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .testTag("checklist_grant_${item.permission.name.lowercase()}"),
        ) {
          Text("Enable")
        }
      }
    }
  }
}

private fun permissionIcon(permission: RiyaPermission): ImageVector =
  when (permission) {
    RiyaPermission.MICROPHONE -> Icons.Rounded.Mic
    RiyaPermission.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
    RiyaPermission.BATTERY_OPTIMIZATION -> Icons.Rounded.BatteryChargingFull
    RiyaPermission.ACCESSIBILITY -> Icons.Rounded.AccessibilityNew
    RiyaPermission.OVERLAY -> Icons.Rounded.Layers
    RiyaPermission.USAGE_ACCESS -> Icons.Rounded.QueryStats
    RiyaPermission.NOTIFICATION_LISTENER -> Icons.Rounded.NotificationsPaused
    RiyaPermission.CONTACTS -> Icons.Rounded.Contacts
    RiyaPermission.PHONE -> Icons.Rounded.Call
    RiyaPermission.SMS -> Icons.Rounded.Sms
    RiyaPermission.CALENDAR -> Icons.Rounded.CalendarMonth
    RiyaPermission.EXACT_ALARM -> Icons.Rounded.Alarm
  }
