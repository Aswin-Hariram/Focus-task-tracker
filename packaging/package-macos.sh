#!/bin/zsh
set -euo pipefail

exec "${0:A:h}/../bin/mac/package-dmg.sh" "$@"
