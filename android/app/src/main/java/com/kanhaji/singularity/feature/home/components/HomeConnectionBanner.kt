package com.kanhaji.singularity.feature.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard

@Composable
fun HomeConnectionBanner(
    isPcConnected: Boolean,
    isPcLoggedIn: Boolean,
    isPcLocked: Boolean,
    onUnlockPc: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(
        cornerRadius = 20.dp,
        elevation = 3.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                !isPcConnected -> {
                    BannerHeader("📡", "PC Disconnected", MaterialTheme.colorScheme.error)
                    Text(
                        text = "Waiting for Arch Linux Singularity daemon on localabstract:singularity_app.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !isPcLoggedIn -> {
                    BannerHeader("🚪", "PC at Login Screen", MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = "Laptop session is at the greeter screen. Ready for biometric login.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                isPcLocked -> {
                    BannerHeader("🔒", "PC Screen is Locked", MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Screen is locked. Tap below or use your fingerprint to unlock.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onUnlockPc,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🔓 Unlock PC with Fingerprint", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                else -> {
                    BannerHeader("⚡", "Singularity Active", MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Connected to Arch Linux. Biometrics & AppBridge synchronized.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerHeader(emoji: String, title: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
    }
}
