# Singularity: Proximity Biometric Authentication (`pam_singularity`) Setup Guide

**Feature:** Proximity Biometric Authentication & `sudo` / Screen Unlock  
**Ecosystem Catalog:** §4.10 (`pam_singularity`)  
**Target Host:** Arch Linux (PAM / `sudo` / `dms` / `greetd`)  
**Target Device:** Xiaomi POCO F5 (Android 16 / Evolution X / KernelSU)  
**Binary Output:** `/usr/lib/security/pam_singularity.so`  
**Android Companion App:** `com.kanhaji.singularity` (`android/`)  

---

## 1. Architectural Overview

`pam_singularity` bridges Linux Pluggable Authentication Modules (PAM) with the Android hardware-backed Biometric subsystem (TEE / Fingerprint Sensor) over the Singularity daemon IPC bus. Zero 3rd-party dependencies on phone (no Termux).

```
┌────────────────────────┐
│  sudo / dms lockscreen │
└───────────┬────────────┘
            │ (PAM stack)
            ▼
┌────────────────────────┐
│   pam_singularity.so   │
└───────────┬────────────┘
            │ (UNIX domain socket: /run/user/<UID>/singularity.sock)
            ▼
┌────────────────────────┐
│   singularity-daemon   │ (Singleton via OS kernel flock /run/user/<UID>/singularity.lock)
└───────────┬────────────┘
            │ (ADB Tunnel: localabstract:singularity_app)
            ▼
┌───────────────────────────────────────┐
│   Xiaomi POCO F5 (Companion App)      │
│   • AppBridgeService (Foreground)     │
│   • Real-time PC Lock State Watcher   │
│   • Persistent Lock Notification      │
│   • BiometricPrompt (Hardware TEE)    │
└───────────────────────────────────────┘
```

### Key Security Guarantees:
1. **Cryptographic Biometric Verification:** Android's native `BiometricPrompt` delegates fingerprint matching to the hardware Trusted Execution Environment (TEE `BIOMETRIC_STRONG`).
2. **Fail-Safe Fallback:** If the phone is offline, Wi-Fi is disconnected, or the prompt times out, `pam_singularity.so` returns `PAM_IGNORE`. Linux PAM immediately and seamlessly falls back to the standard password prompt. You are **never** locked out.
3. **Singleton Guard:** Kernel advisory lock (`flock`) on `/run/user/<UID>/singularity.lock` guarantees zero duplicate daemon instances.
4. **Bi-Directional Unlock Highway:** 
   - When the PC screen locks, the phone displays `🔒 Locked` and posts a persistent unlock notification.
   - Touching the fingerprint sensor (via notification, in-app button, or PAM trigger) validates biometrics and instructs DMS to unlock.

---

## 2. Pre-Login & Cold Boot Configuration

To allow the phone to authenticate you at the initial boot login screen (before your user account logs in):

### A. Enable User Lingering
By default on systemd, user session services and `/run/user/1000/` only launch after interactive login. Enabling linger starts your user systemd manager at cold boot:

```bash
sudo loginctl enable-linger kanhaji
```

### B. Enable Systemd User Service
Link the release binary and enable the user service:

```bash
ln -sf /home/kanhaji/Projects/singularity/target/release/singularity-daemon ~/.local/bin/singularity-daemon
mkdir -p ~/.config/systemd/user/
cp desktop/src/singularity-daemon/singularity-daemon.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now singularity-daemon.service
```

### C. Update NetworkManager ADB Dispatcher
Ensure `/etc/NetworkManager/dispatcher.d/90-adb-connect` provides the user's `HOME` directory so ADB loads your authorized key (`~/.android/adbkey`) before login:

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

---

## 3. Building & Installing `pam_singularity.so`

### Step 1: Build the C-Dynamic Library
```bash
cargo build --release -p pam-singularity
```

### Step 2: Install to Linux PAM Security Directory
Always use `install -m 755` instead of `cp` to safely replace in-memory shared objects without memory corruption:
```bash
sudo install -m 755 target/release/libpam_singularity.so /usr/lib/security/pam_singularity.so
```

---

## 4. PAM Configuration

