@echo off
rem Self-contained launcher: keeps ALL Gradle + JavaFX downloads inside this folder
rem (repo-local GRADLE_USER_HOME), so nothing touches your user profile or system.
setlocal
set "GRADLE_USER_HOME=%~dp0.gradle-home"
call "%~dp0gradlew.bat" run %*
endlocal
