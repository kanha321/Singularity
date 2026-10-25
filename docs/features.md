# Features Catalog — Singularity

This document is the authoritative record of all functional features currently implemented in the **Singularity** ecosystem (Arch Linux Desktop Host & Android Companion App), describing what each feature is and what it does.

---

## 1. Biometric Authentication & Linux PAM Integration (`pam-singularity`)

### 1.1 Remote Biometric `sudo` Authorization
- **What it does:** Allows authorizing `sudo` commands executed in any Arch Linux terminal by tapping your phone's fingerprint sensor instead of typing a password.
- **How it works:** `pam_singularity.so` sends an authentication challenge across the persistent socket to the phone. The phone pops up a biometric prompt (and a high-priority Heads-Up Notification if the screen is on). Tapping the sensor completes the cryptographic handshake and grants `sudo` on the laptop.

### 1.2 Proximity Screen Unlock (`hyprlock` / `swaylock`)
- **What it does:** Unlocks your Wayland lock screen automatically using your phone's presence and fingerprint.
- **How it works:** When waking the laptop from sleep or turning on the screen, PAM checks if the paired phone is reachable over the authenticated ADB TCP bridge with matching RSA keys. Verifying fingerprint on the phone unlocks the desktop session.

### 1.3 Streaming Terminal Feedback
- **What it does:** Displays real-time status updates directly in the Linux terminal (`\r\x1b[2K...`) as the phone progresses through stages (`Sending prompt...`, `Waiting for fingerprint...`, `Authenticated`).

---

## 2. Remote Power Controls & Session Lifecycle

### 2.1 Remote System Power Management
- **What it does:** Enables controlling laptop power states remotely from your phone bed, desk, or across the room.
- **Supported Actions:**
  - **Lock Screen**: Triggers the desktop screen locker via `loginctl lock-session`.
  - **Sleep / Suspend**: Puts the laptop to sleep via `systemctl suspend`.
  - **Reboot**: Gracefully reboots the laptop via `systemctl reboot`.
  - **Power Off**: Safely shuts down the laptop via `systemctl poweroff`.

### 2.2 Biometric Protection for Destructive Actions
- **What it does:** Safeguards against accidental taps by requiring biometric verification and displaying a custom confirmation dialog before executing Reboot or Power Off.

### 2.3 Session-Aware Power Controls & Dynamic Homescreen FAB
- **What it does:** Adapts the homescreen floating action button and power controls grid dynamically based on desktop session status (`isLoggedIn`) and screen lock state (`isLocked`).
- **Homescreen FAB Hierarchy (`isLoggedIn` Priority):**
  - **Logged Out (`!isLoggedIn`) [Top Priority]:** Renders the **Login FAB** (themed via `primaryContainer` and fingerprint icon) to log into the PC at the display manager / greeter via `HomeBiometricLauncher.launchLogin()`.
  - **Logged In & Screen Locked (`isLoggedIn && isLocked`):** Renders the **Unlock FAB** to unlock the active session screen via `HomeBiometricLauncher.launchUnlock()`.
  - **Logged In & Screen Unlocked (`isLoggedIn && !isLocked`):** Renders the **Hold to Lock FAB** using dynamic Material 3 semantic theme tokens and animated progress stroke.
  - **PC Disconnected:** Renders an offline state FAB displaying a snackbar notification on tap.
- **Contextual Power Grid Filtering:**
  - When logged out, the Power Controls screen automatically filters out session-dependent actions (`Logout`, `Suspend`, `Hibernate`), strictly displaying **Reboot** and **Shutdown**.
  - When logged in, the full hardware lifecycle suite (**Reboot**, **Shutdown**, **Suspend**, **Hibernate**, and **Logout**) is accessible.

---

## 3. Remote File Browser & Storage System

### 3.1 Nautilus-Inspired Directory Navigation
- **What it does:** Navigates the laptop's entire filesystem from the phone.
- **Key Capabilities:**
  - **Segmented Breadcrumbs**: 48dp breadcrumb bar with horizontal scrolling to jump directly to any parent folder.
  - **In-Place Path Editor**: Tap the edit icon to type an arbitrary path with autocomplete navigation.
  - **Dual-Stack Transition**: Only the directory item list scrolls; top bar and breadcrumbs remain pinned.
  - **Persistent Scroll Memory**: `FsScrollStateManager` retains the exact pixel offset of each directory, making back-navigation instantaneous with zero flicker.

### 3.2 View Options, Sorting & Dotfile Filtering
- **What it does:** Customizes how files and folders are presented in the browser.
- **Key Capabilities:**
  - **Sorting Fields**: Sort by Name, Date Modified, Size, or File Type.
  - **Sort Direction**: Instant Ascending / Descending toggles.
  - **Hidden Dotfiles**: Toggle visibility of hidden Linux configuration files and folders (`.dotfiles`).
  - **Folders on Top**: Keep directories grouped above individual files.

