# Singularity — Refactoring & Line-Limit Rules

This document defines the code modularity thresholds and refactoring procedures for **Singularity**.

---

## 1. The Core Standard: 150-Line Threshold

To maintain long-term velocity and prevent sprawling files:
- **Maximum File Length:** Production Rust source files should not exceed **150 lines**.
- When a file crosses 150 lines, refactor it into smaller, single-purpose subcomponents.

---

## 2. Refactoring Triggers

A file must be refactored and split when **any** of the following conditions are met:

1. **Line Count Exceeded:** Production code exceeds **150 lines**.
2. **Multiple Responsibilities:** The file addresses more than one concern (e.g., both protocol parsing and socket dispatch).
3. **Compound UI Views:** A view contains more than basic layout composition (e.g., embeds multiple custom cards, toolbars, list renderers).
4. **Multi-Stage Processing:** A module performs parsing, validation, transformation, and orchestration all in one file.

---

## 3. The Refactoring Pattern: File → Directory + Orchestrator

Whenever a file outgrows its single-file form, convert it into a **thin front-door file** accompanied by a **submodule directory** of the exact same name:

### Step-by-Step Transition

#### Stage 1: Starting Simple (Single File)
```
src/singularity-core/src/features/
└── clipboard.rs          # Simple clipboard (< 100 lines)
```

#### Stage 2: Complexity Growth
As features expand (handling Wayland listeners, scrcpy `0x00`/`0x09` framing, deduplication caches, and echo suppression), `clipboard.rs` grows beyond 150 lines.

#### Stage 3: The Split
1. Create a directory matching the file name: `src/singularity-core/src/features/clipboard/`.
2. Extract each distinct component into its own single-purpose file.
3. Refactor `clipboard.rs` to act strictly as the **thin front door and high-level orchestrator**.

```
src/singularity-core/src/features/
├── clipboard.rs              # Front door: pub mod declarations & Feature trait wiring
└── clipboard/
    ├── types.rs              # Domain models (ClipPayload, ClipSource)
    ├── echo_suppressor.rs    # Hash cache & one-directional loop prevention
    ├── wayland_adapter.rs    # wl-clipboard-rs integration
    └── scrcpy_adapter.rs     # scrcpy-server clipboard byte bridge
```

---

## 4. Role of the Main Front-Door File (`foo.rs`)

The main entry file (`foo.rs`) must remain lean:
- **Module declarations:** Declare its submodules (`pub mod types;`, `pub mod echo_suppressor;`, etc.).
- **Public re-exports:** Re-export primary types and entry points (`pub use self::types::*;`).
- **High-level orchestration:** Assemble the subcomponents into the top-level container or pipeline.
- **Zero low-level implementation details:** Complex byte parsing, custom painter routines, and helper calculations belong strictly in their respective submodule files.

---

## 5. Domain-Specific Splitting Guidelines

### A. UI Views & Screens (`singularity-gui`)
- **Headers & Toolbars:** Separate into `<view>/header.rs` or `<view>/toolbar.rs`.
- **Widgets & Cards:** Extract individual cards (battery card, macro pad) into dedicated files.
- **Modals & Dialogs:** Isolate modals (e.g. `call_modal.rs`) in dedicated files.

### B. Bridges & Transports (`singularity-core`, `singularity-daemon`)
- **Bridge Implementations:** Keep `root_bridge.rs`, `shell_bridge.rs`, and `app_bridge.rs` focused on capability dispatch.
- **Protocol Framers:** Separate length-delimited IPC codecs from scrcpy big-endian binary parsers.

---

## 6. Exemptions: When NOT to Split (> 150 Lines Allowed)

Splitting solely to satisfy a line limit is prohibited when it would harm readability or fragment indivisible logic. The following cases are explicitly exempt:

### Exemption 1: Inline Unit Test Suites
- Unit tests co-located at the bottom of the file in `#[cfg(test)] mod tests` do not count against the production line limit.

### Exemption 2: Static Lookup Tables & Key Matrices
- Large static lookup tables (e.g., keycode mapping tables, scrcpy control message code tables) represent a single logical dataset.

### Exemption 3: Pure Enums & Exhaustive Match Dispatchers
- Large enum definitions and exhaustive match arms (e.g., protocol opcode dispatch, IPC request routing).

### Exemption 4: Cohesive Mathematical / DSP Algorithms
- Tightly coupled math algorithms (e.g., spring ODE physics solvers, audio submix resamplers).

---

## 7. Exemption Annotation Syntax

If a file exceeds 150 lines under one of the valid exemptions above, add the following annotation at the very top of the file:

```rust
// singularity: allow-large-file (reason: static scrcpy control message opcode table)
```
