# Singularity: Master System Architecture

**Project Name:** Singularity  
**Status:** Canonical System Architecture Specification  
**Host Platform:** Arch Linux (Hyprland / Wayland / PipeWire / systemd)  
**Target Platform:** Xiaomi POCO (Android 16 / Evolution X / Linux 5.10 / KernelSU v3.0.1)  
**UI Framework:** `egui-widgetkit` (Tactile immediate-mode Rust engine with spring ODE physics)  
**Transport:** Persistent ADB TCP (`10.235.203.184:5555`)  

---

## 1. Executive Architectural Vision

**Singularity** is a high-performance, open convergence platform connecting Arch Linux and a rooted Android device. It is built to achieve seamless continuity (universal clipboard, desktop calling, OTP auto-copy, hardware macro HUD, smart charging, and screen mirroring) with **zero cloud dependencies, sub-millisecond local latency, and complete fault isolation**.

### The 4 Core Architectural Pillars:
1. **The 3-Bridge Access Architecture:** Surgical division of Android capabilities across **`RootBridge`**, **`ShellBridge`**, and **`AppBridge`**, unified under a parent **`DeviceBridge`** behind a strict abstraction barrier.
2. **The Client-Daemon Decoupling:** The background daemon (`singularity-daemon`) runs 24/7 as an ultra-lean `systemd --user` service. The graphical frontend (`singularity-gui`) is a disposable client; if the GUI crashes or closes, the daemon continues running uninterrupted.
3. **The Multi-Crate Rust Workspace:** Modeled after the production-proven architecture from `LumosRGB`, separating protocols, client libraries, daemon services, and user interfaces into cleanly bounded crates.
4. **Heavy `egui-widgetkit` Integration:** The desktop UI is exclusively built upon `egui-widgetkit` (from `/mnt/d/egui-apps/egui-lib`), delivering analytical spring ODE physics, Vim-style modal navigation (`H`/`J`/`K`/`L`), semantic token palettes, and 0.00% idle CPU utilization.

---

## 2. Multi-Crate Workspace Layout

Following the pattern established in `LumosRGB`, the system is structured as a multi-crate Cargo workspace where each crate has a single, strictly bounded responsibility:

```
singularity/
├── docs/                     # Architectural specifications & guides
├── achievements/             # Empirical validation logs & test outputs
├── references/               # Upstream reference repos (scrcpy, kdeconnect)
├── Cargo.toml                # Workspace root resolver
└── src/
    ├── singularity-core/     # The Abstraction Barrier: DeviceBridge & Pluggable Features
    ├── singularity-protocol/ # Pure wire codecs, framing & IPC packet types (Zero I/O)
    ├── singularity-client/   # Lightweight async client for talking to the Daemon
    ├── singularity-daemon/   # Headless 24/7 background service (Tokio, ADB, Wayland)
    ├── singularity-gui/      # Tactical desktop UI built exclusively on egui-widgetkit
    └── singularity-cli/      # Fast command-line utility for Hyprland keybinds & Waybar
```

### Crate Roles & Architectural Boundaries

| Crate | Responsibility | Boundary Rules (What It Must NOT Do) |
|---|---|---|
| **`singularity-core`** | • Defines `DeviceBridge` (the composite parent) and the 3 specialized child bridges (`RootBridge`, `ShellBridge`, `AppBridge`).<br>• Hosts pluggable `Feature` implementations.<br>• Domain models and business logic. | • Must **never** depend on GUI crates (`egui`, `eframe`).<br>• Must **never** hardcode socket connections or UI layout. |
| **`singularity-protocol`** | • Big-endian binary codecs (scrcpy wire framing `0x09`/`0x00`).<br>• Length-delimited IPC message framing.<br>• Shared serialization envelopes (`Packet`, `Payload`). | • Must be **pure data & codecs with zero I/O** (no sockets, no file handles, no network calls). |
| **`singularity-client`** | • Ergonomic async client library wrapping the local UNIX domain socket.<br>• Exposes request-response methods and event subscription streams. | • Must **never** directly talk to ADB or phone hardware (always communicates through the daemon). |
| **`singularity-daemon`** | • Headless background engine running under `systemd --user`.<br>• Manages persistent ADB TCP link and `forensic-adb` forwards.<br>• Manages Wayland clipboard integration (`wl-clipboard-rs`).<br>• Hosts the local UNIX socket IPC server. | • Must **never** pull in GUI rendering dependencies.<br>• Must be completely headless. |
| **`singularity-gui`** | • Desktop control dashboard built exclusively on `egui-widgetkit`.<br>• Visualizes live phone telemetry, incoming call alerts, and macro pads.<br>• Consumes `singularity-client` over the local socket. | • Must be **completely disposable** — closing or crashing the GUI must never affect daemon operation.<br>• Never talks directly to ADB. |
| **`singularity-cli`** | • Fast CLI tool using `clap`.<br>• Used by Waybar status widgets and Hyprland keybindings.<br>• Consumes `singularity-client`. | • Thin CLI wrapper only; no independent state management. |

