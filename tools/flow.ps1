# LinkGuard 端到端验收（适配新的白名单语义）
#   1. 先在设置里选好默认浏览器
#   2. 黑名单 → 命中后完全静默
#   3. 「始终」→ 只加入白名单、跟随默认浏览器，不再强制选浏览器
#   4. 应用屏蔽 → 该应用的链接一律拦下
param(
    [string]$Device = "127.0.0.1:16384",
    [string]$Adb = "D:\adb\platform-tools\adb.exe",
    [string]$Tmp = "C:\Users\micr07\AppData\Local\Temp\lgsh"
)
$PKG = "com.linkguard.browser"
if (-not (Test-Path $Tmp)) { New-Item -ItemType Directory -Force -Path $Tmp | Out-Null }

function Sh([string]$c) { & $Adb -s $Device shell $c 2>&1 | Out-String }

function Get-Disp {
    $o = Sh "dumpsys window displays"; $cur = $null
    foreach ($l in ($o -split "`n")) {
        if ($l -match 'Display:\s*mDisplayId=(\d+)') { $cur = $matches[1] }
        if ($l -match 'mCurrentFocus=' -and $l -match $PKG) { return $cur }
    }
    return $null
}

function Dump([string]$name) {
    $local = Join-Path $Tmp "$name.xml"
    Remove-Item $local -Force -ErrorAction SilentlyContinue
    for ($i = 1; $i -le 4; $i++) {
        if ((Sh "uiautomator dump /sdcard/$name.xml") -match "dumped") {
            & $Adb -s $Device pull "/sdcard/$name.xml" $local 2>&1 | Out-Null
            if (Test-Path $local) { return (Get-Content -Raw -Encoding UTF8 $local) }
        }
        Start-Sleep -Milliseconds 900
    }
    return ""
}