### 3.3 Quick Places & User Bookmarks
- **What it does:** Provides one-tap access to frequently visited laptop locations.
- **Default Places**: Home (`~`), Downloads (`~/Downloads`), Projects (`~/Projects`), Documents (`~/Documents`), Pictures (`~/Pictures`), Videos (`~/Videos`), and Root Filesystem (`/`).
- **Custom Bookmarks**: Bookmark any remote folder to the Quick Places drawer with a single tap.

### 3.4 Biometric-Guarded Sudo Mode
- **What it does:** Protects root and system files from unauthorized access or modification.
- **How it works:** Accessing the root filesystem (`/`) or toggling Sudo Mode in the top bar prompts for phone fingerprint authentication. Elevated sessions are screen-scoped and drop privileges automatically when leaving the screen.

### 3.5 Streaming Folder Size Engine
- **What it does:** Calculates directory size and item counts without locking up the UI.
- **How it works:** The laptop daemon traverses the directory recursively in the background and streams intermediate progress over NDJSON. The phone UI displays real-time item counts and formatted byte sizes as they resolve.

### 3.6 Remote File Management (Write Operations)
- **What it does:** Full file manipulation on laptop storage directly from the phone.
- **Operations Supported:**
  - **New Folder (`mkdir`)**: Create new directories on the laptop via the Speed-Dial FAB.
  - **Rename**: In-place renaming for files and directories with filename validation.
  - **Delete**: Single-item and batch deletion with confirmation dialogs.
  - **Cut, Copy & Paste**: Persistent clipboard mode with a floating paste dock, item count badges, and clear action.

### 3.7 Robocopy-Grade Download Engine ("Save to Phone")
- **What it does:** Transfers single files or entire nested folder trees from laptop to `/sdcard/Download/Singularity/`.
- **Engine Features:**
  - **HTTP Range Resumption (`/z`)**: Automatically resumes interrupted transfers without restarting from byte 0.
  - **Differential Sync**: Checks size and modification timestamp, skipping files that already exist identically on the phone.
  - **Concurrent Worker Pool (`/mt:3`)**: Transfers files across 3 parallel threads for maximum network throughput.
  - **Fault-Tolerant Auto-Retry (`/r:3`)**: Automatically retries failed network chunks up to 3 times before reporting an error.
  - **Smart Exclusions**: Automatically skips temporary files and build artifacts (`.git`, `node_modules`, `target`, `.tmp`).
  - **Empty Folder Preservation (`/e`)**: Recreates empty directory structures accurately.
  - **Persistent Notifications**: Foreground service with real-time transfer progress bars, file counts, and download completion alerts.

### 3.8 Multi-Item Selection Mode
- **What it does:** Batch file management with visual feedback.
- **Capabilities:**
  - Long-press any card to activate selection mode with animated borders and tinted backgrounds.
  - Selection counter in the top bar with "Select All" support.
  - Batch action dock: Save to Phone, Cut, Copy, Delete, Compress to Archive, and Batch Properties Summary.
  - Multi-Folder Properties Dialog displaying individual folder names as horizontal chips alongside async calculated sizes.

### 3.9 Virtual Archive Suite (Browsing, On-Demand Streaming, Extract & Compress)
- **What it does:** Transparent virtual folder navigation, extraction, and compression for archive files on the laptop.
- **Supported Formats:** `.zip`, `.7z`, `.tar`, `.tar.gz`, `.tar.xz`, `.tar.bz2`, `.rar`, `.iso`, `.zst`, `.tgz`, `.txz`.
- **Key Capabilities:**
  - **Transparent Virtual Navigation:** Tap any archive in the file browser to enter it seamlessly like a regular folder (using `<archive_path>!<internal_path>` virtual paths).
  - **Breadcrumb Integration:** Virtual paths inside archives display dedicated zip icons and allow jumping across archive directory levels.
  - **On-Demand Single-File Streaming:** Preview and stream individual text, code, image, and video files stored inside archives without decompressing the entire archive to disk.
  - **Remote Extraction:** Context menu options to "Extract Here" or "Extract to Folder" on the laptop storage via background 7z processes.
  - **Remote Compression:** Compress single or multiple selected files/directories into `.zip`, `.7z`, or `.tar.gz` directly from the selection dock or context menu.
  - **Bidirectional File Operations:** Full Cut, Copy, Paste, Delete, Rename, and New Folder support across all boundaries (Real FS $\leftrightarrow$ Real FS, Archive $\rightarrow$ Real FS, Real FS $\rightarrow$ Archive, Archive $\rightarrow$ Archive). Rename or delete files inside archives (`7z rn`, `7z d`), create subfolders (`7z a`), and paste files into/out of archives with automatic cache invalidation.

