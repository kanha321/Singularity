package com.kanhaji.singularity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import com.kanhaji.singularity.service.AppBridgeService
import com.kanhaji.singularity.service.PendingAuth
import com.kanhaji.singularity.ui.theme.SingularityTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()
        AppBridgeService.start(this)

        setContent {
            SingularityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        onTestBiometric = {
                            val intent = Intent(this, BiometricAuthActivity::class.java).apply {
                                putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Security")
                                putExtra(BiometricAuthActivity.EXTRA_REASON, "Test Fingerprint Verification")
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val km = getSystemService(android.app.KeyguardManager::class.java)
        AppBridgeService.isPhoneLocked.value = km?.isKeyguardLocked == true
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

@Composable
fun DashboardScreen(onTestBiometric: () -> Unit) {
    val pendingAuth by AppBridgeService.currentAuthRequest.collectAsState()
    val isPcConnected by AppBridgeService.isPcConnected.collectAsState()
    val pcLockState by AppBridgeService.pcLockState.collectAsState()
    val isPcLoggedIn by AppBridgeService.pcLoginState.collectAsState()
    val isPhoneLocked by AppBridgeService.isPhoneLocked.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "⚡ SINGULARITY",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Arch Linux Convergence Engine",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Show prominent pending verification card if desktop requested authentication
        if (pendingAuth != null) {
            PendingAuthCard(
                auth = pendingAuth!!,
                onVerify = { AppBridgeService.relaunchBiometricPrompt(context) },
                onReject = { AppBridgeService.rejectActiveAuth("Rejected in app") }
            )
        }

        val isPcLocked = pcLockState == "locked"

        if (!isPcConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = "📡", fontSize = 32.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PC Disconnected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Waiting for Arch Linux Singularity daemon to connect via UNIX socket.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        } else if (!isPcLoggedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🚪 PC at Login Screen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Your laptop is waiting for login at the greeter screen.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        } else if (isPcLocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🔒 PC Screen is Locked",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Your laptop screen is locked. Tap below to unlock with fingerprint.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                    Button(
                        onClick = {
                            val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                                putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Lockscreen")
                                putExtra(BiometricAuthActivity.EXTRA_REASON, "Unlock PC Screen")
                                putExtra(BiometricAuthActivity.EXTRA_IS_UNLOCK, true)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("🔓 Unlock PC with Fingerprint", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        StatusCard(
            title = "PC Connection State",
            subtitle = if (isPcConnected) "Arch Linux ADB Unix Socket" else "Offline / Socket closed",
            status = if (isPcConnected) "🟢 Connected" else "🔴 Disconnected"
        )

        StatusCard(
            title = "PC Login State",
            subtitle = if (isPcConnected) "Arch Linux Session Status" else "Session unavailable while offline",
            status = if (!isPcConnected || pcLockState == "disconnected") "⚪ Disconnected" else (if (isPcLoggedIn) "🟢 Logged In" else "🚪 Logged Out (Login Screen)")
        )

        StatusCard(
            title = "PC Screen State",
            subtitle = if (isPcConnected) "Arch Linux / DMS Lockscreen Status" else "Screen status unavailable while offline",
            status = if (!isPcConnected || pcLockState == "disconnected") "⚪ Disconnected" else (if (!isPcLoggedIn) "🚪 Login Screen" else (if (isPcLocked) "🔒 Locked" else "🔓 Unlocked"))
        )

        StatusCard(
            title = "Phone Screen State",
            subtitle = "POCO F5 Keyguard Status",
            status = if (isPhoneLocked) "🔒 Locked" else "🔓 Unlocked"
        )

        StatusCard(
            title = "AppBridge Highway",
            subtitle = if (isPcConnected) "Streaming on localabstract:singularity_app" else "Listening on localabstract:singularity_app",
            status = if (isPcConnected) "🟢 Active" else "🟡 Idle (Listening)"
        )

        StatusCard(
            title = "Hardware TEE Biometrics",
            subtitle = "Native Fingerprint Sensor Ready",
            status = "🟢 Enabled"
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onTestBiometric,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("🧪 Test Biometric Prompt", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PendingAuthCard(
    auth: PendingAuth,
    onVerify: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "🔐 Auth Request Pending",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = auth.reason,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onVerify,
                    modifier = Modifier.weight(1.2f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("🔑 Tap Fingerprint", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(0.8f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun StatusCard(title: String, subtitle: String, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
            Text(text = status, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}