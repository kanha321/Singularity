#!/usr/bin/env bash
# ==============================================================================
# Singularity: Automated Setup Script for Fedora Linux (Hyprland + DMS)
# ==============================================================================
# Automates the complete setup for Fedora Workstation / Spin:
#   1. System Prerequisites, DNF Repositories (RPM Fusion, COPR), & Tools
#   2. Host Daemon & CLI (Cargo release build, systemd service, linger, ADB dispatcher)
#   3. SELinux & Firewall Configuration (Ports 2829/udp, 8920/tcp, module contexts)
#   4. Biometric PAM Module (/usr/lib64/security/pam_singularity.so for sudo & greetd)
#   5. DMS Lockscreen & QML Focus Fix (dankshell PAM, persistent shell, focus patch)
#   6. Android Companion App (Gradle build with Java 21, ADB install, permissions)
#   7. Verification & Health Check
#
# Usage:
#   ./scripts/setup-fedora.sh [OPTIONS]
#
# Options:
#   --all         Run complete setup (Default if no flags given)
#   --deps        Install DNF packages, RPM Fusion, COPRs, and build tools
#   --host        Setup host daemon, CLI, systemd, and NetworkManager dispatcher
#   --pam         Build & install PAM module, configure sudo & greetd with SELinux
#   --dms         Setup DMS lockscreen PAM and persistent shell with focus fix
#   --firewall    Configure firewalld rules for discovery and file streaming
#   --android     Build, install, and configure Android companion app via ADB
#   --verify      Run verification and health checks on all components
#   -y, --yes     Assume "yes" to all prompts (non-interactive mode)
#   -h, --help    Show this help message
# ==============================================================================

set -euo pipefail

# ANSI Color Codes
BOLD='\033[1m'
DIM='\033[2m'
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# Base Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DESKTOP_DIR="${WORKSPACE_ROOT}/desktop"
ANDROID_DIR="${WORKSPACE_ROOT}/android"
TARGET_RELEASE="${WORKSPACE_ROOT}/target/release"
CURRENT_USER="${USER:-$(whoami)}"
USER_UID="$(id -u "${CURRENT_USER}")"
USER_HOME="${HOME:-/home/${CURRENT_USER}}"
PAM_LIB_DIR="/usr/lib64/security"

# Action Flags
DO_DEPS=false
DO_HOST=false
DO_PAM=false
DO_DMS=false
DO_FIREWALL=false
DO_ANDROID=false
DO_VERIFY=false
BUILD_APK=false
ASSUME_YES=false

# ------------------------------------------------------------------------------
# Logging Functions
# ------------------------------------------------------------------------------
log_banner() {
    echo -e "\n${BOLD}${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${BLUE}║             SINGULARITY FEDORA SYSTEM SETUP (WAYLAND)              ║${NC}"
    echo -e "${BOLD}${BLUE}╚════════════════════════════════════════════════════════════════════╝${NC}\n"
}

log_step() {
    echo -e "${BOLD}${MAGENTA}==>${NC} ${BOLD}$1${NC}"
}

log_info() {
    echo -e "  ${CYAN}ℹ${NC} $1"
}

log_success() {
    echo -e "  ${GREEN}✔${NC} $1"
}

log_warn() {
    echo -e "  ${YELLOW}⚠${NC} $1"
}

log_error() {
    echo -e "  ${RED}✖${NC} $1" >&2
}

prompt_confirm() {
    if [ "${ASSUME_YES}" = true ]; then
        return 0
    fi
    local prompt_msg="$1"
    echo -ne "  ${YELLOW}?${NC} ${prompt_msg} [Y/n]: "
    read -r response
    case "${response}" in
        [yY][eE][sS]|[yY]|"") return 0 ;;
        *) return 1 ;;
    esac
}

