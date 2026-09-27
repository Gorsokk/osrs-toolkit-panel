@echo off
REM Starts a RuneLite dev client with this plugin (gradlew runClient). Needs Java 11+.
REM Uses a JDK installed by IntelliJ in %USERPROFILE%\.jdks if JAVA_HOME isn't set.
cd /d "%~dp0"
if not defined JAVA_HOME (
  for /d %%J in ("%USERPROFILE%\.jdks\temurin-11*") do set "JAVA_HOME=%%~fJ"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradlew.bat runClient
