# Singularity: Universal Clipboard Sync (Phase 1) Implementation Plan

**Target Feature:** Bidirectional Universal Clipboard Synchronization  
**Host Environment:** Arch Linux (Hyprland / Wayland / `wl-clipboard-rs`)  
**Target Device:** Xiaomi POCO (Android 16 / Evolution X / `10.235.203.184:5555`)  
**Transport Backbone:** Persistent ADB TCP Tunnel (`localabstract:scrcpy_singularity`)  
**Wire Codec:** `scrcpy-server` binary wire protocol (`0x09` SET / `0x00` PUSH / `0x01` ACK)  

---

## 1. Architectural Blueprint & Dataflow

```
   ┌────────────────────────────────────────────────────────────────────────────┐
   │                          PC → PHONE PIPELINE                               │
   │                                                                            │
   │   1. User copies text on Arch Linux (Hyprland)                             │
   │      └─► wl-clipboard-rs event watcher receives text                       │
   │   2. Echo Suppression Gate (EchoRingBuffer):                               │
   │      ├─► Hash text (64-bit fast hasher)                                    │
   │      ├─► Check RingBuffer (last 16 hashes within 5s window)                │
   │      ├─► If matches recently received remote hash ──► DROP (echo)          │
   │      └─► If new local copy ──► Record in RingBuffer & proceed              │
   │   3. Protocol Encoding:                                                    │
   │      └─► singularity-protocol::encode_set_clipboard(seq, false, text)      │
   │   4. Transmission:                                                         │
   │      └─► Writes 0x09 binary packet to open scrcpy-server TCP socket        │
   │   5. Execution on Device:                                                  │
   │      └─► scrcpy-server (Shell UID 2000) calls ClipboardManager.setPrimary  │
   │      └─► Phone clipboard updated (< 20ms glass-to-glass)                   │
   └────────────────────────────────────────────────────────────────────────────┘

   ┌────────────────────────────────────────────────────────────────────────────┐
   │                          PHONE → PC PIPELINE                               │
   │                                                                            │
   │   1. User copies text on Xiaomi POCO (Android 16)                          │
   │   2. scrcpy-server detects OnPrimaryClipChangedListener                    │
   │   3. Wire Transmission:                                                    │
   │      └─► Emits 0x00 [type:1B][len:4B][utf8_text:NB] packet over TCP        │
   │   4. Reception & Decoding:                                                 │
   │      └─► singularity-daemon reads from TCP stream                          │
   │      └─► singularity-protocol::decode_device_msg(buf) extracts text        │
   │   5. Echo Suppression Gate (EchoRingBuffer):                               │
   │      ├─► Hash text                                                         │
   │      ├─► If matches recently sent local hash ──► DROP (echo)               │
   │      └─► If genuine remote copy ──► Record in RingBuffer & proceed         │
   │   6. Injection into Wayland:                                               │
   │      └─► wl-clipboard-rs writes text into Wayland clipboard + primary      │
   │      └─► Text immediately pasteable in any Hyprland application            │
   └────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. File Modularity & Component Plan

To strictly adhere to the **150-line file limit** and the **thin front-door pattern**, the implementation is partitioned into isolated, single-purpose subcomponents:

```
desktop/src/
├── singularity-core/src/features/clipboard/
│   ├── config.rs              # [EXISTS] Size limits & enable toggles
│   ├── state.rs               # [UPDATE] Add EchoRingBuffer (16-slot sliding window)
│   ├── handler.rs             # [EXISTS] Domain action dispatcher
│   └── ring_buffer.rs         # [NEW] Multi-hash sliding window with TTL eviction
│
└── singularity-daemon/src/
    ├── wayland/               # Native Wayland clipboard integration
    │   ├── watcher.rs         # Background thread watching Wayland copy events
    │   └── writer.rs          # Injects remote clips into Wayland clipboard
    ├── wayland.rs             # Thin front door for Wayland subsystem
    │
    ├── transport/             # ADB & Phone Highway
    │   ├── adb_tunnel.rs      # Port forwarding (tcp:PORT -> localabstract:scrcpy_*)
    │   ├── deployer.rs        # [EXISTS] Pushes embedded scrcpy-server.jar via ADB
    │   ├── session.rs         # Launches headless server & establishes TCP stream
    │   └── supervisor.rs      # Connection watchdog & exponential backoff reconnect
    ├── transport.rs           # Thin front door for transport subsystem
    │
    └── clipboard/             # Bi-directional runtime bridge
        ├── bridge.rs          # Glues Wayland watcher, TCP socket, and EchoRingBuffer
        └── types.rs           # Channel messages & metrics