### Module Design Guidelines

Rather than prescribing static filenames in advance, components should follow these structural rules as features are implemented:
1. **Feature Modularity (`core`):** Each feature is an isolated module implementing the `Feature` trait. Features communicate only through `DeviceBridge` and never directly depend on each other.
2. **Bridge Separation (`core`):** Low-level capabilities are grouped by access domain (`root`, `shell`, `app`). If an operation requires a shell command, Binder call, or sysfs write, it belongs inside a bridge method, not in the feature.
3. **View-State Separation (`gui`):** UI rendering is decoupled from network state. Views take immutable state references and emit user intent commands through `singularity-client`.

---

## 3. The 3-Bridge Platform Architecture

Android enforces rigid security boundaries that prevent any single privilege domain from handling all operations. Singularity bridges these domains cleanly into three specialized child bridges, each mapped to its native execution context:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              THE 3 SPECIALIZED BRIDGES                                 │
├──────────────────────────┬─────────────────────────────┬───────────────────────────────┤
│ Bridge                   │ Identity & Access Level     │ Capabilities & Responsibilities│
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ RootBridge               │ UID 0                       │ • Direct Linux kernel control │
│                          │ (Permissive SELinux /       │ • /sys/class/power_supply     │
│                          │  KernelSU)                  │   charging threshold (80%)    │
│                          │                             │ • /dev/uinput virtual devices │
│                          │                             │ • Autonomous boot persistence │
│                          │                             │ • Unrestricted raw filesystem │
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ ShellBridge              │ UID 2000                    │ • AOSP platform carve-outs    │
│                          │ (`com.android.shell` via    │ • Headless clipboard read/write│
│                          │  ADB / scrcpy-server)       │ • Zero-prompt screen capture  │
│                          │                             │ • Direct audio submix capture │
│                          │                             │ • Platform CLI: telecom, am   │
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ AppBridge                │ UID 10xxx                   │ • Reactive OS push callbacks  │
│                          │ (Standard SDK Sandbox /     │ • NotificationListenerService │
│                          │  Kotlin Companion App)      │ • RemoteInput inline reply    │
│                          │                             │ • TelephonyManager call alerts│
│                          │                             │ • MediaSessionManager (MPRIS) │
└──────────────────────────┴─────────────────────────────┴───────────────────────────────┘
```

### The Common `Bridge` Lifecycle Trait

Each specialized bridge implements a common lifecycle trait for uniform health monitoring, diagnostic introspection, and teardown:

```rust
#[async_trait]
pub trait Bridge: Send + Sync {
    /// Human-readable identifier for logging and diagnostics (e.g., "RootBridge", "ShellBridge")
    fn name(&self) -> &'static str;

    /// Verifies whether the underlying transport, process, or IPC channel is currently operational
    async fn is_healthy(&self) -> bool;

