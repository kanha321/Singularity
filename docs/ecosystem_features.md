# Singularity: Complete Ecosystem Features & Architectural Vision

**Project Name:** Singularity  
**Status:** Architecture & Feature Specification  
**Host Environment:** Arch Linux (Hyprland / Wayland / PipeWire / systemd)  
**Target Device:** Xiaomi POCO (Android 16 / Evolution X / Linux 5.10 / KernelSU v3.0.1)  
**Transport Backbone:** Persistent, Auto-Reconnecting ADB over TCP (`10.235.203.184:5555`)  

---

## 1. Executive Summary & Vision

**Singularity** is a next-generation device convergence ecosystem built to bridge **Arch Linux** and a **Rooted Android** device. It draws inspiration from Apple’s Continuity and KDE Connect, but systematically eliminates their fundamental flaws:

1. **No Fragile Wi-Fi Discovery:** Unlike KDE Connect’s reliance on UDP broadcast discovery and unauthenticated Wi-Fi sockets (which break under Doze mode and router AP isolation), Singularity is anchored to an **always-on, authenticated ADB TCP tunnel (`:5555`)** backed by automated boot and network dispatcher scripts.
2. **No Sandbox Compromises:** Apple restricts Continuity to its closed garden and disables arbitrary background jobs, terminal access, and filesystem mounting. Singularity leverages **KernelSU Root (UID 0)** and **Shell (UID 2000)** to achieve capabilities that Apple’s iOS sandbox strictly prohibits.
3. **Optimized Division of Privileges:** Features are surgically mapped to the exact privilege level where they work best: Root handles hardware injection and boot persistence; Shell handles privacy-restricted clipboard and zero-prompt display capture; the standard Android App handles reactive event listeners (notifications, telephony, media).

---

## 2. Infrastructure: The Persistent Transport Backbone

The foundational connectivity layer is already implemented and operating:

```
┌─────────────────────────────────────────────────────────┐
│ Arch Linux (Laptop)                                     │
│  - NetworkManager Dispatcher Script                     │
│  - Triggers background reconnect to 10.235.203.184:5555 │
│    whenever laptop associates with any Wi-Fi network    │
└────────────────────────────┬────────────────────────────┘
                             │
                    Persistent ADB TCP (:5555)
                             │
┌────────────────────────────▼────────────────────────────┐
│ Android 16 (POCO / KernelSU 3.0.1)                      │
│  - KernelSU boot script (/data/adb/service.d/)          │
│  - Auto-enables Wi-Fi Hotspot on boot                   │
│  - Forces persistent adbd TCP listener on port 5555     │
└─────────────────────────────────────────────────────────┘
```

### Why this is a Superpower:
- **Zero-Touch Reconnection:** Whether at home, on campus, or tethering in transit, the devices connect automatically without user intervention or manual pairing.
- **Firewall & AP Isolation Immunity:** All communications travel via authenticated ADB TCP, `adb forward`, and `adb reverse` tunnels, completely bypassing local network router restrictions.
- **Survivable Across Reboots:** Both ends autonomously restore the link if either device reboots.

---

## 3. Privilege Architecture: Clean Division of Labor

