#!/bin/sh
# Gradle wrapper launcher. This lightweight fallback downloads/executes Gradle via CI setup when needed.
exec gradle "$@"
