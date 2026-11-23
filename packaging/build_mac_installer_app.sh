#!/usr/bin/env bash
# Focus Productivity Tracker - Builds the 1-Click "Install Focus.app" GUI installer
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

INSTALLER_APP_DIR="${PROJECT_DIR}/packaging/Install Focus.app"
CONTENTS_DIR="${INSTALLER_APP_DIR}/Contents"
MACOS_DIR="${CONTENTS_DIR}/MacOS"
RESOURCES_DIR="${CONTENTS_DIR}/Resources"

rm -rf "${INSTALLER_APP_DIR}"
mkdir -p "${MACOS_DIR}" "${RESOURCES_DIR}"

# 1. Copy App Icon
cp "${PROJECT_DIR}/packaging/icon.icns" "${RESOURCES_DIR}/Focus.icns"

# 2. Write Info.plist
cat <<'EOF' > "${CONTENTS_DIR}/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>installer</string>
    <key>CFBundleIconFile</key>
    <string>Focus</string>
    <key>CFBundleIdentifier</key>
    <string>com.productivity.focus.installer</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>Install Focus</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1.0.0</string>
    <key>LSMinimumSystemVersion</key>
    <string>11.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

# 3. Write native installer executable script
cat <<'EOF' > "${MACOS_DIR}/installer"
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PARENT_DIR="$(cd "${APP_DIR}/.." && pwd)"

SOURCE_APP="${PARENT_DIR}/Focus.app"
TARGET_APP="/Applications/Focus.app"
LAUNCHAGENT_DIR="${HOME}/Library/LaunchAgents"
PLIST_FILE="${LAUNCHAGENT_DIR}/com.productivity.focus.plist"
LOG_DIR="${HOME}/Library/Logs/Focus"

# Helper for displaying native dialogs
gui_dialog() {
    local prompt="$1"
    local title="$2"
    local buttons="$3"
    local def_btn="$4"
    local icon="$5"
    osascript 2>/dev/null <<APPLESCRIPT || echo "${def_btn}"
tell application "System Events"
    activate
    set theResult to button returned of (display dialog "$prompt" with title "$title" buttons $buttons default button "$def_btn" with icon $icon)
    return theResult
end tell
APPLESCRIPT
}

# Verify source app exists
if [[ ! -d "${SOURCE_APP}" ]]; then
    if [[ -d "/Applications/Focus.app" ]]; then
        SOURCE_APP="/Applications/Focus.app"
    else
        osascript -e 'tell application "System Events" to display dialog "Error: Focus.app not found in the installer image." with title "Focus Setup" buttons {"OK"} default button "OK" with icon stop' 2>/dev/null || true
        exit 1
    fi
fi

# 1. Check for Existing Installation
IS_INSTALLED=0
if [[ -d "${TARGET_APP}" ]] || [[ -f "${PLIST_FILE}" ]] || launchctl list 2>/dev/null | grep -q "com.productivity.focus"; then
    IS_INSTALLED=1
fi

if [[ ${IS_INSTALLED} -eq 1 ]]; then
    PROMPT="An existing installation of Focus Productivity Tracker was detected on your Mac.\n\nWould you like to delete the previous installation and perform a fresh install?\n\n• Click 'Delete & Fresh Install' to remove previous version and start fresh.\n• Click 'Upgrade' to keep existing settings and overwrite files.\n• Click 'Cancel' to abort installation."
    CHOICE=$(gui_dialog "${PROMPT}" "Previous Installation Detected" '{"Cancel", "Upgrade", "Delete & Fresh Install"}' "Delete & Fresh Install" "caution")

    if [[ "${CHOICE}" == "Cancel" ]]; then
        exit 0
    elif [[ "${CHOICE}" == "Delete & Fresh Install" ]]; then
        # Unload and stop existing service
        launchctl unload -w "${PLIST_FILE}" 2>/dev/null || true
        killall Focus 2>/dev/null || true
        killall -9 Focus 2>/dev/null || true
        rm -rf "${TARGET_APP}"
        rm -f "${PLIST_FILE}"
    elif [[ "${CHOICE}" == "Upgrade" ]]; then
        launchctl unload -w "${PLIST_FILE}" 2>/dev/null || true
        killall Focus 2>/dev/null || true
    fi
fi

# 2. Install Focus.app to /Applications
mkdir -p "/Applications"
rm -rf "${TARGET_APP}"
cp -R "${SOURCE_APP}" "${TARGET_APP}"

# 3. Automatically Configure and Start Background Service (LaunchAgent)
mkdir -p "${LAUNCHAGENT_DIR}" "${LOG_DIR}"

cat <<PLIST_CONTENT > "${PLIST_FILE}"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.productivity.focus</string>
    <key>ProgramArguments</key>
    <array>
        <string>/Applications/Focus.app/Contents/MacOS/Focus</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <dict>
        <key>SuccessfulExit</key>
        <false/>
        <key>Crashed</key>
        <true/>
    </dict>
    <key>StandardOutPath</key>
    <string>${LOG_DIR}/focus.launchd.log</string>
    <key>StandardErrorPath</key>
    <string>${LOG_DIR}/focus.launchd.error.log</string>
</dict>
</plist>
PLIST_CONTENT

chmod 644 "${PLIST_FILE}"

# Unload previous instance if needed, then load and start
launchctl unload -w "${PLIST_FILE}" 2>/dev/null || true
launchctl load -w "${PLIST_FILE}" 2>/dev/null || true

# 4. Open default web browser to the dashboard
sleep 1.5
open "http://localhost:8484" 2>/dev/null || true

# 5. Display completion confirmation
DONE_MSG="Focus Productivity Tracker has been installed successfully!\n\n• Location: /Applications/Focus.app\n• Background Service: Configured and running (starts on login)\n• Web Dashboard: Active at http://localhost:8484"
gui_dialog "${DONE_MSG}" "Installation Complete" '{"OK"}' "OK" "note" >/dev/null 2>&1 || true
EOF

chmod +x "${MACOS_DIR}/installer"
echo "✓ Created 1-Click Installer app: ${INSTALLER_APP_DIR}"
