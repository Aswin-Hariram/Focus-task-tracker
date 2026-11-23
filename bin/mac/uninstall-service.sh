#!/usr/bin/env bash
# Focus Productivity Tracker - macOS LaunchAgent Service Uninstaller
set -euo pipefail

TARGET_PLIST="${HOME}/Library/LaunchAgents/com.productivity.focus.plist"

echo "=== Uninstalling Focus macOS LaunchAgent ==="

if launchctl list 2>/dev/null | grep -q "com.productivity.focus"; then
    echo "Stopping and unloading service..."
    launchctl unload -w "${TARGET_PLIST}" 2>/dev/null || true
fi

if [[ -f "${TARGET_PLIST}" ]]; then
    rm -f "${TARGET_PLIST}"
    echo "Removed ${TARGET_PLIST}"
fi

echo "Focus LaunchAgent service has been completely uninstalled."
