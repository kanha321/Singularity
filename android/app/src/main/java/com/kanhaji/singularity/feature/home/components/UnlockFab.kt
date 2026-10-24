package com.kanhaji.singularity.feature.home.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kanhaji.core.designsystem.feedback.rememberHapticClick
import com.kanhaji.singularity.feature.home.util.HomeBiometricLauncher
import com.kanhaji.singularity.ui.components.AppSnackbar

@Composable
fun UnlockFab(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val amber = Color(0xFFFBBF24)

    val onClick = rememberHapticClick {
        HomeBiometricLauncher.launchUnlock(context, onNotice = AppSnackbar::show)
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = amber,
        contentColor = Color.Black,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "Unlock PC with Fingerprint",
            modifier = Modifier.size(28.dp)
        )
    }
}
