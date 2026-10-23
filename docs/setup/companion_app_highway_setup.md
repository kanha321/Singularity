# Singularity: Companion AppBridge & Real-Time Highway Setup Guide

**Component:** Android Companion App (`com.kanhaji.singularity`) & Daemon Highway  
**Ecosystem Catalog:** §4.3 (AppBridge), §4.10 (`pam_singularity`), §4.11 (Screen Unlock)  
**Host Architecture:** Arch Linux (Systemd / DMS / Hyprland / Wayland)  
**Target Device:** Android 10+ (Tested on Xiaomi POCO F5 / Android 16 Evolution X)  
**Transport:** ADB-multiplexed UNIX Domain Socket (`/run/user/<UID>/singularity_app.sock` $\leftrightarrow$ `localabstract:singularity_app`)  
**Root Requirement:** **None** (100% compatible with both unrooted and rooted devices)  

---

## 1. Architectural Overview

The Singularity Companion App communicates with the Linux host over an isolated, zero-overhead **native UNIX domain socket pipeline**. Unlike traditional TCP loopback ports, UNIX domain sockets avoid IP stack overhead, eliminate port collision risks, and provide kernel-enforced immediate file descriptor close / EOF notification when either side disconnects.

```
┌────────────────────────────────────────────────────────┐
│                   Arch Linux Host                      │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │                singularity-daemon                │  │
│  │  • Session Watcher (loginctl seat0 query)        │  │
│  │  • Lock Watcher (dms ipc call lock isLocked)     │  │
│  │  • Biometric PAM Relay (sudo / screen unlock)    │  │
│  └────────────────────────┬─────────────────────────┘  │
│                           │ UnixStream                         │
│                           ▼                            │
│  /run/user/<UID>/singularity_app.sock (Local Socket)    │
│                           ▲                            │
│                           │ adb forward                │
└───────────────────────────┼────────────────────────────┘
                            │ (USB or Wi-Fi TCP:5555)
┌───────────────────────────┼────────────────────────────┐
│                           ▼                            │
│            localabstract:singularity_app               │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │     com.kanhaji.singularity (Companion App)      │  │
│  │                                                  │  │
│  │  • AppBridgeService (Persistent Foreground)      │  │
│  │  • Real-Time Keyguard BroadcastReceiver          │  │
│  │  • Connection Watchdog (2.0s Heartbeat)          │  │
│  │  • Jetpack Compose Dashboard (MainActivity)      │  │
│  │  • Hardware TEE BiometricPrompt (Fingerprint)    │  │
│  └──────────────────────────────────────────────────┘  │
│                  POCO F5 (Android 16)                  │
└────────────────────────────────────────────────────────┘
```

### Why Native UNIX Sockets?
1. **Zero Child Process Overhead:** The host daemon maintains a persistent `AppBridgeStream` (`tokio::net::UnixStream`) across ticks instead of spawning child processes or reconnecting TCP sockets.
2. **Instant Disconnect Detection:** When `singularity-daemon` stops or the host sleeps, the Linux kernel terminates the UNIX socket file descriptor immediately, delivering instant `EOF` (`null` on `BufferedReader.readLine()`) to Android without waiting for network timeouts.
3. **Non-Root Compatibility:** ADB port forwarding supports `localabstract:` namespace directly on standard Android user permissions without requiring root, KernelSU, or Magisk.

---

## 2. Wire Protocol Specification

Communication occurs over line-delimited JSON (`\n`) streaming continuously over the socket.

### A. Host $\to$ Phone: Session & Lock State (`lock_state`)
Pushed by `lock_watcher.rs` every 500ms or immediately upon session/lock state transition:

```json
{
  "action": "lock_state",
  "locked": false,
  "logged_in": true,
  "isLoggedIn": true
}
```

**Phone Response:**
```json
{
  "status": "ok",
  "pc_locked": false,
  "logged_in": true,
  "isLoggedIn": true,
  "request_unlock": false
}
```
*Note: If the user tapped "Unlock PC" on the phone, `request_unlock` is returned as `true`, instructing the host daemon to trigger `dms ipc call lock unlock`.*

### B. Host $\to$ Phone: Biometric Prompt (`authenticate`)
Triggered when `sudo` or PAM requires fingerprint authorization:

```json
{
  "action": "authenticate",
  "reason": "sudo /usr/bin/pacman -Syu",
  "timeout_secs": 15
}
```

**Phone Response:**
```json
{
  "status": "success"
}
```
*(or `{"status": "rejected", "reason": "Fingerprint not recognized / cancelled"}`)*

### C. Host $\to$ Phone: Cancel Authentication (`cancel_auth`)
Sent if host user hits `Ctrl+C` or password fallback occurs:

```json
{
  "action": "cancel_auth"
}
```

---

## 3. Host System Setup (Arch Linux)

### Step 1: Build the Host Daemon
```bash
cd /home/kanhaji/Projects/singularity/desktop
cargo build --release -p singularity-daemon
```

### Step 2: Symlink & Setup Systemd User Service
Ensure the binary is in user `PATH` and enable the systemd service:

```bash
mkdir -p ~/.local/bin ~/.config/systemd/user
ln -sf /home/kanhaji/Projects/singularity/target/release/singularity-daemon ~/.local/bin/singularity-daemon
cp src/singularity-daemon/singularity-daemon.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now singularity-daemon.service
```

