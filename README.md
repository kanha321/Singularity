# Singularity

> **Next-Generation Hardware Convergence for Linux & Android**  
> Seamlessly bridge your Linux workstation and Android phone into one continuous computing fabric.

---

## What is Singularity?

**Singularity** is a seamless device continuity ecosystem designed for Linux power users. Inspired by features like Apple Continuity and KDE Connect, Singularity bridges your Linux desktop and your Android phone without the traditional compromises: no fragile local network discovery, no battery-draining background polling, and no restrictive sandboxes.

Whether you are typing `sudo` in a terminal, listening to music, managing files across devices, locating a misplaced phone, or locking your workstation as you walk away, Singularity makes your PC and phone feel like two screens of the exact same machine.

---

## How It Works (At a Glance)

1. **A Lightweight Desktop Daemon:** Runs quietly in the background on your Linux PC (via `systemd --user`). It monitors desktop state, audio sinks, clipboard, notifications, and security services.
2. **A Native Android Companion App:** Beautiful Material 3 interface running on your phone, providing remote controls, media streaming, hardware-attested biometric authorization, and system Quick Settings integration.
3. **An Always-Ready Connection Highway:** Singularity maintains an authenticated, resilient connection over local Wi-Fi or USB. Devices discover and pair via cryptographic challenge-response without manual PIN prompts, surviving phone sleep and network transitions.

---

## Current Features

### 🔐 Hardware-Attested Biometric Security & Desktop Access
- **Cryptographic Challenge-Response PAM (`pam_singularity`):** Linux PAM integration backed by the Android KeyStore (StrongBox / Hardware TEE). Every authentication request generates a single-use cryptographic challenge signed by the phone's hardware-isolated private key (ECDSA P-256) and verified on PC. Immune to replay attacks and plaintext spoofing.
- **Fingerprint `sudo` in Terminal:** Execute administrative commands on your PC and simply touch your phone's fingerprint sensor to authenticate instead of typing your password.
- **Remote Lockscreen & Login Greeter Unlock:** Wake your laptop from sleep or cold boot at the greeter, and unlock your desktop session directly from your phone.
- **Hardware Keyboard Override:** If you prefer typing your password or your phone isn't nearby, pressing `Esc` or typing directly on your PC keyboard instantly cancels the phone prompt with zero delay.
- **Hold-to-Lock Action Button:** The floating button on your phone dynamically updates with PC session state (**Log In** at greeter, **Unlock** at lockscreen, **Hold to Lock** when logged in). Hold-to-lock features smooth circular progress animation, tactile haptics, and auto-syncs with desktop lock state.
- **Universal Lock Integration:** Full native support for `systemd-logind` session locking as well as compositors like Hyprland (`hyprlock`) and DankMaterialShell (`dms`).
- **Dynamic Device Identity:** Dynamically resolves and displays real hardware device names and models (e.g., *POCO F5*, *Pixel 8 Pro*) across pairing and authentication events.

---

### 🔊 Audio Convergence & Latency Control
- **Android Quick Settings Audio Route Tile:** A dedicated system QS tile lets you toggle phone audio output with a single swipe down.
  - **Phone Speaker (`STATE_INACTIVE`):** Local on-device audio playback. Subtitle displays `"Phone Speaker"`.
  - **Laptop Speaker (`STATE_ACTIVE`):** Streams phone audio directly to the laptop's active audio sink. Subtitle displays `"Laptop: <Sink Nick>"` (e.g. `"Laptop: Odyssey G5"`).
  - **Disconnected (`STATE_UNAVAILABLE`):** Indicated whenever PC is unreachable or lacks audio sinks.
- **Stream Phone Audio to PC:** Route audio from apps like YouTube, Spotify, or media players directly to your laptop's built-in speakers or external monitor outputs.
- **Low-Latency Mode Switch:** Toggle between **Low Latency Mode** (~45ms buffer) for real-time video/gaming and **Standard Mode** (~110ms buffer) for rock-solid, crackle-free playback.
- **Audio Device Management:** Manage all PC audio sinks with customizable visibility toggles and auto-healing for dormant HDMI/DisplayPort outputs.
- **Smart App Name Badging:** Active output banners display human-friendly application names (e.g., `via Spotify` or `via YouTube`).

