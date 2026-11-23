#!/usr/bin/env bash
# Focus Productivity Tracker - macOS LaunchAgent Service Installer
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_DIR="$(cd "${BIN_DIR}/.." && pwd)"

PLIST_TEMPLATE="${SCRIPT_DIR}/com.productivity.focus.plist"
TARGET_DIR="${HOME}/Library/LaunchAgents"
TARGET_PLIST="${TARGET_DIR}/com.productivity.focus.plist"

JAVA_PATH="$(command -v java || true)"
if [[ -z "${JAVA_PATH}" ]]; then
    echo "Error: java not found in PATH. Please install Java 17+." >&2
    exit 1
fi

JAR_PATH="${BIN_DIR}/focus.jar"
if [[ ! -f "${JAR_PATH}" ]]; then
    JAR_PATH="${PROJECT_DIR}/productivity-app/target/productivity-app-1.0.0-SNAPSHOT.jar"
fi
if [[ ! -f "${JAR_PATH}" ]]; then
    echo "Error: Application JAR not found." >&2
    echo "Please run 'mvn package -DskipTests' first." >&2
    exit 1
fi

SERVER_PORT="${SERVER_PORT:-8484}"
EMBEDDED_POSTGRES_PORT="${EMBEDDED_POSTGRES_PORT:-5433}"
LOG_DIR="${HOME}/Library/Logs/Focus"

echo "=== Installing Focus as a macOS LaunchAgent ==="
echo "Java binary:       ${JAVA_PATH}"
echo "Jar location:      ${JAR_PATH}"
echo "Working dir:       ${PROJECT_DIR}"
echo "HTTP Port:         ${SERVER_PORT}"
echo "Database Port:     ${EMBEDDED_POSTGRES_PORT}"
echo "Target plist:      ${TARGET_PLIST}"
echo "Logs directory:    ${LOG_DIR}"

mkdir -p "${TARGET_DIR}" "${LOG_DIR}"

# If already loaded, unload first
if launchctl list 2>/dev/null | grep -q "com.productivity.focus"; then
    echo "Unloading existing service instance..."
    launchctl unload "${TARGET_PLIST}" 2>/dev/null || true
fi

# Generate target plist with resolved paths
sed \
    -e "s|{{JAVA_PATH}}|${JAVA_PATH}|g" \
    -e "s|{{JAR_PATH}}|${JAR_PATH}|g" \
    -e "s|{{WORKING_DIR}}|${PROJECT_DIR}|g" \
    -e "s|{{SERVER_PORT}}|${SERVER_PORT}|g" \
    -e "s|{{EMBEDDED_POSTGRES_PORT}}|${EMBEDDED_POSTGRES_PORT}|g" \
    -e "s|{{LOG_DIR}}|${LOG_DIR}|g" \
    "${PLIST_TEMPLATE}" > "${TARGET_PLIST}"

chmod 644 "${TARGET_PLIST}"

# Load and start LaunchAgent
launchctl load -w "${TARGET_PLIST}"

echo ""
echo "Focus LaunchAgent service installed and loaded successfully!"
echo "Web UI: http://localhost:${SERVER_PORT}"
echo "Logs:   ${LOG_DIR}/focus.launchd.log"
echo ""
echo "Management commands:"
echo "  Start:   launchctl start com.productivity.focus"
echo "  Stop:    launchctl stop com.productivity.focus"
echo "  Unload:  launchctl unload ${TARGET_PLIST}"
echo "  Remove:  ${SCRIPT_DIR}/uninstall-service.sh"
