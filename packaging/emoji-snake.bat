@echo off
rem Cross-platform launcher (Windows). Needs a JDK or JRE 25 on PATH (java -version).
rem For a Windows build that needs NO Java installed, use the jpackage app-image instead (package.ps1).
java -cp "%~dp0..\lib\*" com.emojisnake.Launcher %*
