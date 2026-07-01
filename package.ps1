# Build a self-contained, distributable Windows app-image and zip it.
# Self-contained launcher: keeps ALL Gradle downloads inside this folder (repo-local
# GRADLE_USER_HOME), then drives the JDK's own `jpackage` (no third-party plugins) and zips the
# result into dist\emoji-snake-windows.zip — the single file you hand to someone else.
#
#   .\package.ps1            # build dist\Emoji Snake\ + dist\emoji-snake-windows.zip
#   .\package.ps1 -RegenIcon # also regenerate art\snake.ico from art\snake.png first
param([switch]$RegenIcon)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if ($RegenIcon) {
    Write-Host "Regenerating art\snake.ico from art\snake.png ..."
    Add-Type -AssemblyName System.Drawing
    $img = [System.Drawing.Image]::FromFile((Join-Path $root 'art\snake.png'))
    $bmp = New-Object System.Drawing.Bitmap 256, 256
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($img, 0, 0, 256, 256)
    $pngTmp = New-Object System.IO.MemoryStream
    $bmp.Save($pngTmp, [System.Drawing.Imaging.ImageFormat]::Png)
    $png = $pngTmp.ToArray()
    $g.Dispose(); $bmp.Dispose(); $img.Dispose(); $pngTmp.Dispose()
    $n = $png.Length
    $header = [byte[]](0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 32, 0,
        ($n -band 0xFF), (($n -shr 8) -band 0xFF), (($n -shr 16) -band 0xFF), (($n -shr 24) -band 0xFF),
        22, 0, 0, 0)
    $ms = New-Object System.IO.MemoryStream
    $ms.Write($header, 0, $header.Length); $ms.Write($png, 0, $png.Length)
    [System.IO.File]::WriteAllBytes((Join-Path $root 'art\snake.ico'), $ms.ToArray())
    $ms.Dispose()
}

$env:GRADLE_USER_HOME = Join-Path $root '.gradle-home'
& (Join-Path $root 'gradlew.bat') jpackageImage
if ($LASTEXITCODE -ne 0) { throw "jpackageImage failed (exit $LASTEXITCODE)" }

$appImage = Join-Path $root 'dist\Emoji Snake'
$zip = Join-Path $root 'dist\emoji-snake-windows.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Write-Host "Zipping $appImage -> $zip ..."
Compress-Archive -Path $appImage -DestinationPath $zip -Force

# Also build the ONE cross-platform archive (Windows/macOS/Linux, needs a JDK 25 on the target).
& (Join-Path $root 'gradlew.bat') crossPlatformZip
if ($LASTEXITCODE -ne 0) { throw "crossPlatformZip failed (exit $LASTEXITCODE)" }
$xzip = Join-Path $root 'dist\emoji-snake-crossplatform.zip'

Write-Host ""
Write-Host "Done. Distributables:"
Write-Host "  $zip"
Write-Host "    -> Windows, NO Java needed: unzip, run 'Emoji Snake\Emoji Snake.exe'."
Write-Host "  $xzip"
Write-Host "    -> Windows/macOS/Linux, needs Java 25: unzip, run bin/emoji-snake (or bin\emoji-snake.bat)."
