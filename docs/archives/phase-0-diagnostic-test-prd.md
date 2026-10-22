# Feature Test PRD: Android-Side Clipboard Sync Diagnostic (Phase 0)

**Project:** Singularity  
**Scope:** Phase 0 Diagnostic & Protocol Validation Test  
**Outcome / Validation:** [achievements/phase-0-diagnostic-validation.md](file:///home/kanhaji/Projects/singularity/achievements/phase-0-diagnostic-validation.md)

---

## 1. Executive Summary

This PRD defines the architecture for continuous, low-latency, bidirectional clipboard synchronization between a rooted Android device (Evolution X, Android 16, KernelSU v3.0.1) and an Arch Linux laptop, over an already-working persistent ADB TCP connection.

The core finding of the research phase: **the two sync directions are not symmetric and do not share a solution.** Writing to the Android clipboard is unrestricted and trivial from any process. Reading the Android clipboard in the background is deliberately restricted by Android's privacy model, and — critically — **root access does not bypass this restriction the way shell (ADB) identity does.** This single fact drives almost every architectural decision below.

This document is both the research record and the implementation spec, so that design decisions are traceable to the Android platform behavior that forced them.

---

## 2. Problem Statement

Prior attempts at background clipboard reading failed silently or crashed. Before writing any more code, we needed to know *why*, specifically on this ROM/kernel/root combination, rather than guessing. The research below answers that, and the architecture is designed around the answer.

---

## 3. Goals

- Continuous, near-instant clipboard sync in both directions, no manual trigger required (unlike scrcpy's Ctrl+V-gated model).
- Text content in v1; images/files explicitly out of scope for v1.
- Runs unattended: starts on boot/login on both ends, survives reconnects, no user interaction once configured.
- Self-contained protocol, not dependent on the scrcpy GUI client being open.
- Loop-safe: a synced value must never ping-pong back and forth.

## 4. Non-Goals (v1)

- Image/binary clipboard content.
- Clipboard history / multi-item clipboard.
- Multi-device (one Android ↔ one PC only, for now).
- Encryption beyond what ADB already provides (tracked as a risk, not solved in v1 — see §9).

---

## 5. Research Findings

### 5.1 Android's clipboard security model, precisely

Since Android 10 (API 29), reading `ClipboardManager`'s primary clip is restricted to:
1. The app currently holding window focus, or
2. The current default IME, or
3. A process holding the **shell** identity (a deliberate carve-out for ADB-driven UI testing), or
4. A holder of the hidden permission `android.permission.READ_CLIPBOARD_IN_BACKGROUND`.

**Writing** (`setPrimaryClip`) has none of these restrictions — any process, any time, foreground or background, can set the clipboard. This asymmetry is the single most important fact for this project: it means PC→phone is a solved problem on day one, and all the hard engineering is isolated to phone→PC.

The denial, when it happens, surfaces in logcat as an explicit message along the lines of *"Denying clipboard access to [uid], application is not in focus nor is it a system service for user 0"* — this is useful as a diagnostic signature distinct from a crash or a silent no-op.

Source: `frameworks/base/services/core/java/com/android/server/clipboard/ClipboardService.java` (AOSP), and real-world confirmation from a developer hitting this exact wall trying to build a Link-to-Windows-style tool: https://forum.developer.samsung.com/t/clipboard-access-from-foreground/27641

### 5.2 `READ_CLIPBOARD_IN_BACKGROUND` is not a usable escape hatch

This permission exists (it's what Google's own Pixel↔PC "Universal Clipboard" effort reportedly uses internally) but it is declared `protectionLevel="signature|role"`. In practice this means:
- It cannot be granted via `adb shell pm grant <package> android.permission.READ_CLIPBOARD_IN_BACKGROUND` — a real attempt at this from another developer produced `SecurityException: ... is not a changeable permission type`.
- It's reserved for OEM/Google-signed system packages (e.g. `com.google.android.pixelsystemservice`) or apps holding a specific system role.

**Conclusion: do not pursue this path.** It is not accessible to a third-party or self-signed app, rooted or not.

Sources: AOSP CTS commit (`Bug: 216314180`) at https://android.googlesource.com/platform/cts/+/680b05b92af%5E%21/, and https://github.com/TylerBuza/ClipboardVault/issues/1 (the failed `pm grant` attempt), and background context at https://www.androidauthority.com/android-17-universal-clipboard-rumor-3618614/

### 5.3 The critical trap: root (UID 0) is *not* the same as shell (UID 2000) here

This is the finding that explains your prior failed attempts, and it's specific, reproducible, and well-documented in scrcpy's own issue tracker — not a theory.

scrcpy's server picks its "package identity" based on its own UID at startup:
- If UID is 1000 (system), it claims package `"android"`.
- Otherwise, it claims package `"com.android.shell"`.

This works fine when the server is genuinely running as shell (UID 2000, the normal `adb shell` identity) — UID 2000 really does own the package `com.android.shell` in Android's package database, so the clipboard service's internal ownership check passes.

**It breaks when the server runs as root (UID 0) instead**, because UID 0 does *not* own the package `com.android.shell` (UID 2000 does). The clipboard service performs a UID-to-package ownership check as part of granting access, and this throws:

```
java.lang.SecurityException: Package com.android.shell does not belong to 1000
```
(or, on some builds: `Calling uid 0 does not own package com.android.shell`)

This is a documented, recurring scrcpy bug across multiple Android versions, including as recently as Android 16 builds, and scrcpy's own maintainers now state outright that the server **must** run as either system (1000) or shell (2000) — root alone is insufficient and actively breaks clipboard access.

Sources:
- https://github.com/Genymobile/scrcpy/pull/4127
- https://github.com/Genymobile/scrcpy/issues/6224 ("So Scrcpy must run as system (uid 1000) or shell (uid 2000)")
- https://github.com/Genymobile/scrcpy/issues/4614

**This reframes the whole project.** The daemon does not need "root" for the clipboard read — it needs *shell*, specifically. Root is useful for other future features (notifications, input injection, SMS) but is actively counterproductive for this one specific binder call unless the process is made to genuinely assume the shell UID.

### 5.4 KernelSU's SELinux behavior — good news, but not the whole story

KernelSU creates its own `su` SELinux domain and loads kernel-level policy rules making that domain effectively unrestricted (allow-all to any type/class, with every domain permitted to transition into it). This means **SELinux is not what's blocking root-based clipboard access on a KernelSU device** — the block in §5.3 is a Java-level/Binder-level ownership check inside `ClipboardService`, enforced regardless of what SELinux allows.

Practical implication: a process can have full SELinux freedom (via `su`) and still be denied the clipboard call, because the denial happens above the SELinux layer, in framework code that checks `Binder.getCallingUid()` against the package it claims to be.

A corollary worth testing (flagged as a validation task, not yet confirmed on this device): if a root-owned process performs a genuine `setuid(2000)`/`setgid(2000)` (not just claiming the package name, but becoming the real POSIX shell UID) before making the clipboard call, the ownership check should pass, because the system would see a real UID-2000 caller — exactly what an `adb shell` session already is. Whether the SELinux domain also needs to transition to `u:r:shell:s0` for this to work cleanly (rather than staying in `u:r:su:s0`) is unverified and listed as an open risk (§10).

Sources: https://www.8ksec.io/android-selinux-internals-part-ii/, https://kernelsu.org/guide/app-profile.html

### 5.5 scrcpy's control protocol — the clipboard wire format we'll reuse

scrcpy's control socket is a binary protocol, big-endian, one byte of message type followed by a type-specific payload. The two relevant messages:

| Type | Name | Direction | Payload |
|---|---|---|---|
| `0x08` | `GET_CLIPBOARD` | Client→Server | 1 byte: copy-key (`NONE`/`COPY`/`CUT`) |
| `0x09` | `SET_CLIPBOARD` | Client→Server | 8-byte sequence number, 1 byte `paste` flag, 4-byte length, UTF-8 text |
| `0x00` | `CLIPBOARD` (device message) | Server→Client | 4-byte length, UTF-8 text — sent automatically whenever the device clipboard changes |

Key behavioral details confirmed from scrcpy's own documentation and source:
- Device→computer sync is **continuous and automatic** — the server pushes a `CLIPBOARD` device message any time the Android clipboard changes, no polling needed.
- Computer→device sync (`SET_CLIPBOARD`) is **not continuous in the stock client** — it's sent only when the user presses a paste shortcut in the scrcpy window. The server itself almost certainly accepts this message at any time; it's a client-side choice not to send it proactively.
- Max message size is `1 << 18` (262,144 bytes).
- `SET_CLIPBOARD`'s `sequence` field (when non-zero) requests a device-side ACK once the clipboard is actually set — useful for knowing a push landed rather than fire-and-forget.
- Text is transferred as raw UTF-8 over the socket, never shell-interpreted, so there's no quoting/escaping hazard like there would be piping through `adb shell` string arguments.

Sources: https://github.com/Genymobile/scrcpy/issues/1580, https://tangoadb.dev/next/scrcpy/control/clipboard/, https://man.archlinux.org/man/extra/scrcpy/scrcpy.1.en, https://github.com/XRSec/Screen-Remote/wiki/Clipboard-Synchronization

### 5.6 Prior art worth studying directly (not reinventing)

Three existing projects have already solved pieces of this exact problem — worth reading their code before writing ours:

**scrcpy-rfb / cocoonbox** (https://github.com/cocoonstack/scrcpy-rfb, https://github.com/loomhand/cocoonbox) — an independent project that hit the *exact* UID trap from §5.3 and solved it the way we're proposing: it runs its main session as system/root for screen mirroring, and spawns a **second, separate, shell-UID-only scrcpy session dedicated purely to clipboard**, with its own reconnect-with-backoff loop and logging ("clipboard session ended; reconnecting in 250ms, then 500ms, then 1000ms"). This is close to a reference implementation for our Android-side daemon split.

**ClipShare** (`clipshare_clipboard_listener` on pub.dev) — a cross-platform (Android/Windows/Linux/macOS) clipboard sync tool, explicitly documented as requiring **Shizuku or Root** for Android 10+ background monitoring. Real-world confirmation this exact problem has a shipped solution using the Shizuku approach (§5.7).

**scrcpy itself** — already our GET/SET_CLIPBOARD reference and, if we reuse `scrcpy-server.jar` unmodified as a black box, we inherit its already-solved Android-version-compatibility handling (reflection-based `IClipboard` method lookup, handling the Android 14 deviceId parameter change, per-OEM workarounds for Samsung/IQOO devices) for free, instead of re-debugging those edge cases ourselves.

### 5.7 Shizuku as an alternative architecture (worth a build-vs-adopt decision)

Shizuku (https://github.com/RikkaApps/Shizuku) is a mature, actively maintained framework that does roughly what we're about to hand-build: it starts a privileged background server via either ADB (UID 2000, shell) or root (UID 0), and lets client apps make Binder calls through it as that identity, persisting across reboots via a boot-start mechanism.

Relevant to us specifically: **Shizuku explicitly supports starting as shell UID 2000 via ADB**, which is exactly the identity the clipboard read path requires, and it already handles the boot-persistence, reconnection, and permission-granting plumbing we'd otherwise build ourselves.

This is a genuine build-vs-adopt fork in the road:
- **Adopt Shizuku-as-library**: our Android component becomes a small app using `Shizuku-API`, calling `IClipboard` through `ShizukuBinderWrapper`. Less custom daemon code, inherits a well-tested privilege bridge, but adds a dependency and a second moving part (Shizuku itself must be running).
- **Build our own minimal shell-UID daemon**: more code, but zero dependency on a third-party privileged-access app, and simpler mental model (one daemon, not two).

Recommendation: **prototype with a standalone shell-UID `app_process` daemon first** (keeps the system legible and dependency-free), and keep Shizuku-as-library as the fallback if boot-persistence of a raw shell-identity process proves fragile.

Sources: https://github.com/RikkaApps/Shizuku, https://github.com/RikkaApps/Shizuku-API, https://www.xda-developers.com/implementing-shizuku/

### 5.8 Transport layer (already built)

Existing infrastructure, confirmed working:
- `adb tcpip 5555` + persistent reconnect script on the Arch side, survives reboot on both ends, auto-reconnects when both devices join the same network.
- `ro.adb.secure=1` confirmed — RSA key auth is required, but this is authentication only; **the TCP payload after the handshake is plaintext.** This is fine for a trusted home LAN but is explicitly tracked as a risk, not a solved problem (§9).

For this feature we'll use `adb forward tcp:<PORT> tcp:<PORT>` (PC dials out to a port the Android-side daemon listens on) since the PC already initiates the connection in the existing setup; no need for `adb reverse` for this specific feature.

---

## 6. Proposed Architecture

```
┌─────────────────────────────┐          ┌──────────────────────────────────────┐
│ Arch Linux (laptop)          │          │ Android 16 / Evolution X / KernelSU    │
│                               │  adb     │                                        │
│ ┌───────────────────────┐    │ forward  │  ┌──────────────────────────────┐      │
│ │ wl-paste --watch       │    │  tcp     │  │ Shell-UID (2000) daemon       │      │
│ │  (clipboard watcher)   │    │ :PORT    │  │  app_process, clipboard only  │      │
│ └──────────┬─────────────┘    │◄────────►│  │  - GET/SET_CLIPBOARD          │      │
│            │                  │          │  │  - Reflection-based IClipboard│      │
│ ┌──────────▼─────────────┐    │          │  │    lookup (scrcpy-derived)    │      │
│ │ sync-daemon (Rust/Go)   │    │          │  └──────────────────────────────┘      │
│ │  - socket client        │    │          │              ▲                         │
│ │  - loop-prevention      │    │          │              │ (local socket)          │
│ │  - sets system clipboard│    │          │  ┌───────────┴────────────────────┐    │
│ └──────────┬─────────────┘    │          │  │ Root (KernelSU) supervisor       │    │
│            │                  │          │  │  - launched by service.sh at boot│    │
│ systemd --user unit            │          │  │  - respawns shell daemon on crash│    │
│  (keeps both alive,            │          │  │  - will host future root-only   │    │
│   restarts on failure)         │          │  │    features (notifications, etc)│    │
└─────────────────────────────┘          │  └──────────────────────────────────┘    │
                                           └──────────────────────────────────────┘
```

Two cooperating Android-side processes, matching the proven scrcpy-rfb pattern from §5.6:
1. **Shell-UID clipboard daemon** — does only clipboard GET/SET, nothing else, kept intentionally minimal to minimize the surface area that has to be "genuinely shell."
2. **Root supervisor** — launched at boot by a KernelSU `service.sh` module, responsible for keeping the shell daemon alive (respawn on crash/ADB disconnect) and, later, hosting the root-requiring features from the original roadmap (notifications, file access, input injection) that don't hit this UID restriction at all.

This split means the UID-sensitive code stays small and auditable, while everything else in the broader "better than KDE Connect" project can freely use root.

### 6.1 Loop prevention

Both directions must suppress their own echo. Design:
- Maintain a small in-memory ring buffer (last N=5 synced values, each with a timestamp) on both the PC daemon and the Android daemon.
- Before broadcasting a locally-observed clipboard change, check it against the recently-received buffer; if it matches a value received in the last ~2 seconds, treat it as an echo and do not re-send.
- This is deliberately simple (no cryptographic hashing needed for v1 text-only scope) — a content+timestamp check is sufficient for a single-user two-device system.

### 6.2 Reconnection & persistence

Reuses the already-solved problem from the base project: the sync-daemon on the PC side is a `systemd --user` service (`Restart=always`), and the Android-side daemons are supervised by the KernelSU boot script. Neither side needs new reconnect logic beyond what's already built for the base ADB tunnel — the socket client simply retries `adb forward` + socket connect on a backoff until the tunnel is up.

---

## 7. Detailed Design

### 7.1 Wire protocol (PC daemon ↔ Android shell daemon)

v1 recommendation: **reuse scrcpy's binary GET/SET_CLIPBOARD framing verbatim** rather than inventing a new JSON protocol, for two reasons: (a) it's already battle-tested against UTF-8 edge cases (CJK, emoji, control characters — explicitly covered by scrcpy's own protocol tests), and (b) it lets us optionally swap in the unmodified `scrcpy-server.jar` as the Android-side implementation instead of writing our own `IClipboard` reflection code (see 7.2, Option A).

| Field | Type | Notes |
|---|---|---|
| `type` | `u8` | `0x08` GET, `0x09` SET, `0x00` device→client push |
| `sequence` | `u64` (SET only) | 0 = fire-and-forget; non-zero = request ACK |
| `paste` | `u8` (SET only) | 0 for our use case — we never want to auto-inject a paste keystroke, only set the clip |
| `length` | `u32` | byte length of UTF-8 payload |
| `text` | `[u8; length]` | raw UTF-8, never shell-escaped |

### 7.2 Android-side implementation: two options

**Option A — reuse `scrcpy-server.jar` unmodified as the clipboard engine.**
Push the stock server jar, launch it as shell UID with `--control-only`-style flags (video/audio disabled), and only ever send it `GET_CLIPBOARD`/`SET_CLIPBOARD` messages. We get their already-solved IClipboard reflection and OEM workarounds for free. Downside: carrying a full scrcpy server for a clipboard-only daemon is heavier than necessary, and we're coupled to their message framing choices for unrelated message types.

**Option B — minimal custom `app_process` daemon.**
A small Java/Kotlin class launched via `app_process` as shell UID, implementing only:
1. Reflection-based lookup of `IClipboard`'s available methods at runtime (don't hardcode signatures — inspect what's actually present, exactly as scrcpy had to start doing after the Android 14 deviceId parameter change broke their hardcoded calls).
2. A `PrimaryClipChangedListener` registration for the push side.
3. A tiny local socket server speaking the §7.1 framing.

**Recommendation: start with Option B.** It's a few hundred lines, fully under our control, and avoids dragging in scrcpy's video/input/UHID code paths we'll never use. Keep Option A as a fallback if the reflection-based IClipboard lookup turns out to be more fragile on Evolution X than expected — at that point, borrowing scrcpy's exact implementation is a fast unblock.

### 7.3 Launching the shell daemon without an interactive `adb shell`

Two mechanisms, worth implementing both (first is the MVP, second is the hardening step):

1. **MVP: launched by the PC daemon over the existing adb connection.** On startup, the PC daemon runs `adb shell` (plain, no `su`) to launch `app_process ... ClipboardDaemon`, then establishes `adb forward` to its socket. Simple, correct-by-construction (genuinely is a real shell session), but depends on the PC being the one to kick it off.

2. **Phase 2 hardening: boot-time launch by the root supervisor.** The KernelSU `service.sh` (root, runs at boot) spawns the daemon via a privilege-drop to UID/GID 2000 with matching supplementary groups (mirroring what a real `adb shell` session has) before `exec`ing into the daemon binary, so it's running and ready even before the PC reconnects. This needs validation (§10) — specifically whether the SELinux domain also needs an explicit transition to `u:r:shell:s0`, or whether staying in `u:r:su:s0` (which is unrestricted per §5.4) is sufficient since the failing check is Java-level, not SELinux-level.

### 7.4 PC-side

- **Watcher:** `wl-paste --watch` (Wayland) — already identified as the right primitive; test on your specific compositor since `--watch` reliability varies (Hyprland/Sway/KDE Wayland differ slightly).
- **Daemon:** small Rust or Go binary — owns the socket client, the loop-prevention buffer, and setting the local clipboard (`wl-copy` shellout, or a Wayland clipboard library directly).
- **Service management:** `systemd --user` unit, `Restart=always`, `After=graphical-session.target` (must run in-session to access `WAYLAND_DISPLAY`, not as a system-wide service).

### 7.5 Content filtering (v1 scope guard)

- Text/plain only; explicitly ignore non-text MIME types on both watchers for v1 (images/files are a defined non-goal, see §4).
- Debounce rapid-fire duplicate events — some apps write to the clipboard more than once per user-perceived "copy."
- Enforce the 262,144-byte scrcpy protocol ceiling as our own size cap too, rejecting (not truncating) anything larger, to avoid silent corruption.

---

## 8. Milestones

| Phase | Deliverable | Depends on |
|---|---|---|
| 0 | Diagnostic: confirm plain `adb shell` (no su) clipboard read works on this ROM; confirm `su` read fails with the §5.3 SecurityException; capture both logcat traces | Nothing — do this first, before writing daemon code |
| 1 (MVP) | PC→phone push path: watcher + daemon + systemd unit + minimal Android-side `setPrimaryClip` receiver, launched over `adb shell` manually | Phase 0 confirms write path is unblocked (expected: yes, trivially) |
| 2 | Phone→PC read path: Option B shell-UID daemon, GET_CLIPBOARD polling or `PrimaryClipChangedListener` push, PC-side receiver | Phase 0's shell-identity confirmation |
| 3 | Bidirectional + loop prevention | Phases 1 & 2 |
| 4 | Boot-time persistence via root supervisor + privilege drop (§7.3.2) | Phase 3 stable; SELinux domain-transition question resolved |
| 5 (stretch) | Image clipboard, clipboard history, encryption over the ADB channel | Out of v1 scope |

---

## 9. Security Considerations

- **ADB TCP 5555 is plaintext after auth.** On a trusted home LAN this is an accepted risk, but should be revisited before this project grows (e.g., tunneling the forwarded port over something encrypted, or restricting 5555 to a specific interface/firewall rule rather than all interfaces).
- **The shell daemon is a privileged clipboard reader.** Scope it to do only clipboard I/O — resist the temptation to fold in other features here, specifically because shell-identity code is the one piece of this system operating under Android's own security restrictions rather than our own design choices, so it deserves the smallest possible surface area.
- **Clipboard contents are often sensitive** (OTP codes, passwords copied from a manager, etc.) — worth deciding in Phase 3 whether to add a simple content-based exclusion (e.g., skip sync if the source app flagged the clip `IS_SENSITIVE`, which Android already supports as an extra on `ClipData`).

---

## 10. Open Questions / Risks

1. **Unverified:** Does a root process that performs a real `setuid(2000)` before the Binder call pass the §5.3 ownership check without an explicit SELinux domain transition? Needs empirical test on this specific KernelSU build (Phase 4 blocker).
2. **Unverified:** Does Evolution X's ROM-level AOSP fork introduce any clipboard behavior differences from stock, beyond what's been seen on Pixel/Samsung/IQOO in the scrcpy issue tracker? Phase 0's diagnostic should surface this quickly if so.
3. Whether `wl-paste --watch` fires reliably and promptly on your specific Wayland compositor is untested as of this writing — flagged in §7.4, test early.
4. Build-vs-adopt decision on Shizuku (§5.7) is deliberately deferred, not resolved, in this PRD — revisit if the custom shell daemon proves harder to keep alive across reboots than expected.

---

## 11. Reference Links

- scrcpy clipboard protocol & issues: https://github.com/Genymobile/scrcpy/issues/1580, /pull/4127, /issues/6224, /issues/4614
- scrcpy control message docs: https://man.archlinux.org/man/extra/scrcpy/scrcpy.1.en, https://tangoadb.dev/next/scrcpy/control/clipboard/
- Android clipboard focus restriction (real-world dev hitting it): https://forum.developer.samsung.com/t/clipboard-access-from-foreground/27641
- `READ_CLIPBOARD_IN_BACKGROUND` CTS source: https://android.googlesource.com/platform/cts/+/680b05b92af%5E%21/
- `READ_CLIPBOARD_IN_BACKGROUND` not grantable via adb: https://github.com/TylerBuza/ClipboardVault/issues/1
- KernelSU SELinux domain internals: https://www.8ksec.io/android-selinux-internals-part-ii/, https://kernelsu.org/guide/app-profile.html
- Prior art, bidirectional shell-UID clipboard bridge: https://github.com/cocoonstack/scrcpy-rfb/pull/9, https://github.com/loomhand/cocoonbox/pull/64
- Shizuku: https://github.com/RikkaApps/Shizuku, https://github.com/RikkaApps/Shizuku-API
- ClipShare (Shizuku/root-based cross-platform clipboard sync, shipped reference): https://pub.dev/packages/clipshare_clipboard_listener
