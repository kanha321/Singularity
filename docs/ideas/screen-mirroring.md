# Singularity: Screen Mirroring Architecture (The Hybrid Model)

**Status:** Feature Architecture & Design Specification  
**Target Performance:** 60–120 FPS | 15–25ms Glass-to-Glass Latency  
**Host Platform:** Arch Linux (Hyprland / Wayland / PipeWire)  
**Target Device:** Xiaomi POCO (Android 16 / Evolution X / Snapdragon 7+ Gen 2)  

---

## 1. Executive Concept

Screen Mirroring in Singularity provides ultra-low latency, high-refresh display streaming and touch interaction with **zero external dependencies** (no system `scrcpy` installation required).

It adopts a **Hybrid Dual-Mode Display Model**, allowing the user to seamlessly transition between an **Embedded Dashboard HUD** for casual glanceability and an **Uncompromised 120Hz Detached Wayland Window** for heavy productivity, gaming, and media consumption.

---

## 2. The Hybrid Dual-Mode Architecture

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                             HYBRID SCREEN MIRRORING MODES                              │
├───────────────────────────────────────────┬────────────────────────────────────────────┤
│ 1. Embedded HUD Mode (Tactical Dashboard) │ 2. Detached 120Hz Mode (Pure Performance)  │
├───────────────────────────────────────────┼────────────────────────────────────────────┤
│ • Lives directly inside singularity-gui   │ • Pops out as an unencumbered, dedicated   │
│   as an interactive card.                 │   Wayland window managed by Hyprland.      │
│ • Closed-form analytical ODE spring       │ • Direct zero-copy GPU video rendering     │
│   animations drive expand, collapse, and  │   via VA-API / Vulkan hardware acceleration│
│   picture-in-picture docking.             │ • Native 120Hz display refresh rate        │
│ • Tuned for 30–60 FPS to conserve laptop  │ • Sub-20ms glass-to-glass latency          │
│   battery and background GPU cycles.      │ • Ideal for mobile gaming, video playback, │
│ • Perfect for 2FA prompts, quick replies, │   and extended hands-on testing.           │
│   and glanceable telemetry.               │ • Clean Hyprland auto-tiling / floating.   │
└───────────────────────────────────────────┴────────────────────────────────────────────┘
```

### Instant Seamless Pop-Out
Both display modes stream from the **exact same underlying server session**:
- Pressing `[F]`, a dashboard button, or a Hyprland keybinding (`Super + M`) detaches the stream into the 120Hz Wayland surface.
- Pressing `[Esc]` or `[Q]` docks the window back into the dashboard card.
- The phone connection, ADB tunnel, and audio/control streams remain uninterrupted during mode transitions.

---

## 3. Zero-Dependency Self-Contained Engine

Singularity completely eliminates the requirement for the user to install the desktop `scrcpy` package:

1. **Vendored Android Server Bytecode:**  
   The precompiled, verified `scrcpy-server.jar` (~730 KB) is stored in the repository at `assets/scrcpy-server.jar`.
2. **Compile-Time Embedding:**  
   `singularity-daemon` embeds the bytecode slice directly into the binary:
   ```rust
   pub const SCRCPY_SERVER_BYTES: &[u8] = include_bytes!("../../../assets/scrcpy-server.jar");
   ```
3. **Automated Phone Deployment:**  
   On startup, the daemon verifies whether `/data/local/tmp/scrcpy-server.jar` exists on the target device with matching hash/version. If missing or outdated, it pushes the embedded bytes over ADB automatically.

---

## 4. The High-Speed Video Pipeline

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HARDWARE VIDEO PIPELINE                         │
│                                                                        │
│  1. CAPTURE & ENCODE (Android Device)                                  │
│     scrcpy-server under Shell UID 2000 accesses SurfaceControl        │
│     Feeds virtual display directly to Snapdragon MediaCodec            │
│     Hardware-encodes H.264 / H.265 NAL byte stream (1080p @ 120fps)     │
│                            │                                           │
│                            ▼                                           │
│  2. HIGHWAY TRANSMISSION (ADB TCP Tunnel :5555)                        │
│     TCP_NODELAY stream over dedicated video socket                     │
│                            │                                           │
│                            ▼                                           │
│  3. HARDWARE DECODING (Arch Linux Host)                                │
│     singularity-daemon receives NAL byte units                         │
│     Decodes frames on GPU via hardware acceleration (VA-API / Vulkan)  │
│                            │                                           │
│                            ▼                                           │
│  4. ZERO-COPY DISPLAY PRESENTATION                                     │
│     • Embedded Mode: Uploads RGBA frame to egui::TextureHandle         │
│     • Detached Mode: Direct DMA-BUF / Wayland subsurface presentation  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Universal Input & Control Bridge

User interaction in both modes flows directly through Singularity’s standard protocol engine:

- **Touch & Mouse Events:** Mouse clicks, touch drags, and cursor coordinates are mapped to normalized coordinates `(x, y)` and dispatched as scrcpy control message `0x02` (`INJECT_TOUCH_EVENT`).
- **Keyboard Injection:** Keystrokes are converted to Android keycodes and dispatched as `0x00` (`INJECT_KEYCODE`).
- **Vim Modal Navigation:** Keyboard navigation (`H`/`J`/`K`/`L`) translates into smooth programmatic directional swipes, allowing complete hands-on-keyboard phone control from Arch Linux.

---

## 6. Implementation Milestones

1. **Asset Vendoring:** Place `scrcpy-server.jar` in `assets/` and embed into `singularity-daemon`.
2. **Video Socket Framer:** Add video stream demuxing and H.264 NAL parsing to `singularity-protocol`.
3. **Hardware Video Decoder:** Integrate lightweight hardware decoding in `singularity-client` / `singularity-gui`.
4. **Embedded Viewport Widget:** Create the `egui-widgetkit` spring-animated screen card.
5. **Detached Wayland Surface:** Implement the 120Hz pop-out window for Hyprland.