    /// Gracefully tears down connections, child processes, or forwarders
    async fn shutdown(&self) -> Result<()>;
}
```

---

## 4. The Strict Abstraction Barrier: DeviceBridge & Feature Engine

To ensure maximum maintainability, **features are completely blind to low-level operating system plumbing, byte protocols, and process boundaries**. They interact exclusively with the composite parent bridge, **`DeviceBridge`**:

```
┌────────────────────────────────────────────────────────────────────────┐
│                     FEATURE LAYER (Domain Logic Only)                  │
│                                                                        │
│   ClipboardSync  │  DesktopCalling  │  BatteryLimiter  │  OTPAutoCopy  │
│                                                                        │
│   What Features Know:                                                  │
│   • "The battery reached 80%"                                          │
│   • "Text copied: 'https://github.com'"                                │
│   • "Incoming call from Alice (+123456789)"                            │
│                                                                        │
│   What Features NEVER Know:                                            │
│   ✖ Sockets, TCP ports, IP addresses, or ADB forwards                  │
│   ✖ Big-endian binary framing (0x09/0x00) or JSON wire codecs          │
│   ✖ Process UIDs (0 vs 2000), su, app_process, or SELinux policies     │
│   ✖ Reconnection loops, socket drops, or thread management             │
└──────────────────────────────────▲─────────────────────────────────────┘
                                   │
═══════════════════════════════════╪══════════════════════════════════════
                      THE STRICT ABSTRACTION BARRIER
═══════════════════════════════════╪══════════════════════════════════════
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                     COMPOSITE PARENT: DeviceBridge                     │
│                                                                        │
│   pub struct DeviceBridge {                                            │
│       pub info: DeviceInfo,                                            │
│       pub root: Option<Arc<RootBridge>>,   // UID 0: Kernel & Hardware │
│       pub shell: Arc<ShellBridge>,         // UID 2000: ADB & scrcpy   │
│       pub app: Option<Arc<AppBridge>>,     // UID 10xxx: Push events   │
│       pub events: broadcast::Sender<DeviceEvent>,                      │
│   }                                                                    │
└────────────────────────────────────────────────────────────────────────┘
```

### 4.1 The Composite Parent: `DeviceBridge`

`DeviceBridge` uses **composition over inheritance** to unify device lifecycle, capability querying, and event distribution:

1. **Unified Device Lifecycle:** A single persistent handle representing the target device. Reconnection loops or health drops manage child bridges under the hood.
2. **Central Event Distribution:** Dispatches typed domain events (`DeviceEvent::ClipboardChanged`, `DeviceEvent::IncomingCall`, `DeviceEvent::BatteryUpdate`) to all registered features over a Tokio broadcast bus.
3. **Capability Querying:** Features can inspect device capabilities cleanly at runtime (e.g., `device.has_root()`, `device.is_app_installed()`) and gracefully degrade if a child bridge is not available.

```rust
pub struct DeviceBridge {
    pub info: DeviceInfo,
    pub root: Option<Arc<RootBridge>>,   // UID 0: Kernel & Hardware
    pub shell: Arc<ShellBridge>,         // UID 2000: ADB & scrcpy
    pub app: Option<Arc<AppBridge>>,     // UID 10xxx: Push events
    events: broadcast::Sender<DeviceEvent>,
}
```

---

### 4.2 The Unified `Feature` Trait Contract

Adding any new feature to Singularity requires implementing a single clean trait with standardized lifecycle, event, and command hooks:

```rust
#[async_trait]
pub trait Feature: Send + Sync {
    /// Unique machine identifier (e.g., "clipboard", "calling", "battery")
    fn id(&self) -> &'static str;

    /// Human-readable title for UI presentation (e.g., "Universal Clipboard")
    fn name(&self) -> &'static str;

    /// Declares required vs optional child bridges for graceful degradation
    fn requirements(&self) -> FeatureRequirements {
        FeatureRequirements {
            root: RequirementLevel::Optional,
            shell: RequirementLevel::Required,
            app: RequirementLevel::Optional,
        }
    }

    /// Uniform status check for GUI & CLI introspection
    fn status(&self) -> FeatureStatus;

    /// Invoked when a device establishes an active connection
    async fn on_connected(&mut self, device: &DeviceBridge) -> Result<()>;

    /// Invoked when the device disconnects or shuts down (must clean up resources)
    async fn on_disconnected(&mut self) -> Result<()>;

    /// Invoked when a typed domain event is broadcast on the device event bus
    async fn on_event(&mut self, event: &DeviceEvent, device: &DeviceBridge) -> Result<()>;

