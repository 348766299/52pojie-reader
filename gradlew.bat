@if "%DEBUG%"=="" @echo off
@rem Gradle Wrapper start-up script for Windows
setlocal

set "DIRNAME=%~dp0"
if "%DIRNAME%"=="" set "DIRNAME=."
set "APP_HOME=%DIRNAME%"
set "CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"

if defined JAVA_HOME (
    set "JAVACMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVACMD=java.exe"
)

set "DEFAULT_JVM_OPTS=-Xmx64m -Xms64m"

"%JAVACMD%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% ^
    "-Dorg.gradle.appname=gradlew" ^
    -classpath "%CLASSPATH%" ^
    org.gradle.wrapper.GradleWrapperMain %*

endlocal