# ------------------------------------------------------------------------------
# 1. System Dependencies & Repositories (DNF, RPM Fusion, COPR)
# ------------------------------------------------------------------------------
install_dependencies() {
    log_step "1. Configuring Repositories & Installing Dependencies for Fedora..."

    if ! command -v dnf &>/dev/null; then
        log_error "This script is tailored for Fedora (dnf package manager not found)."
        return 1
    fi

    # Enable RPM Fusion Free & Nonfree (needed for full ffmpeg, scrcpy, akmod-v4l2loopback)
    log_info "Enabling RPM Fusion repositories..."
    local fedora_ver
    fedora_ver="$(rpm -E %fedora)"
    sudo dnf install -y \
        "https://mirrors.rpmfusion.org/free/fedora/rpmfusion-free-release-${fedora_ver}.noarch.rpm" \
        "https://mirrors.rpmfusion.org/nonfree/fedora/rpmfusion-nonfree-release-${fedora_ver}.noarch.rpm" \
        2>/dev/null || true

    # Core system packages
    log_info "Installing core toolchain, libraries, and runtime packages..."
    sudo dnf install -y \
        cargo rust gcc gcc-c++ clang make pam-devel pkgconf-pkg-config \
        android-tools systemd polkit ripgrep fd-find 7zip fuse3 curl \
        pipewire wireplumber pulseaudio-utils ffmpeg scrcpy \
        wl-clipboard zenity \
        akmod-v4l2loopback \
        policycoreutils-python-utils

    # Fix fd alias if only fdfind binary exists
    mkdir -p "${USER_HOME}/.local/bin"
    if ! command -v fd &>/dev/null && command -v fdfind &>/dev/null; then
        ln -sf "$(which fdfind)" "${USER_HOME}/.local/bin/fd"
        log_success "Aliased fdfind to ${USER_HOME}/.local/bin/fd."
    fi

    # Install madbfs for transparent storage mounting if missing
    if ! command -v madbfs &>/dev/null; then
        log_info "madbfs not found in repositories. Compiling via cargo..."
        cargo install madbfs --root "${USER_HOME}/.local" || {
            log_warn "madbfs cargo install failed; storage mounting can be configured later."
        }
    fi

    log_success "Fedora dependencies installed successfully."
}

# ------------------------------------------------------------------------------
# 2. Setup Host Daemon & CLI
# ------------------------------------------------------------------------------
setup_host() {
    log_step "2. Setting up Singularity Daemon & CLI..."

    log_info "Creating transparent storage mount directories (/mnt/phone and /mnt/root)..."
    sudo mkdir -p /mnt/phone /mnt/root
    sudo chown "${CURRENT_USER}:${CURRENT_USER}" /mnt/phone /mnt/root
    log_success "Created /mnt/phone and /mnt/root with proper ownership."

    # Allow non-root users in fuse.conf
    if [ -f "/etc/fuse.conf" ]; then
        if grep -q "^#user_allow_other" /etc/fuse.conf; then
            log_info "Uncommenting user_allow_other in /etc/fuse.conf..."
            sudo sed -i 's/^#user_allow_other/user_allow_other/' /etc/fuse.conf
        fi
    fi

    log_info "Compiling singularity-daemon and singularity-cli in release mode..."
    cargo build --release --manifest-path "${WORKSPACE_ROOT}/Cargo.toml" -p singularity-daemon -p singularity-cli

    log_info "Installing binaries into ${USER_HOME}/.local/bin/..."
    mkdir -p "${USER_HOME}/.local/bin"
    ln -sf "${TARGET_RELEASE}/singularity-daemon" "${USER_HOME}/.local/bin/singularity-daemon"
    ln -sf "${TARGET_RELEASE}/singularity-cli" "${USER_HOME}/.local/bin/singularity-cli"

    log_info "Setting up systemd user service..."
    mkdir -p "${USER_HOME}/.config/systemd/user"
    cp "${DESKTOP_DIR}/src/singularity-daemon/singularity-daemon.service" "${USER_HOME}/.config/systemd/user/"

    log_info "Enabling systemd linger for ${CURRENT_USER}..."
    if ! loginctl show-user "${CURRENT_USER}" 2>/dev/null | grep -q "Linger=yes"; then
        sudo loginctl enable-linger "${CURRENT_USER}"
        log_success "Linger enabled."
    else
        log_success "Linger already enabled."
    fi

    log_info "Starting singularity-daemon.service..."
    systemctl --user daemon-reload
    systemctl --user enable --now singularity-daemon.service
    log_success "singularity-daemon is active."

    # NetworkManager ADB Wi-Fi Dispatcher
    log_info "Configuring NetworkManager ADB Wi-Fi dispatcher..."
    local dispatcher_path="/etc/NetworkManager/dispatcher.d/90-adb-connect"
    sudo mkdir -p /etc/NetworkManager/dispatcher.d
    sudo tee "${dispatcher_path}" >/dev/null <<EOF
#!/bin/sh
U=${CURRENT_USER}
[ "\$2" = "up" ] || exit 0
case "\$1" in lo|docker*|veth*|virbr*) exit 0 ;; esac

for i in 1 2 3 4 5; do
  GW=\$(ip route show default dev "\$1" | awk '{print \$3; exit}')
  [ -n "\$GW" ] && break
  sleep 1
done
[ -z "\$GW" ] && exit 0

