# Singularity

> **Next-Generation Hardware Convergence for Linux & Android**  
> Seamlessly bridge your Linux workstation and Android phone into one continuous computing fabric.

---

## What is Singularity?

**Singularity** is a seamless device continuity ecosystem designed for Linux power users. Inspired by features like Apple Continuity and KDE Connect, Singularity bridges your Linux desktop and your Android phone without the traditional compromises: no fragile local network discovery, no battery-draining background polling, and no restrictive sandboxes.

Whether you are typing `sudo` in a terminal, listening to music, managing files across devices, or locking your workstation as you walk away, Singularity makes your PC and phone feel like two screens of the exact same machine.

---

## How It Works (At a Glance)

1. **A Lightweight Desktop Daemon:** Runs quietly in the background on your Linux PC. It monitors your desktop state, audio sinks, clipboard, and security services.
2. **A Native Android Companion App:** Beautiful Material 3 interface that runs on your phone, providing remote controls, media streaming, and biometric authorization.
3. **An Always-Ready Connection Highway:** Unlike tools that break under phone sleep modes or public Wi-Fi router isolation, Singularity maintains an authenticated, resilient connection over local Wi-Fi or USB. When your devices are in range, they instantly discover and talk to each other without manual pairing or PIN prompts.

---

## Current Features

### 🔐 Biometric Security & Desktop Access
- **Fingerprint `sudo` in Terminal:** Execute administrative commands on your PC and simply touch your phone's fingerprint sensor to authenticate instead of typing your password.
- **Remote Lockscreen & Login Greeter Unlock:** Wake your laptop from sleep or cold boot at the greeter, and unlock your desktop session directly from your phone.
- **Hardware Keyboard Override:** If you prefer typing your password or your phone isn't nearby, pressing `Esc` or typing directly on your PC keyboard instantly cancels the phone prompt with zero delay.
- **Context-Aware Smart Action Button:** The floating button on your phone automatically transforms based on your PC's state—showing **Log In** at the greeter, **Unlock** at the lockscreen, and **Lock** when you are actively logged in.

---

### 🔔 Bidirectional Notification Sync & Remote Actions
- **Desktop Alerts on Phone:** Linux desktop notifications (`org.freedesktop.Notifications`) instantly mirror to your Android device with clean app names, icons, and interactive action buttons.
- **Phone Notifications on Desktop:** Incoming phone notifications mirror seamlessly to your desktop via native notifications with reply and dismiss actions.
- **Remote Action Execution:** Tap actions directly from your notification popups (e.g., WhatsApp Reply, YouTube controls, Gmail Archive) or use the desktop inline reply prompt (`zenity`) to send replies straight to your phone.
- **Audio Device Management:** Full visibility over all audio sinks (HDMI/DisplayPort, DACs, internal speakers) with customizable hide/unhide toggles and auto-healing for dormant HDMI outputs.

---

### 🔊 Audio Convergence & Latency Control
- **Stream Phone Audio to PC:** Route audio from apps like YouTube or Spotify directly to your laptop's built-in speakers or connected external monitors (e.g., Odyssey G5).
- **Instant Output Switching:** Switch audio targets on the fly between your Phone Speaker, Laptop Speakers, and External Displays right from the phone.
- **Low-Latency Mode Switch:** Toggle between **Low Latency Mode** (~45ms buffer) for real-time video/gaming and **Standard Mode** (~110ms buffer) for rock-solid, crackle-free playback.
- **Smart App Name Badging:** Active output banners clearly display the human-friendly application name (e.g., `via Spotify` or `via YouTube`).
- **Android Quick Settings Tile:** A dedicated system tile lets you adjust remote laptop volume and switch audio outputs with a single swipe down.

---

### 📋 Universal Clipboard Sync
- **Instant Two-Way Synchronization:** Copy any text, URL, or code snippet on your Linux desktop, and it is instantly ready to paste on your phone—and vice-versa.
- **Zero-Flicker Background Sync:** Operates transparently without opening the app or interrupting what you're doing.

---