### A. Enable for `sudo` (Passwordless Biometric Approval)
In `/etc/pam.d/sudo`, add `pam_singularity.so` as `sufficient` at the very top:

```text
#%PAM-1.0
auth        sufficient    pam_singularity.so
auth        include       system-auth
account     include       system-auth
session     include       system-auth
session     optional      pam_systemd.so class=none
```

### B. Enable for DMS (DankMaterialShell) Lockscreen
DMS manages its lockscreen authentication via `~/.local/state/DankMaterialShell/pam/dankshell`.
Add `auth sufficient pam_singularity.so` directly before `pam_unix.so`:

```text
auth       sufficient                  pam_singularity.so
auth       [success=1 default=bad]     pam_unix.so          try_first_pass nullok
```

To configure automatically:
```bash
python3 -c '
path = "/home/kanhaji/.local/state/DankMaterialShell/pam/dankshell"
with open(path, "r") as f:
    content = f.read()
if "pam_singularity.so" not in content:
    content = content.replace("auth       [success=1 default=bad]     pam_unix.so", "auth       sufficient                  pam_singularity.so\nauth       [success=1 default=bad]     pam_unix.so")
    with open(path, "w") as f:
        f.write(content)
    print("✅ Configured pam_singularity.so in DMS lockscreen!")
else:
    print("Already configured!")
'
```

Verify DMS PAM configuration:
```bash
dms auth validate --path ~/.local/state/DankMaterialShell/pam/dankshell
```

### C. Enable for `greetd` (`dms-greeter` Cold-Boot Login)
`dms-greeter` features native single-touch external authentication (`maybeAutoStartExternalAuth`), which checks `/etc/pam.d/greetd` specifically for `pam_fprintd.so` and requires `"greeterEnableFprint": true` in DMS settings.

1. **Symlink `pam_fprintd.so` to `pam_singularity.so`:**
```bash
sudo ln -sf /usr/lib/security/pam_singularity.so /usr/lib/security/pam_fprintd.so
```

2. **Enable greeter fingerprint in DMS settings (`~/.config/DankMaterialShell/settings.json`):**
```json
{
  "greeterEnableFprint": true
}
```

3. **Sync greeter configuration and PAM stack:**
```bash
dms-greeter sync -a -y
```

This places the managed PAM block at the top of `/etc/pam.d/greetd`:
```text
#%PAM-1.0

# BEGIN DMS GREETER AUTH (managed by dms greeter sync)
auth sufficient pam_fprintd.so max-tries=2 timeout=10
# END DMS GREETER AUTH
auth       required     pam_securetty.so
auth       requisite    pam_nologin.so
auth       include      system-local-login
account    include      system-local-login
session    include      system-local-login
```

> [!NOTE]
> On startup, `dms-greeter` will automatically prompt "Awaiting fingerprint authentication" and trigger the phone biometric prompt. Touching your phone's fingerprint sensor immediately unlocks the PC **without** requiring password entry. If you type your password instead, the greeter buffers it and logs in via password once the fingerprint timeout elapses or is cancelled. This prevents the two-factor authentication (2FA) trap.

---

## 5. Android Companion App Installation

Build and install the native Android companion app (`com.kanhaji.singularity`):

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd appops set com.kanhaji.singularity USE_FULL_SCREEN_INTENT allow
adb shell cmd appops set com.kanhaji.singularity SYSTEM_ALERT_WINDOW allow
adb shell am start-foreground-service -n com.kanhaji.singularity/.service.AppBridgeService
```

---

## 6. Verification & Testing

1. **Verify Daemon & ADB Status:**
   ```bash
   singularity-cli status
   ```
2. **Test CLI Manual Biometric Trigger:**
   ```bash
   singularity-cli auth --reason "Testing Fingerprint"
   ```
3. **Test `sudo` with Phone:**
   ```bash
   sudo -k
   sudo echo "Biometric Authentication Active!"
   ```
4. **Test DMS Lockscreen Unlock:**
   Lock your screen. The phone will display `🔒 Locked` and post the unlock notification. Tap **Unlock with Fingerprint** and touch your sensor to unlock your laptop.