runuser -u "\$U" -- env HOME=/home/\$U adb disconnect >/dev/null 2>&1
timeout 10 runuser -u "\$U" -- env HOME=/home/\$U adb connect "\$GW:5555"
EOF
    sudo chmod 755 "${dispatcher_path}"

    # On Fedora, NetworkManager-dispatcher.service is disabled by default!
    log_info "Enabling NetworkManager-dispatcher.service..."
    sudo systemctl enable --now NetworkManager-dispatcher.service 2>/dev/null || true
    log_success "NetworkManager ADB dispatcher installed and service enabled."
}

# ------------------------------------------------------------------------------
# 3. SELinux & Firewall Configuration
# ------------------------------------------------------------------------------
configure_security() {
    log_step "3. Configuring Firewall & SELinux Policies..."

    # Firewalld Configuration
    if command -v firewall-cmd &>/dev/null && systemctl is-active --quiet firewalld 2>/dev/null; then
        log_info "Opening firewalld ports: 2829/udp (Discovery) and 8920/tcp (File Server)..."
        sudo firewall-cmd --add-port=2829/udp --permanent >/dev/null 2>&1 || true
        sudo firewall-cmd --add-port=8920/tcp --permanent >/dev/null 2>&1 || true
        sudo firewall-cmd --reload >/dev/null 2>&1 || true
        log_success "Firewalld rules updated."
    else
        log_info "Firewalld inactive or not installed; skipping firewall rules."
    fi

    # SELinux Booleans & Directory Contexts
    if command -v getenforce &>/dev/null && [ "$(getenforce)" != "Disabled" ]; then
        log_info "Configuring SELinux booleans for FUSE storage access..."
        sudo setsebool -P use_fusefs_home_dirs 1 2>/dev/null || true
        log_success "SELinux booleans configured."
    fi
}

# ------------------------------------------------------------------------------
# 4. Setup PAM Module (sudo & greetd) with Fedora 64-bit Directory & SELinux
# ------------------------------------------------------------------------------
setup_pam() {
    log_step "4. Building & Installing pam_singularity for Fedora..."

    log_info "Compiling pam-singularity in release mode..."
    cargo build --release --manifest-path "${WORKSPACE_ROOT}/Cargo.toml" -p pam-singularity

    # In Fedora x86_64, PAM modules must be in /usr/lib64/security/
    sudo mkdir -p "${PAM_LIB_DIR}"
    log_info "Installing to ${PAM_LIB_DIR}/pam_singularity.so..."
    sudo install -m 755 "${TARGET_RELEASE}/libpam_singularity.so" "${PAM_LIB_DIR}/pam_singularity.so"

    # Crucial on Fedora: Restore SELinux security context so sudo/greetd can load the module
    if command -v restorecon &>/dev/null; then
        log_info "Restoring SELinux context on ${PAM_LIB_DIR}/pam_singularity.so..."
        sudo restorecon -v "${PAM_LIB_DIR}/pam_singularity.so" 2>/dev/null || true
    fi
    log_success "Installed and labeled ${PAM_LIB_DIR}/pam_singularity.so."

    # Configure /etc/pam.d/sudo
    log_info "Configuring /etc/pam.d/sudo..."
    if ! grep -q "pam_singularity.so" /etc/pam.d/sudo 2>/dev/null; then
        sudo python3 -c '
path = "/etc/pam.d/sudo"
with open(path, "r") as f:
    lines = f.readlines()
idx = 0
for i, line in enumerate(lines):
    if line.strip().startswith("#%PAM"):
        idx = i + 1
        break
lines.insert(idx, "auth        sufficient    pam_singularity.so\n")
with open(path, "w") as f:
    f.writelines(lines)
'
        log_success "Configured pam_singularity.so in /etc/pam.d/sudo."
    else
        log_success "pam_singularity.so already configured in /etc/pam.d/sudo."
    fi

    # Configure /etc/pam.d/hyprlock
    log_info "Configuring /etc/pam.d/hyprlock..."
    if [ ! -f "/etc/pam.d/hyprlock" ]; then
        sudo tee /etc/pam.d/hyprlock >/dev/null <<EOF
#%PAM-1.0
auth        sufficient    pam_singularity.so
auth        include       system-auth
account     include       system-auth
password    include       system-auth
session     include       system-auth
EOF
        log_success "Created /etc/pam.d/hyprlock with pam_singularity.so."
    elif ! grep -q "pam_singularity.so" /etc/pam.d/hyprlock 2>/dev/null; then
        sudo python3 -c '
path = "/etc/pam.d/hyprlock"
with open(path, "r") as f:
    lines = f.readlines()
idx = 0
for i, line in enumerate(lines):
    if line.strip().startswith("#%PAM"):
        idx = i + 1
        break
lines.insert(idx, "auth        sufficient    pam_singularity.so\n")
with open(path, "w") as f:
    f.writelines(lines)
'
        log_success "Configured pam_singularity.so in /etc/pam.d/hyprlock."
    else
        log_success "pam_singularity.so already configured in /etc/pam.d/hyprlock."
    fi

    # Configure greetd (dms-greeter cold-boot login)
    log_info "Configuring greetd login integration..."
    local fprint_pam="${PAM_LIB_DIR}/pam_fprintd.so"
    if [ -f "${fprint_pam}" ] && [ ! -L "${fprint_pam}" ]; then
        log_info "Backing up existing ${fprint_pam} to ${fprint_pam}.orig..."
        sudo mv "${fprint_pam}" "${fprint_pam}.orig"
    fi
    sudo ln -sf "${PAM_LIB_DIR}/pam_singularity.so" "${fprint_pam}"

    if [ -f "${USER_HOME}/.config/DankMaterialShell/settings.json" ]; then
        python3 -c '
import json
path = "'"${USER_HOME}"'/.config/DankMaterialShell/settings.json"
try:
    with open(path, "r") as f:
        data = json.load(f)
    if not data.get("greeterEnableFprint"):
        data["greeterEnableFprint"] = True
        with open(path, "w") as f:
            json.dump(data, f, indent=2)
        print("Enabled greeterEnableFprint in DMS settings")
except Exception as e:
    print(f"Warning: {e}")
'
    fi

    if command -v dms-greeter &>/dev/null; then
        log_info "Syncing dms-greeter PAM stack..."
        dms-greeter sync -a -y || true
        log_success "dms-greeter sync completed."
    fi
}

