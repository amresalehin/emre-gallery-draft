#!/bin/sh
# Gradle launcher fallback. Android Studio can also import and sync this project.
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Open this project in Android Studio and allow Gradle sync." >&2
exit 1
