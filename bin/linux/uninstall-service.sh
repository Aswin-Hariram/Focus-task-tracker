#!/usr/bin/env bash
# Focus Productivity Tracker - Linux systemd Service Uninstaller
set -euo pipefail

USER_MODE=false
if [[ "${1:-}" == "--user" || "$(id -u)" != "0" ]]; then
    USER_MODE=true
fi

if [[ "${USER_MODE}" == "true" ]]; then
    TARGET_FILE="${HOME}/.config/systemd/user/focus.service"
    SYSTEMCTL="systemctl --user"
else
    TARGET_FILE="/etc/systemd/system/focus.service"
    SYSTEMCTL="systemctl"
fi

echo "=== Uninstalling Focus systemd service ==="

${SYSTEMCTL} stop focus 2>/dev/null || true
${SYSTEMCTL} disable focus 2>/dev/null || true

if [[ -f "${TARGET_FILE}" ]]; then
    rm -f "${TARGET_FILE}"
    echo "Removed ${TARGET_FILE}"
fi

${SYSTEMCTL} daemon-reload 2>/dev/null || true

echo "Focus systemd service has been completely uninstalled."
