#!/usr/bin/env bash
# Focus Productivity Tracker - macOS DMG Packager
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_DIR="$(cd "${BIN_DIR}/.." && pwd)"

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "Error: The macOS installer must be built on macOS." >&2
    exit 1
fi

command -v jpackage >/dev/null 2>&1 || { echo "Error: JDK 17+ with jpackage is required." >&2; exit 1; }

# Always package the current sources, even when a release JAR already exists.
MAVEN_ARGS=(-f "${PROJECT_DIR}/pom.xml" package -DskipTests)
if [[ -d "${PROJECT_DIR}/.m2repo" ]]; then
    MAVEN_ARGS+=("-Dmaven.repo.local=${PROJECT_DIR}/.m2repo")
fi
mvn "${MAVEN_ARGS[@]}"
JAR_FILE="${PROJECT_DIR}/productivity-app/target/productivity-app-1.0.0-SNAPSHOT.jar"

RESOURCE_DIR="${PROJECT_DIR}/packaging/resources"
BUILD_DIR="$(mktemp -d "${PROJECT_DIR}/productivity-app/target/mac-package.XXXXXX")"
trap 'rm -rf "${BUILD_DIR}"' EXIT
INPUT_DIR="${BUILD_DIR}/input"
OUTPUT_DIR="${BUILD_DIR}/output"
mkdir -p "${INPUT_DIR}" "${OUTPUT_DIR}"
cp "${JAR_FILE}" "${INPUT_DIR}/focus.jar"

# Generate high-resolution visual artwork if missing
if [[ ! -f "${PROJECT_DIR}/packaging/dmg-background.png" ]]; then
    echo "Generating DMG background artwork..."
    python3 "${PROJECT_DIR}/packaging/generate_dmg_background.py"
fi
if [[ ! -f "${RESOURCE_DIR}/Focus-background.tiff" ]]; then
    sips -s format tiff "${PROJECT_DIR}/packaging/dmg-background.png" --out "${RESOURCE_DIR}/Focus-background.tiff" >/dev/null 2>&1 || true
fi

# 1. Build Focus.app image bundle
echo "Building native Focus.app bundle..."
jpackage \
  --type app-image \
  --name Focus \
  --app-version 1.0.0 \
  --vendor "Focus Productivity" \
  --description "Personal productivity tracker" \
  --mac-package-identifier com.productivity.focus \
  --mac-package-name Focus \
  --mac-app-category public.app-category.productivity \
  --input "${INPUT_DIR}" \
  --main-jar focus.jar \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --java-options "-Dfocus.packaged=true" \
  --icon "${PROJECT_DIR}/packaging/icon.icns" \
  --resource-dir "${RESOURCE_DIR}" \
  --dest "${OUTPUT_DIR}"

# Build 1-Click GUI Installer Application
echo "Building automated 1-Click Install Focus.app..."
"${PROJECT_DIR}/packaging/build_mac_installer_app.sh"
cp -R "${PROJECT_DIR}/packaging/Install Focus.app" "${OUTPUT_DIR}/Install Focus.app"

# Archive standalone app
echo "Creating Focus-1.0.0-macOS.zip..."
(cd "${OUTPUT_DIR}" && zip -q -r -y "${BUILD_DIR}/Focus-1.0.0-macOS.zip" Focus.app)

cp "${JAR_FILE}" "${BUILD_DIR}/focus.jar"
mv -f "${BUILD_DIR}/focus.jar" "${BIN_DIR}/focus.jar"
mv -f "${BUILD_DIR}/Focus-1.0.0-macOS.zip" "${BIN_DIR}/mac/Focus-1.0.0-macOS.zip"

# 2. Build DMG
echo "Creating enhanced macOS DMG installer..."
jpackage \
  --type dmg \
  --app-image "${OUTPUT_DIR}" \
  --name Focus \
  --app-version 1.0.0 \
  --vendor "Focus Productivity" \
  --description "Personal productivity tracker" \
  --mac-package-identifier com.productivity.focus \
  --mac-package-name Focus \
  --mac-app-category public.app-category.productivity \
  --icon "${PROJECT_DIR}/packaging/icon.icns" \
  --resource-dir "${RESOURCE_DIR}" \
  --dest "${BUILD_DIR}"

if [[ -f "${BUILD_DIR}/Focus-1.0.0.dmg" ]]; then
    mv -f "${BUILD_DIR}/Focus-1.0.0.dmg" "${BIN_DIR}/mac/Focus-1.0.0.dmg"
elif [[ -f "${BUILD_DIR}/Focus.dmg" ]]; then
    mv -f "${BUILD_DIR}/Focus.dmg" "${BIN_DIR}/mac/Focus-1.0.0.dmg"
else
    echo "Error: jpackage did not produce a DMG." >&2
    exit 1
fi

echo "macOS Installer DMG created successfully at: ${BIN_DIR}/mac/Focus-1.0.0.dmg"
