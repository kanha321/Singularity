package com.kanhaji.singularity.navigation

import androidx.compose.runtime.Composable
import com.kanhaji.core.navigation.screen.NavScreen
import com.kanhaji.feature.settings.presentation.screen.SettingsScreen
import com.kanhaji.singularity.feature.auth.AuthSettingsScreen
import com.kanhaji.singularity.feature.home.HomeScreenContent
import com.kanhaji.singularity.feature.power.PowerControlsScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeScreen : NavScreen {
    @Composable
    override fun Content() {
        HomeScreenContent()
    }
}

@Serializable
object SettingsNavScreen : NavScreen {
    @Composable
    override fun Content() {
        SettingsScreen()
    }
}

@Serializable
object AuthSettingsNavScreen : NavScreen {
    @Composable
    override fun Content() {
        AuthSettingsScreen()
    }
}

@Serializable
object PowerControlsNavScreen : NavScreen {
    @Composable
    override fun Content() {
        PowerControlsScreen()
    }
}

@Serializable
data class FileBrowserNavScreen(val path: String = "~") : NavScreen {
    @Composable
    override fun Content() {
        com.kanhaji.singularity.feature.files.FileBrowserScreen(path)
    }
}
