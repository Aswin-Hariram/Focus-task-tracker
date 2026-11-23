# Focus Binary, Service, Update & Uninstall Executables (`bin/`)

This directory contains standalone executables, native installers, service managers, updaters, and uninstallers organized by operating system (`mac/`, `linux/`) with cross-platform root dispatchers.

The fat JAR, DMG, and macOS ZIP are generated locally. Build them using the [packaging instructions](../README.md#native-installers--executables).

## Directory Structure

```
bin/
├── focus                         # Universal cross-platform CLI dispatcher (run, start, stop, update, uninstall)
├── update                        # Universal cross-platform updater executable
├── uninstall                     # Universal cross-platform uninstaller executable
├── focus.jar                     # Repackaged executable Spring Boot fat JAR
├── README.md                     # Documentation
│
├── mac/                          # macOS Executables & Installers
│   ├── Focus-1.0.0.dmg           # Enhanced native macOS Disk Image installer (Retina styled)
│   ├── Focus-1.0.0-macOS.zip     # Standalone Focus.app bundle archive with embedded JRE
│   ├── Install Focus.app         # 1-Click Automated GUI installer (auto-service setup, fresh install prompt)
│   ├── Focus-Uninstall           # Native universal Mach-O (arm64 + x86_64) Uninstaller binary
│   ├── Focus-Update              # Native universal Mach-O (arm64 + x86_64) Updater binary
│   ├── uninstall                 # macOS uninstaller executable script
│   ├── update                    # macOS updater executable script
│   ├── focus                     # macOS CLI controller (run, start, stop, status, logs, update, uninstall)
│   ├── install-service.sh        # Registers & loads launchd LaunchAgent service
│   ├── uninstall-service.sh      # Unloads & removes launchd service
│   ├── com.productivity.focus.plist # launchd property list template
│   └── package-dmg.sh            # Builds enhanced macOS DMG package via jpackage & resources
│
└── linux/                        # Linux Executables & Services
    ├── uninstall                 # Linux uninstaller executable script
    ├── update                    # Linux updater executable script
    ├── focus                     # Linux CLI controller (run, start, stop, status, logs, update, uninstall)
    ├── install-service.sh        # Installs systemd service (supports --user or root)
    ├── uninstall-service.sh      # Stops & removes systemd service
    └── focus.service             # systemd unit definition template
```

---

## Universal CLI Dispatchers (`bin/`)

Universal dispatchers automatically detect the current OS (macOS, Linux, or Windows/Git-Bash/MSYS) and delegate seamlessly to the native platform tool:

```bash
# Manage application lifecycle:
./bin/focus {run|start|stop|restart|status|logs|update|uninstall}

# Standalone Update:
./bin/update [options]

# Standalone Uninstall:
./bin/uninstall [options]
```

---

## macOS Executables

### 1. Update Executables
- **Native Mach-O Binary:** [`bin/mac/Focus-Update`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/mac/Focus-Update) (universal arm64 + x86_64 binary; can be double-clicked or run in terminal)
- **Executable Script:** [`bin/mac/update`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/mac/update)

```bash
# Update from source (rebuilds with Maven and verifies health):
./bin/mac/update

# Update from a specific local file or remote release:
./bin/mac/update --file /path/to/focus.jar
./bin/mac/update --url https://releases.example.com/focus-1.1.0.jar

# Immediate rollback to the previous backup version:
./bin/mac/update --rollback
```

### 2. Uninstall Executables
- **Native Mach-O Binary:** [`bin/mac/Focus-Uninstall`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/mac/Focus-Uninstall) (universal arm64 + x86_64 binary)
- **Executable Script:** [`bin/mac/uninstall`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/mac/uninstall)

```bash
# Standard uninstall (safely removes services, app bundles, logs; preserves database & user data):
./bin/mac/uninstall

# Non-interactive standard uninstall:
./bin/mac/uninstall --force

# Complete purge (removes all database and productivity data):
./bin/mac/uninstall --purge
```

---

## Linux Executables

### 1. Update Executable
- **Path:** [`bin/linux/update`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/linux/update)

```bash
# Update from source and restart systemd/daemon:
./bin/linux/update

# Update from file or URL:
./bin/linux/update --file /tmp/focus-new.jar
./bin/linux/update --url https://releases.example.com/focus.jar

# Revert to backup:
./bin/linux/update --rollback
```

### 2. Uninstall Executable
- **Path:** [`bin/linux/uninstall`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/linux/uninstall)

```bash
# Standard uninstall (unloads systemd service, removes desktop entries & symlinks; keeps user data):
./bin/linux/uninstall

# Purge all data:
./bin/linux/uninstall --purge --force
```

---

## Enhanced Packaging Specifications (`packaging/`)

| Operating System | Installer Format | Builder Script / Specification | Key Enhancements |
| :--- | :--- | :--- | :--- |
| **macOS** | `.dmg` (Disk Image) | [`packaging/package-macos.sh`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/packaging/package-macos.sh)<br>[`bin/mac/package-dmg.sh`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/mac/package-dmg.sh) | • 1-Click `Install Focus.app` automated installer (Zero terminal commands)<br>• Detects prior installation & prompts for clean delete/fresh install<br>• Automatically configures & starts `launchd` background service (`RunAtLoad`)<br>• Self-registers background service on first app launch<br>• Automatically opens dashboard (http://localhost:8484)<br>• Retina @2x styled background artwork (`Focus-background.tiff`)<br>• Custom volume icon (`Focus-volume.icns`) & AppleScript geometry |
| **macOS** | `.zip` (App Image) | [`bin/mac/Focus-1.0.0-macOS.zip`](file:///Users/aswinhariram/My%20Creativity/Productivity_Management/personal-productivity-tracker/bin/mac/Focus-1.0.0-macOS.zip) | Standalone `Focus.app` bundle with embedded JRE runtime |