---

### 🔔 Bidirectional Notification Sync & Remote Actions
- **Desktop Alerts on Phone:** Linux desktop notifications (`org.freedesktop.Notifications`) instantly mirror to your Android device with clean app names, icons, and interactive action buttons.
- **Phone Notifications on Desktop:** Incoming phone notifications mirror seamlessly to your desktop via native notifications with reply and dismiss actions.
- **Remote Action Execution & Quick Reply:** Tap actions directly from notification popups (e.g., WhatsApp Reply, YouTube controls, Gmail Archive) or use the desktop inline reply prompt (`zenity`) or CLI to send replies straight to your phone.

---

### 🔍 Find My Phone (Emergency Siren)
- **Instant Terminal Locator:** Misplaced your phone under a couch cushion or in another room? Run `singularity find` to instantly trigger the siren.
- **DND & Silent Mode Override:** Rings the phone alarm at 100% volume regardless of silent mode, vibrate-only, or Do Not Disturb settings.
- **High-Visibility Visual Alert:** Displays a pulsing high-visibility alert screen on the phone with an interactive *"I Found My Phone"* button.
- **Two-Way Cancellation:** Silence the siren by tapping the button on your phone or by running `singularity find --stop` in the terminal.

---

### 📷 Continuity Camera (Phone as Webcam)
- **High-Definition Wireless Webcam:** Stream your phone’s high-resolution camera as a virtual Linux camera (`/dev/video*`), usable in Zoom, Discord, Google Meet, and OBS at smooth 60fps.
- **Granular CLI Control:** Switch lenses (`main`, `ultrawide`, `front`), adjust digital zoom (`0.65x` to `10.0x`), toggle the hardware torch/fill-light, and pipe phone audio to a virtual PipeWire microphone.
- **Power-Saving & Conflict Management:** Automatically turns off the phone screen during streaming to prevent overheating, and detects and handles conflicting loopback readers gracefully.

---

### 📋 Universal Clipboard Sync
- **Instant Two-Way Synchronization:** Copy any text, URL, or code snippet on your Linux desktop, and it is instantly ready to paste on your phone—and vice-versa.
- **Zero-Flicker Background Sync:** Operates transparently without opening the app or interrupting your workflow.

---

### 📁 Remote File Browser & Media Streaming
- **Full PC Filesystem Navigation:** Browse your PC's entire storage from your phone with fast directory navigation, breadcrumbs, and search.
- **Zero-Download Media Streaming:** Stream high-definition videos (`.mp4`, `.mkv`, `.webm`) and music directly from your PC without waiting for downloads. Includes intuitive gesture controls for brightness, volume, and timeline scrubbing.
- **Image, Code & Document Previews:** View full-resolution photos with pinch-to-zoom, preview text/code files with syntax formatting, and open documents seamlessly.
- **Virtual Archive Browser:** Browse inside `.zip`, `.7z`, `.tar.gz`, and `.rar` archives as if they were regular folders, previewing files without extracting to disk.
- **Fast Resumable Downloads:** Transfer individual files or entire directory trees to your phone with automatic resume if the connection drops.
- **Biometric Root Protection:** Browsing sensitive system folders or toggling elevated mode requires phone fingerprint verification.

---

### 💾 Transparent Phone Storage on Your PC
- **Auto-Mounted Drive (`/mnt/phone`):** As soon as your phone connects, its internal storage automatically appears as a regular drive on your Linux workstation. Drag and drop files using your favorite desktop file manager (Nautilus, Dolphin, Thunar, or Yazi).
- **Rootless & Clean:** Requires no Termux setup or extra servers installed on your phone.

---

### ⚡ Remote Power Management
- **Full Power Lifecycle:** Lock your screen, put your PC to sleep, reboot, or shut down remotely from across the room.
- **Biometric Safeguard:** Destructive power actions (Shutdown and Reboot) require biometric confirmation on your phone to prevent accidental triggers.

---

