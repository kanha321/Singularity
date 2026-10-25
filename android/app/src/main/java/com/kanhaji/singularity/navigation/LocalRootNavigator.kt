package com.kanhaji.singularity.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.kanhaji.core.navigation.navigator.AppNavigator

val LocalRootNavigator = staticCompositionLocalOf<AppNavigator?> { null }
