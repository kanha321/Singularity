package com.kanhaji.singularity.feature.power

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.shell.state.rememberCollapsingTopBarState
import com.kanhaji.core.shell.ui.ScreenScaffold
import com.kanhaji.core.shell.ui.SmoothCollapsingTopBar
import com.kanhaji.core.shell.ui.rememberCollapsingTopBarConnection
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import com.kanhaji.singularity.feature.home.components.FeatureSectionHeader
import com.kanhaji.singularity.feature.power.components.PowerGridCard
import com.kanhaji.singularity.feature.power.model.PowerAction
import com.kanhaji.singularity.service.AppBridgeService
import com.kanhaji.singularity.ui.components.AppSnackbar

@Composable
fun PowerControlsScreen() {
    val isPcConnected by AppBridgeService.isPcConnected.collectAsState()
    val isLoggedIn by AppBridgeService.pcLoginState.collectAsState()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val gridState = rememberLazyGridState()
    val collapsingState = rememberCollapsingTopBarState()
    val collapsingConnection = rememberCollapsingTopBarConnection(
        state = collapsingState,
        canExpand = { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
    )

    ScreenScaffold(
        modifier = Modifier.nestedScroll(collapsingConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            SmoothCollapsingTopBar(
                title = "Power Controls",
                showBack = true,
                onBack = { navigator.pop() },
                actions = {},
                state = collapsingState
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                FeatureSectionHeader("Hardware Actions")
            }

            items(
                items = PowerAction.actionsFor(isLoggedIn),
                key = { it.actionId },
                span = { action ->
                    if (action == PowerAction.LOGOUT) GridItemSpan(2) else GridItemSpan(1)
                }
            ) { action ->
                PowerGridCard(
                    action = action,
                    isEnabled = isPcConnected,
                    onClick = {
                        if (!isPcConnected) {
                            AppSnackbar.show("PC is currently offline")
                            return@PowerGridCard
                        }
                        val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                            putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Power")
                            putExtra(BiometricAuthActivity.EXTRA_REASON, "Confirm ${action.title} on Laptop")
                            putExtra(BiometricAuthActivity.EXTRA_POWER_ACTION, action.actionId)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}