### 🌐 Browser Tab Continuity
- **Send Desktop Tabs to Phone:** Push your active desktop browser tab to your phone with a single click via the browser extension or a terminal command (`singularity open-url <url>`). Links open cleanly in Chrome or your default Android browser.

---

### ✨ Tactile Material 3 Design
- **Polished Dark Aesthetic:** Designed with Catppuccin-themed styling to match modern Linux desktop aesthetics.
- **Universal Tactile Feedback:** Crisp haptic vibrations accompany every button press, slider drag, and gesture across the app.

---

## Command Line Interface (`singularity`)

Singularity provides a unified command line tool to inspect status, control devices, and trigger continuity actions:

| Command | Description |
| :--- | :--- |
| `singularity status` | Inspect daemon connection and individual feature statuses |
| `singularity find` | Locate phone by ringing at 100% volume (overriding DND) |
| `singularity find --stop` | Silence active phone siren |
| `singularity auth [-r <reason>]` | Trigger biometric fingerprint challenge on connected phone |
| `singularity camera start` | Start wireless webcam stream (`--lens`, `--fps`, `--no-mic`) |
| `singularity camera stop` | Stop active webcam stream |
| `singularity camera status` | Inspect webcam status, active device, and reader count |
| `singularity camera lens <name>` | Switch camera lens (`main`, `ultrawide`, `front`) |
| `singularity camera torch <on/off>` | Toggle hardware flash/fill-light |
| `singularity camera zoom <level>` | Set digital zoom level (e.g. `0.65`, `1.5`, `2.0`) |
| `singularity clip <text>` | Push text or URL to device clipboard |
| `singularity open-url <url>` | Open URL on connected Android device |
| `singularity mount [path]` | Mount phone storage to `/mnt/phone` (or custom path) via FUSE |
| `singularity unmount` | Unmount phone storage |
| `singularity notifications` | List active mirrored phone notifications |
| `singularity reply <id> <text>` | Send inline quick reply to notification |
| `singularity devices` | List detected, connected, and paired devices |
| `singularity use <serial>` | Switch active target device by serial or IP |
| `singularity pair <serial>` | Add device to trusted paired whitelist |
| `singularity unpair <serial>` | Remove device from trusted paired whitelist |
| `singularity remount [--rw\|--ro]` | Remount system partitions as read-write or read-only (root) |

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
./scripts/setup-fedora.sh --all
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
│         Hardware Keystore (StrongBox/TEE ECDSA)        │
│             Quick Settings Audio Route Tile            │
└───────────────────────────┬────────────────────────────┘
                            │
               Authenticated TCP Link / ADB
               Challenge-Response Pairing Bus
                            │
┌───────────────────────────▼────────────────────────────┐
│               Singularity Desktop Daemon               │
│        Tokio Async Engine • PipeWire Audio Router      │
│     Wayland Clipboard Watcher • HTTP Streaming Server  │
│         Cryptographic Nonce & Signature Verifier       │
└──────────────┬──────────────────────────┬──────────────┘
               │                          │
┌──────────────▼───────────┐  ┌───────────▼──────────────┐
│     Linux System & PAM   │  │   Desktop Compositor     │
│  pam_singularity • FUSE  │  │ Hyprland / Niri / Sway   │
│  systemd • NetworkManager│  │ DMS / Hyprlock Locker    │
└──────────────────────────┘  └──────────────────────────┘
```

- **Universal Core:** File management, biometric PAM, audio routing, and clipboard synchronization adhere strictly to POSIX, systemd, PipeWire, and Wayland standards.
- **Graceful Shell Adapters:** If a specific desktop shell or compositor feature (like Hyprland’s socket2) is absent, Singularity gracefully falls back to standard `systemd-logind` and D-Bus interfaces without interruption.

---

## Roadmap & Planned Features

- 📞 **Desktop Telephony:** View incoming cellular call alerts, answer/reject calls, and reply to SMS directly from your desktop.
- 🖥️ **Virtual Display Expansion:** Headless virtual display mirroring to expand your Linux desktop onto your phone screen.

---

## License

This project is licensed under the [MIT License](LICENSE) or Apache 2.0.
