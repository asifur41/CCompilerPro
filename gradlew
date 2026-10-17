#!/bin/sh
# Launcher: uses the Gradle wrapper jar when present, otherwise falls back to `gradle` on PATH.
# (The binary gradle-wrapper.jar could not be generated offline; run `gradle wrapper --gradle-version 8.5` once to create it.)
APP_HOME=$(cd "$(dirname "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$JAR" ]; then
  exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "gradle-wrapper.jar missing and no 'gradle' on PATH. Install Gradle 8.5 and run: gradle wrapper --gradle-version 8.5" >&2
exit 1
