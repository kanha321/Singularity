# Implementation Plan: Singularity Clipboard Sync (Android ↔ Arch Linux)

**Project:** Singularity
**Test PRD References:** [docs/phase-0-diagnostic-test-prd.md](file:///home/kanhaji/Projects/singularity/docs/phase-0-diagnostic-test-prd.md), [docs/phase-1-pc-side-test-prd.md](file:///home/kanhaji/Projects/singularity/docs/phase-1-pc-side-test-prd.md)
**Status:** Approved & Validated (Phase 0 Diagnostic Passed)

---

## 1. Diagnostic Findings & Baseline Validation (Phase 0 Complete)

Before finalizing this plan, empirical tests were executed against the active target device (`10.235.203.184:5555` — Xiaomi POCO running Evolution X, Android 16 / SDK 36, KernelSU 3.0.1):

1. **Root UID 0 Binder Denial Confirmed:**
   - Directly invoking `service call clipboard` as root (UID 0) triggered `EX_SECURITY` (`java.lang.SecurityException: ... ClipboardService$ClipboardImpl.checkAndSetPrimaryClip`), verifying §5.3 of the PRD: root alone fails the Android package ownership verification.
2. **Shell UID 2000 Success Confirmed:**
   - Launching `scrcpy-server` via `app_process` as shell UID with flags `video=false audio=false control=true tunnel_forward=true send_device_meta=false send_dummy_byte=false` ran with zero video/audio overhead.
   - A prototype socket connection verified:
     - `0x09` `SET_CLIPBOARD`: successfully wrote clipboard and returned `0x01` `ACK_CLIPBOARD`.
     - `0x08` `GET_CLIPBOARD`: successfully read the device clipboard and returned `0x00` `CLIPBOARD` push.
3. **Toolchain Availability:**
   - **Host:** Rust (`cargo`, `rustc`), Go (`go`), Python 3, `wl-paste`, `wl-copy`, and `/usr/share/scrcpy/scrcpy-server` (v5.0.1) are already installed.
   - **Device:** Android 16, KernelSU 3.0.1 with root su access.

---

## 2. System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ Arch Linux Laptop (User Session)                            │
│                                                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ Singularity Daemon (Rust / tokio)                   │   │
│   │                                                     │   │
│   │  ┌───────────────┐        ┌──────────────────────┐  │   │
│   │  │ Wayland In    │        │ SyncEngine           │  │   │
│   │  │ wl-paste watch│───────►│ - Echo suppression   │  │   │
│   │  └───────────────┘        │ - Ring buffer (N=5)  │  │   │
│   │                           │ - Debounce & filters │  │   │
│   │  ┌───────────────┐        └──────────┬───────────┘  │   │
│   │  │ Wayland Out   │◄──────────────────┤              │   │
│   │  │ wl-copy       │                   ▼              │   │
│   │  └───────────────┘        ┌──────────────────────┐  │   │
│   │                           │ Protocol Client      │  │   │
│   │                           │ - Binary framer      │  │   │
│   │                           │ - Reconnect backoff  │  │   │
│   │                           └──────────┬───────────┘  │   │
│   └──────────────────────────────────────┼──────────────┘   │
│                                          │ localhost:28888  │
│   systemd --user: singularity.service    ▼ (adb forward)    │
└──────────────────────────────────────────┼──────────────────┘
                                           │
                              ADB TCP (10.235.203.184:5555)
                                           │
┌──────────────────────────────────────────┼──────────────────┐
│ Android 16 (Evolution X / KernelSU)      ▼                  │
│                                  localabstract:             │
│                                  scrcpy_singularity         │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ Shell Daemon (app_process / scrcpy-server)          │   │
│   │                                                     │   │
│   │  - UID 2000 (Shell identity)                        │   │
│   │  - OnPrimaryClipChangedListener -> pushes 0x00      │   │
│   │  - Handles SET_CLIPBOARD (0x09) & GET (0x08)        │   │
│   │  - Uses FakeContext + internal dropRootPrivileges() │   │
│   └─────────────────────────────────────────────────────┘   │
│                              ▲                              │
│                              │ supervised / kept alive      │
│   ┌──────────────────────────┴──────────────────────────┐   │
│   │ KernelSU Root Supervisor (/data/adb/service.d/)     │   │
│   │ (Phase 4: autonomous boot persistence)              │   │
│   └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Protocol Framing (Wire Specification)

Big-endian binary framing compliant with scrcpy control protocol:

### Outgoing: PC → Android (`SET_CLIPBOARD`)
- `[0]` `u8`: `0x09` (type)
- `[1..9]` `u64`: `sequence` (non-zero for ACK request, 0 for fire-and-forget)
- `[9]` `u8`: `paste` (`0` — set clipboard only, no keystroke injection)
- `[10..14]` `u32`: `length` (byte length of UTF-8 text, max 262,139 bytes)
- `[14..]` `[u8]`: UTF-8 string payload

### Outgoing: PC → Android (`GET_CLIPBOARD`)
- `[0]` `u8`: `0x08` (type)
- `[1]` `u8`: `copy_key` (`0x00` = COPY_KEY_NONE)

### Incoming: Android → PC (`CLIPBOARD` Push)
- `[0]` `u8`: `0x00` (type)
- `[1..5]` `u32`: `length` (byte length of UTF-8 text)
- `[5..]` `[u8]`: UTF-8 string payload

### Incoming: Android → PC (`ACK_CLIPBOARD`)
- `[0]` `u8`: `0x01` (type)
- `[1..9]` `u64`: `sequence`

---

## 4. Loop Prevention & Echo Suppression Algorithm

Echo ping-pong occurs when Device A syncs text to Device B, Device B sets its local clipboard, and Device B's watcher detects this write and re-broadcasts it back to Device A.

### Strategy:
1. **In-Flight Write Flag:**
   - When the PC daemon executes `wl-copy` to apply incoming Android text, it records the exact content in an `echo_ring_buffer` with a current timestamp before invoking `wl-copy`.
2. **Recent Ring Buffer (N=8):**
   - Stores tuples of `(hash64(text), timestamp)`.
   - Retention window: 3000ms.
3. **Local Change Gate:**
   - When `wl-paste --watch` yields new clipboard content:
     - Check length: if `> 262139` bytes or empty, discard.
     - Check ring buffer: if hash exists within the retention window, drop as an echo.
     - Check last seen local hash: if identical to current local clipboard, drop duplicate.
     - Otherwise, add to local ring buffer and dispatch `SET_CLIPBOARD` to Android.
4. **Device-Side Suppression:**
   - `scrcpy-server`'s `Controller.java` already wraps `setClipboard` with `isSettingClipboard.set(true)`, preventing the Android clipboard listener from notifying when applying incoming clips.

---

## 5. Implementation Phases & Step-by-Step Tasks

### Phase 1: Host Daemon Foundation & Scaffolding (Rust)
- **Crate setup:** `cargo init --bin singularity-daemon` in project root.
- **Dependencies:** `tokio` (async runtime, process supervision, net), `log` + `env_logger`, `anyhow`.
- **Protocol Module (`src/protocol.rs`):**
  - Big-endian encoders and decoders for `0x09` (`SetClipboard`), `0x08` (`GetClipboard`), `0x00` (`ClipboardPush`), `0x01` (`AckClipboard`).
  - Unit tests covering UTF-8 boundaries, emojis, and maximum length bounds.
- **Loop Filter Module (`src/echo_filter.rs`):**
  - Fast ring buffer with expiration for echo prevention.

### Phase 2: ADB Tunnel & Device Session Manager
- **ADB Bridge Module (`src/device.rs`):**
  - Check / ensure `scrcpy-server.jar` exists at `/data/local/tmp/scrcpy-server.jar`.
  - Establish `adb forward tcp:28888 localabstract:scrcpy_singularity`.
  - Launch `app_process` with:
    `CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 5.0.1 scid=singularity log_level=info video=false audio=false control=true tunnel_forward=true cleanup=false send_device_meta=false send_dummy_byte=false`
  - Robust connection retry loop with exponential backoff (500ms -> 1s -> 2s -> 5s max) handling ADB disconnects or network blips.

### Phase 3: Bidirectional Wayland Integration
- **Wayland Watcher (`src/watcher.rs`):**
  - Stream clipboard updates using async spawned `wl-paste --watch cat` or `wl-paste -t text/plain --watch cat`.
  - Deduplicate and route to `SyncEngine`.
- **Wayland Writer (`src/writer.rs`):**
  - Pipe text into `wl-copy` asynchronously.
- **Main Event Loop (`src/main.rs`):**
  - Concurrent `tokio::select!` joining Wayland updates, Android socket stream, and connection management.

### Phase 4: systemd User Service & Packaging
- **User unit file:** `singularity.service`
  ```ini
  [Unit]
  Description=Singularity Bidirectional Clipboard Sync
  After=graphical-session.target
  PartOf=graphical-session.target

  [Service]
  ExecStart=%h/.local/bin/singularity-daemon
  Restart=always
  RestartSec=3
  Environment=RUST_LOG=info

  [Install]
  WantedBy=graphical-session.target
  ```
- **Install script:** `install.sh` for easy one-command deployment to `~/.local/bin` and systemd activation.

### Phase 5: KernelSU Boot Module (Hardening)
- Create a KernelSU service script `/data/adb/service.d/singularity_service.sh` to ensure `scrcpy-server` or daemon can stay active on boot even if ADB disconnects.

---

## 6. Verification & Acceptance Criteria

1. **PC → Phone Sync:**
   - Copy text on laptop (`wl-copy "hello"` or browser copy) -> phone clipboard instantly updates (within < 100ms).
2. **Phone → PC Sync:**
   - Copy text on phone -> laptop `wl-paste` outputs the copied text immediately.
3. **Loop Safety:**
   - Copy text on either device -> exactly 1 sync occurs; log displays "Echo suppressed" when change event fires locally, zero loop traffic.
4. **Special Character Handling:**
   - Multiline text, emoji (e.g. 🚀🔥), non-ASCII (CJK, Arabic, accents), and punctuation sync without corruption.
5. **Recovery & Resilience:**
   - Disconnecting WiFi or restarting ADB -> daemon recovers and reconnects automatically without crashing.
