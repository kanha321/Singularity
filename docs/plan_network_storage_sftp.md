# Singularity: Transparent Filesystem Mount (Root SFTP / FUSE) — Implementation Plan

**Feature ID:** `storage`  
**Ecosystem Catalog:** §4.6 (*Transparent Filesystem Mount*)  
**Architecture Layer:** `singularity-core` (Feature & RootBridge) $\to$ `singularity-daemon` (ADB Forward & sshfs Supervisor) $\to$ `singularity-protocol` / `singularity-client` $\to$ `singularity-cli`  
**Host Target:** Arch Linux (FUSE3 / `fusermount3` / `sshfs`)  
**Device Target:** Xiaomi POCO (Android 16 / Evolution X / KernelSU UID 0 / Built-in OpenSSH 9.0p1)  

---

## 1. Architectural Vision & Objective

Transform the connected Android phone into a high-performance, transparently mounted Linux filesystem accessible at `~/Phone` (or `/mnt/phone`):
- **Full Unrestricted Root Access:** Direct terminal access to the raw Linux filesystem (`/sdcard`, `/data/data`, `/`, `/system`).
- **Standard Desktop Integration:** Direct opening, editing, and saving of phone files in Neovim, VSCode, Dolphin, GIMP, and shell scripts (`rsync`, `find`, `cat`).
- **Complete Network Isolation:** The phone's SFTP server binds strictly to `127.0.0.1:2222`. No ports are exposed to local Wi-Fi or cellular networks. Communication occurs strictly over the authenticated ADB TCP tunnel.
- **Fail-Soft Lifecycle:** Auto-unmounts cleanly (`fusermount3 -u`) on device disconnect or daemon shutdown, leaving zero stale FUSE handles.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TRANSPARENT STORAGE FLOW                        │
│                                                                        │
│   Arch Linux (Host)                           Xiaomi POCO (Android 16) │
│   ┌─────────────────────┐                     ┌─────────────────────┐  │
│   │ Neovim / Dolphin /  │                     │ Flash Storage /     │  │
│   │ ~/Phone (FUSE Mount)│                     │ Kernel Filesystem   │  │
│   └──────────┬──────────┘                     └──────────▲──────────┘  │
│              │ (vfs calls)                               │ (raw I/O)   │
│   ┌──────────▼──────────┐                     ┌──────────┴──────────┐  │
│   │ sshfs (FUSE Client) │                     │ OpenSSH 9.0p1 sshd  │  │
│   │ /usr/bin/fusermount3│                     │ internal-sftp       │  │
│   └──────────┬──────────┘                     │ (KernelSU UID 0)    │  │
│              │ (SFTP stream)                  └──────────▲──────────┘  │
│   ┌──────────▼──────────┐                     ┌──────────┴──────────┐  │
│   │ 127.0.0.1:<dyn_port>│ ──[ADB Forward]───► │ 127.0.0.1:2222      │  │
│   │ (adb forward)       │                     │ (loopback only)     │  │
│   └─────────────────────┘                     └─────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Empirical Device Capabilities & Android 16 Findings

Our live discovery audit on the connected POCO (`10.235.203.184:5555`, Evolution X / Android 16) established critical empirical constraints:
1. **KernelSU Root:** Fully operational (`uid=0(root) gid=0(root) context=u:r:su:s0`).
2. **AOSP OpenSSH vs Termux OpenSSH on Android 16:**
   - The pre-installed `/product/bin/sshd` in Evolution X crashes on Android 16 with:
     `Unable to shield host key ... accumulate_host_timing_secret: ssh_digest_start fatal()` due to a BoringSSL digest initialization mismatch.
   - **Working Alternative:** Termux is installed on the device with standard OpenSSH 10.2p1 and OpenSSL 3.6.1 at:
     - Binary: `/data/data/com.termux/files/usr/bin/sshd`
     - SFTP Subsystem: `/data/data/com.termux/files/usr/libexec/sftp-server`
     - Shared Libs: `/data/data/com.termux/files/usr/lib`
   - Running under `su -mm` with `LD_LIBRARY_PATH` and `-o "StrictModes no"` executes flawlessly as root (`UID 0`) in the global mount namespace.
3. **OpenSSH SFTP Path Resolution (Double-Slash Requirement):**
   - OpenSSH SFTP interprets `root@host:/path` relative to the SFTP user's home directory (`/data/data/com.termux/files/home`).
   - Absolute filesystem mounting requires the double-slash syntax: `root@127.0.0.1://data/media/0` or `root@127.0.0.1://`.
4. **Symlink Canonicalization:**
   - `/sdcard` on Android is a symlink to `/storage/self/primary`, which maps to `/data/media/0`. Pointing `sshfs` directly to `/data/media/0` eliminates symlink dereferencing overhead and latency.
5. **Host FUSE Engine:** Arch Linux has `fuse3` (`3.18.3-1`), `fusermount3`, and `sshfs` installed.

---

## 3. Privilege Matrix & Bridge Separation

Following `docs/architecture.md` §3 and §4:

| Component | Privilege Level | Responsibilities |
|---|---|---|
| **`RootBridge`** | **UID 0** (KernelSU) | • Provisions host ED25519 public key into `/data/ssh/authorized_keys`.<br>• Launches Termux `sshd` on `127.0.0.1:2222` with `sftp-server`.<br>• Monitors daemon PID and terminates it upon disconnect. |
| **`ShellBridge`** | **UID 2000** (ADB) | • Allocates dynamic local TCP port via `adb forward tcp:0 tcp:2222`.<br>• Tears down port forward on disconnect. |
| **`StorageFeature`** | **Safe Rust** (`singularity-core`) | • Manages user config (`~/Phone`, remote path, auto-mount).<br>• Tracks in-memory state (`is_mounted`, mount time, PID).<br>• Implements the `Feature` trait lifecycle (`on_connected`, `on_disconnected`, `on_command`). |
| **`StorageSupervisor`** | **Host Tokio Task** (`singularity-daemon`) | • Manages the host `sshfs` child process.<br>• Executes `fusermount3 -u` on disconnect and daemon shutdown to guarantee zero hung mount points. |

---

## 4. Security & Cryptographic Handshake

1. **Dedicated Keypair:**  
   The daemon ensures an isolated, unencrypted ED25519 keypair at:
   - Private key: `~/.config/singularity/storage_id_ed25519` (`0600`)
   - Public key: `~/.config/singularity/storage_id_ed25519.pub` (`0644`)
2. **Zero-Touch Device Authorization:**  
   Via `RootBridge.exec_su()`, the public key is appended to `/data/ssh/authorized_keys` with `chmod 600`.
3. **Loopback Lockdown:**  
   The device `sshd` command is invoked with:
   ```bash
   env LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib \
       /data/data/com.termux/files/usr/bin/sshd -D -p 2222 -h /data/ssh/ssh_host_ed25519_key \
       -o "ListenAddress 127.0.0.1" \
       -o "AuthorizedKeysFile /data/ssh/authorized_keys" \
       -o "StrictModes no" \
       -o "PasswordAuthentication no" \
       -o "Subsystem sftp /data/data/com.termux/files/usr/libexec/sftp-server"
   ```
   No packet can reach port 2222 from the Wi-Fi or cellular interface.
4. **Host Mount Execution:**  
   `sshfs` connects locally over the ADB tunnel with double-slash pathing:
   ```bash
   sshfs root@127.0.0.1://data/media/0 ~/Phone -p <dyn_port> \
       -o IdentityFile=~/.config/singularity/storage_id_ed25519 \
       -o StrictHostKeyChecking=no \
       -o UserKnownHostsFile=/dev/null \
       -o reconnect,ServerAliveInterval=15,ServerAliveCountMax=3,follow_symlinks
   ```

---

## 5. File Blueprint & Modularity (150-Line Limit)

Every file adheres strictly to the Thin Front-Door pattern and the 150-line limit:

```
desktop/src/
├── singularity-protocol/src/
│   └── status.rs                        # Added Storage IPC request/response variants
├── singularity-core/src/features/
│   ├── storage.rs                       # Front door: StorageFeature struct & Feature trait wiring (<80 lines)
│   └── storage/
│       ├── config.rs                    # StorageConfig: mount_point, remote_path, auto_mount (<50 lines)
│       ├── state.rs                     # StorageState: is_mounted, mount_point, PID (<50 lines)
│       └── handler.rs                   # Key generation, su provisioning & command dispatch (<90 lines)
├── singularity-daemon/src/
│   ├── storage.rs                       # Front door: StorageSupervisor (<40 lines)
│   └── storage/
│       ├── forward.rs                   # ADB tunnel forward tcp:0 tcp:2222 (<80 lines)
│       └── process.rs                   # sshfs spawn & fusermount3 teardown (<90 lines)
└── singularity-cli/src/
    └── main.rs                          # Added `singularity mount` & `singularity unmount` (<110 lines)
```

---

## 6. CLI Command UX

Users will be able to mount, inspect, and unmount phone storage instantly from the shell:

```bash
# 1. Mount device root to default ~/Phone
$ singularity mount
Mounted POCO filesystem at /home/kanhaji/Phone (root: /)

# 2. Or mount only the user media directory (/sdcard)
$ singularity mount /sdcard
Mounted POCO media storage at /home/kanhaji/Phone (root: /sdcard)

# 3. Check live status
$ singularity status
⚡ Singularity Daemon: Online

STATE FEATURE                  ID           DETAILS
----- ------------------------ ------------ --------------------
🟢    Universal Clipboard      clipboard    
🟢    Transparent Storage      storage      (Mounted: ~/Phone)
🟢    Desktop Calling          calling      

# 4. Clean unmount
$ singularity unmount
Unmounted /home/kanhaji/Phone
```

---

## 7. Execution Steps

1. **Step 1: Host Dependency Verification**
   - Verify `sshfs` presence on host (or instruct user `sudo pacman -S sshfs`).
2. **Step 2: Protocol Extensions**
   - Add `IpcRequest::MountStorage`, `IpcRequest::UnmountStorage`, `IpcRequest::GetStorageStatus` to `singularity-protocol`.
3. **Step 3: Core Storage Feature**
   - Implement `desktop/src/singularity-core/src/features/storage.rs` + `config.rs`, `state.rs`, `handler.rs`.
   - Add co-located unit tests verifying key generation, config serialization, and state transitions.
4. **Step 4: Daemon SFTP & sshfs Supervisor**
   - Implement `storage/forward.rs` and `storage/process.rs` in `singularity-daemon`.
   - Wire `StorageFeature` into daemon `FeatureRegistry`.
5. **Step 5: CLI Subcommands**
   - Add `singularity mount [path]` and `singularity unmount` to `singularity-cli`.
6. **Step 6: Empirical Hardware Verification**
   - Mount live POCO filesystem to `~/Phone`.
   - Test reading `/sdcard/DCIM` and writing a test file from terminal.
   - Test clean unmount and disconnect resilience.
