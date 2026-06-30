# Self-contained launcher: keeps ALL Gradle + JavaFX downloads inside this folder
# (repo-local GRADLE_USER_HOME), so nothing touches your user profile or system.
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
& (Join-Path $PSScriptRoot 'gradlew.bat') run @args