function Texts { return ([regex]::Matches((Dump "fw"), 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' }) }

function Tap-Prefix([string]$prefix) {
    for ($try = 1; $try -le 3; $try++) {
        $xml = Dump "fw"
        foreach ($m in [regex]::Matches($xml, '<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"')) {
            if ($m.Groups[1].Value -like "$prefix*") {
                $cx = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
                $cy = [int](([int]$m.Groups[3].Value + [int]$m.Groups[5].Value) / 2)
                $disp = Get-Disp
                if (-not $disp) { return $false }
                Sh "input -d $disp tap $cx $cy" | Out-Null
                Start-Sleep -Seconds 2
                return $true
            }
        }
        Start-Sleep -Milliseconds 800
    }
    return $false
}

function Scroll-Down {
    $disp = Get-Disp
    if ($disp) { Sh "input -d $disp swipe 960 800 960 420 300" | Out-Null; Start-Sleep -Milliseconds 900 }
}

# 所有 display 的焦点窗口拼起来判断，避免读到别的屏上的陈旧活动
function Focused {
    $o = Sh "dumpsys window displays"
    $acc = New-Object System.Collections.ArrayList
    foreach ($l in ($o -split "`n")) {
        if ($l -match 'mCurrentFocus=Window\{[^}]*\s(\S+/\S+)\}') { [void]$acc.Add($matches[1]) }
    }
    if ($acc.Count -eq 0) { return "?" }
    return ($acc -join " ")
}

function Wait-For([scriptblock]$cond, [int]$seconds = 10) {
    for ($i = 0; $i -lt ($seconds * 2); $i++) {
        if (& $cond) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Dialog-Showing {
    if ((Focused) -like "*InterceptActivity*") { return $true }
    return ((Texts) -contains "有应用想打开一个链接")
}

function Trigger([string]$url) {
    Sh "am force-stop $PKG" | Out-Null
    Start-Sleep -Milliseconds 700
    Sh "am start -n $PKG/.InterceptActivity -a android.intent.action.VIEW -d `"$url`"" | Out-Null
    [void](Wait-For { (Focused) -like "*InterceptActivity*" } 8)
    Start-Sleep -Milliseconds 900
}

$ok = 0; $bad = 0
function Check([string]$name, [bool]$pass, [string]$detail) {
    if ($pass) { $script:ok++ } else { $script:bad++ }
    $tag = if ($pass) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1}  {2}" -f $tag, $name, $detail)
}

Write-Host "=== LinkGuard 端到端验收 ==="
Sh "pm clear $PKG" | Out-Null
Start-Sleep -Seconds 1

# ---- 0. 在设置里选默认浏览器 ----
Sh "am start -n $PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 3
[void](Tap-Prefix "修改默认打开方式")
[void](Tap-Prefix "Chrome")
Start-Sleep -Seconds 1
$t = Texts
Check "0. 设置里选好默认浏览器 = Chrome" ([bool]($t | Where-Object { $_ -like "*默认使用：Chrome*" })) ("texts=" + ($t -join "|"))

# ---- 1. 黑名单 ----
Trigger "https://m.taobao.com/item/1"
Check "1. 触发链接弹出询问窗（显示根网址）" (Dialog-Showing) "url=m.taobao.com"
[void](Tap-Prefix "加入黑名单")
Start-Sleep -Seconds 2
Trigger "https://m.taobao.com/item/2"
Check "2. 黑名单网址被静默拦截（不再弹窗）" (-not (Dialog-Showing)) ("focused=" + (Focused))

# ---- 2. 白名单：点「始终」不再强制选浏览器 ----
# ---- 2. 「仅这一次」也应该直接用默认浏览器，不再问 ----
Trigger "https://m.qq.com/index"
$t = Texts
Check "3. 弹窗里写明将用哪个浏览器打开" ([bool]($t | Where-Object { $_ -like "*将用「Chrome」打开*" })) ("texts=" + ($t -join "|"))
[void](Tap-Prefix "仅这一次")
Start-Sleep -Seconds 3
$t = Texts
$askedBrowser = [bool]($t | Where-Object { $_ -like "*选一个只用于本次打开*" })
Check "4. 点「仅这一次」没有再弹浏览器选择" (-not $askedBrowser) ("texts=" + ($t -join "|"))
Check "5. 直接用默认浏览器 Chrome 打开" ((Focused) -like "*chrome*") ("focused=" + (Focused))

# 「仅这一次」不该记住任何东西：同一网址再触发，仍然要弹窗
Trigger "https://m.qq.com/other"
Check "6. 「仅这一次」没有写进名单（同一网址仍然弹窗）" (Dialog-Showing) ("dialog=" + (Dialog-Showing))
[void](Tap-Prefix "加入黑名单")   # 清掉，免得影响后面
Start-Sleep -Seconds 2

# ---- 3. 白名单：点「始终」同样不再强制选浏览器 ----
Trigger "https://m.bilibili.com/video/1"
Check "7. 另一域名弹出询问窗" (Dialog-Showing) "url=m.bilibili.com"
[void](Tap-Prefix "始终（加入白名单）")
Start-Sleep -Seconds 3
$t = Texts
$askedBrowser = [bool]($t | Where-Object { $_ -like "*选一个只用于本次打开*" })
Check "8. 点「始终」后没有再弹浏览器选择" (-not $askedBrowser) ("texts=" + ($t -join "|"))
Check "9. 直接用默认浏览器 Chrome 打开" ((Focused) -like "*chrome*") ("focused=" + (Focused))

Trigger "https://m.bilibili.com/video/2"
$f = Focused
Check "10. 白名单网址不再询问，直接开" ((-not (Dialog-Showing)) -and ($f -like "*chrome*")) ("dialog=" + (Dialog-Showing) + " focused=" + $f)

# ---- 4. 应用屏蔽 ----
Trigger "https://www.zhihu.com/question/1"
Check "11. 第三个域名弹出询问窗" (Dialog-Showing) "url=zhihu.com"
[void](Tap-Prefix "屏蔽「Shell」")
Start-Sleep -Seconds 2
Trigger "https://www.zhihu.com/question/2"
Check "12. 被屏蔽应用的链接一律拦下" (-not (Dialog-Showing)) ("focused=" + (Focused))

# ---- 5. 三份名单的计数 ----
Sh "am start -n $PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 3
[void](Scroll-Down); [void](Scroll-Down)
$t = Texts
$joined = $t -join "|"
Check "13. 黑名单计数 = 2" ($joined -like "*黑名单（2）*") ("texts=" + $joined)
Check "14. 白名单计数 = 1" ($joined -like "*白名单（1）*") ""
Check "15. 已屏蔽的应用计数 = 1" ($joined -like "*已屏蔽的应用（1）*") ""

Write-Host ""
Write-Host ("=== {0} passed, {1} failed ===" -f $ok, $bad)
