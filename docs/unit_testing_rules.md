# Singularity — Unit Testing Rules

This document specifies how unit tests must be written, structured, and maintained in **Singularity**.

---

## 1. What Qualifies as a Unit Test

A test is a unit test in this project only if all of the following hold:

- **No Real I/O:** No live TCP sockets, no real ADB connections, no phone hardware, no spawned daemon processes.
- **Microsecond Execution:** Runs in milliseconds and is 100% deterministic on every execution.
- **Co-located:** Lives in the same file as the code it tests.
- **Single Behavioral Focus:** Exercises one function or a small cluster of tightly coupled functions.

Tests requiring live ADB links, a running phone, or full systemd daemon lifecycles belong in integration test suites (`tests/` directory) or manual diagnostic scripts, never in inline unit tests.

---

## 2. Co-location Standard

Every file containing testable logic must feature a `#[cfg(test)] mod tests` block at the bottom of the same file:

```rust
pub fn parse_clipboard_packet(buf: &[u8]) -> Result<ClipboardEvent, ProtocolError> {
    // ...
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_clipboard_packet_returns_ok_for_valid_utf8() {
        // ...
    }

    #[test]
    fn parse_clipboard_packet_returns_err_on_short_buffer() {
        // ...
    }
}
```

Do not create standalone `_test.rs` files shadowing the source directory.

---

## 3. Test Naming Convention

Format: `<function>_<expected_behavior>[_<condition>]`

Examples:
- `encode_set_clipboard_produces_scrcpy_wire_format`
- `echo_suppressor_rejects_duplicate_hash_within_window`
- `battery_limiter_triggers_root_cutoff_at_eighty_percent`

The test name should immediately communicate what failed before reading the test body.

---

## 4. Fakeable Dependencies First

Downstream modules must remain unit-testable without a connected device. Abstract transport and bridge layers behind traits:

```rust
#[async_trait]
pub trait BridgeTransport: Send + Sync {
    async fn send_packet(&self, packet: &[u8]) -> Result<()>;
    async fn receive_packet(&self) -> Result<Vec<u8>>;
}
```

Implement fake test doubles (`FakeBridgeTransport`, `FakeDeviceBridge`) under `#[cfg(test)]` (or in a shared `test_support` module) providing canned responses and packet verification.

---

## 5. One Assertion Focus per Test

Prefer multiple small, focused tests over a single large test with multiple assertions. If an assertion fails, the test suite should pinpoint exactly which contract or edge case broke.
