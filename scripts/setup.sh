#!/usr/bin/env bash
# ==============================================================================
# Singularity: Comprehensive Automated Setup Script
# ==============================================================================
# Automates the complete setup for:
#   1. Host Daemon & CLI (Cargo release build, systemd service, linger, ADB dispatcher)
#   2. Biometric PAM Module (pam_singularity.so for sudo & greetd cold-boot login)
#   3. DMS Lockscreen & QML Focus Fix (dankshell PAM, persistent shell, focus patch)
#   4. Android Companion App (Gradle build with Java 21, ADB install, permissions)
#   5. Verification & Health Check
#
# Usage:
#   ./scripts/setup.sh [OPTIONS]
#
# Options:
#   --all         Run complete setup (Default if no flags given)
#   --host        Setup host daemon, CLI, systemd, and NetworkManager dispatcher
#   --pam         Build and install PAM module, configure sudo & greetd
#   --dms         Setup DMS lockscreen PAM and persistent shell with focus fix
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
NC='\033[0m' # No Color

# Base Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DESKTOP_DIR="${WORKSPACE_ROOT}/desktop"
ANDROID_DIR="${WORKSPACE_ROOT}/android"
TARGET_RELEASE="${WORKSPACE_ROOT}/target/release"
CURRENT_USER="${USER:-$(whoami)}"
USER_UID="$(id -u "${CURRENT_USER}")"
USER_HOME="${HOME:-/home/${CURRENT_USER}}"

# Flags
DO_HOST=false
DO_PAM=false
DO_DMS=false
DO_ANDROID=false
DO_VERIFY=false
ASSUME_YES=false