    /// Invoked when an IPC command targeting this feature is received from GUI or CLI
    async fn on_command(&mut self, cmd: &FeatureCommand, device: &DeviceBridge) -> Result<CommandResponse> {
        Ok(CommandResponse::Unsupported)
    }
}
```

---

### 4.3 The Standard Feature Anatomy (The 4-File Blueprint)

To maintain absolute uniformity across all 14 ecosystem features, every feature in `singularity-core/src/features/` follows the exact same thin front-door folder layout:

```
src/singularity-core/src/features/
├── <feature>.rs             # The Front Door: struct definition & Feature trait implementation
└── <feature>/
    ├── config.rs            # Typed settings (deserialized from ~/.config/singularity/)
    ├── state.rs             # In-memory runtime state, health metrics & statistics
    └── handler.rs           # Pure event matchers & semantic action dispatchers
```

#### Strict Responsibilities per File:
- **`config.rs`:** Defines the serializable user configuration struct (e.g. `ClipboardConfig`). Zero runtime state or bridge interaction.
- **`state.rs`:** Defines the in-memory state and performance counters (e.g. `is_paused: bool`, `sync_count: u64`, `last_synced_at: Option<Instant>`).
- **`handler.rs`:** Implements discrete event handling routines (e.g. `handle_remote_clip()`, `handle_local_clip()`).
- **`<feature>.rs`:** The thin front door. Implements `pub mod`, public re-exports, and wires the `Feature` trait methods to the underlying handlers.

---

### 4.4 The 7 Core Architectural Rules for Features

Every feature implementation must strictly adhere to these seven laws:

1. **The Isolation Rule (Zero Direct Inter-Feature Coupling):**
   A feature must **never** import, reference, or call methods on another feature. If `OTPAutoCopy` extracts a verification code that belongs in the clipboard, it broadcasts `DeviceEvent::OtpReceived`; `ClipboardSync` listens to the event bus independently.
2. **The Abstraction Rule (Zero Direct I/O or Plumbing):**
   Features must **never** open TCP sockets, run raw ADB shell commands, or invoke `su`. All operations must query semantic bridge methods (`device.shell().accept_call().await?`, `device.root().set_charge_limit(80).await?`).
3. **The "Fail-Soft" Capability Degradation Rule:**
   Features must **never assume all bridges are present**. If a non-rooted device connects or the companion app is not yet installed, features requiring missing bridges must report `FeatureStatus::Degraded { reason }` and continue operating in limited mode rather than crashing or panicking.
4. **The Lifecycle Discipline (Clean Resets & Zero Leaks):**
   On `on_disconnected()`, features must abort all spawned background tasks, flush queues, and clear in-flight caches. No zombie processes or leaked memory may survive a disconnect.
5. **The Echo Suppression & Idempotency Rule:**
   Any bidirectional synchronization feature (Clipboard, Media MPRIS, Volume) must implement deterministic echo suppression (content hashing with a sliding origin window). Event handling must be idempotent—receiving the same event twice must produce zero adverse side effects.
6. **The Non-Blocking Event Flow Rule (Zero Dispatcher Starvation):**
   `on_event()` runs directly on the event dispatch loop and must return in microseconds. Long-lived streaming or heavy computation must be spawned into background Tokio tasks.
7. **The Uniform Introspection & IPC Rule:**
   Every feature must expose a serializable `FeatureStatus` and respond to `FeatureCommand`s, allowing `singularity-gui` and `singularity-cli` to inspect and toggle any feature through a uniform, predictable IPC schema.

---

### 4.5 The 4-Layer Extension Guide (Adding Anything Later)

Adding new capabilities or features later is completely isolated and follows a deterministic 1-to-2 step process:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HOW TO EXTEND SINGULARITY                       │
├──────────────────────┬─────────────────────────────────────────────────┤
│ To Add:              │ Steps Required:                                 │
├──────────────────────┼─────────────────────────────────────────────────┤
│ New Hardware / OS    │ 1. Add typed method to RootBridge, ShellBridge, │
│ Capability           │    or AppBridge.                                │
│                      │ 2. Done. No existing features or bridges break. │
├──────────────────────┼─────────────────────────────────────────────────┤
│ New System Domain    │ 1. Add enum variant to DeviceEvent.             │
│ Event                │ 2. Only features that care handle it; all other │
│                      │    features safely ignore it via wildcard.      │
├──────────────────────┼─────────────────────────────────────────────────┤
│ Brand New Ecosystem  │ 1. Create features/<name>.rs + folder blueprint.│
│ Feature              │ 2. Implement Feature trait.                     │
│                      │ 3. Add one line to FeatureRegistry::default().  │
│                      │ 4. Done. Core engine is untouched.              │
├──────────────────────┼─────────────────────────────────────────────────┤
│ New Desktop UI Card  │ 1. Handle FeatureCommand in feature.rs.         │
│ or Keybinding        │ 2. Add egui-widgetkit card in singularity-gui   │
│                      │    that sends the command via client.           │
└──────────────────────┴─────────────────────────────────────────────────┘
```

