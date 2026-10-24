package com.kanhaji.singularity.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.shell.state.rememberCollapsingTopBarState
import com.kanhaji.core.shell.ui.ScreenScaffold
import com.kanhaji.core.shell.ui.SmoothCollapsingTopBar
import com.kanhaji.core.shell.ui.rememberCollapsingTopBarConnection
import com.kanhaji.singularity.feature.auth.components.*
import com.kanhaji.singularity.feature.auth.data.AuthSettingsManager
import com.kanhaji.singularity.feature.home.components.FeatureSectionHeader

@Composable
fun AuthSettingsScreen() {
    val navigator = LocalNavigator.current
    val scrollState = rememberScrollState()
    val collapsingState = rememberCollapsingTopBarState()
    val collapsingConnection = rememberCollapsingTopBarConnection(
        state = collapsingState,
        canExpand = { scrollState.value == 0 }
    )

    val settings by AuthSettingsManager.settings.collectAsState()
    val logs by AuthSettingsManager.logs.collectAsState()

    ScreenScaffold(
        modifier = Modifier.nestedScroll(collapsingConnection),
        containerColor = Color.Transparent,
        topBar = {
            SmoothCollapsingTopBar(
                title = "Authentication",
                showBack = true,
                onBack = { navigator.pop() },
                actions = {},
                state = collapsingState
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 32.dp
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AuthSnoozeCard(
                isSnoozed = settings.isSnoozed,
                snoozeUntilEpochMs = settings.snoozeUntilEpochMs,
                selectedDurationMinutes = settings.snoozeDurationMinutes,
                onSetSnooze = { AuthSettingsManager.setSnooze(it) }
            )

            FeatureSectionHeader("Action Policies")

            AuthModeSelectorCard(
                title = "Screen Unlock",
                subtitle = "DMS & hyprlock screen unlock authorization",
                selectedMode = settings.unlockMode,
                onModeSelected = { AuthSettingsManager.updateSettings(settings.copy(unlockMode = it)) }
            )

            AuthModeSelectorCard(
                title = "Sudo Elevation",
                subtitle = "Terminal sudo & administrative commands",
                selectedMode = settings.sudoMode,
                onModeSelected = { AuthSettingsManager.updateSettings(settings.copy(sudoMode = it)) }
            )

            AuthModeSelectorCard(
                title = "Desktop Login",
                subtitle = "greetd & cold-boot login authorization",
                selectedMode = settings.loginMode,
                onModeSelected = { AuthSettingsManager.updateSettings(settings.copy(loginMode = it)) }
            )

            FeatureSectionHeader("Timeouts")

            AuthTimeoutsCard(
                promptTimeoutSecs = settings.promptTimeoutSecs,
                notifyTimeoutSecs = settings.notifyTimeoutSecs,
                onPromptTimeoutChange = { AuthSettingsManager.updateSettings(settings.copy(promptTimeoutSecs = it)) },
                onNotifyTimeoutChange = { AuthSettingsManager.updateSettings(settings.copy(notifyTimeoutSecs = it)) }
            )

            FeatureSectionHeader("Recent Requests")

            AuthLogSection(logs = logs)
        }
    }
}