```

---

## 3. Step-by-Step Implementation Sequence

### Step 1: `EchoRingBuffer` Implementation (`singularity-core`)
* **File:** `desktop/src/singularity-core/src/features/clipboard/ring_buffer.rs`
* **Features:**
  - Fixed-size array `[(u64, Instant); 16]`. Zero heap allocations during runtime.
  - Method `record(hash: u64)`: Adds hash with current timestamp.
  - Method `contains(hash: u64, ttl: Duration) -> bool`: Checks if hash exists within the validity window (default: 5 seconds).
* **Unit Tests:**
  - Unknown hash returns false.
  - Recently recorded hash returns true.
  - Expired hash (> 5s) returns false.
  - Ring buffer wraps around cleanly without panicking.

---

### Step 2: Wayland Clipboard Subsystem (`singularity-daemon`)
* **Files:** `wayland/watcher.rs`, `wayland/writer.rs`, `wayland.rs`
* **Watcher (`watcher.rs`):**
  - Runs a background worker using `wl_clipboard_rs::paste::get_contents` / clipboard monitoring.
  - Sends captured text over a Tokio `mpsc::channel`.
  - Non-blocking to the Tokio event loop.
* **Writer (`writer.rs`):**
  - Uses `wl_clipboard_rs::copy::Options` to write text to `ClipboardType::Regular` and `ClipboardType::Primary`.
* **Unit Tests:**
  - Verify writer handles empty strings and multiline strings without panicking.

---

### Step 3: ADB Transport & Session Subsystem (`singularity-daemon`)
* **Files:** `transport/adb_tunnel.rs`, `transport/session.rs`, `transport/supervisor.rs`, `transport.rs`
* **Tunnel (`adb_tunnel.rs`):**
  - Connects to device serial `10.235.203.184:5555`.
  - Executes `adb -s 10.235.203.184:5555 forward tcp:PORT localabstract:scrcpy_singularity`.
* **Session (`session.rs`):**
  - Uses existing `ServerDeployer` to push `assets/scrcpy-server.jar` if missing on the device.
  - Spawns headless `scrcpy-server` via `adb shell` with `video=false audio=false control=true clipboard_autosync=true`.
  - Connects `tokio::net::TcpStream` to `127.0.0.1:PORT`.
  - Reads the 1-byte dummy connection byte (`0x00`).
* **Supervisor (`supervisor.rs`):**
  - Supervises the connection. If the socket closes or Wi-Fi drops, waits 5s and cleanly restarts the tunnel without dropping daemon state.

---

### Step 4: Bi-Directional Clipboard Bridge (`singularity-daemon`)
* **File:** `clipboard/bridge.rs`
* **Execution Loop:**
  - **Task A (PC $\to$ Phone):** Reads text from Wayland watcher channel $\to$ checks `EchoRingBuffer` $\to$ calls `singularity_protocol::encode_set_clipboard(seq, false, text)` $\to$ writes to TCP socket.
  - **Task B (Phone $\to$ PC):** Reads from TCP socket $\to$ calls `singularity_protocol::decode_device_msg()` $\to$ checks `EchoRingBuffer` $\to$ calls `wayland::writer::copy_to_wayland(text)`.
  - Records hashes on both paths to guarantee zero echo bounces.

---

## 4. Live Hardware Verification & Acceptance Criteria

Once implemented, we will run the live verification tests against the physical POCO device:

1. **Test 1: PC $\to$ Phone Sync:**
   - Execute `wl-copy "Singularity test from Arch Linux 🚀"`.
   - Verify device clipboard via ADB: `adb shell "service call clipboard 2 i32 1 ..."` or paste in an app.
   - Acceptance: Text updates within < 25ms.
2. **Test 2: Phone $\to$ PC Sync:**
   - Set device clipboard via `scrcpy-server` or copy text in Android.
   - Run `wl-paste` on Arch Linux.
   - Acceptance: Outputs the exact phone text immediately.
3. **Test 3: Echo Suppression Integrity:**
   - Copy text on laptop $\to$ verify log shows 1 outgoing packet, followed by 1 incoming packet marked `[Echo Suppressed: Loop Prevented]`.
   - Zero endless bounce loops.
4. **Test 4: Special Characters & Large Text:**
   - Test multiline code snippets, emoji, CJK/Arabic Unicode characters, and 500KB text payload.
   - Acceptance: 100% byte-for-byte fidelity without truncation.
