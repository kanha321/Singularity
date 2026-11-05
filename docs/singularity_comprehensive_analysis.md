# Singularity — Comprehensive Project Analysis

**Project Name:** Singularity  
**Host Platform:** Arch Linux (Hyprland / Wayland / PipeWire / systemd)  
**Target Platform:** Xiaomi POCO (Android 16 / Evolution X / Linux 5.10 / KernelSU v3.0.1)  
**Desktop Framework:** Safe Rust + `egui-widgetkit` (Catppuccin Mocha/Frappe, ODE spring physics)  
**Mobile Framework:** Android Companion App (Kotlin / Jetpack Compose / Koin / Coroutines)  
**Transport Backbone:** Persistent Authenticated ADB over TCP (`:5555`)  

---

## 1. Executive Summary & Vision

**Singularity** is an open-source, ultra-low-latency Linux $\leftrightarrow$ Android device convergence ecosystem. It connects an Arch Linux desktop with a rooted Android device into a unified hardware fabric with **zero cloud dependencies, sub-millisecond local latency, and complete fault isolation**.

### Why Singularity Exists (Overcoming Upstream Pitfalls)
| Problem in Existing Solutions | KDE Connect / Local Wi-Fi Apps | Apple Continuity | Singularity Solution |
| :--- | :--- | :--- | :--- |
| **Discovery Fragility** | Relies on UDP broadcast discovery and unauthenticated Wi-Fi sockets; breaks under Android Doze mode, router AP isolation, or campus networks. | Limited strictly to Apple hardware and iCloud account pairing. | Anchored to an **always-on, authenticated ADB TCP tunnel (`:5555`)** with automated NetworkManager and KernelSU boot scripts. |
| **Sandbox Constraints** | Restricted to standard unprivileged Android app APIs; cannot capture clipboard in background or access system nodes. | Strictly sandboxed; no filesystem mounting, terminal access, or kernel control. | **The 3-Bridge Privilege Matrix**: KernelSU Root (UID 0), Android Shell (UID 2000), and Companion App UID are surgical divided. |
| **Desktop Architecture** | Monolithic process; if GUI or network drops, entire session state freezes. | Proprietary macOS daemon. | **Client-Daemon Decoupling**: Headless background service (`singularity-daemon`) runs 24/7 under `systemd --user`; the GUI is completely disposable. |

---

## 2. Master System Architecture & The 3-Bridge Pattern

At the core of Singularity lies the **Three-Bridge Pattern**, orchestrated by the composite `DeviceBridge` in `singularity-core`:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        singularity-core                                │
│                                                                        │
│                       ┌──────────────┐                                 │
│                       │ DeviceBridge │                                 │
│                       └──────┬───────┘                                 │
│          ┌───────────────────┼───────────────────┐                     │
│          ▼                   ▼                   ▼                     │
│    RootBridge           ShellBridge          AppBridge                 │
│     (UID 0)              (UID 2000)          (App UID)                 │
└──────────┬───────────────────┼───────────────────┼─────────────────────┘
           │                   │                   │
   KernelSU su -c      adb shell / scrcpy    ADB reverse socket (8920)
           │                   │                   │
