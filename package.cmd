@echo off
rem Build a self-contained, distributable Windows app-image and zip it (see package.ps1 for details).
rem Keeps all Gradle downloads inside this folder, drives the JDK's own jpackage, then zips the result.
setlocal
set "GRADLE_USER_HOME=%~dp0.gradle-home"
call "%~dp0gradlew.bat" jpackageImage || goto :fail
powershell -NoProfile -Command "if (Test-Path '%~dp0dist\emoji-snake-windows.zip') { Remove-Item '%~dp0dist\emoji-snake-windows.zip' -Force }; Compress-Archive -Path '%~dp0dist\Emoji Snake' -DestinationPath '%~dp0dist\emoji-snake-windows.zip' -Force" || goto :fail
echo.
echo Done. Distributable: %~dp0dist\emoji-snake-windows.zip
echo Recipients unzip it and run "Emoji Snake\Emoji Snake.exe" - no Java install required.
endlocal
goto :eof
:fail
echo Packaging failed.
endlocal
exit /b 1
