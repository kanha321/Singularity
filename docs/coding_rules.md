# Singularity — Coding Rules

This is the standing reference for how code gets organized across all crates in **Singularity**. It is meant to be consulted before adding a new module or feature, not only when something feels disorganized.

The folder structure in §3 is **illustrative** — it demonstrates the *shape* a module should take, using representative names, not an exhaustive pre-planned file tree. Every module (clipboard, calling, battery limiter, IPC protocol) will have its own specific files. What must stay constant across all of them is the architectural pattern, not arbitrary filenames.

---

## 1. Crate Boundaries — One-Way Dependency Direction

```
singularity-core  →  singularity-protocol  →  singularity-client  →  { singularity-daemon, singularity-cli, singularity-gui }
```

- **`singularity-core`** has zero socket or IPC awareness. It defines `DeviceBridge`, the specialized child bridges (`RootBridge`, `ShellBridge`, `AppBridge`), and pluggable domain `Feature` implementations.
- **`singularity-protocol`** contains pure data types, big-endian binary framing (scrcpy wire codecs `0x09`/`0x00`), and length-delimited IPC envelopes. It contains **zero I/O** (no sockets, no file handles, no network calls).
- **`singularity-client`** holds the async UNIX socket connection, request-response RPC, and event stream subscription logic shared by all client frontends.
- **`singularity-gui`** and **`singularity-cli`** depend solely on `singularity-client` — never directly on `singularity-core` or ADB transport internals.
- **`singularity-daemon`** links against `singularity-core` and `singularity-protocol` to run the 24/7 background engine, managing the persistent ADB link, Wayland clipboard listener (`wl-clipboard-rs`), and the local UNIX socket.

**Rule:** Never add a dependency that points backwards along that chain. If a UI crate ever seems to need something from `singularity-core` directly, that is a sign the required data belongs in `singularity-protocol` or `singularity-client`.

---

## 2. File-Level Rule: One File, One Job

- Prefer many small, focused files over large monolithic files.
- Split a file when it starts answering more than one architectural question.
- Small, tightly coupled constructs that are always used together (a struct and its small companion enum) can share a file. Unrelated logic never should.

---

## 3. Module Shape: Thin Front Door + Focused Submodule Folder

Rust allows a module's root file to live *beside* its submodule directory instead of inside it — `thing.rs` next to `thing/`, rather than `thing/mod.rs`. **Use this pattern universally.** Avoid `mod.rs` to keep editor tabs and navigation unambiguous.

The root file should remain **dumb** — `pub mod` declarations and a handful of `pub use` re-exports, nothing else. It serves as the module's public front door.

### The Standard Pattern (Generic)

```
features/clipboard.rs      <- Front door: struct definition & Feature trait wiring
features/clipboard/
├── config.rs             <- Deserializable user settings (~/.config/singularity/)
├── state.rs              <- In-memory runtime state, health metrics & counters
└── handler.rs            <- Discrete event matchers & semantic bridge actions
```

---

## 4. Internal Layering: Keep the Pipeline One-Directional

Order files within a module as a pipeline — types → sub-steps / adapters → orchestrator — and never let a lower file call upward into a higher one.

---

## 5. Testing Discipline

- **Unit tests** live in the same file as the code they test (`#[cfg(test)] mod tests` at the bottom). A fix and its test remain adjacent.
- **Integration tests** that cross module or process boundaries belong in the crate's `tests/` directory.

---

## 6. Constants & Configuration

Centralize values that would otherwise be duplicated across files (socket paths, port numbers, timeout durations, protocol magic bytes) in a `constants.rs` per crate.

---

## 7. Error Handling

One shared error type per crate (`error.rs`, built on `thiserror`) rather than ad-hoc stringly-typed errors. This makes graceful fallback and fault tolerance achievable across disconnects and device reboots.

---

## 8. Wire-Format Discipline

Treat any change to an IPC or protocol packet variant as a breaking change. Bump the protocol version rather than assuming backward compatibility on binary streams.