### 3.10 Remote File Previews & Cached Thumbnails
- **What it does:** Displays visual previews (thumbnails) for remote images and videos directly within file list cards and the 3-dot context menu sheet instead of generic vector icons.
- **Daemon-Side Optimization:**
  - Dedicated `/api/fs/thumbnail?path=...` endpoint on `singularity-daemon`.
  - Computes deterministic hash keys `(path, mtime, size)` to instantly serve cached thumbnails or invalidate upon file modification.
  - High-speed on-demand extraction via `ffmpeg` downscaling to 128x128 JPEG (~2.5 KB payload per media file) with 7-day HTTP cache headers (`Cache-Control: public, max-age=604800`).
- **Client-Side Optimization:**
  - Seamless Coil Compose integration (`FsItemThumbnail`) with automatic request cancellation during rapid list fling/scroll.
  - Dual-tier caching (RAM LRU + app disk cache) for instant zero-network re-rendering on back navigation.
  - Vector icon placeholder with smooth crossfade to preview upon load completion and seamless fallback on error or non-media files.
  - Integrated into both `FsListCard` and the 3-dot context menu bottom sheet (`FsItemContextMenuSheet`) with enlarged 48dp preview header.

---

## 4. In-App Remote Media Streaming & File Viewers

### 4.1 Direct Video & Audio Streaming (YAMP Engine)
- **What it does:** Plays media files (`.mp4`, `.mkv`, `.webm`, `.mp3`, `.flac`) directly from laptop storage with zero download required.
- **Underlying Engine:** ExoPlayer configured to stream from the laptop daemon over HTTP range requests for instant seeking and timeline scrubbing.
- **Tactile Gesture Controls:**
  - **Vertical Swipe (Left Edge)**: Adjusts display brightness with a right-aligned HUD overlay.
  - **Vertical Swipe (Right Edge)**: Adjusts media volume with a left-aligned HUD overlay.
  - **Horizontal Drag**: Real-time scrubbing with relative time delta indicator (`+0:15`, `-1:20`).
  - **Double-Tap**: Animated 10-second seek ripple arrows on left/right edges.
  - **Tap-and-Hold**: Instant $2.0\times$ playback speed acceleration.
- **Track Selection**: Dynamic audio track and subtitle stream selectors inside the player.

### 4.2 Image Viewer
- **What it does:** High-resolution image inspection with smooth pan and zoom.
- **Capabilities:** Boundary-clamped zoom ($1.0\times - 5.0\times$), double-tap to toggle 2.5x zoom, and horizontal gesture paging across images in the directory.

### 4.3 Code & Document Viewer
- **What it does:** Fast preview of text, code, and configuration files.
- **Capabilities:** Monospace font viewer with line numbers and syntax styling for `.txt`, `.md`, `.rs`, `.kt`, `.json`, `.py`, etc.
- **External Document Dispatcher**: Passes PDFs, Office documents, and unrecognized formats to native Android viewers via secure `SingularityFileProvider`.

---

## 5. UI System & Default Tactile Haptics

### 5.1 Universal Default Tactile Haptics
- **What it does:** Provides crisp tactile vibration feedback on every interaction across the entire app.
- **How it works:** Button, icon button, card, chip, switch, and gesture interactions delegate through `rememberHapticClick` (`HapticFeedbackConstants.VIRTUAL_KEY`) and `rememberHapticLongClick` (`HapticFeedbackConstants.LONG_PRESS`) using `FLAG_IGNORE_VIEW_SETTING`.
- **Developer Opt-Out:** Haptics are enabled by default on all components; developers pass `hapticFeedback = false` to explicitly disable feedback for specific widgets.

### 5.2 Singularity Outlined Design Language
- **What it does:** Premium dark-mode visual aesthetic matching the desktop Catppuccin theme.
- **Components:** Outlined dialogs (`SingularityDialog`, `SingularityConfirmDialog`) featuring 1dp primary/outline borders, rounded corners, and pass-through background animations (`AnimatedBackground`).

---

## 6. Infrastructure & Transport Backbone

### 6.1 Persistent ADB TCP Transport (`:5555`)
- **What it does:** High-reliability socket connection between Arch Linux and Android over local Wi-Fi or Hotspot.
- **Resilience:** Immune to Android Doze mode and router AP isolation. Automatically reconnects when network state changes without user interaction.

### 6.2 Reverse HTTP File Server Bridge
- **What it does:** The desktop daemon runs a Tokio HTTP file server on port `8920`, reverse-forwarded directly into Android's localhost via ADB reverse tunnels.

### 6.3 Systemd User Service Supervision
- **What it does:** `singularity-daemon.service` runs as a systemd user service on Arch Linux, starting on login and restarting automatically on failure.