# ------------------------------------------------------------------------------
# 5. Setup DMS Lockscreen & QML Focus Fix
# ------------------------------------------------------------------------------
setup_dms() {
    log_step "5. Configuring DankMaterialShell (DMS) Lockscreen & Focus Fix..."

    local has_dms=false
    if command -v dms &>/dev/null || [ -d "${USER_HOME}/.local/state/DankMaterialShell" ] || [ -d "${USER_HOME}/.local/state/dms" ]; then
        has_dms=true
    fi

    if [ "${has_dms}" = false ]; then
        if [ "${DO_DMS_EXPLICIT}" = true ]; then
            log_info "DMS explicitly requested: enabling COPR and installing dms..."
            sudo dnf copr enable -y avengemedia/dms 2>/dev/null || true
            sudo dnf copr enable -y errornointernet/quickshell 2>/dev/null || true
            sudo dnf install -y dms dms-greeter || true
        else
            log_info "DMS (DankMaterialShell) not detected on this system; skipping DMS integration."
            log_info "Singularity uses native Wayland (hyprlock, swaylock) and systemd-logind locker adapters."
            return 0
        fi
    fi

    # Support both legacy and modern DMS state directories
    local dms_pam_paths=(
        "${USER_HOME}/.local/state/DankMaterialShell/pam/dankshell"
        "${USER_HOME}/.local/state/dms/pam/dankshell"
    )

    for dms_pam in "${dms_pam_paths[@]}"; do
        if [ -f "${dms_pam}" ]; then
            log_info "Adding pam_singularity.so to DMS lockscreen PAM (${dms_pam})..."
            python3 -c '
path = "'"${dms_pam}"'"
with open(path, "r") as f:
    content = f.read()
if "pam_singularity.so" not in content:
    target = "auth       [success=1 default=bad]     pam_unix.so"
    if target in content:
        content = content.replace(target, "auth       sufficient                  pam_singularity.so\n" + target)
        with open(path, "w") as f:
            f.write(content)
        print("Added pam_singularity.so to dankshell PAM")
'
            log_success "DMS lockscreen PAM configured at ${dms_pam}."
        fi
    done

    # Persistent Shell Setup
    local custom_shell_dir="${USER_HOME}/.config/DankMaterialShell/shell"
    local runtime_shell_dir
    runtime_shell_dir="$(find "/run/user/${USER_UID}" -maxdepth 2 -type d \( -name "*danklinux-shell*" -o -name "*dms-shell*" \) 2>/dev/null | head -n 1 || true)"

    if [ -n "${runtime_shell_dir}" ] && [ -d "${runtime_shell_dir}" ]; then
        log_info "Copying running DMS shell to persistent directory (${custom_shell_dir})..."
        mkdir -p "${custom_shell_dir}"
        cp -rn "${runtime_shell_dir}/"* "${custom_shell_dir}/"
        chmod -R u+w "${custom_shell_dir}"

        log_info "Applying password field auto-focus patch to DMS QML..."
        # 1. Patch LockSurface.qml
        python3 -c '
path = "'"${custom_shell_dir}"'/Modules/Lock/LockSurface.qml"
try:
    with open(path, "r") as f:
        c = f.read()
    if "lockContent.focusPasswordField()" not in c:
        c = c.replace(
            "Component.onCompleted: forceActiveFocus()",
            "Component.onCompleted: {\n        forceActiveFocus();\n        Qt.callLater(() => lockContent.focusPasswordField());\n    }"
        )
        c = c.replace(
            "onIsLockedChanged: {\n        if (isLocked) {\n            forceActiveFocus();\n            lockContent.resetLockState();",
            "onIsLockedChanged: {\n        if (isLocked) {\n            forceActiveFocus();\n            lockContent.resetLockState();\n            Qt.callLater(() => lockContent.focusPasswordField());"
        )
        c = c.replace(
            "if (videoScreensaver.active && videoScreensaver.inputEnabled) {\n            videoScreensaver.dismiss();\n            event.accepted = true;\n        }",
            "if (videoScreensaver.active && videoScreensaver.inputEnabled) {\n            videoScreensaver.dismiss();\n            event.accepted = true;\n            return;\n        }\n        lockContent.focusPasswordField();"
        )
        with open(path, "w") as f:
            f.write(c)
        print("Patched LockSurface.qml")
except Exception as e:
    print(f"Notice: {e}")
'
        # 2. Patch LockScreenContent.qml
        python3 -c '
path = "'"${custom_shell_dir}"'/Modules/Lock/LockScreenContent.qml"
try:
    with open(path, "r") as f:
        c = f.read()
    if "if (!activeFocus) {\n                                forceActiveFocus();" not in c:
        c = c.replace(
            "cursorPosition = pos + value.length;\n                        }",
            "cursorPosition = pos + value.length;\n                            if (!activeFocus) {\n                                forceActiveFocus();\n                            }\n                        }"
        )
        c = c.replace(
            "Keys.onPressed: event => {\n                                passwordField.handleKey(event);",
            "Keys.onPressed: event => {\n                                if (!passwordField.activeFocus) {\n                                    passwordField.forceActiveFocus();\n                                }\n                                passwordField.handleKey(event);"
        )
        with open(path, "w") as f:
            f.write(c)
        print("Patched LockScreenContent.qml")
except Exception as e:
    print(f"Notice: {e}")
'
        # 3. Patch Lock.qml
        python3 -c '
path = "'"${custom_shell_dir}"'/Modules/Lock/Lock.qml"
try:
    with open(path, "r") as f:
        c = f.read()
    old_scope = "FocusScope {\n        anchors.fill: parent\n        focus: sessionLock.secure\n\n        Keys.onPressed: event => {"
    new_scope = "Item {\n        anchors.fill: parent\n\n        Keys.onPressed: event => {"
    if old_scope in c:
        c = c.replace(old_scope, new_scope)
        with open(path, "w") as f:
            f.write(c)
        print("Patched Lock.qml")
except Exception as e:
    print(f"Notice: {e}")
'
        # Configure persistent environment
        mkdir -p "${USER_HOME}/.config/environment.d"
        local env_file="${USER_HOME}/.config/environment.d/90-dms.conf"
        if ! grep -q "DMS_SHELL_DIR" "${env_file}" 2>/dev/null; then
            echo "DMS_SHELL_DIR=${custom_shell_dir}" >> "${env_file}"
        fi

        systemctl --user set-environment DMS_SHELL_DIR="${custom_shell_dir}" 2>/dev/null || true
        systemctl --user restart dms.service 2>/dev/null || true
        log_success "DMS persistent shell and keyboard focus fix active."
    else
        log_warn "Running DMS shell not found in /run/user/${USER_UID}/; skipping QML patch."
    fi
}

