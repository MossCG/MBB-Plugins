# MBB-Trpg 构建脚本
param(
    [string]$Bot = "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar",
    [switch]$Test
)

$ErrorActionPreference = "Stop"

# Windows PowerShell 5.1 在 Stop 策略下会把原生命令写到 stderr 的输出当成终止性错误
# （NativeCommandError），而 javac / java 在打印版本号或提示时就会写 stderr。
# 下面每处调用原生命令前临时放宽为 Continue，只按 $LASTEXITCODE 判定成败。
$nativeErrorAction = $ErrorActionPreference

$pluginName = "MBB-Trpg"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcJava = Join-Path $root "src\main\java"
$srcResources = Join-Path $root "src\main\resources"
$srcTestJava = Join-Path $root "src\test\java"
$outDir = Join-Path $root "out"
$buildDir = Join-Path $outDir "build"
$classesDir = Join-Path $buildDir "classes"
$testClassesDir = Join-Path $buildDir "test-classes"
$testLibsDir = Join-Path $outDir "test-libs"
$jarPath = Join-Path $outDir ($pluginName + ".jar")

Write-Host ("==== " + $pluginName + " 构建 ====")

if (-not (Test-Path -LiteralPath $Bot)) {
    Write-Host ("找不到 MoBoxBot.jar：" + $Bot)
    exit 1
}

$resolvedRoot = [System.IO.Path]::GetFullPath($root)
$resolvedBuild = [System.IO.Path]::GetFullPath($buildDir)
if (-not $resolvedBuild.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Host ("构建目录不在插件目录内，已中止：" + $resolvedBuild)
    exit 1
}

# Java 8 字节码：javac 9 及以上需要 --release 8，CI 的 javac 8 不认该参数
# javac 把版本号写到标准错误，借 cmd 合并两个流，避免 Windows PowerShell 5.1 误报 NativeCommandError
$javacVersionText = (& cmd /c "javac -version 2>&1" | Out-String).Trim()
$releaseArgs = @()
if ($javacVersionText -match "javac\s+(\d+)") {
    $javacMajor = [int]$Matches[1]
    if ($javacMajor -ge 9) {
        $releaseArgs = @("--release", "8", "-Xlint:-options")
    }
}
Write-Host ("编译工具：" + $javacVersionText)

