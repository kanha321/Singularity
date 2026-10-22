# Workspace Rules — Singularity

This file provides standing project rules and operational guidelines for Antigravity in the **Singularity** workspace. These instructions apply across all tasks and conversations.

---

## 0. Mandatory Pre-Action Analysis (CRITICAL — EVERY SINGLE RESPONSE)
Before executing ANY task, proposing ANY design, writing ANY code, or replying to user requests in this workspace, you MUST ALWAYS consult, analyze, and align with all markdown specification documents in `docs/`:
1. `docs/architecture.md` — Master system architecture, the 3 Bridges (`RootBridge`, `ShellBridge`, `AppBridge`), composite `DeviceBridge`, and IPC socket spec.
2. `docs/ecosystem_features.md` — 14-feature catalog, privilege matrix, and hardware collaboration flows.
3. `docs/coding_rules.md` — One-way crate dependencies, one-file-one-job, and thin front-door pattern.
4. `docs/unit_testing_rules.md` — Unit tests co-located at bottom of files, hardware/transport mocking (`FakeBridge`, `FakeTransport`), and deterministic tests.
5. `docs/refactoring_rules.md` — 150-line limit, build script warning enforcement, splitting steps, and exemptions.

---

## 1. Git Operations & Commit Guidelines (CRITICAL)
- **NO AUTOMATIC GIT MODIFICATIONS:** NEVER run any git state-modifying commands (`git add`, `git commit`, `git push`, `git reset`, `git checkout`, `git rebase`, `git stash`, etc.) unless the user **explicitly** asks you to.
- **Concise One-Line Commits:** When asked to commit, always write concise, single-line commit messages.
- **Prefix Convention:** Commit messages must strictly follow the conventional prefix format:
  - `"core: <message>"`
  - `"feat: <message>"`
  - `"fix: <message>"`

---

## 2. File Modularity & Refactoring Discipline
- **150-Line Limit:** Keep files small, focused, and single-purpose. If production code exceeds 150 lines, refactor into subcomponents immediately per `docs/refactoring_rules.md`.
- **The Thin Front-Door Pattern:** Never use `mod.rs`. Every module with child components must be a `foo.rs` file sitting beside a `foo/` directory. `foo.rs` acts strictly as the high-level orchestrator and public front door (`pub mod ...; pub use ...;`).
- **Exemptions:** Large test suites (`#[cfg(test)]`), static mapping tables, pure data enums with match arms, and cohesive algorithms may exceed 150 lines with the `// singularity: allow-large-file (reason)` annotation.

---

## 3. Crate Boundaries & Dependency Flow
- **One-Way Dependency Hierarchy:**
  ```
  singularity-core  →  singularity-protocol  →  singularity-client  →  { singularity-daemon, singularity-gui, singularity-cli }
  ```
- **Core Isolation:** `singularity-core` defines domain logic, `DeviceBridge`, and `Feature` implementations. It has zero socket, IPC, or child process awareness.
- **Protocol Purity:** `singularity-protocol` contains pure codecs, big-endian framing, and packet types with zero I/O.
- **Thin Clients:** `singularity-gui` and `singularity-cli` depend strictly on `singularity-client`. They never link against `singularity-core` or directly touch ADB/hardware.
- **Daemon Ownership:** Only `singularity-daemon` handles the persistent ADB transport, Wayland clipboard listener, and local UNIX domain socket.

---

## 4. UI Library Integration (`egui-widgetkit`)
- `singularity-gui` uses the local `egui-widgetkit` suite located in `/mnt/d/egui-apps/egui-lib` pinned to `egui 0.27`:
  - `egui-widgets`: Tactile buttons, spring sliders, segmented tabs, morphing dropdowns.
  - `egui-themes`: Token-based semantic themes (Catppuccin Mocha / Frappe).
  - `egui-spring`: Analytical ODE spring physics and continuous focus rects (settling to 0.00% CPU).
  - `egui-vim-nav`: Spatial keyboard navigation (`H`/`J`/`K`/`L`) and focus graphs.
- **Universal Action Vocabulary (5 Standard Actions):**
  All UI interactions must query high-level semantic actions via `UnifiedInput`, `WidgetInput<BasicAction>`, or `FocusBinding` instead of hardcoding device keys:
  - **`Primary`** (Select / Toggle / Activate): `[Enter]` / `[Space]` / `[F]` | `Left Click` $\to$ `.is_primary_click()`, `focus.is_triggered()`.
  - **`Secondary`** (Context Menu / Secondary Toggle): `[D]` | `Right Click` $\to$ `.is_secondary_click()`.
  - **`Cancel`** (Back / Close Modal / Unfocus): `[Esc]` / `[Q]` | `Thumb Back` $\to$ `.is_cancel()`.
  - **`Navigate`** (Directional movement): `HJKL` / `Arrow Keys` | `Hover` / `Direct Click` $\to$ `.nav_direction()`.
  - **`Adjust`** (Value / Slider scrubbing): `Shift + H/L` / `Shift + Arrows` | `Middle Click + Drag` $\to$ `.adjust_delta()`.

---

## 5. Storage & Config Isolation
- Runtime sockets live in `/run/user/<UID>/singularity.sock`.
- Persistent configs and paired device metadata reside in `~/.config/singularity/`.
- The repository stays clean of user data; test fixtures reside in `src/singularity-core/tests/fixtures/`.

---

## 6. Unit Testing Standards
- **Unit Tests:** Always co-located at the bottom of the source file (`#[cfg(test)] mod tests`).
- **Integration Tests:** Cross-module pipeline tests go into crate `tests/` directories.
- **Transport Mocking:** Keep bridges fakeable (`FakeBridge`, `FakeTransport`) for deterministic offline testing without requiring a live device.
- **Descriptive Naming:** `<function>_<expected_behavior>[_<condition>]`.

---

## 7. Workspace Isolation & Boundary Guardrail
- **STRICT WORKSPACE BOUNDARY:** NEVER modify, edit, or delete files outside this workspace (`/home/kanhaji/Projects/singularity`). Sibling libraries (such as `/mnt/d/egui-apps/egui-lib` and `/mnt/d/egui-apps/LumosRGB`) must be treated strictly as external, read-only references.