### 📁 Remote File Browser & Media Streaming
- **Full PC Filesystem Navigation:** Browse your PC's entire storage from your phone with fast directory navigation, breadcrumb navigation, and search.
- **Zero-Download Media Streaming:** Stream high-definition videos (`.mp4`, `.mkv`, `.webm`) and music directly from your PC without waiting for files to download. Includes intuitive gesture controls for brightness, volume, and timeline scrubbing.
- **Image, Code & Document Previews:** View full-resolution photos with pinch-to-zoom, preview text/code files with syntax formatting, and open documents seamlessly.
- **Virtual Archive Browser:** Browse inside `.zip`, `.7z`, `.tar.gz`, and `.rar` archives as if they were regular folders, previewing files without extracting the archive to disk.
- **Fast Resumable Downloads:** Transfer individual files or entire directory trees to your phone with automatic resume if the connection drops.
- **Biometric Root Protection:** Browsing sensitive system folders or toggling elevated mode requires phone fingerprint verification.

---

### 💾 Transparent Phone Storage on Your PC
- **Auto-Mounted Drive (`/mnt/phone`):** As soon as your phone connects, its internal storage automatically appears as a regular drive on your Linux workstation. You can drag and drop files using your favorite desktop file manager (Nautilus, Dolphin, Thunar, or Yazi).
- **Rootless & Clean:** Requires no Termux setup or extra servers installed on your phone.

---

### ⚡ Remote Power Management
- **Full Power Lifecycle:** Lock your screen, put your PC to sleep, reboot, or shut down remotely from across the room.
- **Biometric Safeguard:** Destructive power actions (Shutdown and Reboot) require biometric confirmation on your phone to prevent accidental triggers.

---

### 📷 Continuity Camera (Phone as Webcam)
- **High-Definition Wireless Webcam:** Stream your phone’s high-resolution camera as a virtual Linux camera (`/dev/video*`), usable in Zoom, Discord, Google Meet, and OBS at smooth 60fps.

---

### 🌐 Browser Tab Continuity
- **Send Desktop Tabs to Phone:** Push your active desktop browser tab to your phone with a single click via the browser extension or a terminal command (`singularity open-url <url>`). Links open cleanly in Chrome or your default Android browser.

---

### ✨ Tactile Material 3 Design
- **Polished Dark Aesthetic:** Designed with Catppuccin-themed styling to match modern Linux desktop aesthetics.
- **Universal Tactile Feedback:** Crisp haptic vibrations accompany every button press, slider drag, and gesture across the app.

---

## Supported Environments

| Platform / Compositor | Status | Notes |
| :--- | :--- | :--- |
| **Arch Linux (Hyprland, Niri, Sway, etc.)** | 🟢 **Primary / Verified** | Fully tested and validated on real hardware. Uses standard Wayland protocols, PipeWire, and PAM. Dedicated zero-latency event socket integration when running on Hyprland. |
| **Fedora Linux (Hyprland, Niri, Sway, etc.)** | 🟡 **Experimental** | Automated setup provided (`setup-fedora.sh`). Built on universal Wayland, systemd-logind, and PipeWire standards, but **not yet tested on real hardware**. |
| **Other Wayland Compositors (River, GNOME, KDE)** | 🟢 **Universal Fallback** | Core continuity features (Clipboard, Biometric PAM, Audio Routing, Remote Storage, Media Streaming) work natively across all Wayland environments via standard Linux protocols. |
| **Android Device** | 🟢 **Android 11+** | Tested on Android 16 (Evolution X). Works seamlessly on standard unrooted devices; root (KernelSU/Magisk) is strictly optional for advanced features like system root mounting. |

---

## System Dependencies

The automated setup scripts (`setup.sh` on Arch or `setup-fedora.sh` on Fedora) automatically install and configure these host dependencies:

