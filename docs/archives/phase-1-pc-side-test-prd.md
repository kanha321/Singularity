# Feature Test PRD: PC-Side Wayland & ADB Stack Validation (Phase 1)

**Project:** Singularity  
**Scope:** Phase 1 Arch Linux PC-Side Validation Test  
**Companion Test PRD:** [docs/phase-0-diagnostic-test-prd.md](file:///home/kanhaji/Projects/singularity/docs/phase-0-diagnostic-test-prd.md)  
**Outcome / Validation:** [achievements/phase-1-pc-side-validation.md](file:///home/kanhaji/Projects/singularity/achievements/phase-1-pc-side-validation.md)
**Scope:** The Arch Linux component only. Android side is done — the Phase 0 diagnostic proved `scrcpy-server.jar`, launched headless (`video=false audio=false`) as shell UID, is the production clipboard engine. Nothing further to build on-device for v1.

---

## 1. Executive Summary

Four components remain, matching what was previously scoped at a high level:

1. A native Wayland clipboard watcher (not a shelled-out `wl-paste --watch` wrapper — a better option exists, see §2.2).
2. An ADB-aware launcher that automates what the Phase 0 test did by hand (push the server jar, run the exact `app_process` invocation, set up the forward).
3. A client implementing the now-validated wire protocol (`SET_CLIPBOARD` / `GET_CLIPBOARD` / `CLIPBOARD` push / `ACK_CLIPBOARD`).
4. A `systemd --user` unit — with one compositor-specific gotcha that needs handling explicitly, not assumed (§2.1).

This document is the research record for the choices below, same format as the Phase 0 PRD.

---

## 2. Research Findings

### 2.1 systemd user units do not automatically see `WAYLAND_DISPLAY` — compositor-dependent, and Hyprland is the worst case

This is worth flagging before writing a single line of the daemon, because it's the most likely cause of "the service starts but the clipboard watcher silently does nothing."

`systemd --user` units only inherit environment variables that have been explicitly imported into the systemd user manager's environment block — they do **not** automatically inherit whatever was set in the shell that logged you in, and critically, Wayland compositors differ in whether they do this import for you:

- **Hyprland explicitly does not start the systemd user session by default.** Its own FAQ/docs confirm that without an explicit `exec-once = dbus-update-activation-environment --systemd WAYLAND_DISPLAY XDG_CURRENT_DESKTOP` (and typically also `systemctl --user start hyprland-session.target`) in `hyprland.conf`, `systemd --user` units simply won't have `WAYLAND_DISPLAY` set, and any portal or Wayland-dependent service started "before that has happened" fails to find the display.
- Sway has had its own history of environment-propagation bugs (`WAYLAND_DISPLAY` env var not matching the actual socket in multi-instance scenarios), and Arch's sway package ships a `50-systemd-user.conf` specifically to help with this — so even on Sway, don't assume it's automatic without checking.
- The practical pattern used by several real projects shipping systemd-user Wayland daemons (a voice-dictation tool and a status-bar shell, both confirmed via their install docs) is: compositor config does `exec-once = dbus-update-activation-environment --systemd WAYLAND_DISPLAY XDG_CURRENT_DESKTOP`, binds the daemon's unit to a compositor-specific systemd target (`hyprland-session.target`, `sway-session.target`, etc.) via `systemctl --user add-wants <session-target> <your-unit>` instead of a generic `graphical-session.target`, and the unit's own `[Unit]` section declares `After=<session-target>`.

**Action item:** don't assume `After=graphical-session.target` alone is sufficient on Hyprland. Add the explicit `dbus-update-activation-environment` line to the compositor config and verify with `systemctl --user show-environment | grep WAYLAND_DISPLAY` before debugging the daemon itself if the watcher appears to start but never fires.

Sources: https://wiki.hypr.land/FAQ/, https://github.com/swaywm/sway/issues/7782, https://danklinux.com/docs/dankmaterialshell/compositors, https://pypi.org/project/dictum-asr/

### 2.2 Use `wl-clipboard-rs`'s native `Watcher`, not a shelled-out `wl-paste --watch`

Originally scoped as "shell out to `wl-paste --watch bash -c ...`." Better option found: the `wl-clipboard-rs` crate (the same project that implements the `wl-paste`/`wl-copy` CLI tools) now ships a library-level `watch` module with a `Watcher` API — a blocking call that polls the Wayland `data-control` protocol directly and yields typed `ClipboardEvent` values (content changed vs. `ClipboardEvent::Cleared`), rather than us spawning a subprocess, parsing its stdout, and re-invoking `wl-paste` on every single change.

This is a straightforward upgrade over the shell-out approach:
- No process-spawn-per-clipboard-change overhead.
- No stdout-parsing/quoting fragility.
- Distinguishes a genuine clear from an empty string.
- 100% safe Rust, no `libwayland-client.so` linkage required unless the `native_lib` feature is explicitly opted into.

Use it as a direct dependency (`wl-clipboard-rs`), not the CLI binary. Same crate also exposes `wl-copy`-equivalent write functions for the receive side (setting the local clipboard when a push arrives from Android), so one dependency covers both directions on the PC side.

Source: https://github.com/YaLTeR/wl-clipboard-rs, https://github.com/YaLTeR/wl-clipboard-rs/pull/83 (the `Watcher` API)

### 2.3 Don't shell out to the `adb` binary either — a Rust ADB crate is the better fit, with one compatibility caveat

Three real options surfaced, each with a different relationship to the existing persistent ADB setup:

| Crate | Connects via | Async | Notes |
|---|---|---|---|
| `forensic-adb` | existing local `adb` server (port 5037) | tokio | Fork of Mozilla's `mozdevice`, used in production test tooling; talks to the *same* `adb` server your existing persistence scripts already manage |
| `adb_client` | server-proxy **or** direct USB/TCP, your choice | blocking | Most widely used/downloaded; flexible but blocking API would need a `spawn_blocking` wrapper in an async daemon |
| `rsadb` | **direct to `adbd`**, bypasses the adb server entirely | tokio | Pure Rust, zero C deps, no port-5037 dependency at all — but this means a *second, independent* connection path to the device, parallel to your already-working reconnect infrastructure |

**Recommendation: `forensic-adb`.** Your existing persistence setup (`adb tcpip 5555`, the reconnect-on-network-up script) is already built around the standard `adb` server model — extending that with a crate that talks to the *same* server keeps one source of truth for the connection, rather than introducing `rsadb`'s direct-to-adbd path as a second, independently-reconnecting channel that could race or conflict with the first. `rsadb` is worth keeping in mind later if you ever want to drop the `adb` server dependency project-wide, but that's a bigger architectural change than this daemon should make unilaterally.

Sources: https://github.com/kpcyrd/forensic-adb, https://github.com/cocool97/adb_client, https://github.com/tbraun96/rsadb

### 2.4 The wire protocol is now a known quantity — finalize it from the Phase 0 test, don't re-derive it

Your own validation already pinned this down exactly. Treat it as the spec, not a draft:

| Type | Name | Direction | Payload |
|---|---|---|---|
| `0x08` | `GET_CLIPBOARD` | PC→Android | 1 byte copy-key (use `0x00` NONE) |
| `0x09` | `SET_CLIPBOARD` | PC→Android | 8-byte sequence (non-zero to request ACK), 1 byte paste flag (always `0` for us), 4-byte length, UTF-8 text |
| `0x00` | `CLIPBOARD` | Android→PC (device-pushed) | 4-byte length, UTF-8 text |
| `0x01` | `ACK_CLIPBOARD` | Android→PC | 8-byte sequence, echoing the `SET_CLIPBOARD` that completed |

Server launch parameters to replicate exactly in the automated launcher (these are the flags your manual test proved work):

```
CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server <version> \
  scid=<8-hex-digit-id> log_level=debug video=false audio=false control=true \
  tunnel_forward=true cleanup=false send_device_meta=false send_dummy_byte=false
```

One thing to change versus the manual test: **don't hardcode `scid=01234567`.** The roadmap includes screen mirroring later, which means a second scrcpy server instance may run concurrently on the same device — if both use the same `scid`, the abstract socket name (`@scrcpy_<scid>`) collides. Generate a random 8-hex-digit `scid` per launch (scrcpy's own client does this), and have the launcher thread that value through to the `adb forward` call it sets up afterward.

### 2.5 Loop prevention is now one-directional only — simpler than originally scoped

The original PRD assumed a symmetric ring-buffer check on both ends. That's no longer necessary: scrcpy's `Controller.java` already sets an `isSettingClipboard` flag internally while applying an incoming `SET_CLIPBOARD`, which suppresses the corresponding `PrimaryClipChangedListener` firing back out as a false "device changed" push. The Android side is self-guarding.

What's left: the PC daemon must avoid re-broadcasting a `CLIPBOARD` push it just received the moment `wl-clipboard-rs`'s `Watcher` reports that same value as a local change (because setting the local clipboard via `wl-copy`-equivalent *will* trigger the watcher). A single "last value received, with a short timestamp window" check is sufficient — no need for anything more elaborate.

---

## 3. Proposed Architecture (PC side)

```
┌─────────────────────────────────────────────────────────┐
│ systemd --user unit (Restart=always)                      │
│ After=<compositor-session-target>  (NOT graphical-session) │
│                                                             │
│  ┌───────────────────────┐      ┌─────────────────────┐   │
│  │ wl-clipboard-rs        │      │ forensic-adb         │   │
│  │  Watcher (read)         │      │  - adb forward setup │   │
│  │  write fn (set local)   │      │  - push server jar   │   │
│  └───────────┬─────────────┘      │  - launch app_process│   │
│              │                    └──────────┬────────────┘ │
│              ▼                               ▼                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ singularity-daemon (tokio)                            │   │
│  │  - wire protocol codec (§2.4)                          │   │
│  │  - last-value echo guard (§2.5)                        │   │
│  │  - reconnect/backoff loop around the forward+socket    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Milestones

| Step | Deliverable |
|---|---|
| 1 | Launcher: random `scid`, push jar, spawn server via `forensic-adb`, set up forward — replaces the manual two-terminal harness |
| 2 | Wire protocol codec as a standalone module, unit-tested against the exact bytes from the Phase 0 transcript |
| 3 | `wl-clipboard-rs` `Watcher` wired to the codec's `SET_CLIPBOARD` path (PC→phone) |
| 4 | `CLIPBOARD` push handling wired to `wl-clipboard-rs`'s write function (phone→PC), with the echo guard from §2.5 |
| 5 | systemd unit + compositor `exec-once` line, verified with `systemctl --user show-environment` before trusting it |
| 6 | Reconnect/backoff around the whole pipeline (forward drops, server dies, socket resets) |

---

## 5. Open Questions

1. Compositor-specific `exec-once` lines need to be written per-compositor if this is ever meant to be portable beyond your own Hyprland setup — not blocking for v1 (single-user, single-machine).
2. `forensic-adb`'s async model and `wl-clipboard-rs`'s blocking `Watcher` need a `spawn_blocking` bridge inside the tokio runtime — straightforward, but worth getting the task boundary right early rather than retrofitting it.

---

## 6. References

- https://wiki.hypr.land/FAQ/
- https://github.com/swaywm/sway/issues/7782
- https://danklinux.com/docs/dankmaterialshell/compositors
- https://github.com/YaLTeR/wl-clipboard-rs
- https://github.com/YaLTeR/wl-clipboard-rs/pull/83
- https://github.com/kpcyrd/forensic-adb
- https://github.com/cocool97/adb_client
- https://github.com/tbraun96/rsadb