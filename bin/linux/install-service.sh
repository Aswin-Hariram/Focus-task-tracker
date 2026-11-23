#!/usr/bin/env bash
# Focus Productivity Tracker - Linux systemd Service Installer
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_DIR="$(cd "${BIN_DIR}/.." && pwd)"

SERVICE_TEMPLATE="${SCRIPT_DIR}/focus.service"

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
STORAGE_ROOT="${PROJECT_DIR}/storage"
POSTGRES_DATA_DIR="${PROJECT_DIR}/postgres-data"

USER_MODE=false
if [[ "${1:-}" == "--user" || "$(id -u)" != "0" ]]; then
    USER_MODE=true
fi

if [[ "${USER_MODE}" == "true" ]]; then
    TARGET_DIR="${HOME}/.config/systemd/user"
    TARGET_FILE="${TARGET_DIR}/focus.service"
    SERVICE_USER="$(id -un)"
    SERVICE_GROUP="$(id -gn)"
    SYSTEMCTL="systemctl --user"
    mkdir -p "${TARGET_DIR}"
    echo "Installing as user service for ${SERVICE_USER}..."
else
    TARGET_DIR="/etc/systemd/system"
    TARGET_FILE="${TARGET_DIR}/focus.service"
    SERVICE_USER="${SERVICE_USER:-focus}"
    SERVICE_GROUP="${SERVICE_GROUP:-focus}"
    SYSTEMCTL="systemctl"
    if ! id -u "${SERVICE_USER}" >/dev/null 2>&1; then
        echo "Creating dedicated system user: ${SERVICE_USER}"
        useradd -r -s /bin/false -U -d "${PROJECT_DIR}" "${SERVICE_USER}" 2>/dev/null || true
    fi
    echo "Installing as system service..."
fi

mkdir -p "${STORAGE_ROOT}" "${POSTGRES_DATA_DIR}"

sed \
    -e "s|{{JAVA_PATH}}|${JAVA_PATH}|g" \
    -e "s|{{JAR_PATH}}|${JAR_PATH}|g" \
    -e "s|{{WORKING_DIR}}|${PROJECT_DIR}|g" \
    -e "s|{{SERVICE_USER}}|${SERVICE_USER}|g" \
    -e "s|{{SERVICE_GROUP}}|${SERVICE_GROUP}|g" \
    -e "s|{{SERVER_PORT}}|${SERVER_PORT}|g" \
    -e "s|{{EMBEDDED_POSTGRES_PORT}}|${EMBEDDED_POSTGRES_PORT}|g" \
    -e "s|{{STORAGE_ROOT}}|${STORAGE_ROOT}|g" \
    -e "s|{{POSTGRES_DATA_DIR}}|${POSTGRES_DATA_DIR}|g" \
    "${SERVICE_TEMPLATE}" > "${TARGET_FILE}"

echo "Wrote service unit to: ${TARGET_FILE}"

${SYSTEMCTL} daemon-reload
${SYSTEMCTL} enable --now focus

echo ""
echo "Focus systemd service installed and started successfully!"
echo "Web UI: http://localhost:${SERVER_PORT}"
echo ""
echo "Management commands:"
echo "  Status:  ${SYSTEMCTL} status focus"
echo "  Stop:    ${SYSTEMCTL} stop focus"
echo "  Restart: ${SYSTEMCTL} restart focus"
echo "  Logs:    journalctl ${USER_MODE:+--user} -u focus -f"
echo "  Remove:  ${SCRIPT_DIR}/uninstall-service.sh ${USER_MODE:+--user}"