# ------------------------------------------------------------------------------
# 6. Setup Android Companion App
# ------------------------------------------------------------------------------
setup_android() {
    log_step "6. Deploying Android Companion App..."

    local apk_path="${WORKSPACE_ROOT}/assets/singularity.apk"

    if [ "${BUILD_APK}" = true ]; then
        log_info "Compiling Android APK from source (--build-apk requested)..."
        local java_home="/usr/lib/jvm/java-21-openjdk"
        if [ ! -d "${java_home}" ]; then
            java_home="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(which javac 2>/dev/null || which java)")")")}"
        fi

        log_info "Building Android APK with Java: ${java_home}..."
        (
            cd "${ANDROID_DIR}"
            JAVA_HOME="${java_home}" ./gradlew assembleDebug --no-daemon --no-configuration-cache
        )
        apk_path="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
        if [ ! -f "${apk_path}" ]; then
            log_error "APK build output not found at ${apk_path}"
            return 1
        fi
        log_success "APK built successfully from source: ${apk_path}"
    elif [ ! -f "${apk_path}" ]; then
        if [ -f "${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk" ]; then
            apk_path="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
            log_info "Using pre-existing local APK: ${apk_path}"
        else
            log_info "Pre-built APK not found locally. Downloading from GitHub Release v0.2.0..."
            mkdir -p "${WORKSPACE_ROOT}/assets"
            if curl -fL --progress-bar -o "${apk_path}" "https://github.com/kanha321/Singularity/releases/download/v0.2.0/singularity.apk"; then
                log_success "Downloaded companion APK: ${apk_path}"
            else
                log_error "Failed to download companion APK from GitHub."
                log_info "You can build from source using --build-apk (requires JDK 21 and Android SDK)."
                return 1
            fi
        fi
    else
        log_info "Using pre-built APK: ${apk_path}"
    fi

    adb start-server >/dev/null 2>&1 || true

    echo ""
    log_info "${BOLD}Please prepare your Android phone for setup:${NC}"
    echo -e "      1. Connect your phone to this PC via ${BOLD}USB cable${NC} (or ensure it is on the same Wi-Fi)."
    echo -e "      2. Enable ${BOLD}Developer Options${NC} (Settings > About Phone > Tap 'Build Number' 7 times)."
    echo -e "      3. Enable ${BOLD}USB Debugging${NC} in Developer Options."
    echo -e "      4. Check your phone screen and tap ${BOLD}'Allow USB debugging'${NC} (check 'Always allow')."
    echo ""

    local target_dev=""
    local timeout=20

    while true; do
        log_info "Waiting for connected Android device (timeout: ${timeout}s)..."
        local elapsed=0
        local last_unauth_warn=0

        while [ "${elapsed}" -lt "${timeout}" ]; do
            local dev_list
            dev_list="$(adb devices 2>/dev/null | sed '1d' | grep -v '^$' || true)"

            # Check for authorized device
            local found_dev
            found_dev="$(echo "${dev_list}" | awk '$2=="device" {print $1; exit}')"

            if [ -n "${found_dev}" ]; then
                target_dev="${found_dev}"
                log_success "Authorized Android device detected: ${target_dev}"
                break 2
            fi

            # Check for unauthorized / offline status
            local unauth_dev
            unauth_dev="$(echo "${dev_list}" | awk '$2=="unauthorized" {print $1; exit}')"
            local offline_dev
            offline_dev="$(echo "${dev_list}" | awk '$2=="offline" {print $1; exit}')"

            if [ -n "${unauth_dev}" ]; then
                if [ $((elapsed - last_unauth_warn)) -ge 5 ]; then
                    log_warn "Device '${unauth_dev}' is UNAUTHORIZED."
                    echo -e "         ${CYAN}👉 Unlock your phone screen now and tap 'Allow' on the USB debugging prompt!${NC}"
                    last_unauth_warn=${elapsed}
                fi
            elif [ -n "${offline_dev}" ]; then
                log_info "Device '${offline_dev}' is offline. Waiting for handshake..."
            fi

            sleep 1
            elapsed=$((elapsed + 1))
        done

        log_warn "No authorized Android device detected within ${timeout} seconds."
        if [ "${ASSUME_YES}" = true ]; then
            log_warn "Non-interactive mode: Skipping Android companion deployment."
            return 0
        fi

        echo -e "\n  ${YELLOW}?${NC} What would you like to do?"
        echo -e "    ${BOLD}[r]${NC} Retry (wait another ${timeout} seconds)"
        echo -e "    ${BOLD}[s]${NC} Skip / Abort Android app setup for now"
        echo -ne "  ${YELLOW}Selection [r/S]: ${NC}"
        read -r choice
        case "${choice}" in
            [rR]|[rR][eE][tT][rR][yY])
                log_info "Retrying device discovery..."
                ;;
            *)
                log_info "Skipping Android deployment. You can install later using: ./setup-fedora.sh --android"
                return 0
                ;;
        esac
    done

    # Switch USB device to persistent Wi-Fi TCP/IP 5555
    if [[ "${target_dev}" != *":"* ]]; then
        log_info "Configuring Wireless Debugging port 5555 on ${target_dev}..."
        adb -s "${target_dev}" tcpip 5555 >/dev/null 2>&1 || true
        log_info "Restarting TCP server (brief disconnect)..."
        sleep 2

        log_info "Discovering phone Wi-Fi IP address..."
        local phone_ip
        phone_ip="$(adb -s "${target_dev}" shell "ip route" 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src" && $(i+1)!="127.0.0.1" && $(i+1)!~"^192\\.0\\.0") {print $(i+1); exit}}' || true)"
        if [ -z "${phone_ip}" ]; then
            phone_ip="$(adb -s "${target_dev}" shell "ip -o -4 addr show up" 2>/dev/null | grep -E "wlan|wifi|ap|swlan" | awk '{print $4}' | cut -d/ -f1 | head -n1 || true)"
        fi

        if [ -n "${phone_ip}" ]; then
            log_info "Discovered phone IP: ${phone_ip}. Connecting over Wi-Fi (port 5555)..."
            adb connect "${phone_ip}:5555" >/dev/null 2>&1 || true
            sleep 2
            if adb devices 2>/dev/null | grep -q "${phone_ip}:5555.*device"; then
                log_success "Connected to phone over Wi-Fi at ${phone_ip}:5555!"
                target_dev="${phone_ip}:5555"
            else
                log_warn "Wi-Fi connect pending; continuing installation over USB connection."
            fi
        else
            log_warn "Could not resolve phone Wi-Fi IP automatically; continuing over USB connection."
        fi
    fi

    # Install APK
    log_info "Installing companion APK onto ${target_dev}..."
    adb -s "${target_dev}" install -r "${apk_path}"
    log_success "APK successfully installed."

    # Grant required permissions
    log_info "Granting notification listener, full-screen alerts, and background permissions..."
    adb -s "${target_dev}" shell cmd appops set com.kanhaji.singularity USE_FULL_SCREEN_INTENT allow 2>/dev/null || true
    adb -s "${target_dev}" shell cmd appops set com.kanhaji.singularity SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
    adb -s "${target_dev}" shell pm grant com.kanhaji.singularity android.permission.POST_NOTIFICATIONS 2>/dev/null || true
    adb -s "${target_dev}" shell cmd notification allow_listener com.kanhaji.singularity/com.kanhaji.singularity.feature.media.session.SingularityMediaListenerService 2>/dev/null || true
    adb -s "${target_dev}" shell dumpsys deviceidle whitelist +com.kanhaji.singularity 2>/dev/null || true

    # Start AppBridgeService & launch MainActivity
    log_info "Launching Singularity AppBridgeService on ${target_dev}..."
    adb -s "${target_dev}" shell am start-foreground-service -n com.kanhaji.singularity/.service.AppBridgeService 2>/dev/null || true
    adb -s "${target_dev}" shell am start -n com.kanhaji.singularity/.MainActivity 2>/dev/null || true
    log_success "Companion app deployed, configured, and running on ${target_dev}."
}

