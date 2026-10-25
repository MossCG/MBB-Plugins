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
$pluginVersions = [ordered]@{}
foreach ($plugin in $plugins) {
    $jarPath = Join-Path $plugin.FullName ("out\" + $plugin.Name + ".jar")
    if (-not (Test-Path -LiteralPath $jarPath)) {
        Write-Host ($plugin.Name + " 没有找到构建产物：" + $jarPath)
        exit 1
    }
    $descriptionPath = Join-Path $plugin.FullName "src\main\resources\plugin.json"
    if (-not (Test-Path -LiteralPath $descriptionPath)) {
        Write-Host ($plugin.Name + " 没有找到 plugin.json：" + $descriptionPath)
        exit 1
    }
    $descriptionText = [System.IO.File]::ReadAllText(
        (Resolve-Path -LiteralPath $descriptionPath).Path,
        (New-Object System.Text.UTF8Encoding($false))
    )
    $description = $descriptionText | ConvertFrom-Json
    $manifestName = ([string]$description.name).Trim()
    $manifestVersion = ([string]$description.version).Trim()
    if (-not $manifestName) {
        Write-Host ($plugin.Name + " 的 plugin.json 缺少 name。")
        exit 1
    }
    if (-not $manifestVersion) {
        Write-Host ($plugin.Name + " 的 plugin.json 缺少 version。")
        exit 1
    }
    if ($pluginVersions.Contains($manifestName)) {
        Write-Host ("插件名重复：" + $manifestName)
        exit 1
    }
    $hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Copy-Item -LiteralPath $jarPath -Destination $outputDir -Force
    $pluginVersions[$manifestName] = [ordered]@{
        version = $manifestVersion
        file = $plugin.Name + ".jar"
        sha256 = $hash
    }
    $copied++
}

$versionsManifest = [ordered]@{
    schema = 1
    source = "MBB-Plugins"
    generatedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    pluginCount = $pluginVersions.Count
    plugins = $pluginVersions
}
$versionsPath = Join-Path $outputDir "versions.json"
$versionsJson = $versionsManifest | ConvertTo-Json -Depth 8
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($versionsPath,$versionsJson,$utf8WithoutBom)

Write-Host ("全部插件构建完成，已复制 " + $copied + " 个 JAR 到：" + $outputDir)
Write-Host ("版本清单已生成：" + $versionsPath)
