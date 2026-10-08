# MBB-Leave build script
param(
    [string]$Bot = "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcJava = Join-Path $root "src\main\java"
$srcResources = Join-Path $root "src\main\resources"
$outDir = Join-Path $root "out"
$buildDir = Join-Path $outDir "build"
$classesDir = Join-Path $buildDir "classes"
$jarPath = Join-Path $outDir "MBB-Leave.jar"

Write-Host "==== MBB-Leave build ===="
if (-not (Test-Path -LiteralPath $Bot)) { Write-Host "MoBoxBot.jar not found: $Bot"; exit 1 }
if (Test-Path -LiteralPath $buildDir) { Remove-Item -LiteralPath $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$javaFiles = Get-ChildItem -LiteralPath $srcJava -Recurse -File -Filter *.java | Select-Object -ExpandProperty FullName
& javac -encoding UTF-8 -cp $Bot -d $classesDir $javaFiles
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item -Path (Join-Path $srcResources "*") -Destination $classesDir -Recurse -Force
if (Test-Path -LiteralPath $jarPath) { Remove-Item -LiteralPath $jarPath -Force }
Push-Location $classesDir
& jar cf $jarPath .
Pop-Location
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$size = [math]::Round((Get-Item -LiteralPath $jarPath).Length / 1KB,1)
Write-Host ("Build complete: " + $jarPath + " (" + $size + " KB)")