| Category | Arch Linux | Fedora Linux | Purpose |
| :--- | :--- | :--- | :--- |
| **Android Transport** | `android-tools`, `curl` | `android-tools`, `curl` | Persistent ADB connection (`:5555`), device discovery, and APK download |
| **Core Compilation** | `rust`, `cargo`, `pam` | `rust`, `cargo`, `gcc`, `clang`, `pam-devel`, `make` | Compiling the daemon and the biometric PAM security module |
| **Audio Routing** | `pipewire`, `wireplumber` | `pipewire`, `wireplumber`, `pulseaudio-utils` | Live audio capture, sink routing (`pactl`), and latency control |
| **Wayland Clipboard** | `wl-clipboard` | `wl-clipboard` | Universal Wayland clipboard synchronization (`wlr-data-control`) |
| **Storage & Previews** | `madbfs`, `fuse3`, `ripgrep`, `fd`, `7zip`, `ffmpeg` | `madbfs` (via cargo), `fuse3`, `ripgrep`, `fd-find`, `7zip`, `ffmpeg` | Phone drive mounting (`/mnt/phone`), fast search, archive inspection, and thumbnails |
| **Video & Camera** | `scrcpy`, `v4l2loopback-dkms` | `scrcpy`, `akmod-v4l2loopback` | High-throughput capture highway and wireless continuity webcam |
| **Optional Lockers** *(Optional)* | `hyprlock`, `swaylock`, `dms` | `hyprlock`, `swaylock`, `dms` | Screen lock/unlock detection (Singularity auto-detects your locker or falls back to `systemd-logind`) |

*Note: On Fedora, multimedia and hardware packages (`ffmpeg`, `scrcpy`, `akmod-v4l2loopback`) are installed via RPM Fusion. DankMaterialShell (DMS) is strictly optional; if you happen to use DMS, pass `--dms` to configure its custom QML focus fix. If you wish to build the Android APK from source using `--build-apk`, Java 21 (`java-21-openjdk-devel`) and the Android SDK are also required.*

---

## Quick Start

### 1. Arch Linux (Primary)

Clone the repository and run the comprehensive setup script:

```bash
git clone https://github.com/kanhaji/singularity.git
cd singularity
./scripts/setup.sh --all
```

The script builds the desktop daemon, installs the PAM biometric module, configures lockscreen integration, and deploys the companion APK to your connected phone.

---

### 2. Fedora Linux (Experimental)

On Fedora (Hyprland, Niri, Sway, or any Wayland session):

```bash
./setup-fedora.sh --all
```

*Note: The Fedora setup script automatically manages 64-bit PAM directories (`/usr/lib64/security`), SELinux contexts (`restorecon`), and `firewalld` port openings (UDP 2829 & TCP 8920).*

---

### 3. Android Companion App

The setup scripts automatically fetch the pre-built companion APK from [GitHub Releases](https://github.com/kanha321/Singularity/releases/tag/v0.2.0) and install it directly to your connected phone. **No Java or Android SDK is required to install or use Singularity.**

If you are developing or wish to build the Android app from source:

```bash
# Via setup script:
./scripts/setup.sh --android --build-apk

# Or directly with Gradle (requires JDK 21 and Android SDK):
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open **Singularity** on your phone, ensure Wireless Debugging or USB connection is enabled, and your PC will pair automatically.

---

## Architecture Overview

Singularity is built using a clean, layered approach:

```text
┌────────────────────────────────────────────────────────┐
│                   Android Companion                    │
│   Jetpack Compose UI • Media Player • Biometric Prompt │
└───────────────────────────┬────────────────────────────┘
                            │
               Authenticated TCP Link / ADB
                            │
┌───────────────────────────▼────────────────────────────┐
│               Singularity Desktop Daemon               │
│        Tokio Async Engine • PipeWire Audio Router      │
│     Wayland Clipboard Watcher • HTTP Streaming Server  │
└──────────────┬──────────────────────────┬──────────────┘
               │                          │
┌──────────────▼───────────┐  ┌───────────▼──────────────┐
│     Linux System & PAM   │  │   Desktop Compositor     │
│  pam_singularity • FUSE  │  │ Hyprland / Niri / Sway   │
│  systemd • NetworkManager│  │ DMS / Hyprlock Locker    │
└──────────────────────────┘  └──────────────────────────┘
```

- **Universal Core:** File management, biometric PAM, audio routing, and clipboard synchronization adhere strictly to POSIX, systemd, PipeWire, and Wayland standards.
- **Graceful Shell Adapters:** If a specific desktop shell or compositor feature (like Hyprland’s socket2) is absent, Singularity gracefully falls back to standard systemd and interval polling timers without interruption.

---

## Roadmap & Planned Features

- 📞 **Desktop Telephony:** View incoming cellular call alerts, answer/reject calls, and reply to SMS directly from your desktop.
- 🖥️ **Virtual Display Expansion:** Headless virtual display mirroring to expand your Linux desktop onto your phone screen.

---

## License

This project is licensed under the [MIT License](LICENSE) or Apache 2.0.