# ------------------------------------------------------------------------------
# 7. Verification & Health Check
# ------------------------------------------------------------------------------
verify_installation() {
    log_step "7. Verifying Singularity Components on Fedora..."

    # Daemon
    echo -n "  • singularity-daemon.service: "
    if systemctl --user is-active --quiet singularity-daemon.service; then
        echo -e "${GREEN}RUNNING${NC}"
    else
        echo -e "${RED}INACTIVE${NC}"
    fi

    # UNIX Socket
    local sock_path="/run/user/${USER_UID}/singularity.sock"
    echo -n "  • IPC UNIX Domain Socket (${sock_path}): "
    if [ -S "${sock_path}" ]; then
        echo -e "${GREEN}ACTIVE${NC}"
    else
        echo -e "${RED}MISSING${NC}"
    fi

    # PAM Module (64-bit Fedora path)
    echo -n "  • PAM Security Binary (${PAM_LIB_DIR}/pam_singularity.so): "
    if [ -f "${PAM_LIB_DIR}/pam_singularity.so" ]; then
        echo -e "${GREEN}INSTALLED${NC}"
    else
        echo -e "${RED}MISSING${NC}"
    fi

    # Sudo PAM
    echo -n "  • Sudo PAM Integration (/etc/pam.d/sudo): "
    if grep -q "pam_singularity.so" /etc/pam.d/sudo 2>/dev/null; then
        echo -e "${GREEN}CONFIGURED${NC}"
    else
        echo -e "${RED}NOT FOUND${NC}"
    fi

    # Greeter Symlink
    echo -n "  • Greeter pam_fprintd.so Symlink: "
    if [ -L "${PAM_LIB_DIR}/pam_fprintd.so" ] && [ "$(readlink -f "${PAM_LIB_DIR}/pam_fprintd.so")" = "${PAM_LIB_DIR}/pam_singularity.so" ]; then
        echo -e "${GREEN}CONFIGURED${NC}"
    else
        echo -e "${YELLOW}NOT SYMLINKED${NC}"
    fi

    # NetworkManager Dispatcher Service
    echo -n "  • NetworkManager-dispatcher.service: "
    if systemctl is-active --quiet NetworkManager-dispatcher.service 2>/dev/null; then
        echo -e "${GREEN}ACTIVE${NC}"
    else
        echo -e "${YELLOW}INACTIVE${NC}"
    fi

    # Firewall Ports
    if command -v firewall-cmd &>/dev/null && systemctl is-active --quiet firewalld 2>/dev/null; then
        echo -n "  • Firewalld Ports (2829/udp, 8920/tcp): "
        if sudo firewall-cmd --list-ports 2>/dev/null | grep -q "2829/udp" && sudo firewall-cmd --list-ports 2>/dev/null | grep -q "8920/tcp"; then
            echo -e "${GREEN}OPEN${NC}"
        else
            echo -e "${YELLOW}NOT OPENED${NC}"
        fi
    fi

    # ADB Connected Device
    local dev_count
    dev_count="$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
    echo -n "  • Connected Android Devices: "
    if [ "${dev_count}" -gt 0 ]; then
        echo -e "${GREEN}${dev_count} online${NC}"
    else
        echo -e "${YELLOW}0 online (Wi-Fi ADB offline)${NC}"
    fi

    echo ""
    log_info "Test sudo with phone biometric:"
    echo -e "      ${BOLD}sudo -k && sudo echo 'Hello from Singularity'${NC}"
    log_info "Test DMS lockscreen unlock:"
    echo -e "      ${BOLD}dms ipc call lock lock${NC}"
}