┌──────────▼───────────────────▼───────────────────▼─────────────────────┐
│             Android Target (POCO F5 / Evolution X / KSU)               │
└────────────────────────────────────────────────────────────────────────┘
```

1. **`RootBridge` (UID 0 — KernelSU):**
   - Direct execution via `su -c`.
   - Access to low-level Linux kernel interfaces (`/sys/class/power_supply/...` for battery charging limits).
   - Boot persistence (`/data/adb/service.d/`) ensuring ADB TCP is always enabled on port 5555.
2. **`ShellBridge` (UID 2000 — Android Shell):**
   - High-privilege unrooted operations (`cmd`, `am`, `pm`, `dumpsys`).
   - Headless `scrcpy-server.jar` execution for zero-prompt Wayland clipboard synchronization and sub-30ms screen/media capture.
   - ActivityManager URL dispatch for browser continuity.
3. **`AppBridge` (Companion App UID):**
   - Bidirectional event streaming over reverse TCP socket (`8920`).
   - High-priority Biometric authentication prompts (`BIOMETRIC_STRONG`).
   - Heads-Up Notifications (HUN) and real-time state telemetry.

---

## 3. Multi-Crate Workspace Layout & Dependency Hierarchy

Singularity enforces a strict, one-way architectural dependency hierarchy:
```
singularity-core  →  singularity-protocol  →  singularity-client  →  { singularity-daemon, singularity-cli, singularity-gui }
```

```
singularity/
├── android/                     # Android Companion App (Kotlin, Compose, Koin)
├── desktop/
│   └── src/
│       ├── singularity-core/    # Abstraction Barrier: DeviceBridge & Feature implementations (Zero IPC)
│       ├── singularity-protocol/# Pure data types, codecs & binary wire framing (Zero I/O)
│       ├── singularity-client/  # Async UNIX domain socket client library
│       ├── singularity-daemon/  # Headless 24/7 background engine (systemd --user, Tokio, ADB)
│       ├── singularity-gui/     # Disposable GUI dashboard built with egui-widgetkit
│       ├── singularity-cli/     # Fast CLI tool for Hyprland keybinds & Waybar
│       └── pam-singularity/     # Dynamic PAM module (cdylib) for biometric sudo & screen unlock
├── extension/                   # Manifest V3 browser extension for instant tab transfer
├── docs/                        # Canonical architecture, features, and engineering rules
└── assets/                      # Embedded binary payloads (scrcpy-server.jar)
```

---

## 4. Current Feature Catalog (Implemented Capabilities)

As recorded in `docs/features.md`:

1. **Remote Biometric Authentication (`pam-singularity`):**
   - Biometric `sudo`: Authorize Linux terminal commands by tapping the phone's fingerprint sensor.
   - Proximity Unlock: Unlock `hyprlock` / `swaylock` Wayland screens via phone fingerprint.
   - Streaming terminal status rewrites (`\r\x1b[2K...`) during auth handshakes.
2. **Remote Power & Session Lifecycle:**
   - Lock screen (`loginctl lock-session`), Suspend, Reboot, and Power Off from the phone.
   - Biometric confirmation guards for destructive actions (reboot/shutdown).
   - Dynamic contextual Homescreen FAB adapting to desktop login/lock states.
3. **Remote File Browser & Storage System:**
   - Nautilus-style breadcrumbs, scroll offset memory, and in-place path editing.
   - Virtual Archive Suite: Seamlessly browse and stream inside `.zip`, `.7z`, `.tar`, `.rar` without unpacking to disk.
   - Robocopy-Grade Download Engine: HTTP range resumption (`/z`), differential sync, and 3-worker concurrency (`/mt:3`).
   - Dual Transparent SFTP Auto-Mounting: Automatically mounts phone storage to `/mnt/phone` and root filesystem to `/mnt/root` via Termux OpenSSH SFTP over ADB tunnel.
   - Streaming folder size calculator and cached thumbnail engine (ffmpeg downscaled to 128x128).
4. **Remote Media Streaming (YAMP Engine):**
   - ExoPlayer streaming `.mp4`, `.mkv`, `.flac`, `.mp3` over HTTP range requests directly from the PC.
   - Tactile gesture controls: swipe edges for brightness/volume HUD, horizontal scrub, double-tap seek, and hold for 2.0x speed.
5. **Universal Tactile Haptics & Visual Aesthetic:**
   - Framework-wide LRA haptic clicks on all Compose widgets (`VIRTUAL_KEY` / `LONG_PRESS`).
   - Outlined Catppuccin-themed dark UI.
6. **Browser Tab Continuity:**
   - Manifest V3 browser extension sending active URLs via daemon HTTP endpoint (`/api/browser/open`) directly into Android Chrome.

---

## 5. Engineering Invariants & Coding Disciplines

1. **150-Line Limit & Thin Front Door:**
   - Production source files strictly stay under 150 lines unless annotated with `// singularity: allow-large-file (reason)`.
   - Modules follow `foo.rs` sitting beside `foo/` as a clean public front door without `mod.rs`.
2. **Linker Safety for Dynamic Libraries (`cdylib`):**
   - `pam-singularity` includes `-Wl,-z,nodelete` via `build.rs` to prevent `dlclose()` crashes from thread-local destructor unmapping.
   - Installation uses `install -m 755` to preserve mapped inodes during live reloads.
3. **Co-located Unit Testing:**
   - 112 unit tests across desktop crates pass cleanly in ~0.08s with hardware mocking (`FakeBridge`, `FakeTransport`).