if (Test-Path -LiteralPath $buildDir) { Remove-Item -LiteralPath $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$javaFiles = @()
if (Test-Path -LiteralPath $srcJava) {
    $javaFiles = @(Get-ChildItem -LiteralPath $srcJava -Recurse -File -Filter *.java | Select-Object -ExpandProperty FullName)
}
if ($javaFiles.Count -eq 0) {
    Write-Host ("没有找到 Java 源码：" + $srcJava)
    exit 1
}
Write-Host ("编译源码：" + $javaFiles.Count + " 个文件")
$ErrorActionPreference = "Continue"
& javac -encoding UTF-8 @releaseArgs -cp $Bot -d $classesDir $javaFiles
$compileExit = $LASTEXITCODE
$ErrorActionPreference = $nativeErrorAction
if ($compileExit -ne 0) {
    Write-Host "编译失败，已中止。"
    exit 1
}

if (Test-Path -LiteralPath $srcResources) {
    Copy-Item -Path (Join-Path $srcResources "*") -Destination $classesDir -Recurse -Force
}

if (Test-Path -LiteralPath $jarPath) { Remove-Item -LiteralPath $jarPath -Force }
Push-Location $classesDir
$ErrorActionPreference = "Continue"
& jar cf $jarPath .
$jarExit = $LASTEXITCODE
$ErrorActionPreference = $nativeErrorAction
Pop-Location
if ($jarExit -ne 0) {
    Write-Host "打包失败，已中止。"
    exit 1
}

$size = [math]::Round((Get-Item -LiteralPath $jarPath).Length / 1KB, 1)
Write-Host ("构建完成：" + $jarPath + "（" + $size + " KB）")

if (-not $Test) {
    exit 0
}

# ===== 测试载体：首次从 Maven Central 下载 junit / hamcrest 到 out/test-libs 并校验 sha1 =====
# 期望 sha1 直接取自 Maven Central 同名 .sha1 文件，下载后再取远端 .sha1 复核；
# 远端不可达时仍可用已登记 sha1 校验本地 jar（离线环境与受限网络），但下载失败一律构建失败。
Write-Host "==== 准备测试依赖 ===="

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$mavenBase = "https://repo1.maven.org/maven2"
$testLibs = @(
    @{ Name = "junit-4.13.2.jar";      Url = $mavenBase + "/junit/junit/4.13.2/junit-4.13.2.jar";              Sha1 = "8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12" },
    @{ Name = "hamcrest-core-1.3.jar"; Url = $mavenBase + "/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"; Sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0" }
)

New-Item -ItemType Directory -Force -Path $testLibsDir | Out-Null
$testJars = @()
foreach ($lib in $testLibs) {
    $target = Join-Path $testLibsDir $lib.Name
    if (Test-Path -LiteralPath $target) {
        $actual = (Get-FileHash -LiteralPath $target -Algorithm SHA1).Hash.ToLower()
        if ($actual -eq $lib.Sha1) {
            Write-Host ("已存在且校验通过：" + $target)
            $testJars += $target
            continue
        }
        Write-Host ("已存在的测试依赖校验失败，删除后重新下载：" + $target)
        Remove-Item -LiteralPath $target -Force
    }

    $tempFile = $target + ".part"
    # Maven Central 在 CI 共享出口上偶发 429 / 5xx：重试三次并退避，全部失败才判定构建失败。
    $attempts = 3
    $delays = @(5, 15)
    $downloaded = $false
    $lastError = ""
    for ($attempt = 1; $attempt -le $attempts; $attempt++) {
        Write-Host ("下载测试依赖（第 " + $attempt + "/" + $attempts + " 次）：" + $lib.Url)
        try {
            Invoke-WebRequest -Uri $lib.Url -OutFile $tempFile -UseBasicParsing -TimeoutSec 120
            $downloaded = $true
            break
        } catch {
            $lastError = $_.Exception.Message
            if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile -Force }
            if ($attempt -lt $attempts) {
                $delay = $delays[$attempt - 1]
                Write-Host ("下载失败，原因：" + $lastError + "；" + $delay + " 秒后重试。")
                Start-Sleep -Seconds $delay
            }
        }
    }
    if (-not $downloaded) {
        Write-Host ("下载测试依赖失败：" + $lib.Url)
        Write-Host ("原因：" + $lastError)
        Write-Host "请在可联网环境重试，或手工把 jar 放入 out\test-libs 后重跑（内置 sha1 校验会兜底）。"
        exit 1
    }

    $actual = (Get-FileHash -LiteralPath $tempFile -Algorithm SHA1).Hash.ToLower()
    if ($actual -ne $lib.Sha1) {
        Remove-Item -LiteralPath $tempFile -Force
        Write-Host ("测试依赖校验失败：" + $lib.Url)
        Write-Host ("期望 sha1：" + $lib.Sha1 + "，实际：" + $actual)
        exit 1
    }

    try {
        $remoteSha1 = ((Invoke-WebRequest -Uri ($lib.Url + ".sha1") -UseBasicParsing -TimeoutSec 60).Content | Out-String).Trim().ToLower()
        if ($remoteSha1 -ne $lib.Sha1) {
            Remove-Item -LiteralPath $tempFile -Force
            Write-Host ("远端 sha1 与内置登记值不一致：" + ($lib.Url + ".sha1"))
            Write-Host ("远端：" + $remoteSha1 + "，内置：" + $lib.Sha1)
            exit 1
        }
    } catch {
        Write-Host ("无法读取远端 sha1（" + ($lib.Url + ".sha1") + "），改用内置登记值校验通过。")
    }

    Move-Item -LiteralPath $tempFile -Destination $target -Force
    Write-Host ("已校验并入位：" + $target)
    $testJars += $target
}

if (-not (Test-Path -LiteralPath $srcTestJava)) {
    Write-Host ("没有找到测试源码目录：" + $srcTestJava)
    exit 1
}
$testJavaFiles = @(Get-ChildItem -LiteralPath $srcTestJava -Recurse -File -Filter *.java | Select-Object -ExpandProperty FullName)
if ($testJavaFiles.Count -eq 0) {
    Write-Host ("没有找到测试源码：" + $srcTestJava)
    exit 1
}

New-Item -ItemType Directory -Force -Path $testClassesDir | Out-Null
$testClassPath = $Bot + ";" + $classesDir + ";" + ($testJars -join ";")
Write-Host ("编译测试源码：" + $testJavaFiles.Count + " 个文件")
$ErrorActionPreference = "Continue"
& javac -encoding UTF-8 @releaseArgs -cp $testClassPath -d $testClassesDir $testJavaFiles
$testCompileExit = $LASTEXITCODE
$ErrorActionPreference = $nativeErrorAction
if ($testCompileExit -ne 0) {
    Write-Host "测试源码编译失败，已中止。"
    exit 1
}

$testClassNames = @()
foreach ($file in (Get-ChildItem -LiteralPath $testClassesDir -Recurse -File -Filter *.class)) {
    if ($file.Name -like '*$*') { continue }
    # 只跑测试类，测试基座（如 JdbcStorageFake）不参与运行
    if ($file.Name -notlike '*Test.class') { continue }
    $relative = $file.FullName.Substring($testClassesDir.Length).TrimStart('\', '/')
    $testClassNames += ($relative -replace '[\\/]', '.') -replace '\.class$', ''
}
if ($testClassNames.Count -eq 0) {
    Write-Host "没有找到可执行的测试类。"
    exit 1
}

$runClassPath = $testClassPath + ";" + $testClassesDir
Write-Host ("运行测试：" + ($testClassNames -join ", "))
$ErrorActionPreference = "Continue"
& java -cp $runClassPath org.junit.runner.JUnitCore @testClassNames
$testRunExit = $LASTEXITCODE
$ErrorActionPreference = $nativeErrorAction
if ($testRunExit -ne 0) {
    Write-Host "测试未通过，已中止。"
    exit $testRunExit
}
Write-Host ("测试全部通过：" + $testClassNames.Count + " 个测试类")
exit 0
