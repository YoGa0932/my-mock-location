#!/bin/sh
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVACMD="java"
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
fi
cd "`dirname \"$0\"`"
APP_HOME="`pwd -P`"
exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"