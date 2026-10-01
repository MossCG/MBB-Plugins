# MBB-Plugins 批量构建脚本
param(
    [string]$Bot = "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$outputDir = Join-Path $root "out"
$plugins = Get-ChildItem -LiteralPath $root -Directory | Where-Object { $_.Name -like "MBB-*" } | Sort-Object Name

if (-not $plugins -or $plugins.Count -eq 0) {
    Write-Host "没有找到 MBB-* 插件目录。"
    exit 1
}

foreach ($plugin in $plugins) {
    $build = Join-Path $plugin.FullName "build.ps1"
    if (-not (Test-Path -LiteralPath $build)) {
        Write-Host ("跳过 " + $plugin.Name + "：没有 build.ps1")
        continue
    }
    Write-Host ("==== 构建 " + $plugin.Name + " ====")
    & powershell -NoProfile -ExecutionPolicy Bypass -File $build -Bot $Bot
    if ($LASTEXITCODE -ne 0) {
        Write-Host ($plugin.Name + " 构建失败，已中止。")
        exit $LASTEXITCODE
    }
}

if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
}
Get-ChildItem -LiteralPath $outputDir -File -Filter "MBB-*.jar" | Remove-Item -Force

$copied = 0
foreach ($plugin in $plugins) {
    $jarPath = Join-Path $plugin.FullName ("out\" + $plugin.Name + ".jar")
    if (-not (Test-Path -LiteralPath $jarPath)) {
        Write-Host ($plugin.Name + " 没有找到构建产物：" + $jarPath)
        exit 1
    }
    Copy-Item -LiteralPath $jarPath -Destination $outputDir -Force
    $copied++
}

Write-Host ("全部插件构建完成，已复制 " + $copied + " 个 JAR 到：" + $outputDir)
