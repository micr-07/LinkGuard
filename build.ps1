<#
  链接守卫 —— 纯命令行 Android 构建脚本（不依赖 Gradle / 网络）。

  依赖：
    * Android SDK build-tools 36.1.0（aapt2 / d8 / zipalign / apksigner / keytool）
    * Android SDK platform android-36.1（android.jar）
    * JDK 21（Android Studio 自带 jbr 即可）
    * Python 3（只用来把 classes.dex 追加进 APK，保证 resources.arsc 保持 STORED）

  用法：  powershell -ExecutionPolicy Bypass -File build.ps1
#>
param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jbr = "C:\Program Files\Android\Android Studio\jbr",
    [string]$Python = "python",
    [string]$Platform = "android-36.1",
    [string]$BuildTools = "36.1.0",
    [int]$MinSdk = 21,
    [int]$TargetSdk = 36
)

$ErrorActionPreference = 'Stop'

$Bt  = Join-Path $Sdk "build-tools\$BuildTools"
$Jar = Join-Path $Sdk "platforms\$Platform\android.jar"

foreach ($p in @($Bt, $Jar, (Join-Path $Jbr 'bin\javac.exe'))) {
    if (-not (Test-Path $p)) { throw "找不到必需组件: $p" }
}

$env:JAVA_HOME = $Jbr
$JavaC = Join-Path $Jbr 'bin\javac.exe'
$JarTool = Join-Path (Split-Path $JavaC -Parent) 'jar.exe'
$KeyTool = Join-Path $Jbr 'bin\keytool.exe'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$AppName   = '链接守卫'
$Version   = '2.1'
$VersionCode = 12
$OutName   = 'LinkGuard-v2.1'

Write-Host "=== 构建 $AppName $Version ===" -ForegroundColor Cyan

# ---------------------------------------------------------------- 清理
foreach ($d in @('gen', 'classes', 'dexout', 'build')) {
    if (Test-Path $d) { Remove-Item $d -Recurse -Force }
    New-Item -ItemType Directory -Path $d | Out-Null
}

# ---------------------------------------------------------------- 图标
if ($Python -and $Python -ne 'none') {
    Write-Host '[1/9] 生成图标'
    & $Python (Join-Path $Root 'tools\make_icon.py')
    if ($LASTEXITCODE -ne 0) { throw '图标生成失败' }
} else {
    Write-Host '[1/9] 跳过图标生成'
}

# ---------------------------------------------------------------- 资源
Write-Host '[2/9] aapt2 compile'
& (Join-Path $Bt 'aapt2.exe') compile --dir res -o build\res.zip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile 失败' }

Write-Host '[3/9] aapt2 link'
& (Join-Path $Bt 'aapt2.exe') link `
    -o build\base.apk `
    -I $Jar `
    -A assets `
    --manifest AndroidManifest.xml `
    --java gen `
    --min-sdk-version $MinSdk `
    --target-sdk-version $TargetSdk `
    --version-code $VersionCode `
    --version-name $Version `
    --no-version-vectors `
    build\res.zip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link 失败' }

# ---------------------------------------------------------------- 编译
Write-Host '[4/9] javac'
$srcs = @(Get-ChildItem -Recurse -Path src, gen -Filter *.java | ForEach-Object { $_.FullName })
& $JavaC -encoding UTF-8 -source 8 -target 8 -nowarn -bootclasspath $Jar -d classes @srcs
if ($LASTEXITCODE -ne 0) { throw 'javac 失败' }

Write-Host '[5/9] d8'
# class 文件多了以后直接列在命令行会超出 Windows 的 32K 限制（"The command line is too long"），
# 先打成 jar 再交给 d8。
& $JarTool cf build\classes.jar -C classes . | Out-Null
if ($LASTEXITCODE -ne 0) { throw '打包 classes.jar 失败' }
& (Join-Path $Bt 'd8.bat') --min-api $MinSdk --lib $Jar --output dexout build\classes.jar
if ($LASTEXITCODE -ne 0) { throw 'd8 失败' }

# ---------------------------------------------------------------- 打包
Write-Host '[6/9] 写入 classes.dex'
& $Python (Join-Path $Root 'tools\adddex.py') (Resolve-Path build\base.apk) (Resolve-Path dexout\classes.dex)
if ($LASTEXITCODE -ne 0) { throw '写入 dex 失败' }

Write-Host '[7/9] zipalign'
& (Join-Path $Bt 'zipalign.exe') -f -p 4 build\base.apk build\aligned.apk
if ($LASTEXITCODE -ne 0) { throw 'zipalign 失败' }

# ---------------------------------------------------------------- 签名
Write-Host '[8/9] 签名'
$KsDir = Join-Path $Root 'keystore'
$Ks = Join-Path $KsDir 'linkguard.jks'
$KsPass = 'linkguard2024'
$KsAlias = 'linkguard'
if (-not (Test-Path $Ks)) {
    New-Item -ItemType Directory -Force -Path $KsDir | Out-Null
    Write-Host '    生成新的签名证书…'
    & $KeyTool -genkeypair -v -keystore $Ks -alias $KsAlias `
        -keyalg RSA -keysize 2048 -validity 10950 `
        -storepass $KsPass -keypass $KsPass `
        -dname "CN=LinkGuard, O=LinkGuard, C=CN"
    if ($LASTEXITCODE -ne 0) { throw 'keytool 失败' }
    $info = @(
        '签名文件 : keystore/linkguard.jks',
        "别名     : $KsAlias",
        "密码     : $KsPass",
        '说明     : 请务必保留，后续升级安装必须使用同一签名。'
    ) -join "`r`n"
    Set-Content -Path (Join-Path $KsDir 'keystore-info.txt') -Value $info -Encoding UTF8
}

$Signed = "build\$OutName.apk"
& (Join-Path $Bt 'apksigner.bat') sign `
    --ks $Ks --ks-pass "pass:$KsPass" --key-pass "pass:$KsPass" `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out $Signed build\aligned.apk
if ($LASTEXITCODE -ne 0) { throw 'apksigner 失败' }

& (Join-Path $Bt 'apksigner.bat') verify --print-certs $Signed | Select-Object -First 4

# ---------------------------------------------------------------- 校验
Write-Host '[9/9] 校验'
& (Join-Path $Bt 'aapt2.exe') dump badging $Signed | Select-String -Pattern "^(package|application-label|launchable-activity|uses-permission)" | Select-Object -First 10
& (Join-Path $Bt 'zipalign.exe') -c -v 4 $Signed | Select-Object -Last 1

$final = Join-Path $Root "$OutName.apk"
Copy-Item $Signed $final -Force

Write-Host ''
Write-Host "构建完成: $final" -ForegroundColor Green
Get-Item $final | Select-Object FullName, Length, LastWriteTime