A central architectural discovery of Singularity is that **privileges must be strictly decoupled into specialized bridges**:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               SINGULARITY BRIDGE MATRIX                                │
├──────────────────────────┬─────────────────────────────┬───────────────────────────────┤
│ Bridge                   │ Responsibilities            │ Key Capabilities              │
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ RootBridge (UID 0)       │ Hardware & System Control   │ • Autonomous boot persistence │
│ (KernelSU)               │                             │ • /dev/uinput virtual devices │
│                          │                             │ • Battery sysfs charge limits │
│                          │                             │ • Full /sdcard & /data mount  │
│                          │                             │ • Silent OS permission grants │
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ ShellBridge (UID 2000)   │ Android Security Framework  │ • Headless clipboard read/write│
│ (ADB Daemon / scrcpy)    │ Bypass                      │ • Zero-prompt screen capture  │
│                          │                             │ • Direct audio submix capture │
│                          │                             │ • Platform CLI: telecom, am   │
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ AppBridge (UID 10xxx)    │ Reactive OS Event System    │ • NotificationListenerService │
│ (Kotlin Companion App)   │                             │ • RemoteInput quick reply     │
│                          │                             │ • Telephony & SMS database    │
│                          │                             │ • MediaSessionManager (MPRIS) │
│                          │                             │ • Battery & network telemetry │
└──────────────────────────┴─────────────────────────────┴───────────────────────────────┘
```

---

## 4. Comprehensive Feature Catalog

### 4.1 Universal Clipboard
- **Concept:** Continuous, sub-millisecond, bidirectional clipboard synchronization between Wayland and Android.
- **Mechanisms:**
  - **PC Side:** `wl-clipboard-rs` listens to Wayland clipboard events natively in safe Rust (zero child process spawning). One-directional echo suppression prevents reflection loops.
  - **Android Side:** Headless `scrcpy-server` running as **Shell UID 2000** (`video=false audio=false control=true`). Bypasses Android 10+ background reading restrictions without triggering the Root UID 0 `SecurityException`.
  - **Wire Framing:** Binary big-endian framing (`0x09` SET, `0x08` GET, `0x00` PUSH, `0x01` ACK).
- **Status:** Validated in Phase 0 & Phase 1. Ready for production assembly.

---

### 4.2 Seamless Calling & Audio Routing (The Desk Model)
- **Concept:** Make and receive cellular calls from your laptop without Bluetooth complexity or headset pairing.
- **The Desk Insight:** Because the phone is connected via hotspot or local Wi-Fi, it is physically sitting near the laptop on the desk.
- **Mechanisms:**
  - **Downlink Audio (Hearing Caller):** `scrcpy --audio-source=voice-call-downlink` captures the incoming caller's voice directly from the cellular baseband DSP and streams it to the laptop speakers/headphones over ADB TCP.
  - **Uplink Voice (Speaking):** The user speaks naturally at their desk. The phone's onboard studio microphones (equipped with hardware far-field pickup and acoustic noise cancellation) capture the user's voice for the cellular modem.
  - **Control Plane:** The desktop UI displays caller ID, contact photo, and **[Answer]** / **[Decline]** buttons, issuing `telecom accept-call` or `telecom end-call` over ADB.
- **Key Advantage:** Zero Bluetooth pairing, no audio driver conflicts, 100% reliable over Wi-Fi.

---

### 4.3 Automated 2FA / OTP Auto-Clip & Paste
- **Concept:** Never manually type a SMS verification code or 2FA token again.
- **Mechanisms:**
  - Android companion app's `NotificationListenerService` monitors incoming SMS and authenticator apps.
  - A regex engine matches OTP patterns (e.g. `\b\d{4,8}\b` preceded by "code", "OTP", or "verification").
  - The extracted OTP is automatically pushed to the laptop clipboard via the Singularity protocol, accompanied by a desktop alert:
    ```text
    GitHub 2FA: 582910 [Copied to Clipboard]
    ```
  - The user simply presses `Ctrl+V` in the active browser, terminal, or SSH session.

---

### 4.4 Stream Deck & Real-Time Hardware HUD
- **Concept:** Transform the phone lying on your desk into a customizable touch macro pad and PC performance monitor.
- **Mechanisms:**
  - **Live PC Metrics:** Arch Linux daemon streams CPU/GPU temps, RAM usage, battery wattage, network bandwidth, and active Hyprland workspaces to the phone.
  - **One-Tap Macro Keys:**
    - Toggle microphone mute in PipeWire.
    - Switch Hyprland window layouts and development workspaces.
    - Toggle OBS recording/streaming scenes.
    - Trigger custom bash/fish scripts on Arch Linux.
  - **Context Sensitivity:** When Neovim or VSCode gains focus on Arch, the phone UI automatically shifts to show Git status, test runners, or debugger controls.

---

### 4.5 Kernel Battery Health & Smart Charge Limiting
- **Concept:** Prevent battery degradation caused by keeping the phone plugged into the desk charger or laptop all day.
- **Mechanisms:**
  - Arch Linux daemon continuously monitors battery percentage and temperature via ADB.
  - Because KernelSU provides **Root (UID 0)**, the laptop can directly command the Linux kernel power supply interface on the phone:
    ```bash
    # Stop charging at hardware level:
    echo 0 > /sys/class/power_supply/battery/charging_enabled
    # Or set a hardware charge limit (e.g. 80%):
    echo 80 > /sys/class/power_supply/battery/charge_control_limit_max
    ```
  - When the battery reaches 80%, charging halts automatically, preserving battery longevity indefinitely.

---

### 4.6 Transparent Filesystem Mount (FUSE / Root SFTP)
- **Concept:** Mount the phone's entire filesystem directly to `/mnt/phone` on Arch Linux.
- **Mechanisms:**
  - An embedded root SFTP daemon or ADB FUSE bridge exposes the phone's storage.
  - Unlike Apple's sandboxed AirDrop or MTP, the user has full terminal access:
    ```bash
    cd /mnt/phone/sdcard/DCIM/Camera
    rsync -avP ~/Music/ /mnt/phone/sdcard/Music/
    nvim /mnt/phone/sdcard/notes/todo.txt
    ```
  - Direct opening, editing, and saving of phone files from Neovim, VSCode, GIMP, and Dolphin.

---

### 4.7 Supercharged Screen Mirroring & Freeform Desktop Mode
- **Concept:** Low-latency, high-fps interactive phone control and desktop multi-window execution.
- **Mechanisms:**
  - Uses `scrcpy` backend over ADB TCP:
    ```bash
    scrcpy --tcpip=10.235.203.184:5555 --turn-screen-off --stay-awake \
           --audio-codec=opus --audio-buffer=40 --power-off-on-close
    ```
  - **Superior to Apple iPhone Mirroring:**
    - Up to 120Hz display refresh rates.
    - Sub-30ms latency with hardware H.264/HEVC encoding.
    - Screen stays completely off on the phone.
    - Dynamic resolution and density overrides (`wm size`, `wm density`) enable wide 16:9 or freeform resizable desktop windows on Arch Linux.

---

### 4.8 Continuity Camera & Virtual Microphone (V4L2 Loopback)
- **Concept:** Use the Xiaomi POCO's 50MP sensor as a studio-grade PC webcam and conference mic.
- **Mechanisms:**
  - **Video:** `scrcpy --video-source=camera --camera-size=1920x1080 --camera-fps=60 --v4l2-sink=/dev/video2 --no-video-playback`. Exposes the phone camera directly to Linux kernel's `v4l2loopback` device, instantly visible to Zoom, Discord, Google Meet, and OBS.
  - **Microphone:** `scrcpy --audio-source=mic-voice-communication` captured over ADB TCP and wired into PipeWire as a virtual microphone input.

---

### 4.9 Universal Control & Boundary Edge Crossing
- **Concept:** Move your laptop mouse cursor past the screen edge to seamlessly control the Android phone on your desk.
- **Mechanisms:**
  - In Hyprland, a cursor listener detects when the pointer hits the display boundary (e.g. right edge).
  - Hyprland grabs mouse/keyboard focus and pipes input events into the Singularity daemon.
  - The daemon injects events into the phone via **Root `/dev/uinput`**, providing true hardware-level pointer and multi-touch control without relying on slow accessibility APIs.

---

### 4.10 Proximity Biometric Authentication (`pam_singularity`)
- **Concept:** Use your phone’s presence and fingerprint sensor to unlock your Arch laptop and authorize `sudo`.
- **Mechanisms:**
  - A custom Linux PAM module (`pam_singularity.so`) binds to `hyprlock`, `swaylock`, and `sudo`.
  - **Zero-Touch Mode:** When waking the laptop, PAM verifies that the phone is actively connected over the authenticated ADB TCP tunnel with matching cryptographic RSA keys. If verified, session unlocks without a password.
  - **Biometric Mode:** Typing `sudo` prompts a biometric confirmation on the phone: tapping the fingerprint sensor approves root access on the laptop.

---

### 4.11 Notification Mirroring & Inline Quick Reply
- **Concept:** Two-way notification sync with full interactive action support.
- **Mechanisms:**
  - Android `NotificationListenerService` mirrors incoming notifications (with app icons, title, and body) to desktop.
  - Dismissing a notification on Arch clears it on the phone.
  - Desktop inline replies invoke Android's `RemoteInput` API, allowing direct replies to WhatsApp, Signal, Telegram, and SMS from native desktop notification popups.

---

### 4.12 Cellular Bypass & SOCKS5 Proxy Node
- **Concept:** Route laptop traffic through the phone’s 5G/LTE connection when connected to restricted Wi-Fi.
- **Mechanisms:**
  - When connected to restrictive public or campus Wi-Fi (blocked ports, filtered sites), Singularity opens a SOCKS5 proxy tunnel over ADB.
  - Laptop network traffic routes transparently through the phone's mobile data without requiring Wi-Fi tethering.

---

### 4.13 Sidecar: Android as a Virtual Secondary Display
- **Concept:** Use the Android phone/tablet screen as an extended monitor for Arch Linux.
- **Mechanisms:**
  - Hyprland spawns a headless virtual output (`hyprctl output create headless`).
  - Wayland renders the workspace to a hardware-encoded H.264 stream.
  - The stream displays with sub-frame latency on Android via a low-latency surface receiver.

---

### 4.14 Background Task & Build Offloader
- **Concept:** Offload heavy background tasks to the phone’s multi-core Snapdragon processor to save laptop battery.
- **Mechanisms:**
  - Tasks (e.g. large `rsync` jobs, batch media conversions, background downloads) are dispatched to run inside **Termux / chroot** on the phone.
  - The phone executes them independently and alerts the laptop upon completion.

---

## 5. Architectural Comparison Matrix

| Feature / Capability | Apple Ecosystem (macOS + iOS) | KDE Connect | Singularity (Arch + Rooted Android) |
|---|---|---|---|
| **Underlying Transport** | Proprietary AWDL / BLE / Wi-Fi | Wi-Fi UDP Broadcast / TCP | **Persistent ADB TCP (:5555) + Tunnels** |
| **Connection Stability** | High (within Apple hardware) | Low (broken by Doze & AP isolation) | **Immune to Doze & router isolation** |
| **Background Clipboard** | 2-minute TTL; often drops | Fragile logcat & floating 1x1 activity hacks | **Headless Shell UID 2000 (continuous)** |
| **Calling Integration** | Apple apps only | Notification alert only | **scrcpy audio downlink + phone desk mic** |
| **SMS OTP Extraction** | Safari only | Notification display only | **System-wide regex -> instant auto-clipboard** |
| **Desk Macro Pad / HUD** | None native | Run predefined commands only | **Live hardware HUD + dynamic macro deck** |
| **Kernel Charge Limiter** | None | None | **sysfs battery charging cut-off at 80%** |
| **Filesystem Access** | Sandboxed AirDrop only | SFTP to `/sdcard` only (Scoped Storage) | **Full root filesystem mount to `/mnt/phone`** |
| **Biometric `sudo` Unlock** | Apple Watch only | None | **`pam_singularity` with phone fingerprint** |
| **Screen Mirroring FPS** | 60fps max; phone locked | None (experimental RDP) | **Up to 120fps (`scrcpy`), screen off, sub-30ms** |
| **Webcam (Continuity Cam)**| High quality (Mac/iOS only) | None | **`v4l2loopback` 1080p/4K 60fps into Linux kernel** |
| **Input Injection** | Universal Control (Mac/iPad only)| Android IME switch required | **Raw `/dev/uinput` kernel injection (zero lag)** |

---

## 6. Implementation Phasing Roadmap

```
Phase 1: Clipboard Core (In Progress)
  ├── singularity-daemon (Rust / Tokio / systemd --user)
  ├── Wire protocol codec (0x09 SET, 0x08 GET, 0x00 PUSH, 0x01 ACK)
  ├── wl-clipboard-rs integration & echo filter
  └── Headless scrcpy-server supervisor over ADB

Phase 2: Hardware & Desktop Multimedia Bridge
  ├── scrcpy one-click screen mirroring (screen off, 120fps)
  ├── Call audio downlink via scrcpy voice-call-downlink
  ├── Continuity camera via v4l2loopback
  └── Kernel charge limiter script (80% battery guard)

Phase 3: Android Companion App (Kotlin / Jetpack Compose)
  ├── NotificationListenerService & RemoteInput quick reply
  ├── SMS OTP regex auto-clip dispatcher
  ├── Telephony signaling (caller ID, answer, hangup)
  └── Battery & hardware state telemetry

Phase 4: Advanced Desktop Interactivity
  ├── Desktop HUD & Stream Deck mode on phone
  ├── pam_singularity biometric sudo & lockscreen unlock
  ├── Universal Control edge crossing via /dev/uinput
  └── Root SFTP / FUSE filesystem mount to /mnt/phone
```