---

### 4.6 The Feature Registry & Fault Isolation

Features are orchestrated inside `singularity-daemon` via a centralized **`FeatureRegistry`**. The registry isolates errors so a single misbehaving feature can never compromise the daemon or the connection:

```rust
pub struct FeatureRegistry {
    features: Vec<Box<dyn Feature>>,
}

impl FeatureRegistry {
    pub async fn dispatch_event(&mut self, event: &DeviceEvent, device: &DeviceBridge) {
        for feature in &mut self.features {
            if let Err(e) = feature.on_event(event, device).await {
                tracing::warn!(feature = feature.id(), error = %e, "Feature event dispatch failed");
            }
        }
    }
}
```

---

## 5. Multi-Bridge Collaboration: Sensor-Actuator Model

Complex continuity features naturally span multiple bridges without coupling. Singularity structures these workflows using the **Sensor-Actuator Pattern**:

- **`AppBridge`** acts as the **Sensor**: Employs zero-overhead Android OS listeners (`NotificationListenerService`, `TelephonyManager`, `ContentObserver`) to intercept real-time events with 0% idle CPU.
- **`ShellBridge`** and **`RootBridge`** act as the **Actuators**: Execute privileged actions that standard sandboxed Android applications cannot perform.

```
┌────────────────────────────────────────────────────────────────────────┐
│                     MULTI-BRIDGE COLLABORATION FLOW                    │
│                                                                        │
│  1. EVENT DETECTION (Sensor)                                           │
│     AppBridge: Catches incoming call via TelephonyManager              │
│     Emits DeviceEvent::IncomingCall { caller: "Alice", ... }           │
│                            │                                           │
│                            ▼                                           │
│  2. ORCHESTRATION (PC Daemon & GUI)                                    │
│     singularity-daemon receives event via ADB highway                   │
│     Dispatches event to DesktopCalling feature                         │
│     GUI displays animated spring CallResponder modal                   │
│     User clicks [Answer] on desktop                                    │
│                            │                                           │
│                            ▼                                           │
│  3. PRIVILEGED EXECUTION (Actuator)                                    │
│     ShellBridge: Runs `telecom accept-call` via ADB shell              │
│     ShellBridge: Launches scrcpy voice-call-downlink audio stream      │
│     RootBridge: Sets real-time audio scheduling priority               │
│                                                                        │
│  4. TWO-WAY CONVERSATION (The Desk Model)                              │
│     Caller voice streams to laptop speakers / headphones               │
│     User speaks into phone's physical desk microphone                  │
└────────────────────────────────────────────────────────────────────────┘
```

### Real-World Multi-Bridge Scenarios

| Scenario | Sensor (Event Trigger) | Orchestration (Feature Engine) | Actuator (Execution) |
|---|---|---|---|
| **Universal Clipboard** | `ShellBridge` receives scrcpy `0x00` PUSH packet | `ClipboardSync` validates hash & avoids echo loops | `wl-clipboard-rs` writes to Wayland clipboard |
| **Desktop Calling** | `AppBridge` `TelephonyManager` detects ringing | `DesktopCalling` presents UI responder | `ShellBridge` executes `telecom accept-call` + scrcpy downlink |
| **Instant OTP Interception** | `AppBridge` SMS ContentObserver regex matches OTP | `OTPAutoCopy` extracts code | `ShellBridge` writes to phone clipboard; daemon syncs to PC |
| **Smart Battery Limiter** | `AppBridge` broadcast or `RootBridge` sysfs reports ≥80% | `BatteryLimiter` triggers cutoff rule | `RootBridge` writes `0` to `/sys/class/power_supply/battery/charging_enabled` |