# ------------------------------------------------------------------------------
# Logging Functions
# ------------------------------------------------------------------------------
log_banner() {
    echo -e "\n${BOLD}${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${BLUE}║                  SINGULARITY SYSTEM SETUP                          ║${NC}"
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
# Prerequisites Check
# ------------------------------------------------------------------------------
check_prerequisites() {
    log_step "Checking system prerequisites..."

    local missing=()
    for cmd in cargo rustc adb systemctl loginctl; do
        if ! command -v "${cmd}" &>/dev/null; then
            missing+=("${cmd}")
        fi
    done

    if [ ${#missing[@]} -gt 0 ]; then
        log_error "Missing required tools: ${missing[*]}"
        log_info "Please install them via pacman: sudo pacman -S ${missing[*]}"
        exit 1
    fi

    log_success "Core build & system tools available."
}

# ------------------------------------------------------------------------------
# 1. Setup Host Daemon & CLI
# ------------------------------------------------------------------------------
setup_host() {
    log_step "1. Setting up Singularity Daemon & CLI..."

    log_info "Compiling singularity-daemon and singularity-cli in release mode..."
    cargo build --release --manifest-path "${WORKSPACE_ROOT}/Cargo.toml" -p singularity-daemon -p singularity-cli

    log_info "Installing binaries into ${USER_HOME}/.local/bin/..."
    mkdir -p "${USER_HOME}/.local/bin"
    ln -sf "${TARGET_RELEASE}/singularity-daemon" "${USER_HOME}/.local/bin/singularity-daemon"
    ln -sf "${TARGET_RELEASE}/singularity-cli" "${USER_HOME}/.local/bin/singularity-cli"

    log_info "Setting up systemd user service..."
    mkdir -p "${USER_HOME}/.config/systemd/user"
    cp "${DESKTOP_DIR}/src/singularity-daemon/singularity-daemon.service" "${USER_HOME}/.config/systemd/user/"

    log_info "Enabling systemd linger for ${CURRENT_USER} (ensures pre-login cold-boot daemon)..."
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

    # NetworkManager ADB Dispatcher
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
    log_success "NetworkManager Wi-Fi ADB dispatcher installed at ${dispatcher_path}."
}

# ------------------------------------------------------------------------------
# 2. Setup PAM Module (sudo & greetd)
# ------------------------------------------------------------------------------
setup_pam() {
    log_step "2. Building & Installing pam_singularity..."

    log_info "Compiling pam-singularity in release mode..."
    cargo build --release --manifest-path "${WORKSPACE_ROOT}/Cargo.toml" -p pam-singularity

    log_info "Installing to /usr/lib/security/pam_singularity.so..."
    sudo install -m 755 "${TARGET_RELEASE}/libpam_singularity.so" "/usr/lib/security/pam_singularity.so"
    log_success "Installed /usr/lib/security/pam_singularity.so."

    # Configure /etc/pam.d/sudo
    log_info "Configuring /etc/pam.d/sudo..."
    if ! grep -q "pam_singularity.so" /etc/pam.d/sudo 2>/dev/null; then
        sudo python3 -c '
path = "/etc/pam.d/sudo"
with open(path, "r") as f:
    lines = f.readlines()
# Place directly under header
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

    # Configure greetd (dms-greeter cold-boot login)
    log_info "Configuring greetd login integration..."
    sudo ln -sf /usr/lib/security/pam_singularity.so /usr/lib/security/pam_fprintd.so

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
# 3. Setup DMS Lockscreen & QML Focus Fix
# ------------------------------------------------------------------------------
setup_dms() {
    log_step "3. Configuring DankMaterialShell (DMS) Lockscreen & Focus Fix..."

    local dms_pam="${USER_HOME}/.local/state/DankMaterialShell/pam/dankshell"
    if [ -f "${dms_pam}" ]; then
        log_info "Adding pam_singularity.so to DMS lockscreen PAM stack..."
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
        log_success "DMS lockscreen PAM configured."
    fi

    # Persistent Shell Setup
    local custom_shell_dir="${USER_HOME}/.config/DankMaterialShell/shell"
    local runtime_shell_dir
    runtime_shell_dir="$(find "/run/user/${USER_UID}/danklinux-shell" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | head -n 1 || true)"

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

    # Ensure focus on lock and keystrokes
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
        # 3. Patch Lock.qml (Remove focus stealing from outer FocusScope)
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
# 4. Setup Android Companion App
# ------------------------------------------------------------------------------
setup_android() {
    log_step "4. Building & Deploying Android Companion App..."

    local java_home="/usr/lib/jvm/java-21-openjdk"
    if [ ! -d "${java_home}" ]; then
        java_home="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(which javac 2>/dev/null || which java)")")")}"
    fi

    log_info "Building Android APK with Java: ${java_home}..."
    (
        cd "${ANDROID_DIR}"
        JAVA_HOME="${java_home}" ./gradlew assembleDebug --no-daemon --no-configuration-cache
    )

    local apk_path="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
    if [ ! -f "${apk_path}" ]; then
        log_error "APK build output not found at ${apk_path}"
        return 1
    fi

    log_info "Checking connected ADB devices..."
    local adb_devices
    adb_devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"

    if [ -z "${adb_devices}" ]; then
        log_warn "No active ADB devices detected over USB or Wi-Fi."
        log_info "Make sure your phone has Wireless Debugging enabled on port 5555."
        log_info "Run: adb connect <PHONE_IP>:5555"
        log_info "APK built successfully at: ${apk_path}"
        return 0
    fi

    for dev in ${adb_devices}; do
        log_info "Installing APK onto device: ${dev}..."
        adb -s "${dev}" install -r "${apk_path}"
        log_info "Granting full-screen & alert window permissions..."
        adb -s "${dev}" shell cmd appops set com.kanhaji.singularity USE_FULL_SCREEN_INTENT allow 2>/dev/null || true
        adb -s "${dev}" shell cmd appops set com.kanhaji.singularity SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
        adb -s "${dev}" shell pm grant com.kanhaji.singularity android.permission.POST_NOTIFICATIONS 2>/dev/null || true

        log_info "Starting AppBridgeService on ${dev}..."
        adb -s "${dev}" shell am start-foreground-service -n com.kanhaji.singularity/.service.AppBridgeService 2>/dev/null || true
        log_success "Companion app deployed and running on ${dev}."
    done
}

# ------------------------------------------------------------------------------
# 5. Verification & Health Check
# ------------------------------------------------------------------------------
verify_installation() {
    log_step "5. Verifying Singularity Components..."

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

    # PAM Module
    echo -n "  • PAM Security Binary (/usr/lib/security/pam_singularity.so): "
    if [ -f "/usr/lib/security/pam_singularity.so" ]; then
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
    if [ -L "/usr/lib/security/pam_fprintd.so" ] && [ "$(readlink -f /usr/lib/security/pam_fprintd.so)" = "/usr/lib/security/pam_singularity.so" ]; then
        echo -e "${GREEN}CONFIGURED${NC}"
    else
        echo -e "${YELLOW}NOT SYMLINKED${NC}"
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

Singularity Automated System Setup Script.

Options:
  --all         Execute all setup steps (Daemon, PAM, DMS, Android, Verify)
  --host        Setup host daemon, CLI, systemd, and NetworkManager dispatcher
  --pam         Build and install PAM module, configure sudo & greetd
  --dms         Setup DMS lockscreen PAM and persistent shell with focus fix
  --android     Build, install, and configure Android companion app via ADB
  --verify      Run verification and health checks on all components
  -y, --yes     Assume "yes" to all prompts (non-interactive mode)
  -h, --help    Show this help message

Examples:
  ./scripts/setup.sh --all
  ./scripts/setup.sh --pam
  ./scripts/setup.sh --android -y
EOF
}

main() {
    log_banner

    if [ $# -eq 0 ]; then
        DO_HOST=true
        DO_PAM=true
        DO_DMS=true
        DO_ANDROID=true
        DO_VERIFY=true
    fi

    while [ $# -gt 0 ]; do
        case "$1" in
            --all)
                DO_HOST=true
                DO_PAM=true
                DO_DMS=true
                DO_ANDROID=true
                DO_VERIFY=true
                shift
                ;;
            --host)
                DO_HOST=true
                shift
                ;;
            --pam)
                DO_PAM=true
                shift
                ;;
            --dms)
                DO_DMS=true
                shift
                ;;
            --android)
                DO_ANDROID=true
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

    check_prerequisites

    if [ "${DO_HOST}" = true ]; then
        setup_host
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

    log_success "Singularity setup process completed successfully!"
}

main "$@"
