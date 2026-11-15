#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_dir"
gradle_bin="${GRADLE_BIN:-gradle}"
"$gradle_bin" :app:assembleDebug "$@"
mkdir -p build
cp app/build/outputs/apk/debug/app-debug.apk build/Timetable-0.10.4.apk
