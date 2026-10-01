# MBB-Plugins 批量构建脚本
param(
    [string]$Bot = "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
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

Write-Host "全部插件构建完成。"
