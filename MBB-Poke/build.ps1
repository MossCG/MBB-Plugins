# MBB-Poke 构建脚本
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
$jarPath = Join-Path $outDir "MBB-Poke.jar"

Write-Host "==== MBB-Poke 构建 ===="
if (-not (Test-Path -LiteralPath $Bot)) { Write-Host "找不到 MoBoxBot.jar：$Bot"; exit 1 }
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
Write-Host ("构建完成：" + $jarPath + "（" + $size + " KB）")
