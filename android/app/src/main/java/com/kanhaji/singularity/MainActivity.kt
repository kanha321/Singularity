package com.kanhaji.singularity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kanhaji.core.designsystem.backgrounds.AnimatedBackground
import com.kanhaji.core.navigation.display.ScreenNavDisplay
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.navigation.navigator.rememberAppNavigator
import com.kanhaji.core.theme.composable.AppTheme
import com.kanhaji.core.theme.manager.ThemeManager
import com.kanhaji.singularity.navigation.HomeScreen
import com.kanhaji.singularity.service.AppBridgeService
import com.kanhaji.singularity.ui.components.SingularitySnackbarHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()
        AppBridgeService.start(this)

        setContent {
            val navigator = rememberAppNavigator(HomeScreen)
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedBackground(
                            type = ThemeManager.backgroundType,
                            enableParallax = ThemeManager.enableParallax
                        )
                        CompositionLocalProvider(LocalNavigator provides navigator) {
                            ScreenNavDisplay(navigator = navigator)
                        }
                        SingularitySnackbarHost(
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppBridgeService.isActivityVisible.set(true)
    }

    override fun onResume() {
        super.onResume()
        AppBridgeService.isActivityVisible.set(true)
        val km = getSystemService(android.app.KeyguardManager::class.java)
        AppBridgeService.isPhoneLocked.value = km?.isKeyguardLocked == true
    }

    override fun onStop() {
        super.onStop()
        AppBridgeService.isActivityVisible.set(false)
        AppBridgeService.onActivityStopped(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}