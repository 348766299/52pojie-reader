#!/bin/sh
# Gradle Wrapper start-up script (POSIX)
set -e

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# 注意：这里不能带引号字面量，否则 java 会把 "-Xmx64m" 当成主类名
# （Error: Could not find or load main class "-Xmx64m"）。
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
