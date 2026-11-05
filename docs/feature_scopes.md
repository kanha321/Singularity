# Feature Scope & Portability Catalog — Singularity

This document catalogues the **architectural scope and portability boundaries** for every feature in the **Singularity** ecosystem. It specifies the exact layer at which each capability operates—distinguishing between shell-specific configs, compositor-specific hooks, Wayland-generic protocols, systemd standards, Linux kernel/PAM universals, and phone-side privilege requirements.

---

## 1. Scope Hierarchy Overview

The Singularity architecture is designed as a layered onion model: core services operate at the highest level of Linux OS universality, while shell-specific interfaces are decoupled into swappable adapters.

```text
┌────────────────────────────────────────────────────────────────────────┐
│ LEVEL 1: Shell / Config-Specific      (Caelestia only / DMS only)      │
├────────────────────────────────────────────────────────────────────────┤
│ LEVEL 2: Compositor-Specific          (Hyprland only)                  │
├────────────────────────────────────────────────────────────────────────┤
│ LEVEL 3: Display-Server-Specific      (All Wayland compositors)        │
├────────────────────────────────────────────────────────────────────────┤
│ LEVEL 4: Session / Init-Specific      (All systemd / elogind distros)  │
├────────────────────────────────────────────────────────────────────────┤
│ LEVEL 5: Network-Stack-Specific       (All NetworkManager systems)     │
├────────────────────────────────────────────────────────────────────────┤
│ LEVEL 6: Distro / Package-Specific    (Arch Linux package setup)       │
├────────────────────────────────────────────────────────────────────────┤
│ LEVEL 7: Universal Linux Kernel / PAM (All Linux distributions)        │
├────────────────────────────────────────────────────────────────────────┤
│ LEVEL 8: Completely OS-Agnostic       (Pure Rust / POSIX / HTTP)       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Master Feature Scope Matrix

| Feature | Feature Scope | Desktop Layer & Subsystem | Phone Requirement | Root Needed? |
| :--- | :--- | :--- | :--- | :---: |
| **Biometric `sudo` Authorization** | 🐧 **Universal Linux** | Linux PAM (`pam_singularity.so`) + UNIX socket | Android App (`AppBridgeService`) | ❌ No |
| **Biometric GUI Greeter / Lock Unlock** | 🐧 **Universal Linux** | Linux PAM + Kernel `evdev` (`/dev/input/*`) | Android App (`AppBridgeService`) | ❌ No |
| **Hardware Keyboard Cancellation** | 🐧 **Universal Linux** | Linux Kernel `evdev` + POSIX `/dev/tty` | None (Local hardware keyboard) | ❌ No |
| **Caelestia Lock & Unlock** | 🐚 **Caelestia Only** | `caelestia shell lock {isLocked,lock,unlock}` | Android Homescreen Lock FAB | ❌ No |
| **DMS Lock & Unlock** | 🐚 **DMS Only** | `dms ipc call lock {isLocked,lock,unlock}` | Android Homescreen Lock FAB | ❌ No |
| **Hyprlock Surface Lock Probe** | 🪟 **Hyprland Only** | `hyprctl layers` surface probe | Android Homescreen Lock FAB | ❌ No |
| **Hyprland Zero-Latency Event Stream** | 🪟 **Hyprland Only** | Socket2 (`/run/user/<UID>/hypr/*/.socket2.sock`) | None (Local event streamer) | ❌ No |
| **Pre-Auth Display Wake** | 🌐 **All Wayland** | Virtual keyboard protocol via `wtype -k Return` | None (Triggered before auth) | ❌ No |
| **Bidirectional Clipboard Sync** | 🌐 **All Wayland** | `wlr-data-control` protocol (`wl-clipboard-rs`) | Android `scrcpy-server` highway | ❌ No |
| **Power Management (`reboot`, etc.)** | ⚙️ **All systemd Distros**| `systemctl {reboot,poweroff,suspend,hibernate}`| Android Power Grid & FAB | ❌ No |
| **Session Tracking (`isLoggedIn`)** | ⚙️ **All systemd Distros**| `loginctl list-sessions` parsing | Android UI session state | ❌ No |
| **Generic Lock/Unlock Fallback** | ⚙️ **All systemd Distros**| `loginctl lock-session` / D-Bus `ScreenSaver` | Android Homescreen Lock FAB | ❌ No |
| **Persistent Daemon Background Run** | ⚙️ **All systemd Distros**| `systemd --user` unit + `loginctl enable-linger` | None (Host service manager) | ❌ No |
| **Wi-Fi Auto-Reconnection** | 📶 **NetworkManager** | NM Dispatcher (`/etc/NetworkManager/dispatcher.d`) | Wireless ADB enabled (`:5555`) | ❌ No |
| **Automated System Setup (`setup.sh`)**| 📦 **Arch Linux Only** | Arch `pacman` dependencies & Arch PAM layouts | None | ❌ No |
| **Transparent Storage Mount (`/mnt/*`)** | 🐧 **Universal Linux** | Userspace FUSE (`sshfs` over ADB TCP tunnel) | Root OpenSSH / SFTP daemon | ⚡ **Yes** |
| **HTTP File Server & Streaming** | 💻 **OS-Agnostic** | Pure Rust Tokio HTTP engine (Port 8920) | Android File Browser Tab | ❌ No |
| **Archive Virtual Inspection / Unpack**| 💻 **OS-Agnostic** | Posix process pipe (`7z l -slt`, `7z e`, `7z x`)| Android Archive Viewer | ❌ No |
| **Browser Tab Transfer (`open-url`)** | 💻 **OS-Agnostic** | HTTP endpoint `/api/browser/open` + Web Extension | Standard Android `am start VIEW` | ❌ No |
| **Desktop Telephony (Call Controls)** | 💻 **OS-Agnostic** | Pure Rust IPC commands | Android `telecom` CLI via ADB | ❌ No |

---

## 3. Detailed Scope Breakdown by Level

### Level 1: Shell / Config-Specific Scope
These features hook directly into specific desktop shell IPC mechanisms. They are decoupled via the `LockerAdapter` trait (`desktop/src/singularity-daemon/src/lock_watcher/adapter.rs`).

* **Caelestia Shell Adapter (`caelestia.rs`):**
  * **Target:** Systems running the Caelestia QuickShell desktop (`qs -c caelestia`).
  * **IPC Mechanism:** Invokes `caelestia shell lock isLocked`, `caelestia shell lock lock`, and `caelestia shell lock unlock`.
  * **Behavior:** Directly triggers or clears the Caelestia QML lockscreen overlay.
* **DankMaterialShell Adapter (`dms.rs`):**
  * **Target:** Systems running DankMaterialShell (DMS).
  * **IPC Mechanism:** Invokes `dms ipc call lock isLocked`, `dms ipc call lock lock`, and `dms ipc call lock unlock`.

---

### Level 2: Compositor-Specific Scope (Hyprland Only)
These capabilities leverage internal Hyprland IPC sockets and commands.

* **Hyprland Socket2 Real-Time Streamer (`socket2.rs`):**
  * **Target:** Exclusively Hyprland.
  * **Mechanism:** Streams Unix domain socket events from `/run/user/<UID>/hypr/$HYPRLAND_INSTANCE_SIGNATURE/.socket2.sock`.
  * **Events Handled:** `lockscreen>>1`, `lockscreen>>0`, `workspace>>`, `openwindow>>`, `closewindow>>`.
  * **Graceful Degradation:** If Hyprland is not running or the socket is unreachable, the daemon automatically falls back to a 1-second heartbeat polling cycle.
* **Hyprlock Layer Surface Detector (`hyprlock.rs`):**
  * **Target:** Hyprland compositor running `hyprlock`.
  * **Mechanism:** Runs `hyprctl layers` to inspect the Wayland layer shell tree for namespace `hyprlock`.
* **Compositor Clean Exit (`trigger_pc_logout`):**
  * **Mechanism:** Runs `hyprctl dispatch exit` when under Hyprland, falling back to `loginctl terminate-session` otherwise.

---

### Level 3: Display-Server-Specific Scope (All Wayland Compositors)
These features function on **any Wayland compositor** supporting standard Wayland protocol extensions (Hyprland, Sway, River, Wayfire, Niri, Cosmic, labwc).

* **Bidirectional Clipboard Synchronization (`wayland/`):**
  * **Target:** All Wayland compositors implementing `wlr-data-control-unstable-v1`.
  * **Mechanism:** Uses `wl-clipboard-rs` to read from and write to both `ClipboardType::Regular` and `ClipboardType::Primary`.
  * **Limitation:** Does not support legacy X11 sessions (requires an `xclip`/`xsel` backend) and is restricted under standard GNOME Wayland due to sandboxed background clipboard access.
* **Pre-Auth Display Wake (`wtype`):**
  * **Target:** Wayland compositors supporting the virtual keyboard protocol.
  * **Mechanism:** Dispatches `wtype -k Return` to turn on displays and invoke PAM authentication prompts before the user interacts with their device.

---

### Level 4: Session / Init-Specific Scope (All `systemd` / `elogind` Distros)
These features operate across virtually all modern mainstream Linux distributions (Arch, Fedora, Debian, Ubuntu, openSUSE, Pop!_OS, Linux Mint, Manjaro).

* **System Power Controls (`session.rs`):**
  * **Mechanism:** Issues D-Bus commands via `systemctl {reboot, poweroff, suspend, hibernate}`.
* **Session Login Tracking:**
  * **Mechanism:** Evaluates `loginctl list-sessions --no-legend` to determine seat occupancy and login state (`is_logged_in`).
* **Universal Lockind Adapter (`logind.rs`):**
  * **Mechanism:** Uses `loginctl lock-session` / `unlock-session` and reads `LockedHint` via systemd or `org.freedesktop.ScreenSaver` over D-Bus.
* **Service Supervisor Lifecycle:**
  * **Mechanism:** Packaged as a standard `systemd --user` service with user lingering (`loginctl enable-linger`) so the convergence engine stays alive across logout and lock boundaries.

---

### Level 5: Network-Stack-Specific Scope (NetworkManager Systems)
* **Wi-Fi Hotspot & Home Network Auto-Discovery:**
  * **Target:** Linux systems managing wireless profiles with NetworkManager.
  * **Mechanism:** `/etc/NetworkManager/dispatcher.d/90-adb-connect` evaluates `$2 == "up"` and `$CONNECTION_ID == "<HOME_SSID>"` to initiate `adb connect <IP>:5555`.
  * **Fallback:** On `systemd-networkd`, `iwd`, or static networks, connectivity can be triggered via `singularity connect <IP>` or a custom timer.

---

### Level 6: Distro / Package-Specific Scope (Arch Linux Only)
* **Automated Installation Script (`scripts/setup.sh`):**
  * **Scope:** Tailored for Arch Linux and Arch-based derivatives (EndeavourOS, Manjaro).
  * **Details:** Hardcodes `pacman -S` invocations for host packages (`android-tools`, `sshfs`, `fd`, `jdk21-openjdk`) and links `/etc/pam.d/sudo`.
  * **Portability Note:** The compiled binaries (`singularity-daemon`, `singularity-cli`, `pam_singularity.so`) contain no distribution-specific code and run on any Linux ELF x86_64 system.

---

### Level 7: Universal Linux Kernel / PAM Scope (All Linux Distros)
These features depend solely on the Linux Kernel, POSIX system calls, and the PAM C ABI. They function identically across **any** Linux distribution (Debian, Ubuntu, Fedora, Alpine, Void, NixOS, etc.).

* **Biometric PAM Security Module (`pam_singularity.so`):**
  * **Scope:** Standard Linux Pluggable Authentication Module (`/usr/lib/security/pam_singularity.so`).
  * **Compatible Targets:** `sudo`, `su`, `doas`, `login`, `polkit-1`, GDM, SDDM, LightDM, `hyprlock`, `swaylock`.
  * **Handshake:** Bridges PAM authentication requests through `/run/user/<UID>/singularity.sock` to the Android companion app.
* **Hardware Keyboard Cancel Watcher (`input.rs`):**
  * **Scope:** Linux Kernel `evdev` interface & POSIX TTY.
  * **How it works:**
    * In CLI sessions: Monitors `/dev/tty` for key events.
    * In Display Managers & Lock screens: Directly probes `/dev/input/by-path/*-event-kbd` using raw Linux `poll()`. Tapping `Enter` or `Esc` immediately cancels biometric verification and falls back to password entry.
* **Transparent Storage Auto-Mounting (`/mnt/phone`, `/mnt/root`):**
  * **Scope:** Universal Linux FUSE (`fuse3` + `madbfs`).
  * **Permissions:** Runs in unprivileged user space by pointing to pre-created mount directories owned by UID 1000. No root or Termux required for `/mnt/phone` (`/sdcard`).

---

### Level 8: Completely OS-Agnostic Scope (Rust / POSIX / HTTP)
These features contain zero platform-dependent assumptions and operate across any operating system (Linux, macOS, BSD).

* **Embedded HTTP File Server & Streaming Engine:**
  * **Port:** `http://127.0.0.1:8920`
  * **Capabilities:** Streaming HTTP file server, chunked byte-range requests for video playback, async file deletion/creation, directory listing, and recursive folder size calculations.
* **Archive Virtual Inspection Engine:**
  * **Mechanism:** Wraps 7-Zip CLI (`7z`) to provide virtual archive navigation and single-file stream decompression without prior extraction.
* **Browser Tab Push (`open-url`):**
  * **Mechanism:** HTTP receiver endpoint (`/api/browser/open`) dispatches URLs to Android via standard `am start` intents.
* **Desktop Telephony Integration:**
  * **Mechanism:** Accepts and terminates cellular calls via Android's built-in `telecom` utility.

---

### Level 9: Phone-Side Privilege Boundaries (Android)

| Capability | Android Privilege Level | Minimum OS | Mechanism |
| :--- | :---: | :---: | :--- |
| **Biometric Auth Prompt** | ❌ **Non-Root (Standard App)** | Android 10+ | Android `BiometricPrompt` API via `AppBridgeService` |
| **Bidirectional Clipboard** | ❌ **Non-Root (Standard ADB)** | Android 5.0+ | `scrcpy-server` process deployed over ADB |
| **Browser Tab Receiving** | ❌ **Non-Root (Standard ADB)** | Android 5.0+ | `am start -a android.intent.action.VIEW -d <url>` |
| **Desktop Telephony** | ❌ **Non-Root (Standard ADB)** | Android 8.0+ | `telecom accept-call` / `telecom end-call` |
| **Homescreen Companion UI** | ❌ **Non-Root (Standard App)** | Android 10+ | Jetpack Compose + Material 3 UI |
| **Transparent Storage (/mnt/phone)** | ❌ **Non-Root (Standard ADB UID 2000)** | Android 5.0+ | Modern ADB FUSE (`madbfs`), full access to `/sdcard` & `/sdcard/Android/*` |
| **System Root Mount (/mnt/root)** | ⚡ **Rooted Android (Optional)** | Android 10+ | KernelSU / Magisk root filesystem mount via `madbfs` |

---

## 4. Environment Transition & Portability Guide

If you change components of your workstation environment, consult this guide to understand the impact:

### Scenario A: Switching Desktop Shells (e.g. Caelestia → DMS or Hyprlock)
* **Impact:** **Zero configuration or code changes required.**
* **Mechanism:** The `LockerDetector` automatically probes available shells in order (`Caelestia` → `DMS` → `Hyprlock` → `Logind`) upon daemon startup and dynamically routes locking, unlocking, and status inspection to the active shell.

### Scenario B: Switching Wayland Compositors (e.g. Hyprland → Sway, River, or Niri)
* **What keeps working:**
  * Clipboard sync, Biometric PAM (`sudo` & lockscreen), Transparent Storage mounts, Media Streaming, Power Controls, and Telephony.
* **What changes:**
  * Hyprland Socket2 real-time event streaming gracefully deactivates; the daemon seamlessly falls back to its 1-second heartbeat polling loop.

### Scenario C: Switching Linux Distributions (e.g. Arch → Fedora, Ubuntu, or Debian)
* **What keeps working:**
  * All compiled binaries (`singularity-daemon`, `singularity-cli`, `pam_singularity.so`).
* **What changes:**
  * Re-run setup using the target distribution's package manager (`apt install`, `dnf install`) to acquire runtime dependencies (`android-tools`, `madbfs`, `fd`).

### Scenario D: Using a Non-Rooted Android Phone
* **What keeps working:**
  * **Transparent Storage at `/mnt/phone` (`/sdcard`):** Mounts automatically without root, with full read/write access to user files and `/sdcard/Android/data` & `/sdcard/Android/obb`.
  * Biometric PAM (`sudo` and screen unlock), Clipboard sync, Browser tab push, Media streaming on port 8920, and Desktop Telephony.
* **What changes:**
  * Only `/mnt/root` (the system root partition `/`) requires root.
  * Termux is completely unnecessary and can be removed.

