# Project Dependencies — Singularity

This document catalogues all system, runtime, build, and hardware dependencies across the **Singularity** ecosystem (Arch Linux Desktop Host, Android Companion App, and Kernel Bridges).

---

## 1. Arch Linux / Desktop Host

### 1.1 Core Runtime Dependencies (`depends`)
These packages are strictly required for `singularity-daemon`, local IPC, and authentication.

| Dependency | Package (Arch Linux) | Purpose / Usage |
| :--- | :--- | :--- |
| **`adb`** | `android-tools` | Persistent ADB TCP transport (`:5555`), reverse port tunnels (`8920`), device discovery, and app launching. |
| **`pam`** | `pam` | Linux Pluggable Authentication Modules hosting `pam_singularity.so` for biometric and proximity unlock. |
| **`systemd`** | `systemd` | Service supervisor (`singularity-daemon.service`), session inhibitor locks, and power control (`poweroff`, `reboot`, `suspend`). |
| **`polkit`** | `polkit` | Privilege authorization framework for desktop actions. |

---

### 1.2 Feature Runtime Dependencies (`optdepends`)
These external tools unlock specific convergence modules. The daemon detects their presence gracefully at runtime.

| Tool / Service | Package (Arch Linux) | Feature Module | Capability Provided |
| :--- | :--- | :--- | :--- |
| **`7z`** | `7-zip` (or `p7zip`) | **Virtual Archive Browser** | Header inspection (`7z l -slt`), zero-download browsing, single-file streaming (`7z e -so`), and remote laptop extraction (`7z x`). |
| **`scrcpy`** | `scrcpy` | **Display & Media** | 120Hz sub-30ms screen mirroring, cellular voice downlink audio capture, and headless shell clipboard supervisor. |
| **`wl-clipboard`** | `wl-clipboard` | **Universal Clipboard** | Wayland clipboard read/write events (`wl-paste`, `wl-copy`) and desktop echo suppression. |
| **`pipewire`** | `pipewire`, `wireplumber` | **Virtual Mic & Audio** | Virtual audio source sink routing phone microphone into Linux desktop calls (Zoom, Discord, OBS). |
| **`v4l2loopback`** | `v4l2loopback-dkms` | **Continuity Camera** | Virtual Linux webcam device (`/dev/videoX`) receiving high-resolution 60fps video feed from phone camera. |
| **`sshfs` / `fuse3`** | `sshfs`, `fuse3` | **Network Storage** | Direct transparent mounting of phone storage to `/mnt/phone` on Arch Linux. |
| **`rg` / `fd`** | `ripgrep`, `fd` | **Blazing Search** | Sub-millisecond typeahead filesystem search across laptop drives. |
| **`ffmpeg`** | `ffmpeg` | **File Previews & Thumbnails** | Rapid video keyframe extraction and image downscaling for 128x128 cached thumbnails. |

---

### 1.3 Desktop Build & Toolchain Dependencies (`makedepends`)

| Tool | Version / Package | Purpose |
| :--- | :--- | :--- |
| **`rustc` / `cargo`** | Stable 1.80+ | Rust crate compilation (`singularity-daemon`, `pam-singularity`, `singularity-core`, `singularity-client`). |
| **`clang` / `gcc`** | Base devel | C toolchain and dynamic library linking (`-Wl,-z,nodelete` for `cdylib`). |
| **`pam` headers** | `pam` (headers included) | Compiling `pam-singularity` C-bindings. |

---

## 2. Android Companion App (POCO F5 / Android 11+)

### 2.1 Hardware Capabilities
| Hardware Sensor / HAL | Minimum Spec | Used By |
| :--- | :--- | :--- |
| **Fingerprint Sensor** | Strong Biometrics (`BIOMETRIC_STRONG`) | Sudo privilege elevation, root folder protection, and `pam_singularity` laptop unlock. |
| **Haptic Actuator** | Linear Resonant Actuator (LRA) | Framework-wide tactile clicks (`VIRTUAL_KEY`) and long-press feedback (`LONG_PRESS`). |
| **Camera Hardware** | Camera2 API / Multi-lens | Continuity Camera desktop streaming. |
| **Wi-Fi / Hotspot** | 802.11ac / 5GHz Hotspot | Low-latency TCP socket link (`10.x.x.x:5555`). |

---

### 2.2 System Permissions & Operating Policies
| Permission / Setting | Level | Reason |
| :--- | :--- | :--- |
| **Wireless / USB Debugging** | Developer Options | Enables persistent `adbd` TCP daemon on port 5555. |
| **Heads-Up Notifications** | Global Settings (`heads_up_notifications_enabled = 1`) | Unconditional floating banners for incoming laptop biometric auth requests. |
| **Full-Screen Intent** | `USE_FULL_SCREEN_INTENT` | Allows auth prompts to wake screen immediately when laptop requests `sudo` or unlock. |
| **Foreground Service** | `FOREGROUND_SERVICE_DATA_SYNC` | Background multi-threaded file & recursive folder downloads with persistent progress bar. |
| **Battery Optimization** | Whitelisted | Prevents Android Doze mode from dropping the persistent TCP reverse bridge. |
| **Notification Listener** | System Permission | Intercepts incoming 2FA SMS and authenticator tokens to auto-clip to Wayland. |

---

### 2.3 Elevated / Kernel Bridges (Root Privileges)
| Bridge / Tool | Privilege Level | Capability Provided |
| :--- | :--- | :--- |
| **KernelSU / Magisk** | Root (UID 0) | Kernel battery charge limiter (`sysfs`), raw input injection (`/dev/uinput`), full storage mount (`/mnt/phone`), and automated boot persistence. |
| **Shell Daemon** | Shell (UID 2000) | Headless clipboard reading (bypasses Android 10+ background restriction) and display mirroring. |

---

### 2.4 Android Build & Toolchain
| Tool | Version | Path / Env |
| :--- | :--- | :--- |
| **JDK** | Java 21 (`java-21-openjdk`) | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` |
| **Android SDK** | API 30 to 37 (Build-Tools 35.0.0+) | `ANDROID_HOME=/home/kanhaji/Android/Sdk` |
| **Gradle** | 8.x + Android Gradle Plugin 8.7+ | `./gradlew` |
| **Kotlin** | 2.0+ | Compose Compiler 2.0+ |

---

## 3. Host System Quick Verification Command

Run this one-liner on Arch Linux to check which dependencies are installed on your machine:

```bash
printf "%-18s: %s\n" \
  "adb" "$(which adb 2>/dev/null || echo 'MISSING')" \
  "7z" "$(which 7z 2>/dev/null || echo 'MISSING')" \
  "scrcpy" "$(which scrcpy 2>/dev/null || echo 'MISSING')" \
  "wl-copy" "$(which wl-copy 2>/dev/null || echo 'MISSING')" \
  "rg" "$(which rg 2>/dev/null || echo 'MISSING')" \
  "fd" "$(which fd 2>/dev/null || echo 'MISSING')" \
  "pipewire" "$(which pipewire 2>/dev/null || echo 'MISSING')" \
  "sshfs" "$(which sshfs 2>/dev/null || echo 'MISSING')"
```