# ------------------------------------------------------------------------------
# Argument Parsing
# ------------------------------------------------------------------------------
show_help() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Singularity Automated Setup Script for Fedora Linux (Hyprland, Niri, Sway, DMS).

Options:
  --all         Execute all setup steps (Default)
  --deps        Install DNF packages, RPM Fusion, COPRs, and build tools
  --host        Setup host daemon, CLI, systemd, and NetworkManager dispatcher
  --security    Configure firewalld rules and SELinux booleans
  --pam         Build and install PAM module into /usr/lib64/security
  --dms         Setup DMS lockscreen PAM and persistent shell with focus fix
  --android     Install and configure Android companion app via ADB (uses pre-built APK)
  --build-apk   Compile Android companion app from source with Gradle (requires JDK 21 & Android SDK)
  --verify      Run verification and health checks on all components
  -y, --yes     Assume "yes" to all prompts (non-interactive mode)
  -h, --help    Show this help message

Examples:
  ./setup-fedora.sh --all
  ./setup-fedora.sh --deps
  ./setup-fedora.sh --android
  ./setup-fedora.sh --android --build-apk
EOF
}

main() {
    log_banner

    if [ $# -eq 0 ]; then
        DO_DEPS=true
        DO_HOST=true
        DO_FIREWALL=true
        DO_PAM=true
        DO_DMS=true
        DO_ANDROID=true
        DO_VERIFY=true
    fi

    while [ $# -gt 0 ]; do
        case "$1" in
            --all)
                DO_DEPS=true
                DO_HOST=true
                DO_FIREWALL=true
                DO_PAM=true
                DO_DMS=true
                DO_ANDROID=true
                DO_VERIFY=true
                shift
                ;;
            --deps)
                DO_DEPS=true
                shift
                ;;
            --host)
                DO_HOST=true
                shift
                ;;
            --security|--firewall)
                DO_FIREWALL=true
                shift
                ;;
            --pam)
                DO_PAM=true
                shift
                ;;
            --dms)
                DO_DMS=true
                DO_DMS_EXPLICIT=true
                shift
                ;;
            --android)
                DO_ANDROID=true
                shift
                ;;
            --build-apk|--compile-android)
                DO_ANDROID=true
                BUILD_APK=true
                shift
                ;;
            --verify)
                DO_VERIFY=true
                shift
                ;;
            -y|--yes)
                ASSUME_YES=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done

    if [ "${DO_DEPS}" = true ]; then
        install_dependencies
    fi

    if [ "${DO_HOST}" = true ]; then
        setup_host
    fi

    if [ "${DO_FIREWALL}" = true ]; then
        configure_security
    fi

    if [ "${DO_PAM}" = true ]; then
        setup_pam
    fi

    if [ "${DO_DMS}" = true ]; then
        setup_dms
    fi

    if [ "${DO_ANDROID}" = true ]; then
        setup_android
    fi

    if [ "${DO_VERIFY}" = true ]; then
        verify_installation
    fi

    log_success "Singularity setup process for Fedora completed successfully!"
}

main "$@"