### Step 3: Enable User Lingering (Cold Boot Support)
Allows user systemd services and the `/run/user/1000/` runtime directory to initialize before graphical login:

```bash
sudo loginctl enable-linger kanhaji
```

### Step 4: NetworkManager Wi-Fi ADB Dispatcher
Ensure `/etc/NetworkManager/dispatcher.d/90-adb-connect` automatically connects to your phone over Wi-Fi when connected to your home network:

```sh
#!/bin/sh
U=kanhaji
[ "$2" = "up" ] || exit 0
case "$1" in lo|docker*|veth*|virbr*) exit 0 ;; esac

for i in 1 2 3 4 5; do
  GW=$(ip route show default dev "$1" | awk '{print $3; exit}')
  [ -n "$GW" ] && break
  sleep 1
done
[ -z "$GW" ] && exit 0

runuser -u "$U" -- env HOME=/home/$U adb disconnect >/dev/null 2>&1
timeout 10 runuser -u "$U" -- env HOME=/home/$U adb connect "$GW:5555"
```
Make executable: `sudo chmod +x /etc/NetworkManager/dispatcher.d/90-adb-connect`

---

## 4. Android Companion App Setup

### Step 1: Build Debug APK
Build using Java 21:

```bash
cd /home/kanhaji/Projects/singularity/android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug --no-daemon --no-configuration-cache
```

### Step 2: Install via ADB
```bash
adb -s <DEVICE_IP>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Start AppBridge Foreground Service
```bash
adb -s <DEVICE_IP>:5555 shell am start-foreground-service -n com.kanhaji.singularity/.service.AppBridgeService
adb -s <DEVICE_IP>:5555 shell am start -n com.kanhaji.singularity/.MainActivity
```

---

## 5. Real-Time State Machine Matrix

The companion app tracks and identifies all states reactively:

| State Field | Connected (PC Online) | Disconnected (PC Offline / Asleep) | Source |
| :--- | :--- | :--- | :--- |
| **PC Connection** | `🟢 Connected`<br>*`Arch Linux ADB Unix Socket`* | `🔴 Disconnected`<br>*`Offline / Socket closed`* | Active UNIX stream + 2.0s Watchdog |
| **PC Login State** | `🟢 Logged In` / `🚪 Logged Out`<br>*`Arch Linux Session Status`* | `⚪ Disconnected`<br>*`Session unavailable while offline`* | `loginctl list-sessions --no-legend` |
| **PC Screen State** | `🔓 Unlocked` / `🔒 Locked`<br>*`Arch Linux / DMS Lockscreen`* | `⚪ Disconnected`<br>*`Screen status unavailable while offline`* | `dms ipc call lock isLocked` |
| **Phone Screen State** | `🔓 Unlocked` / `🔒 Locked`<br>*`POCO F5 Keyguard Status`* | `🔓 Unlocked` / `🔒 Locked`<br>*`POCO F5 Keyguard Status`* | `BroadcastReceiver` (`ACTION_SCREEN_ON/OFF/PRESENT`) |
| **AppBridge Highway** | `🟢 Active`<br>*`Streaming on localabstract`* | `🟡 Idle (Listening)`<br>*`Listening on localabstract`* | `LocalServerSocket` listener |

### Disconnect Clean-Up Protocol
When the connection drops (either daemon stop or 2.0s without packets):
1. `isPcConnected` immediately flips to `false`.
2. `pcLockState` resets to `"disconnected"`.
3. `pcLoginState` resets to `false`.
4. Any active biometric prompt (`BiometricAuthActivity`) is immediately dismissed so the phone is not locked in a prompt for a disconnected PC.
5. The silent PC lock status notification (`💻 PC Screen Locked`) is cleared from the notification shade.
6. The foreground highway notification updates to `⚪ Highway listening on localabstract:singularity_app`.
7. Tapping the highway status notification at any time directly opens `MainActivity` via an immutable `PendingIntent`.

---

## 6. Verification & Troubleshooting

### Check Host UNIX Socket Forward
Verify ADB is listening on the host runtime socket:
```bash
ss -a -p | grep singularity_app
```
*Expected: `u_str LISTEN` on `/run/user/1000/singularity_app.sock` and `u_str ESTAB` connected to the daemon.*

### View Companion App Logcat
Monitor realtime events on the phone:
```bash
adb -s <DEVICE_IP>:5555 logcat -s SingularityAppBridge
```

### Inspect Notification ContentIntent
Verify the highway status notification has an active click intent:
```bash
adb -s <DEVICE_IP>:5555 shell dumpsys notification --noredact | grep -A 8 "id=2828"
```
*Expected output contains: `contentIntent=PendingIntent{... com.kanhaji.singularity startActivity}`.*

### Simulate Disconnect & Reconnect
1. Stop host daemon: `systemctl --user stop singularity-daemon`
   - Phone immediately presents red `📡 PC Disconnected` banner.
   - All PC states flip to `⚪ Disconnected`.
2. Start host daemon: `systemctl --user start singularity-daemon`
   - Phone instantly dismisses the banner and displays `🟢 Connected` with live session/lock status.