---

## 6. The Desktop UI Architecture (`singularity-gui`)

The desktop application is built **exclusively using `egui-widgetkit`**, drawing upon its tactile game-feel and keyboard-centric design.

```
┌────────────────────────────────────────────────────────────────────────┐
│                      singularity-gui ARCHITECTURE                      │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ egui-widgetkit Foundation Layers                                 │  │
│  │                                                                  │  │
│  │  ⚡ spring-core & egui-spring:                                    │  │
│  │     Closed-form analytical ODE physics drive all card expands,   │  │
│  │     dock slides, and toast popups. Settles to 0.00% CPU.         │  │
│  │                                                                  │  │
│  │  ⌨️ egui-vim-nav:                                                │  │
│  │     Directional focus graph (H/J/K/L). Complete hands-on-keyboard│  │
│  │     navigation across device cards, settings, and call popups.   │  │
│  │                                                                  │  │
│  │  🎨 egui-themes:                                                 │  │
│  │     Token-driven semantic palettes (Catppuccin Mocha / Frappe).  │  │
│  │     Zero hardcoded colors. Perfect Hyprland aesthetic match.     │  │
│  │                                                                  │  │
│  │  📐 egui-nav-stack & egui-layout:                                │  │
│  │     In-place morphing geometry over jarring popups.              │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│  ┌──────────────────────────────────▼───────────────────────────────┐  │
│  │ Singularity Tactical View Hierarchy                              │  │
│  │                                                                  │  │
│  │  • StatusHUD: Live battery %, charging wattage, 5G bars, ping    │  │
│  │  • ClipboardDeck: Synced clip history, pause toggle, loop status │  │
│  │  • CallResponder: Animated floating modal for incoming calls    │  │
│  │  • MirrorLauncher: Instant 120fps scrcpy session activator       │  │
│  │  • HardwareHUD: PC CPU/GPU load graphs, Hyprland workspace sync  │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│  ┌──────────────────────────────────▼───────────────────────────────┐  │
│  │ singularity-client (Unix Domain Socket / Non-blocking Tokio)     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### The Disposable GUI Principle:
- `singularity-ui` is **completely decoupled** from the background daemon.
- It communicates solely over `/run/user/<UID>/singularity.sock`.
- If Hyprland reloads, the compositor crashes, or the user closes the window, **the daemon continues running unaffected**.

---

## 7. Local IPC Specification (Daemon ↔ GUI / CLI)

The communication bridge between `singularity-daemon` and `singularity-ui` / `singularity-cli` operates over a standard local UNIX domain socket:

- **Socket Path:** `/run/user/<UID>/singularity.sock`
- **Framing:** Length-delimited JSON envelopes (`tokio_util::codec::LengthDelimitedCodec`).

### Request-Response Schema:
```json
// Client -> Daemon Request:
{
  "id": 1,
  "action": "execute_shell",
  "params": {
    "command": "telecom accept-call"
  }
}

// Daemon -> Client Response:
{
  "id": 1,
  "success": true,
  "data": {
    "exit_code": 0
  }
}
```

### Event Streaming (Pub/Sub):
When the GUI connects, it can subscribe to the daemon's live event bus:
```json
// Daemon -> Client Broadcast:
{
  "event": "battery.update",
  "data": {
    "percentage": 82,
    "temperature": 31.4,
    "is_charging": true
  }
}
```

---

## 8. Summary & Standards

| Architectural Dimension | Singularity Standard |
|---|---|
| **Language** | 100% Safe Rust across all desktop components |
| **GUI Framework** | `egui` (0.27) + `egui-widgetkit` (Spring physics, Vim nav, Themes) |
| **Async Runtime** | `tokio` (multi-threaded work-stealing scheduler) |
| **IPC Protocol** | UNIX Domain Socket with length-delimited JSON envelopes |
| **Android Transport** | Persistent ADB over TCP (`10.235.203.184:5555`) |
| **Service Lifecycle** | `systemd --user` unit (`singularity.service`, `Restart=always`) |
| **Fault Isolation** | Daemon runs 24/7 independently; GUI is disposable |
