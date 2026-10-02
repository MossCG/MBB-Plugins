# MBB-Chat 构建脚本
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
$jarPath = Join-Path $outDir "MBB-Chat.jar"

Write-Host "==== MBB-Chat 构建 ===="

if (-not (Test-Path -LiteralPath $Bot)) {
    Write-Host "找不到 MoBoxBot.jar：$Bot"
    exit 1
}

$resolvedRoot = [System.IO.Path]::GetFullPath($root)
$resolvedBuild = [System.IO.Path]::GetFullPath($buildDir)
if (-not $resolvedBuild.StartsWith($resolvedRoot,[System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Host "构建目录不在插件目录内，已中止：$resolvedBuild"
    exit 1
}

if (Test-Path -LiteralPath $buildDir) { Remove-Item -LiteralPath $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$javaFiles = Get-ChildItem -LiteralPath $srcJava -Recurse -File -Filter *.java | Select-Object -ExpandProperty FullName
if (-not $javaFiles -or $javaFiles.Count -eq 0) {
    Write-Host "没有找到 Java 源码：$srcJava"
    exit 1
}
Write-Host ("编译源码：" + $javaFiles.Count + " 个文件")
& javac -encoding UTF-8 -cp $Bot -d $classesDir $javaFiles
if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败，已中止。"
    exit 1
}

Copy-Item -Path (Join-Path $srcResources "*") -Destination $classesDir -Recurse -Force

if (Test-Path -LiteralPath $jarPath) { Remove-Item -LiteralPath $jarPath -Force }
Push-Location $classesDir
& jar cf $jarPath .
Pop-Location
if ($LASTEXITCODE -ne 0) {
    Write-Host "打包失败，已中止。"
    exit 1
}

$size = [math]::Round((Get-Item -LiteralPath $jarPath).Length / 1KB,1)
Write-Host ("构建完成：" + $jarPath + "（" + $size + " KB）")
